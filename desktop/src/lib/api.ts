export interface SystemHealth {
  status: string;
  application: string;
  version: string;
  database: string;
  javaVersion: string;
  timestamp: string;
}

async function resolveApiBaseUrl(): Promise<string> {
  if (window.billingDesktop) {
    const runtime = await window.billingDesktop.getRuntimeInfo();
    return runtime.apiBaseUrl;
  }

  return import.meta.env.VITE_API_BASE_URL ?? "";
}

export async function fetchSystemHealth(signal?: AbortSignal): Promise<SystemHealth> {
  const apiBaseUrl = await resolveApiBaseUrl();
  const response = await fetch(`${apiBaseUrl}/api/v1/system/health`, {
    method: "GET",
    headers: {
      Accept: "application/json",
      "X-Correlation-Id": crypto.randomUUID()
    },
    signal
  });

  if (!response.ok) {
    throw new Error(`Local service returned HTTP ${response.status}.`);
  }

  return response.json() as Promise<SystemHealth>;
}

