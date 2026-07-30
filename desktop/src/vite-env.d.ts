/// <reference types="vite/client" />

interface BillingRuntimeInfo {
  applicationVersion: string;
  platform: string;
  apiBaseUrl: string;
}

interface BillingDesktopBridge {
  getRuntimeInfo: () => Promise<BillingRuntimeInfo>;
}

interface Window {
  billingDesktop?: BillingDesktopBridge;
}

