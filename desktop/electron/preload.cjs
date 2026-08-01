const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("billingDesktop", {
  getRuntimeInfo: () => ipcRenderer.invoke("billing:get-runtime-info"),
  storeRefreshToken: (rawToken) => ipcRenderer.invoke("billing:session:store", rawToken),
  loadRefreshToken: () => ipcRenderer.invoke("billing:session:load"),
  clearRefreshToken: () => ipcRenderer.invoke("billing:session:clear"),
  getDiagnostics: () => ipcRenderer.invoke("billing:diagnostics:get"),
  exportSupportBundle: () => ipcRenderer.invoke("billing:diagnostics:export"),
  getStartupRecovery: () => ipcRenderer.invoke("billing:backup:startup-recovery"),
  createBackup: (password, configuration) => ipcRenderer.invoke("billing:backup:create", password, configuration),
  configureBackupSchedule: (password, retention, configuration) => ipcRenderer.invoke("billing:backup:schedule", { password, retention, configuration }),
  disableBackupSchedule: () => ipcRenderer.invoke("billing:backup:schedule:disable"),
  restoreBackup: (password) => ipcRenderer.invoke("billing:backup:restore", password),
  restoreLatestBackup: (password) => ipcRenderer.invoke("billing:backup:restore-latest", password),
  applyOfflineUpdate: (password) => ipcRenderer.invoke("billing:update:apply", password),
  listPrinters: () => ipcRenderer.invoke("billing:printers:list"),
  testPrinter: (deviceName) => ipcRenderer.invoke("billing:printers:test", deviceName),
  printBarcodeLabels: (options) => ipcRenderer.invoke("billing:print:barcode-labels", options),
  printReceipt: (options) => ipcRenderer.invoke("billing:print:receipt", options),
  printReport: () => ipcRenderer.invoke("billing:print:report")
});
