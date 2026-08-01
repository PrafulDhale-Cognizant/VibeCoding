import type {
  AuthResponse,
  CategoryResponse,
  DashboardReportResponse,
  InitialSetupRequest,
  InventoryPage,
  KhataBalanceStatus,
  KhataCustomerResponse,
  KhataLedgerEntryResponse,
  KhataSettlementResponse,
  KhataSummaryResponse,
  ProductAlertResponse,
  ProductCreateRequest,
  ProductLookupResponse,
  ProductResponse,
  ProductSort,
  ProductUpdateRequest,
  PurchaseResponse,
  PurchaseReturnReason,
  PurchaseReturnResponse,
  PurchaseReturnSummaryResponse,
  PurchaseSummaryResponse,
  PurchasingSummaryResponse,
  SalesReportResponse,
  SaleReturnResponse,
  SaleReturnSourceInvoice,
  ReturnDisposition,
  PosInvoiceResponse,
  PosPaymentRequest,
  PosQuoteRequest,
  PosQuoteResponse,
  SetupStatus,
  StockAdjustmentReasonCode,
  StockStatus,
  StockTransactionResponse,
  SettlementMode,
  SupplierBalanceStatus,
  SupplierAnalyticsResponse,
  SupplierLedgerResponse,
  SupplierPaymentMode,
  SupplierPaymentResponse,
  SupplierResponse,
  StoreDetails,
  StoreProfile,
  UnitResponse,
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
    ),
  listCategories: (accessToken: string, includeInactive = false) =>
    request<CategoryResponse[]>(
      `/api/v1/inventory/categories?includeInactive=${includeInactive}`,
      {},
      accessToken
    ),
  createCategory: (accessToken: string, name: string) =>
    request<CategoryResponse>(
      "/api/v1/inventory/categories",
      { method: "POST", body: JSON.stringify({ name }) },
      accessToken
    ),
  updateCategory: (
    accessToken: string,
    categoryId: string,
    body: { name: string; active: boolean; version: number }
  ) =>
    request<CategoryResponse>(
      `/api/v1/inventory/categories/${categoryId}`,
      { method: "PUT", body: JSON.stringify(body) },
      accessToken
    ),
  listUnits: (accessToken: string) =>
    request<UnitResponse[]>("/api/v1/inventory/units", {}, accessToken),
  searchProducts: (
    accessToken: string,
    filters: {
      query?: string;
      categoryId?: string;
      active?: boolean | null;
      stockStatus?: StockStatus;
      page?: number;
      size?: number;
      sort?: ProductSort;
    }
  ) => {
    const parameters = new URLSearchParams();
    if (filters.query?.trim()) parameters.set("query", filters.query.trim());
    if (filters.categoryId) parameters.set("categoryId", filters.categoryId);
    if (filters.active !== null && filters.active !== undefined) {
      parameters.set("active", String(filters.active));
    }
    parameters.set("stockStatus", filters.stockStatus ?? "ALL");
    parameters.set("page", String(filters.page ?? 0));
    parameters.set("size", String(filters.size ?? 25));
    parameters.set("sort", filters.sort ?? "NAME_ASC");
    return request<InventoryPage<ProductResponse>>(
      `/api/v1/inventory/products?${parameters.toString()}`,
      {},
      accessToken
    );
  },
  getProduct: (accessToken: string, productId: string) =>
    request<ProductResponse>(`/api/v1/inventory/products/${productId}`, {}, accessToken),
  findProductByBarcode: (accessToken: string, barcode: string) =>
    request<ProductLookupResponse>(
      `/api/v1/inventory/products/by-barcode/${encodeURIComponent(barcode)}`,
      {},
      accessToken
    ),
  createProduct: (accessToken: string, body: ProductCreateRequest) =>
    request<ProductResponse>(
      "/api/v1/inventory/products",
      { method: "POST", body: JSON.stringify(body) },
      accessToken
    ),
  updateProduct: (
    accessToken: string,
    productId: string,
    body: ProductUpdateRequest
  ) =>
    request<ProductResponse>(
      `/api/v1/inventory/products/${productId}`,
      { method: "PUT", body: JSON.stringify(body) },
      accessToken
    ),
  generateBarcode: (accessToken: string) =>
    request<{ barcode: string }>(
      "/api/v1/inventory/barcodes/generate",
      { method: "POST" },
      accessToken
    ),
  adjustStock: (
    accessToken: string,
    productId: string,
    body: {
      quantityDelta: number;
      reasonCode: StockAdjustmentReasonCode;
      notes: string;
      stockVersion: number;
    }
  ) =>
    request<ProductResponse>(
      `/api/v1/inventory/products/${productId}/stock-adjustments`,
      { method: "POST", body: JSON.stringify(body) },
      accessToken
    ),
  getStockLedger: (accessToken: string, productId: string, page = 0, size = 25) =>
    request<InventoryPage<StockTransactionResponse>>(
      `/api/v1/inventory/products/${productId}/stock-ledger?page=${page}&size=${size}`,
      {},
      accessToken
    ),
  getStockAlerts: (
    accessToken: string,
    status: "LOW_STOCK" | "OUT_OF_STOCK",
    page = 0,
    size = 25
  ) =>
    request<InventoryPage<ProductAlertResponse>>(
      `/api/v1/inventory/stock-alerts?status=${status}&page=${page}&size=${size}`,
      {},
      accessToken
    ),
  quoteSale: (accessToken: string, body: PosQuoteRequest) =>
    request<PosQuoteResponse>(
      "/api/v1/pos/quote",
      { method: "POST", body: JSON.stringify(body) },
      accessToken
    ),
  checkoutSale: (
    accessToken: string,
    idempotencyKey: string,
    body: PosQuoteRequest & { payments: PosPaymentRequest[]; notes: string }
  ) =>
    request<PosInvoiceResponse>(
      "/api/v1/pos/checkout",
      {
        method: "POST",
        headers: { "Idempotency-Key": idempotencyKey },
        body: JSON.stringify(body)
      },
      accessToken
    ),
  getInvoice: (accessToken: string, invoiceId: string) =>
    request<PosInvoiceResponse>(`/api/v1/pos/invoices/${invoiceId}`, {}, accessToken),
  findSaleReturnSource: (accessToken: string, invoiceNumber: string) =>
    request<SaleReturnSourceInvoice>(
      `/api/v1/pos/return-source?${new URLSearchParams({ invoiceNumber }).toString()}`,
      {}, accessToken
    ),
  returnSale: (
    accessToken: string,
    invoiceId: string,
    idempotencyKey: string,
    body: {
      items: Array<{ invoiceItemId: string; quantity: number; disposition: ReturnDisposition }>;
      refunds: PosPaymentRequest[];
      reason: string;
    }
  ) => request<SaleReturnResponse>(
    `/api/v1/pos/invoices/${invoiceId}/returns`,
    { method: "POST", headers: { "Idempotency-Key": idempotencyKey }, body: JSON.stringify(body) },
    accessToken
  ),
  cancelSale: (
    accessToken: string,
    invoiceId: string,
    idempotencyKey: string,
    body: { refunds: PosPaymentRequest[]; reason: string }
  ) => request<SaleReturnResponse>(
    `/api/v1/pos/invoices/${invoiceId}/cancel`,
    { method: "POST", headers: { "Idempotency-Key": idempotencyKey }, body: JSON.stringify(body) },
    accessToken
  ),
  getSaleReturn: (accessToken: string, saleReturnId: string) =>
    request<SaleReturnResponse>(`/api/v1/pos/returns/${saleReturnId}`, {}, accessToken),
  searchKhataCustomers: (
    accessToken: string,
    filters: {
      query?: string;
      active?: boolean | null;
      balanceStatus?: KhataBalanceStatus;
      page?: number;
      size?: number;
    }
  ) => {
    const parameters = new URLSearchParams();
    if (filters.query?.trim()) parameters.set("query", filters.query.trim());
    if (filters.active !== null && filters.active !== undefined) {
      parameters.set("active", String(filters.active));
    }
    parameters.set("balanceStatus", filters.balanceStatus ?? "ALL");
    parameters.set("page", String(filters.page ?? 0));
    parameters.set("size", String(filters.size ?? 25));
    return request<InventoryPage<KhataCustomerResponse>>(
      `/api/v1/khata/customers?${parameters.toString()}`,
      {},
      accessToken
    );
  },
  getKhataSummary: (accessToken: string) =>
    request<KhataSummaryResponse>("/api/v1/khata/summary", {}, accessToken),
  getKhataCustomer: (accessToken: string, customerId: string) =>
    request<KhataCustomerResponse>(`/api/v1/khata/customers/${customerId}`, {}, accessToken),
  createKhataCustomer: (
    accessToken: string,
    body: { name: string; phone: string; notes: string }
  ) => request<KhataCustomerResponse>(
    "/api/v1/khata/customers",
    { method: "POST", body: JSON.stringify(body) },
    accessToken
  ),
  updateKhataCustomer: (
    accessToken: string,
    customerId: string,
    body: { name: string; phone: string; notes: string; active: boolean; version: number }
  ) => request<KhataCustomerResponse>(
    `/api/v1/khata/customers/${customerId}`,
    { method: "PUT", body: JSON.stringify(body) },
    accessToken
  ),
  getKhataStatement: (accessToken: string, customerId: string, page = 0, size = 50) =>
    request<InventoryPage<KhataLedgerEntryResponse>>(
      `/api/v1/khata/customers/${customerId}/statement?page=${page}&size=${size}`,
      {},
      accessToken
    ),
  settleKhata: (
    accessToken: string,
    customerId: string,
    idempotencyKey: string,
    body: {
      amount: number;
      paymentMode: SettlementMode;
      reference: string;
      notes: string;
      balanceVersion: number;
    }
  ) => request<KhataSettlementResponse>(
    `/api/v1/khata/customers/${customerId}/settlements`,
    {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify(body)
    },
    accessToken
  ),
  getDashboardReport: (accessToken: string) =>
    request<DashboardReportResponse>("/api/v1/reports/dashboard", {}, accessToken),
  getSalesReport: (accessToken: string, from: string, to: string) => {
    const parameters = new URLSearchParams({ from, to });
    return request<SalesReportResponse>(
      `/api/v1/reports/sales?${parameters.toString()}`,
      {},
      accessToken
    );
  },
  getPurchasingSummary: (accessToken: string) =>
    request<PurchasingSummaryResponse>("/api/v1/purchasing/summary", {}, accessToken),
  searchSuppliers: (
    accessToken: string,
    filters: { query?: string; active?: boolean | null; balanceStatus?: SupplierBalanceStatus; page?: number; size?: number }
  ) => {
    const parameters = new URLSearchParams();
    if (filters.query?.trim()) parameters.set("query", filters.query.trim());
    if (filters.active !== null && filters.active !== undefined) parameters.set("active", String(filters.active));
    parameters.set("balanceStatus", filters.balanceStatus ?? "ALL");
    parameters.set("page", String(filters.page ?? 0));
    parameters.set("size", String(filters.size ?? 25));
    return request<InventoryPage<SupplierResponse>>(
      `/api/v1/purchasing/suppliers?${parameters.toString()}`, {}, accessToken
    );
  },
  createSupplier: (
    accessToken: string,
    body: { name: string; phone: string; gstin: string; address: string; notes: string }
  ) => request<SupplierResponse>(
    "/api/v1/purchasing/suppliers", { method: "POST", body: JSON.stringify(body) }, accessToken
  ),
  updateSupplier: (
    accessToken: string,
    supplierId: string,
    body: { name: string; phone: string; gstin: string; address: string; notes: string; active: boolean; version: number }
  ) => request<SupplierResponse>(
    `/api/v1/purchasing/suppliers/${supplierId}`,
    { method: "PUT", body: JSON.stringify(body) }, accessToken
  ),
  getSupplier: (accessToken: string, supplierId: string) =>
    request<SupplierResponse>(`/api/v1/purchasing/suppliers/${supplierId}`, {}, accessToken),
  getSupplierStatement: (accessToken: string, supplierId: string, page = 0, size = 50) =>
    request<InventoryPage<SupplierLedgerResponse>>(
      `/api/v1/purchasing/suppliers/${supplierId}/statement?page=${page}&size=${size}`, {}, accessToken
    ),
  paySupplier: (
    accessToken: string,
    supplierId: string,
    idempotencyKey: string,
    body: { amount: number; paymentMode: SupplierPaymentMode; reference: string; notes: string; balanceVersion: number }
  ) => request<SupplierPaymentResponse>(
    `/api/v1/purchasing/suppliers/${supplierId}/payments`,
    { method: "POST", headers: { "Idempotency-Key": idempotencyKey }, body: JSON.stringify(body) },
    accessToken
  ),
  searchPurchases: (
    accessToken: string,
    filters: { query?: string; supplierId?: string; from?: string; to?: string; page?: number; size?: number }
  ) => {
    const parameters = new URLSearchParams();
    if (filters.query?.trim()) parameters.set("query", filters.query.trim());
    if (filters.supplierId) parameters.set("supplierId", filters.supplierId);
    if (filters.from) parameters.set("from", filters.from);
    if (filters.to) parameters.set("to", filters.to);
    parameters.set("page", String(filters.page ?? 0));
    parameters.set("size", String(filters.size ?? 25));
    return request<InventoryPage<PurchaseSummaryResponse>>(
      `/api/v1/purchasing/purchases?${parameters.toString()}`, {}, accessToken
    );
  },
  receivePurchase: (
    accessToken: string,
    idempotencyKey: string,
    body: {
      supplierId: string;
      supplierInvoiceNumber: string;
      invoiceDate: string;
      pricesIncludeTax: boolean;
      items: Array<{ productId: string; quantity: number; unitCost: number }>;
      amountPaid: number;
      paymentMode: SupplierPaymentMode | null;
      paymentReference: string;
      notes: string;
    }
  ) => request<PurchaseResponse>(
    "/api/v1/purchasing/purchases",
    { method: "POST", headers: { "Idempotency-Key": idempotencyKey }, body: JSON.stringify(body) },
    accessToken
  ),
  getPurchase: (accessToken: string, purchaseId: string) =>
    request<PurchaseResponse>(`/api/v1/purchasing/purchases/${purchaseId}`, {}, accessToken),
  returnPurchase: (
    accessToken: string,
    purchaseId: string,
    idempotencyKey: string,
    body: {
      returnDate: string;
      reason: PurchaseReturnReason;
      items: Array<{ purchaseItemId: string; quantity: number }>;
      notes: string;
    }
  ) => request<PurchaseReturnResponse>(
    `/api/v1/purchasing/purchases/${purchaseId}/returns`,
    { method: "POST", headers: { "Idempotency-Key": idempotencyKey }, body: JSON.stringify(body) },
    accessToken
  ),
  searchPurchaseReturns: (
    accessToken: string,
    filters: {
      query?: string;
      supplierId?: string;
      purchaseId?: string;
      from?: string;
      to?: string;
      page?: number;
      size?: number;
    }
  ) => {
    const parameters = new URLSearchParams();
    if (filters.query?.trim()) parameters.set("query", filters.query.trim());
    if (filters.supplierId) parameters.set("supplierId", filters.supplierId);
    if (filters.purchaseId) parameters.set("purchaseId", filters.purchaseId);
    if (filters.from) parameters.set("from", filters.from);
    if (filters.to) parameters.set("to", filters.to);
    parameters.set("page", String(filters.page ?? 0));
    parameters.set("size", String(filters.size ?? 25));
    return request<InventoryPage<PurchaseReturnSummaryResponse>>(
      `/api/v1/purchasing/returns?${parameters.toString()}`, {}, accessToken
    );
  },
  getPurchaseReturn: (accessToken: string, purchaseReturnId: string) =>
    request<PurchaseReturnResponse>(
      `/api/v1/purchasing/returns/${purchaseReturnId}`, {}, accessToken
    ),
  getSupplierAnalytics: (accessToken: string, from: string, to: string) => {
    const parameters = new URLSearchParams({ from, to });
    return request<SupplierAnalyticsResponse>(
      `/api/v1/purchasing/analytics?${parameters.toString()}`, {}, accessToken
    );
  }
};
