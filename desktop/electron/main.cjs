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
let backupTimer;
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
function backupSchedulePath() { return path.join(app.getPath("userData"), "backup-schedule.json"); }
function managedBackupDirectory() { return path.join(app.getPath("userData"), "backups"); }

function normalizeBackupConfiguration(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return {};
  const configuration = Object.fromEntries(Object.entries(value)
    .filter(([key, entry]) => key.startsWith("simplified-billing.") && typeof entry === "string")
    .slice(0, 100));
  if (Buffer.byteLength(JSON.stringify(configuration), "utf8") > 1024 * 1024) {
    throw new Error("Desktop backup configuration is too large.");
  }
  return configuration;
}

function isBackupHeaderValid(filePath) {
  try {
    const descriptor = fs.openSync(filePath, "r");
    const header = Buffer.alloc(BACKUP_MAGIC.length);
    fs.readSync(descriptor, header, 0, header.length, 0);
    fs.closeSync(descriptor);
    return header.equals(BACKUP_MAGIC);
  } catch { return false; }
}

function latestBackup() {
  const schedule = readBackupSchedule(false);
  const directories = [managedBackupDirectory(), schedule?.destination,
    path.join(app.getPath("userData"), "pre-update-backups")].filter(Boolean);
  const candidates = directories.flatMap((directory) => {
    try {
      return fs.readdirSync(directory).filter((name) => name.toLowerCase().endsWith(".sbk"))
        .map((name) => { const filePath = path.join(directory, name); const stats = fs.statSync(filePath);
          return { filePath, fileName: name, createdAt: stats.mtime.toISOString(), size: stats.size }; });
    } catch { return []; }
  }).filter((candidate) => isBackupHeaderValid(candidate.filePath))
    .sort((left, right) => Date.parse(right.createdAt) - Date.parse(left.createdAt));
  return candidates[0] || null;
}

function readBackupSchedule(includeSecret = false) {
  try {
    const schedule = JSON.parse(fs.readFileSync(backupSchedulePath(), "utf8"));
    if (!includeSecret) return { enabled: true, destination: schedule.destination,
      retention: schedule.retention, lastAttemptAt: schedule.lastAttemptAt || null };
    if (!safeStorage.isEncryptionAvailable()) throw new Error("Operating-system encryption is unavailable.");
    return { ...schedule, password: safeStorage.decryptString(Buffer.from(schedule.encryptedPassword, "base64")) };
  } catch { return null; }
}

function writeBackupSchedule(destination, password, retention, rawConfiguration = {}) {
  if (!safeStorage.isEncryptionAvailable()) throw new Error("Operating-system encryption is unavailable.");
  const encryptedPassword = safeStorage.encryptString(password).toString("base64");
  fs.writeFileSync(backupSchedulePath(), JSON.stringify({ destination, retention, encryptedPassword,
    configuration: normalizeBackupConfiguration(rawConfiguration), lastAttemptAt: null }), { mode: 0o600 });
}

async function runScheduledBackupIfDue() {
  const schedule = readBackupSchedule(true);
  if (!schedule) return;
  const last = schedule.lastAttemptAt ? Date.parse(schedule.lastAttemptAt) : 0;
  if (Date.now() - last < 24 * 60 * 60 * 1000) return;
  const attempt = new Date().toISOString();
  const target = path.join(schedule.destination, `simplified-billing-scheduled-${attempt.replace(/[:.]/g, "-")}.sbk`);
  try {
    await createBackupToPath(target, schedule.password, schedule.configuration);
    const backups = fs.readdirSync(schedule.destination)
      .filter((name) => /^simplified-billing-scheduled-.*\.sbk$/i.test(name))
      .map((name) => ({ name, path: path.join(schedule.destination, name), time: fs.statSync(path.join(schedule.destination, name)).mtimeMs }))
      .sort((left, right) => right.time - left.time);
    backups.slice(schedule.retention).forEach((backup) => fs.unlinkSync(backup.path));
  } finally {
    const stored = JSON.parse(fs.readFileSync(backupSchedulePath(), "utf8"));
    stored.lastAttemptAt = attempt;
    fs.writeFileSync(backupSchedulePath(), JSON.stringify(stored), { mode: 0o600 });
  }
}

function startBackupScheduler() {
  void runScheduledBackupIfDue().catch((error) => console.error("Scheduled backup failed.", error));
  backupTimer = setInterval(() => void runScheduledBackupIfDue().catch(
    (error) => console.error("Scheduled backup failed.", error)), 60 * 60 * 1000);
}

async function createBackupToPath(targetPath, password, rawConfiguration = {}) {
  const config = databaseConfig();
  if (!config.password) throw new Error("BILLING_DB_PASSWORD is required for backup.");
  const temporary = path.join(app.getPath("temp"), `billing-${crypto.randomUUID()}.sql`);
  const payload = path.join(app.getPath("temp"), `billing-${crypto.randomUUID()}.json`);
  try {
    await runDatabaseTool(process.env.BILLING_MYSQLDUMP || "mysqldump", [
      "--single-transaction", "--routines", "--triggers", "--set-gtid-purged=OFF",
      "--host", config.host, "--port", config.port, "--user", config.username,
      "--default-character-set=utf8mb4", config.database
    ], { stdoutPath: temporary });
    fs.writeFileSync(payload, JSON.stringify({
      format: "simplified-billing-backup", formatVersion: 2,
      createdAt: new Date().toISOString(), applicationVersion: app.getVersion(),
      databaseSql: fs.readFileSync(temporary).toString("base64"),
      configuration: normalizeBackupConfiguration(rawConfiguration)
    }), { mode: 0o600 });
    encryptFile(payload, targetPath, password);
    const status = { successful: true, createdAt: new Date().toISOString(),
      fileName: path.basename(targetPath), size: fs.statSync(targetPath).size };
    recordBackupStatus(status);
    return status;
  } catch (error) {
    recordBackupStatus({ successful: false, failedAt: new Date().toISOString(), message: error.message });
    throw error;
  } finally {
    if (fs.existsSync(temporary)) fs.unlinkSync(temporary);
    if (fs.existsSync(payload)) fs.unlinkSync(payload);
  }
}

function decryptBackupToSql(sourcePath, sqlPath, password) {
  const decrypted = path.join(app.getPath("temp"), `billing-payload-${crypto.randomUUID()}`);
  try {
    decryptFile(sourcePath, decrypted, password);
    const bytes = fs.readFileSync(decrypted);
    try {
      const payload = JSON.parse(bytes.toString("utf8"));
      if (payload.format !== "simplified-billing-backup" || payload.formatVersion !== 2
          || typeof payload.databaseSql !== "string") throw new Error("Unsupported backup payload.");
      fs.writeFileSync(sqlPath, Buffer.from(payload.databaseSql, "base64"), { mode: 0o600 });
      return normalizeBackupConfiguration(payload.configuration);
    } catch (error) {
      if (bytes.toString("utf8", 0, Math.min(bytes.length, 128)).trimStart().startsWith("{")) {
        throw new Error(`The backup payload is damaged or unsupported: ${error.message}`);
      }
      fs.writeFileSync(sqlPath, bytes, { mode: 0o600 });
      return {};
    }
  } finally { if (fs.existsSync(decrypted)) fs.unlinkSync(decrypted); }
}

async function restoreBackupFromPath(source, password) {
  if (!app.isPackaged || !resolveBackendJar()) {
    throw new Error("Restore is available only when the desktop application manages the backend lifecycle.");
  }
  const backupDirectory = managedBackupDirectory();
  fs.mkdirSync(backupDirectory, { recursive: true });
  const preRestore = path.join(backupDirectory, `pre-restore-${new Date().toISOString().replace(/[:.]/g, "-")}.sbk`);
  const temporary = path.join(app.getPath("temp"), `billing-restore-${crypto.randomUUID()}.sql`);
  const config = databaseConfig();
  let stopped = false;
  try {
    const configuration = decryptBackupToSql(source, temporary, password);
    await createBackupToPath(preRestore, password);
    await stopBackendAsync(); stopped = true;
    await runDatabaseTool(process.env.BILLING_MYSQL_CLIENT || "mysql", [
      "--host", config.host, "--port", config.port, "--user", config.username,
      "--default-character-set=utf8mb4", config.database
    ], { stdinPath: temporary });
    return { restoredAt: new Date().toISOString(), preRestoreBackup: path.basename(preRestore), configuration };
  } finally {
    if (fs.existsSync(temporary)) fs.unlinkSync(temporary);
    if (stopped) startBackendIfConfigured();
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
    disk, logFilePresent: fs.existsSync(logFile), backup: readBackupStatus(),
    backupSchedule: readBackupSchedule(false)
  };
}

function compareVersions(left, right) {
  const a = String(left).split(".").map(Number); const b = String(right).split(".").map(Number);
  for (let index = 0; index < Math.max(a.length, b.length); index++) {
    const difference = (a[index] || 0) - (b[index] || 0);
    if (difference !== 0) return difference;
  }
  return 0;
}

function updatePublicKey() {
  const configured = process.env.BILLING_UPDATE_PUBLIC_KEY;
  if (configured) return configured.includes("BEGIN PUBLIC KEY") ? configured : fs.readFileSync(configured, "utf8");
  const packagedKey = path.join(process.resourcesPath, "update-public-key.pem");
  if (!fs.existsSync(packagedKey)) throw new Error("No offline-update verification key is configured.");
  return fs.readFileSync(packagedKey, "utf8");
}

function verifyUpdatePackage(installerPath) {
  const manifestPath = `${installerPath}.json`; const signaturePath = `${installerPath}.sig`;
  if (!fs.existsSync(manifestPath) || !fs.existsSync(signaturePath)) {
    throw new Error("The update requires matching .json manifest and .sig signature files.");
  }
  const manifestBytes = fs.readFileSync(manifestPath);
  const manifest = JSON.parse(manifestBytes.toString("utf8"));
  const installerHash = crypto.createHash("sha256").update(fs.readFileSync(installerPath)).digest("hex");
  if (manifest.product !== "Simplified Billing" || manifest.sha256 !== installerHash) {
    throw new Error("The update manifest is incompatible or its installer hash does not match.");
  }
  if (compareVersions(manifest.version, app.getVersion()) <= 0) {
    throw new Error(`Update version ${manifest.version} is not newer than ${app.getVersion()}.`);
  }
  const signature = Buffer.from(fs.readFileSync(signaturePath, "utf8").trim(), "base64");
  if (!crypto.verify("sha256", manifestBytes, updatePublicKey(), signature)) {
    throw new Error("The update signature is invalid.");
  }
  return manifest;
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
    ipcMain.handle("billing:backup:startup-recovery", (event) => {
      assertTrustedIpcSender(event);
      const backup = latestBackup();
      return backup ? { fileName: backup.fileName, createdAt: backup.createdAt, size: backup.size,
        restoreAvailable: Boolean(app.isPackaged && resolveBackendJar()) } : null;
    });
    ipcMain.handle("billing:backup:create", async (event, rawPassword, rawConfiguration) => {
      assertTrustedIpcSender(event);
      const password = requireBackupPassword(rawPassword);
      const directory = managedBackupDirectory();
      fs.mkdirSync(directory, { recursive: true });
      const target = path.join(directory, `simplified-billing-${new Date().toISOString().replace(/[:.]/g, "-")}.sbk`);
      return createBackupToPath(target, password, rawConfiguration);
    });
    ipcMain.handle("billing:backup:schedule", async (event, options) => {
      assertTrustedIpcSender(event);
      const password = requireBackupPassword(options?.password);
      const retention = Number(options?.retention);
      if (!Number.isInteger(retention) || retention < 1 || retention > 30) {
        throw new Error("Scheduled backup retention must be between 1 and 30 files.");
      }
      const selected = await dialog.showOpenDialog(mainWindow, {
        title: "Choose scheduled backup folder", properties: ["openDirectory", "createDirectory"]
      });
      if (selected.canceled || !selected.filePaths[0]) return null;
      writeBackupSchedule(selected.filePaths[0], password, retention, options?.configuration);
      await runScheduledBackupIfDue();
      return readBackupSchedule(false);
    });
    ipcMain.handle("billing:backup:schedule:disable", (event) => {
      assertTrustedIpcSender(event);
      if (fs.existsSync(backupSchedulePath())) fs.unlinkSync(backupSchedulePath());
      return true;
    });
    ipcMain.handle("billing:backup:restore", async (event, rawPassword) => {
      assertTrustedIpcSender(event);
      const password = requireBackupPassword(rawPassword);
      const selected = await dialog.showOpenDialog(mainWindow, {
        title: "Select encrypted billing backup",
        properties: ["openFile"],
        filters: [{ name: "Simplified Billing backup", extensions: ["sbk"] }]
      });
      if (selected.canceled || !selected.filePaths[0]) return null;
      return restoreBackupFromPath(selected.filePaths[0], password);
    });
    ipcMain.handle("billing:backup:restore-latest", async (event, rawPassword) => {
      assertTrustedIpcSender(event);
      const password = requireBackupPassword(rawPassword);
      const backup = latestBackup();
      if (!backup) throw new Error("No valid local backup is available.");
      return restoreBackupFromPath(backup.filePath, password);
    });
    ipcMain.handle("billing:update:apply", async (event, rawPassword) => {
      assertTrustedIpcSender(event);
      const password = requireBackupPassword(rawPassword);
      if (process.platform !== "win32" || !app.isPackaged) {
        throw new Error("Offline updates are available only in the installed Windows application.");
      }
      const selected = await dialog.showOpenDialog(mainWindow, {
        title: "Select signed offline update installer",
        properties: ["openFile"],
        filters: [{ name: "Simplified Billing update", extensions: ["exe"] }]
      });
      if (selected.canceled || !selected.filePaths[0]) return null;
      const installerPath = selected.filePaths[0];
      const manifest = verifyUpdatePackage(installerPath);
      const backupDirectory = path.join(app.getPath("userData"), "pre-update-backups");
      fs.mkdirSync(backupDirectory, { recursive: true });
      const backupPath = path.join(backupDirectory,
        `pre-update-${app.getVersion()}-${new Date().toISOString().replace(/[:.]/g, "-")}.sbk`);
      await createBackupToPath(backupPath, password);
      const child = spawn(installerPath, ["/S"], { detached: true, stdio: "ignore", windowsHide: true });
      child.unref();
      setImmediate(() => app.quit());
      return { version: manifest.version, preUpdateBackup: path.basename(backupPath) };
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
            if (success) {
              resolve(true);
            } else if (/cancel(?:led|ed)/i.test(failureReason || "")) {
              resolve(false);
            } else {
              reject(new Error(failureReason || "Report printing failed."));
            }
          }
        );
      });
    });
    ipcMain.handle("billing:invoice:save-pdf", async (event, suggestedFileName) => {
      assertTrustedIpcSender(event);
      const safeName = String(suggestedFileName || "invoice")
        .replace(/[^A-Za-z0-9._-]/g, "_")
        .replace(/\.pdf$/i, "")
        .slice(0, 80) || "invoice";
      const selected = await dialog.showSaveDialog(mainWindow, {
        title: "Save invoice as PDF",
        defaultPath: `${safeName}.pdf`,
        filters: [{ name: "PDF document", extensions: ["pdf"] }]
      });
      if (selected.canceled || !selected.filePath) return null;
      const pdf = await event.sender.printToPDF({
        pageSize: "A4",
        printBackground: false,
        preferCSSPageSize: true
      });
      fs.writeFileSync(selected.filePath, pdf, { mode: 0o600 });
      return { fileName: path.basename(selected.filePath) };
    });

    startBackendIfConfigured();
    startBackupScheduler();
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
  if (backupTimer) clearInterval(backupTimer);
  stopBackend();
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") {
    app.quit();
  }
});
