const { app, BrowserWindow, ipcMain, net, protocol, safeStorage, session } = require("electron");
const { spawn } = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");
const { pathToFileURL } = require("node:url");

const APPLICATION_SCHEME = "billing";
const APPLICATION_ORIGIN = `${APPLICATION_SCHEME}://app`;
const DEFAULT_API_BASE_URL = "http://127.0.0.1:8080";

let mainWindow;
let backendProcess;

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
