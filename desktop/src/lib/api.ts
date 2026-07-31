import type {
  AuthResponse,
  InitialSetupRequest,
  SetupStatus,
  StoreDetails,
  StoreProfile,
  UserRole,
  UserSummary
} from "../types";

export interface SystemHealth {
  status: string;
  application: string;
  version: string;
  database: string;
  javaVersion: string;
  timestamp: string;
}

interface ApiErrorPayload {
  code?: string;
  message?: string;
  fieldErrors?: Array<{ field: string; message: string }>;
}

export class ApiClientError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: Array<{ field: string; message: string }>;

  constructor(status: number, payload: ApiErrorPayload) {
    super(payload.message ?? `The local service returned HTTP ${status}.`);
    this.name = "ApiClientError";
    this.status = status;
    this.code = payload.code ?? "REQUEST_FAILED";
    this.fieldErrors = payload.fieldErrors ?? [];
  }
}

async function resolveApiBaseUrl(): Promise<string> {
  if (window.billingDesktop) {
    const runtime = await window.billingDesktop.getRuntimeInfo();
    return runtime.apiBaseUrl;
  }
  return import.meta.env.VITE_API_BASE_URL ?? "";
}

async function request<T>(
  path: string,
  init: RequestInit = {},
  accessToken?: string
): Promise<T> {
  const apiBaseUrl = await resolveApiBaseUrl();
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  headers.set("X-Correlation-Id", crypto.randomUUID());
  if (accessToken) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }
  if (init.body && !(init.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(`${apiBaseUrl}${path}`, { ...init, headers });
  if (!response.ok) {
    let payload: ApiErrorPayload = {};
    try {
      payload = (await response.json()) as ApiErrorPayload;
    } catch {
      // The normalized fallback below is used for non-JSON failures.
    }
    throw new ApiClientError(response.status, payload);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export function fetchSystemHealth(signal?: AbortSignal): Promise<SystemHealth> {
  return request<SystemHealth>("/api/v1/system/health", { method: "GET", signal });
}

export const api = {
  setupStatus: () => request<SetupStatus>("/api/v1/setup/status"),
  initialize: (body: InitialSetupRequest) =>
    request<AuthResponse>("/api/v1/setup", {
      method: "POST",
      body: JSON.stringify(body)
    }),
  login: (username: string, password: string) =>
    request<AuthResponse>("/api/v1/auth/login", {
      method: "POST",
      body: JSON.stringify({ username, password })
    }),
  refresh: (refreshToken: string) =>
    request<AuthResponse>("/api/v1/auth/refresh", {
      method: "POST",
      body: JSON.stringify({ refreshToken })
    }),
  logout: (refreshToken: string) =>
    request<void>("/api/v1/auth/logout", {
      method: "POST",
      body: JSON.stringify({ refreshToken })
    }),
  me: (accessToken: string) => request<UserSummary>("/api/v1/auth/me", {}, accessToken),
  changePassword: (accessToken: string, currentPassword: string, newPassword: string) =>
    request<void>(
      "/api/v1/auth/change-password",
      {
        method: "POST",
        body: JSON.stringify({ currentPassword, newPassword })
      },
      accessToken
    ),
  getStore: (accessToken: string) =>
    request<StoreDetails>("/api/v1/store", {}, accessToken),
  updateStore: (accessToken: string, profile: StoreProfile, version: number) =>
    request<StoreDetails>(
      "/api/v1/store",
      { method: "PUT", body: JSON.stringify({ profile, version }) },
      accessToken
    ),
  updateLogo: (accessToken: string, file: File) => {
    const body = new FormData();
    body.append("file", file);
    return request<StoreDetails>("/api/v1/store/logo", { method: "PUT", body }, accessToken);
  },
  deleteLogo: (accessToken: string) =>
    request<void>("/api/v1/store/logo", { method: "DELETE" }, accessToken),
  listUsers: (accessToken: string) =>
    request<UserSummary[]>("/api/v1/users", {}, accessToken),
  createUser: (
    accessToken: string,
    body: { username: string; displayName: string; password: string; roles: UserRole[] }
  ) =>
    request<UserSummary>(
      "/api/v1/users",
      { method: "POST", body: JSON.stringify(body) },
      accessToken
    ),
  updateUser: (
    accessToken: string,
    userId: string,
    body: { displayName: string; roles: UserRole[]; active: boolean; version: number }
  ) =>
    request<UserSummary>(
      `/api/v1/users/${userId}`,
      { method: "PATCH", body: JSON.stringify(body) },
      accessToken
    ),
  resetPassword: (accessToken: string, userId: string, newPassword: string) =>
    request<void>(
      `/api/v1/users/${userId}/reset-password`,
      { method: "POST", body: JSON.stringify({ newPassword }) },
      accessToken
    )
};
