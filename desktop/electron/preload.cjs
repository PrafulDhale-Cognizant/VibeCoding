const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("billingDesktop", {
  getRuntimeInfo: () => ipcRenderer.invoke("billing:get-runtime-info"),
  storeRefreshToken: (rawToken) => ipcRenderer.invoke("billing:session:store", rawToken),
  loadRefreshToken: () => ipcRenderer.invoke("billing:session:load"),
  clearRefreshToken: () => ipcRenderer.invoke("billing:session:clear"),
  getDiagnostics: () => ipcRenderer.invoke("billing:diagnostics:get"),
  exportSupportBundle: () => ipcRenderer.invoke("billing:diagnostics:export"),
  createBackup: (password) => ipcRenderer.invoke("billing:backup:create", password),
  restoreBackup: (password) => ipcRenderer.invoke("billing:backup:restore", password),
  applyOfflineUpdate: (password) => ipcRenderer.invoke("billing:update:apply", password),
  listPrinters: () => ipcRenderer.invoke("billing:printers:list"),
  testPrinter: (deviceName) => ipcRenderer.invoke("billing:printers:test", deviceName),
  printBarcodeLabels: (options) => ipcRenderer.invoke("billing:print:barcode-labels", options),
  printReceipt: (options) => ipcRenderer.invoke("billing:print:receipt", options),
  printReport: () => ipcRenderer.invoke("billing:print:report")
});
