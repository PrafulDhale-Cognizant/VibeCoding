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
  printBarcodeLabels: (options: { widthMm: number; heightMm: number }) => Promise<void>;
  printReceipt: (options: { widthMm: 58 | 80 }) => Promise<void>;
  printReport: () => Promise<void>;
}

interface Window {
  billingDesktop?: BillingDesktopBridge;
}
