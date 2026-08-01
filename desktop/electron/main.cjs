const { app, BrowserWindow, dialog, ipcMain, net, protocol, safeStorage, session } = require("electron");
const { spawn } = require("node:child_process");
const crypto = require("node:crypto");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { pathToFileURL } = require("node:url");

const APPLICATION_SCHEME = "billing";
const APPLICATION_ORIGIN = `${APPLICATION_SCHEME}://app`;
const DEFAULT_API_BASE_URL = "http://127.0.0.1:8080";

let mainWindow;
let backendProcess;
const BACKUP_MAGIC = Buffer.from("SBK1");

protocol.registerSchemesAsPrivileged([
  {
    scheme: APPLICATION_SCHEME,
    privileges: {
      standard: true,
      secure: true,
      supportFetchAPI: true
    }
  }
]);

function getApiBaseUrl() {
  return process.env.BILLING_API_BASE_URL || DEFAULT_API_BASE_URL;
}

function getSessionFilePath() {
  return path.join(app.getPath("userData"), "secure-session.bin");
}

function assertTrustedIpcSender(event) {
  const senderUrl = event.senderFrame?.url || "";
  const developmentUrl = process.env.BILLING_RENDERER_URL;
  const trusted = developmentUrl
    ? senderUrl.startsWith(developmentUrl)
    : senderUrl.startsWith(APPLICATION_ORIGIN);

  if (!trusted) {
    throw new Error("Untrusted renderer.");
  }
}

function storeRefreshToken(rawToken) {
  if (!safeStorage.isEncryptionAvailable()) {
    throw new Error("Operating-system credential encryption is unavailable.");
  }

  const encrypted = safeStorage.encryptString(rawToken);
  const sessionPath = getSessionFilePath();
  fs.mkdirSync(path.dirname(sessionPath), { recursive: true });
  fs.writeFileSync(sessionPath, encrypted, { mode: 0o600 });
}

function loadRefreshToken() {
  const sessionPath = getSessionFilePath();
  if (!fs.existsSync(sessionPath) || !safeStorage.isEncryptionAvailable()) {
    return null;
  }

  try {
    return safeStorage.decryptString(fs.readFileSync(sessionPath));
  } catch (error) {
    console.error("Stored session could not be decrypted.", error);
    return null;
  }
}

function clearRefreshToken() {
  const sessionPath = getSessionFilePath();
  if (fs.existsSync(sessionPath)) {
    fs.unlinkSync(sessionPath);
  }
}

function databaseConfig() {
  const raw = process.env.BILLING_DB_URL || "jdbc:mysql://127.0.0.1:3306/billing";
  const parsed = new URL(raw.replace(/^jdbc:/, ""));
  return {
    host: parsed.hostname,
    port: parsed.port || "3306",
    database: parsed.pathname.replace(/^\//, ""),
    username: process.env.BILLING_DB_USERNAME || "billing_app",
    password: process.env.BILLING_DB_PASSWORD || ""
  };
}

function requireBackupPassword(value) {
  if (typeof value !== "string" || value.length < 12 || value.length > 256) {
    throw new Error("Backup password must contain 12 to 256 characters.");
  }
  return value;
}

function runDatabaseTool(executable, args, options = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(executable, args, {
      windowsHide: true,
      env: { ...process.env, MYSQL_PWD: databaseConfig().password },
      stdio: [options.stdinPath ? "pipe" : "ignore", options.stdoutPath ? "pipe" : "ignore", "pipe"]
    });
    const errors = [];
    child.stderr.on("data", (chunk) => errors.push(chunk));
    if (options.stdoutPath) child.stdout.pipe(fs.createWriteStream(options.stdoutPath, { mode: 0o600 }));
    if (options.stdinPath) fs.createReadStream(options.stdinPath).pipe(child.stdin);
    child.once("error", reject);
    child.once("close", (code) => code === 0 ? resolve() : reject(new Error(
      Buffer.concat(errors).toString("utf8").trim() || `${path.basename(executable)} exited with code ${code}.`
    )));
  });
}

function encryptFile(sourcePath, targetPath, password) {
  const salt = crypto.randomBytes(16);
  const iv = crypto.randomBytes(12);
  const key = crypto.pbkdf2Sync(password, salt, 310000, 32, "sha256");
  const cipher = crypto.createCipheriv("aes-256-gcm", key, iv);
  const encrypted = Buffer.concat([cipher.update(fs.readFileSync(sourcePath)), cipher.final()]);
  const tag = cipher.getAuthTag();
  fs.writeFileSync(targetPath, Buffer.concat([BACKUP_MAGIC, salt, iv, tag, encrypted]), { mode: 0o600 });
}

function decryptFile(sourcePath, targetPath, password) {
  const payload = fs.readFileSync(sourcePath);
  if (payload.length < 48 || !payload.subarray(0, 4).equals(BACKUP_MAGIC)) {
    throw new Error("This is not a Simplified Billing encrypted backup.");
  }
  const salt = payload.subarray(4, 20);
  const iv = payload.subarray(20, 32);
  const tag = payload.subarray(32, 48);
  const key = crypto.pbkdf2Sync(password, salt, 310000, 32, "sha256");
  const decipher = crypto.createDecipheriv("aes-256-gcm", key, iv);
  decipher.setAuthTag(tag);
  try {
    fs.writeFileSync(targetPath, Buffer.concat([decipher.update(payload.subarray(48)), decipher.final()]), { mode: 0o600 });
  } catch {
    throw new Error("Backup password is incorrect or the backup is damaged.");
  }
}

function backupStatusPath() { return path.join(app.getPath("userData"), "backup-status.json"); }
function recordBackupStatus(status) { fs.writeFileSync(backupStatusPath(), JSON.stringify(status), { mode: 0o600 }); }

async function createBackupToPath(targetPath, password) {
  const config = databaseConfig();
  if (!config.password) throw new Error("BILLING_DB_PASSWORD is required for backup.");
  const temporary = path.join(app.getPath("temp"), `billing-${crypto.randomUUID()}.sql`);
  try {
    await runDatabaseTool(process.env.BILLING_MYSQLDUMP || "mysqldump", [
      "--single-transaction", "--routines", "--triggers", "--set-gtid-purged=OFF",
      "--host", config.host, "--port", config.port, "--user", config.username,
      "--default-character-set=utf8mb4", config.database
    ], { stdoutPath: temporary });
    encryptFile(temporary, targetPath, password);
    const status = { successful: true, createdAt: new Date().toISOString(),
      fileName: path.basename(targetPath), size: fs.statSync(targetPath).size };
    recordBackupStatus(status);
    return status;
  } catch (error) {
    recordBackupStatus({ successful: false, failedAt: new Date().toISOString(), message: error.message });
    throw error;
  } finally {
    if (fs.existsSync(temporary)) fs.unlinkSync(temporary);
  }
}

async function stopBackendAsync() {
  if (!backendProcess || backendProcess.killed) return;
  const processToStop = backendProcess;
  await new Promise((resolve) => {
    const timer = setTimeout(resolve, 10000);
    processToStop.once("exit", () => { clearTimeout(timer); resolve(); });
    processToStop.kill();
  });
}

function readBackupStatus() {
  try { return JSON.parse(fs.readFileSync(backupStatusPath(), "utf8")); }
  catch { return null; }
}

function sanitizedDiagnostics() {
  const backendJar = resolveBackendJar();
  const logFile = process.env.BILLING_LOG_FILE || path.join(app.getPath("logs"), "billing-backend.log");
  let disk = null;
  try { const stats = fs.statfsSync(app.getPath("userData")); disk = { freeBytes: stats.bavail * stats.bsize, totalBytes: stats.blocks * stats.bsize }; } catch { }
  return {
    generatedAt: new Date().toISOString(), applicationVersion: app.getVersion(),
    platform: `${process.platform} ${os.release()} ${process.arch}`,
    packaged: app.isPackaged, apiBaseUrl: getApiBaseUrl(), backendManaged: Boolean(backendProcess),
    backendJarPresent: Boolean(backendJar && fs.existsSync(backendJar)),
    database: { host: databaseConfig().host, port: databaseConfig().port, database: databaseConfig().database },
    disk, logFilePresent: fs.existsSync(logFile), backup: readBackupStatus()
  };
}

function resolveJavaExecutable() {
  const executableName = process.platform === "win32" ? "java.exe" : "java";
  const packagedRuntime = path.join(process.resourcesPath, "runtime", "bin", executableName);
  return fs.existsSync(packagedRuntime) ? packagedRuntime : executableName;
}

function resolveBackendJar() {
  if (process.env.BILLING_BACKEND_JAR) {
    return path.resolve(process.env.BILLING_BACKEND_JAR);
  }

  if (app.isPackaged) {
    return path.join(process.resourcesPath, "backend", "billing-backend.jar");
  }

  return null;
}

function startBackendIfConfigured() {
  const backendJar = resolveBackendJar();
  if (!backendJar || !fs.existsSync(backendJar)) {
    return;
  }

  backendProcess = spawn(resolveJavaExecutable(), ["-jar", backendJar], {
    env: {
      ...process.env,
      BILLING_SERVER_ADDRESS: "127.0.0.1",
      BILLING_SERVER_PORT: new URL(getApiBaseUrl()).port || "8080"
    },
    windowsHide: true,
    stdio: app.isPackaged ? "ignore" : "inherit"
  });

  backendProcess.once("exit", (code, signal) => {
    if (!app.isQuitting && code !== 0) {
      console.error(`Billing backend stopped unexpectedly: code=${code}, signal=${signal}`);
    }
    backendProcess = undefined;
  });
}

function stopBackend() {
  if (backendProcess && !backendProcess.killed) {
    backendProcess.kill();
  }
}

function registerApplicationProtocol() {
  protocol.handle(APPLICATION_SCHEME, (request) => {
    const rendererRoot = path.resolve(__dirname, "..", "dist");
    const requestUrl = new URL(request.url);
    const requestedPath = decodeURIComponent(requestUrl.pathname).replace(/^\/+/, "");
    const candidate = path.resolve(rendererRoot, requestedPath || "index.html");
    const safePrefix = `${rendererRoot}${path.sep}`;
    const isSafe = candidate === path.join(rendererRoot, "index.html")
      || candidate.startsWith(safePrefix);

    if (!isSafe) {
      return new Response("Not found", { status: 404 });
    }

    const filePath = fs.existsSync(candidate) && fs.statSync(candidate).isFile()
      ? candidate
      : path.join(rendererRoot, "index.html");

    return net.fetch(pathToFileURL(filePath).toString());
  });
}

function createWindow() {
  mainWindow = new BrowserWindow({
    title: "Simplified Billing",
    width: 1440,
    height: 900,
    minWidth: 1180,
    minHeight: 720,
    show: false,
    backgroundColor: "#f8fafc",
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, "preload.cjs"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      devTools: !app.isPackaged
    }
  });

  mainWindow.webContents.setWindowOpenHandler(() => ({ action: "deny" }));
  mainWindow.webContents.on("will-navigate", (event, navigationUrl) => {
    const developmentUrl = process.env.BILLING_RENDERER_URL;
    const allowed = developmentUrl
      ? navigationUrl.startsWith(developmentUrl)
      : navigationUrl.startsWith(APPLICATION_ORIGIN);

    if (!allowed) {
      event.preventDefault();
    }
  });

  const rendererUrl = process.env.BILLING_RENDERER_URL || `${APPLICATION_ORIGIN}/index.html`;
  mainWindow.loadURL(rendererUrl);
  mainWindow.once("ready-to-show", () => mainWindow.show());
}

const hasSingleInstanceLock = app.requestSingleInstanceLock();

if (!hasSingleInstanceLock) {
  app.quit();
} else {
  app.on("second-instance", () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) {
        mainWindow.restore();
      }
      mainWindow.focus();
    }
  });

  app.whenReady().then(() => {
    registerApplicationProtocol();
    session.defaultSession.setPermissionRequestHandler((_webContents, _permission, callback) => {
      callback(false);
    });

    ipcMain.handle("billing:get-runtime-info", (event) => {
      assertTrustedIpcSender(event);
      return {
      applicationVersion: app.getVersion(),
      platform: process.platform,
      apiBaseUrl: getApiBaseUrl()
      };
    });
    ipcMain.handle("billing:diagnostics:get", (event) => {
      assertTrustedIpcSender(event);
      return sanitizedDiagnostics();
    });
    ipcMain.handle("billing:diagnostics:export", async (event) => {
      assertTrustedIpcSender(event);
      const selected = await dialog.showSaveDialog(mainWindow, {
        title: "Export sanitized support bundle",
        defaultPath: `simplified-billing-support-${new Date().toISOString().slice(0, 10)}.json`,
        filters: [{ name: "JSON support bundle", extensions: ["json"] }]
      });
      if (selected.canceled || !selected.filePath) return null;
      fs.writeFileSync(selected.filePath, JSON.stringify(sanitizedDiagnostics(), null, 2), { mode: 0o600 });
      return { fileName: path.basename(selected.filePath) };
    });
    ipcMain.handle("billing:backup:create", async (event, rawPassword) => {
      assertTrustedIpcSender(event);
      const password = requireBackupPassword(rawPassword);
      const selected = await dialog.showSaveDialog(mainWindow, {
        title: "Create encrypted billing backup",
        defaultPath: `simplified-billing-${new Date().toISOString().replace(/[:.]/g, "-")}.sbk`,
        filters: [{ name: "Simplified Billing backup", extensions: ["sbk"] }]
      });
      if (selected.canceled || !selected.filePath) return null;
      return createBackupToPath(selected.filePath, password);
    });
    ipcMain.handle("billing:backup:restore", async (event, rawPassword) => {
      assertTrustedIpcSender(event);
      const password = requireBackupPassword(rawPassword);
      if (!app.isPackaged || !resolveBackendJar()) {
        throw new Error("Restore is available only when the desktop application manages the backend lifecycle.");
      }
      const selected = await dialog.showOpenDialog(mainWindow, {
        title: "Select encrypted billing backup",
        properties: ["openFile"],
        filters: [{ name: "Simplified Billing backup", extensions: ["sbk"] }]
      });
      if (selected.canceled || !selected.filePaths[0]) return null;
      const source = selected.filePaths[0];
      const preRestore = path.join(path.dirname(source),
        `pre-restore-${new Date().toISOString().replace(/[:.]/g, "-")}.sbk`);
      const temporary = path.join(app.getPath("temp"), `billing-restore-${crypto.randomUUID()}.sql`);
      const config = databaseConfig();
      let stopped = false;
      try {
        decryptFile(source, temporary, password);
        await createBackupToPath(preRestore, password);
        await stopBackendAsync();
        stopped = true;
        await runDatabaseTool(process.env.BILLING_MYSQL_CLIENT || "mysql", [
          "--host", config.host, "--port", config.port, "--user", config.username,
          "--default-character-set=utf8mb4", config.database
        ], { stdinPath: temporary });
        return { restoredAt: new Date().toISOString(), preRestoreBackup: path.basename(preRestore) };
      } finally {
        if (fs.existsSync(temporary)) fs.unlinkSync(temporary);
        if (stopped) startBackendIfConfigured();
      }
    });
    ipcMain.handle("billing:printers:list", async (event) => {
      assertTrustedIpcSender(event);
      return event.sender.getPrintersAsync();
    });
    ipcMain.handle("billing:printers:test", async (event, deviceName) => {
      assertTrustedIpcSender(event);
      if (typeof deviceName !== "string" || deviceName.length > 300) throw new Error("Invalid printer.");
      const testWindow = new BrowserWindow({ show: false, webPreferences: { sandbox: true } });
      try {
        const html = `<!doctype html><meta charset=utf-8><style>body{font:14px sans-serif;text-align:center;padding:24px}h1{font-size:18px}</style><h1>Simplified Billing</h1><p>Printer test successful</p><p>${new Date().toLocaleString()}</p>`;
        await testWindow.loadURL(`data:text/html,${encodeURIComponent(html)}`);
        await new Promise((resolve, reject) => testWindow.webContents.print({
          silent: Boolean(deviceName), deviceName: deviceName || undefined, printBackground: false
        }, (success, reason) => success ? resolve() : reject(new Error(reason || "Test print failed."))));
        return true;
      } finally { testWindow.destroy(); }
    });
    ipcMain.handle("billing:session:store", (event, rawToken) => {
      assertTrustedIpcSender(event);
      if (typeof rawToken !== "string" || rawToken.length < 32 || rawToken.length > 512) {
        throw new Error("Invalid session token.");
      }
      storeRefreshToken(rawToken);
    });
    ipcMain.handle("billing:session:load", (event) => {
      assertTrustedIpcSender(event);
      return loadRefreshToken();
    });
    ipcMain.handle("billing:session:clear", (event) => {
      assertTrustedIpcSender(event);
      clearRefreshToken();
    });
    ipcMain.handle("billing:print:barcode-labels", (event, options) => {
      assertTrustedIpcSender(event);
      const widthMm = Number(options?.widthMm);
      const heightMm = Number(options?.heightMm);
      if (!Number.isFinite(widthMm) || !Number.isFinite(heightMm)
          || widthMm < 20 || widthMm > 100 || heightMm < 15 || heightMm > 100) {
        throw new Error("Invalid barcode label dimensions.");
      }
      return new Promise((resolve, reject) => {
        event.sender.print(
          {
            silent: false,
            printBackground: false,
            margins: { marginType: "none" },
            pageSize: {
              width: Math.round(widthMm * 1000),
              height: Math.round(heightMm * 1000)
            }
          },
          (success, failureReason) => {
            if (success) {
              resolve();
            } else {
              reject(new Error(failureReason || "Barcode label printing was cancelled or failed."));
            }
          }
        );
      });
    });
    ipcMain.handle("billing:print:receipt", (event, options) => {
      assertTrustedIpcSender(event);
      const widthMm = Number(options?.widthMm);
      if (widthMm !== 58 && widthMm !== 80) {
        throw new Error("Receipt width must be 58 mm or 80 mm.");
      }
      return new Promise((resolve, reject) => {
        event.sender.print(
          {
            silent: false,
            printBackground: false,
            margins: { marginType: "none" },
            pageSize: { width: widthMm * 1000, height: 297000 }
          },
          (success, failureReason) => {
            if (success) resolve();
            else reject(new Error(failureReason || "Receipt printing was cancelled or failed."));
          }
        );
      });
    });
    ipcMain.handle("billing:print:report", (event) => {
      assertTrustedIpcSender(event);
      return new Promise((resolve, reject) => {
        event.sender.print(
          {
            silent: false,
            printBackground: false,
            margins: { marginType: "default" },
            pageSize: "A4"
          },
          (success, failureReason) => {
            if (success || /cancel(?:led|ed)/i.test(failureReason || "")) {
              resolve();
            } else {
              reject(new Error(failureReason || "Report printing failed."));
            }
          }
        );
      });
    });

    startBackendIfConfigured();
    createWindow();

    app.on("activate", () => {
      if (BrowserWindow.getAllWindows().length === 0) {
        createWindow();
      }
    });
  });
}

app.on("before-quit", () => {
  app.isQuitting = true;
  stopBackend();
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") {
    app.quit();
  }
});
