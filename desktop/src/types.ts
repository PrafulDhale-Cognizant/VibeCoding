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
export type StockReasonCode =
  | "PHYSICAL_COUNT"
  | "DAMAGE"
  | "EXPIRY"
  | "THEFT_LOSS"
  | "FOUND_STOCK"
  | "DATA_CORRECTION"
  | "OTHER";

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
  reasonCode: StockReasonCode | "OPENING_STOCK";
  referenceType: string | null;
  referenceId: string | null;
  notes: string | null;
  actorUserId: string | null;
  occurredAt: string;
}
