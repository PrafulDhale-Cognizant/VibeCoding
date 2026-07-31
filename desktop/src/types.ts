export type UserRole = "OWNER" | "ADMIN" | "CASHIER" | "INVENTORY_MANAGER" | "VIEWER";
export type ReceiptWidth = "MM_58" | "MM_80";

export interface UserSummary {
  id: string;
  username: string;
  displayName: string;
  roles: UserRole[];
  active: boolean;
  lastLoginAt: string | null;
  version: number;
}

export interface AuthResponse {
  tokenType: "Bearer";
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
  user: UserSummary;
}

export interface SetupStatus {
  configured: boolean;
  shopName: string | null;
}

export interface StoreProfile {
  shopName: string;
  ownerName: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  stateName: string;
  stateCode: string;
  postalCode: string;
  phone: string;
  email: string;
  gstRegistered: boolean;
  gstin: string;
  currencyCode: "INR";
  timezone: "Asia/Kolkata";
  invoicePrefix: string;
  financialYearStartMonth: number;
  receiptWidth: ReceiptWidth;
}

export interface StoreDetails extends Omit<StoreProfile, "addressLine2" | "email" | "gstin"> {
  addressLine2: string | null;
  email: string | null;
  gstin: string | null;
  logoAvailable: boolean;
  version: number;
  setupCompletedAt: string;
  updatedAt: string;
}

export interface InitialSetupRequest {
  store: StoreProfile;
  owner: {
    username: string;
    displayName: string;
    password: string;
  };
  dataResponsibilityAccepted: boolean;
}

export type ProductUnit =
  | "PIECE"
  | "KILOGRAM"
  | "GRAM"
  | "LITRE"
  | "MILLILITRE"
  | "PACKET"
  | "BOX"
  | "DOZEN";

export type StockStatus = "ALL" | "IN_STOCK" | "LOW_STOCK" | "OUT_OF_STOCK";
export type ProductSort = "NAME_ASC" | "NAME_DESC" | "UPDATED_DESC" | "PRICE_ASC" | "STOCK_ASC";
export type StockAdjustmentReasonCode =
  | "PHYSICAL_COUNT"
  | "DAMAGE"
  | "EXPIRY"
  | "THEFT_LOSS"
  | "FOUND_STOCK"
  | "DATA_CORRECTION"
  | "OTHER";
export type StockReasonCode =
  | StockAdjustmentReasonCode
  | "OPENING_STOCK"
  | "SALE"
  | "SALE_RETURN";

export interface CategoryResponse {
  id: string;
  name: string;
  active: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface UnitResponse {
  code: ProductUnit;
  displayName: string;
  symbol: string;
  decimalAllowed: boolean;
}

export interface ProductResponse {
  id: string;
  name: string;
  receiptName: string;
  sku: string | null;
  barcode: string;
  internalBarcode: boolean;
  category: CategoryResponse;
  unit: ProductUnit;
  hsnCode: string | null;
  gstRate: number;
  purchaseCost: number;
  sellingPrice: number;
  stockQuantity: number;
  minimumStockLevel: number;
  stockStatus: Exclude<StockStatus, "ALL">;
  active: boolean;
  version: number;
  stockVersion: number;
  createdAt: string;
  updatedAt: string;
}

export interface ProductLookupResponse {
  id: string;
  name: string;
  receiptName: string;
  barcode: string;
  unit: ProductUnit;
  gstRate: number;
  sellingPrice: number;
  stockQuantity: number;
  active: boolean;
}

export interface ProductAlertResponse {
  productId: string;
  name: string;
  sku: string | null;
  unit: ProductUnit;
  stockQuantity: number;
  minimumStockLevel: number;
  suggestedReorderQuantity: number;
  stockStatus: "LOW_STOCK" | "OUT_OF_STOCK";
}

export interface InventoryPage<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface ProductCreateRequest {
  name: string;
  receiptName: string;
  sku: string;
  barcode: string;
  generateBarcode: boolean;
  categoryId: string;
  unit: ProductUnit;
  hsnCode: string;
  gstRate: number;
  purchaseCost: number;
  sellingPrice: number;
  openingStock: number;
  minimumStockLevel: number;
}

export interface ProductUpdateRequest extends Omit<ProductCreateRequest, "generateBarcode" | "openingStock"> {
  active: boolean;
  version: number;
}

export interface StockTransactionResponse {
  id: string;
  productId: string;
  transactionType: "OPENING" | "ADJUSTMENT" | "PURCHASE" | "PURCHASE_RETURN" | "SALE" | "SALE_RETURN" | "CORRECTION";
  quantityDelta: number;
  balanceAfter: number;
  reasonCode: StockReasonCode;
  referenceType: string | null;
  referenceId: string | null;
  notes: string | null;
  actorUserId: string | null;
  occurredAt: string;
}

export type DiscountType = "NONE" | "PERCENTAGE" | "FIXED";
export type TaxMode = "INTRA_STATE" | "INTER_STATE";
export type PaymentMode = "CASH" | "UPI" | "CARD" | "UDHAAR";

export interface PosCartItemRequest {
  productId: string;
  quantity: number;
  discountType: DiscountType;
  discountValue: number;
}

export interface PosQuoteRequest {
  items: PosCartItemRequest[];
  billDiscountType: DiscountType;
  billDiscountValue: number;
  taxMode: TaxMode;
}

export interface PosQuoteLine {
  lineNumber: number;
  productId: string;
  name: string;
  receiptName: string;
  barcode: string;
  unit: ProductUnit;
  quantity: number;
  availableQuantity: number | null;
  unitPrice: number;
  gstRate: number;
  grossAmount: number;
  lineDiscountAmount: number;
  billDiscountAmount: number;
  taxableAmount: number;
  cgstAmount: number;
  sgstAmount: number;
  igstAmount: number;
  lineTotal: number;
}

export interface PosQuoteResponse {
  lines: PosQuoteLine[];
  taxMode: TaxMode;
  pricesIncludeGst: boolean;
  subtotalAmount: number;
  lineDiscountAmount: number;
  billDiscountAmount: number;
  taxableAmount: number;
  cgstAmount: number;
  sgstAmount: number;
  igstAmount: number;
  roundOffAmount: number;
  totalAmount: number;
}

export interface PosPaymentRequest {
  mode: PaymentMode;
  amount: number;
  tenderedAmount?: number;
  reference?: string;
  customerId?: string;
}

export interface PosInvoiceResponse {
  id: string;
  invoiceNumber: string;
  status: "COMPLETED" | "CANCELLED";
  cashierUserId: string;
  completedAt: string;
  notes: string | null;
  store: {
    shopName: string;
    address: string;
    phone: string;
    gstin: string | null;
    receiptWidth: ReceiptWidth;
  };
  totals: PosQuoteResponse;
  payments: Array<{
    mode: PaymentMode;
    amount: number;
    tenderedAmount: number | null;
    changeAmount: number;
    reference: string | null;
    customerId: string | null;
    customerName: string | null;
  }>;
  idempotentReplay: boolean;
}

export type KhataBalanceStatus = "ALL" | "DUE" | "CLEAR";
export type KhataEntryType = "CREDIT_SALE" | "SETTLEMENT";
export type SettlementMode = "CASH" | "UPI" | "CARD";

export interface KhataCustomerResponse {
  id: string;
  name: string;
  phone: string;
  notes: string | null;
  active: boolean;
  outstandingAmount: number;
  version: number;
  balanceVersion: number;
  createdAt: string;
  updatedAt: string;
}

export interface KhataLedgerEntryResponse {
  id: string;
  customerId: string;
  entryType: KhataEntryType;
  amount: number;
  balanceAfter: number;
  invoiceId: string | null;
  paymentMode: SettlementMode | null;
  paymentReference: string | null;
  notes: string | null;
  actorUserId: string;
  occurredAt: string;
}

export interface KhataSummaryResponse {
  totalOutstanding: number;
  customersWithDue: number;
  activeCustomers: number;
}

export interface KhataSettlementResponse {
  entryId: string;
  customerId: string;
  amount: number;
  balanceAfter: number;
  paymentMode: SettlementMode;
  occurredAt: string;
  idempotentReplay: boolean;
}

export interface SalesSummaryResponse {
  billCount: number;
  subtotalAmount: number;
  discountAmount: number;
  taxableAmount: number;
  cgstAmount: number;
  sgstAmount: number;
  igstAmount: number;
  totalTax: number;
  roundOffAmount: number;
  totalSales: number;
  snapshotCost: number;
  grossMargin: number;
  paymentTotals: Record<PaymentMode, number>;
}

export interface DailySalesResponse {
  businessDate: string;
  billCount: number;
  totalSales: number;
  snapshotCost: number;
  grossMargin: number;
}

export interface ReportStockAlertResponse {
  productId: string;
  name: string;
  sku: string;
  unit: ProductUnit;
  stockQuantity: number;
  minimumStockLevel: number;
  suggestedReorderQuantity: number;
}

export interface DashboardReportResponse {
  businessDate: string;
  timezone: string;
  shopName: string;
  today: SalesSummaryResponse;
  inventory: {
    lowStockCount: number;
    outOfStockCount: number;
    lowStockItems: ReportStockAlertResponse[];
    outOfStockItems: ReportStockAlertResponse[];
  };
  credit: KhataSummaryResponse;
  generatedAt: string;
}

export interface SalesReportResponse {
  from: string;
  to: string;
  timezone: string;
  shopName: string;
  summary: SalesSummaryResponse;
  dailySales: DailySalesResponse[];
  generatedAt: string;
}
