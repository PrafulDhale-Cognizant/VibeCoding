const DEVELOPMENT_SESSION_KEY = "billing.refreshToken";

export async function storeRefreshToken(token: string): Promise<void> {
  if (window.billingDesktop) {
    await window.billingDesktop.storeRefreshToken(token);
    return;
  }
  sessionStorage.setItem(DEVELOPMENT_SESSION_KEY, token);
}

export async function loadRefreshToken(): Promise<string | null> {
  if (window.billingDesktop) {
    return window.billingDesktop.loadRefreshToken();
  }
  return sessionStorage.getItem(DEVELOPMENT_SESSION_KEY);
}

export async function clearRefreshToken(): Promise<void> {
  if (window.billingDesktop) {
    await window.billingDesktop.clearRefreshToken();
    return;
  }
  sessionStorage.removeItem(DEVELOPMENT_SESSION_KEY);
}
