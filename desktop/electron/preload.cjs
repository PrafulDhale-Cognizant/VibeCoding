const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("billingDesktop", {
  getRuntimeInfo: () => ipcRenderer.invoke("billing:get-runtime-info")
});

