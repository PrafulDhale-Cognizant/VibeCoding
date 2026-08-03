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
    backupSchedule: { enabled: boolean; destination: string; retention: number; lastAttemptAt: string | null } | null;
  }>;
  exportSupportBundle: () => Promise<{ fileName: string } | null>;
  getStartupRecovery: () => Promise<{ fileName: string; createdAt: string; size: number; restoreAvailable: boolean } | null>;
  createBackup: (password: string, configuration?: Record<string, string>) => Promise<{ successful: boolean; createdAt: string; fileName: string; size: number } | null>;
  configureBackupSchedule: (password: string, retention: number, configuration?: Record<string, string>) => Promise<{ enabled: boolean; destination: string; retention: number; lastAttemptAt: string | null } | null>;
  disableBackupSchedule: () => Promise<boolean>;
  restoreBackup: (password: string) => Promise<{ restoredAt: string; preRestoreBackup: string; configuration: Record<string, string> } | null>;
  restoreLatestBackup: (password: string) => Promise<{ restoredAt: string; preRestoreBackup: string; configuration: Record<string, string> }>;
  applyOfflineUpdate: (password: string) => Promise<{ version: string; preUpdateBackup: string } | null>;
  listPrinters: () => Promise<Array<{ name: string; displayName: string; isDefault: boolean; status: number }>>;
  testPrinter: (deviceName: string) => Promise<boolean>;
  printBarcodeLabels: (options: { widthMm: number; heightMm: number }) => Promise<void>;
  printReceipt: (options: { widthMm: 58 | 80 }) => Promise<void>;
  printReport: () => Promise<boolean>;
  saveInvoicePdf: (suggestedFileName: string) => Promise<{ fileName: string } | null>;
}

interface Window {
  billingDesktop?: BillingDesktopBridge;
}
