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
