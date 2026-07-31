const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("billingDesktop", {
  getRuntimeInfo: () => ipcRenderer.invoke("billing:get-runtime-info"),
  storeRefreshToken: (rawToken) => ipcRenderer.invoke("billing:session:store", rawToken),
  loadRefreshToken: () => ipcRenderer.invoke("billing:session:load"),
  clearRefreshToken: () => ipcRenderer.invoke("billing:session:clear"),
  printBarcodeLabels: (options) => ipcRenderer.invoke("billing:print:barcode-labels", options),
  printReceipt: (options) => ipcRenderer.invoke("billing:print:receipt", options)
});
