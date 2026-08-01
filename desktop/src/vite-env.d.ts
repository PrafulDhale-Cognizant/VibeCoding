/// <reference types="vite/client" />

interface BillingRuntimeInfo {
  applicationVersion: string;
  platform: string;
  apiBaseUrl: string;
}

interface BillingDesktopBridge {
  getRuntimeInfo: () => Promise<BillingRuntimeInfo>;
  storeRefreshToken: (rawToken: string) => Promise<void>;
  loadRefreshToken: () => Promise<string | null>;
  clearRefreshToken: () => Promise<void>;
  getDiagnostics: () => Promise<{
    generatedAt: string; applicationVersion: string; platform: string; packaged: boolean;
    apiBaseUrl: string; backendManaged: boolean; backendJarPresent: boolean;
    database: { host: string; port: string; database: string };
    disk: { freeBytes: number; totalBytes: number } | null;
    logFilePresent: boolean;
    backup: { successful: boolean; createdAt?: string; failedAt?: string; fileName?: string; size?: number; message?: string } | null;
  }>;
  exportSupportBundle: () => Promise<{ fileName: string } | null>;
  createBackup: (password: string) => Promise<{ successful: boolean; createdAt: string; fileName: string; size: number } | null>;
  restoreBackup: (password: string) => Promise<{ restoredAt: string; preRestoreBackup: string } | null>;
  applyOfflineUpdate: (password: string) => Promise<{ version: string; preUpdateBackup: string } | null>;
  listPrinters: () => Promise<Array<{ name: string; displayName: string; isDefault: boolean; status: number }>>;
  testPrinter: (deviceName: string) => Promise<boolean>;
  printBarcodeLabels: (options: { widthMm: number; heightMm: number }) => Promise<void>;
  printReceipt: (options: { widthMm: 58 | 80 }) => Promise<void>;
  printReport: () => Promise<void>;
}

interface Window {
  billingDesktop?: BillingDesktopBridge;
}
