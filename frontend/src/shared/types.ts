export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}
export interface User {
  id: string;
  businessId: string | null;
  email: string;
  displayName: string;
  role: 'SUPER_ADMIN' | 'BUSINESS_USER';
  businessName: string;
  mustChangePassword: boolean;
  lastLoginAt?: string | null;
}
export interface Business {
  id: string;
  name: string;
  taxId: string | null;
  active: boolean;
  createdAt: string;
  planId?: string | null;
  planName?: string | null;
  subscriptionStatus?: SubscriptionStatus | null;
  trialEndsAt?: string | null;
  nextBillingDate?: string | null;
}
export interface AdminUser {
  id: string;
  businessId: string | null;
  businessName: string | null;
  email: string;
  displayName: string;
  role: 'SUPER_ADMIN' | 'BUSINESS_USER';
  active: boolean;
  mustChangePassword: boolean;
  lastLoginAt: string | null;
  createdAt?: string;
}

export type SubscriptionStatus = 'TRIAL' | 'ACTIVE' | 'GRACE_PERIOD' | 'SUSPENDED' | 'CANCELLED';
export type SubscriptionPaymentStatus = 'PENDING' | 'PAID' | 'FAILED' | 'CANCELLED';

export interface SubscriptionPlan {
  id: string;
  name: string;
  description: string | null;
  monthlyPrice: number;
  billingPeriodDays: number;
  gracePeriodDays: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Subscription {
  id: string;
  businessId: string;
  businessName: string;
  planId: string;
  planName: string;
  monthlyPrice: number;
  currency: string;
  status: SubscriptionStatus;
  startedAt: string;
  trialEndsAt: string | null;
  currentPeriodStart: string | null;
  currentPeriodEnd: string | null;
  graceEndsAt: string | null;
  nextBillingDate: string | null;
  suspendedAt: string | null;
  remainingDays: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface SubscriptionPayment {
  id: string;
  subscriptionId: string;
  businessId: string;
  businessName: string;
  amount: number;
  currency: string;
  paymentMethod: 'MANUAL_TRANSFER' | 'MANUAL_CASH' | 'OTHER';
  status: SubscriptionPaymentStatus;
  externalReference: string | null;
  paidAt: string | null;
  createdAt: string;
  updatedAt: string;
}
export interface Category {
  id: string;
  name: string;
  active: boolean;
}
export interface Product {
  id: string;
  businessId: string;
  categoryId: string;
  name: string;
  description: string | null;
  barcode: string | null;
  salePrice: number;
  costPrice: number | null;
  stock: number;
  minimumStock: number;
  imageKey: string | null;
  active: boolean;
}
export type ProductInput = Pick<
  Product,
  | 'categoryId'
  | 'name'
  | 'description'
  | 'barcode'
  | 'salePrice'
  | 'costPrice'
  | 'minimumStock'
  | 'active'
>;
export interface Settings {
  operatingMode: 'POS' | 'INVENTORY_ONLY';
  scannerEnabled: boolean;
  stockAlerts: boolean;
  taxEnabled: boolean;
  taxRate: number;
  generateReceipt: boolean;
  timezone: string;
}
export interface Movement {
  id: string;
  productId: string;
  productName: string;
  type: string;
  quantity: number;
  previousStock: number;
  newStock: number;
  referenceId: string | null;
  performedBy: string;
  createdAt: string;
}
export interface Sale {
  id: string;
  subtotal: number;
  taxAmount: number;
  total: number;
  paymentMethod: string;
  paymentMode: string;
  paymentReference: string | null;
  status: string;
  createdAt: string;
}
export interface SaleItem {
  id: string;
  productId: string;
  productNameSnapshot: string;
  quantity: number;
  unitPrice: number;
  taxAmount: number;
  subtotal: number;
}
export interface SaleDetails {
  sale: Sale;
  items: SaleItem[];
}
export interface Receipt extends SaleDetails {
  businessName: string;
  taxId: string | null;
  timezone: string;
  documentType: string;
  fiscalDocument: boolean;
}
export interface ReportSummary {
  revenue: number;
  saleCount: number;
  unitsSold: number;
  lowStockCount: number;
  topProduct: string | null;
  topCategory: string | null;
  peakHour: number | null;
  inventoryExits: number;
  from: string;
  to: string;
  series: { date: string; total: number; units: number }[];
  hours: { hour: number; total: number; sales: number }[];
}
export interface Notification {
  id: string;
  productId: string;
  productName: string;
  stock: number;
  read: boolean;
  createdAt: string;
}
