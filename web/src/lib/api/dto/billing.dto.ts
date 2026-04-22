/**
 * Billing DTOs
 * Based on aicraft-billing API (port 8092)
 */

// ── Enums ──────────────────────────────────────────────────────────────────────

export type PaymentProvider = "STRIPE" | "WECHATPAY";

export type OrderStatus =
  | "INIT"
  | "PENDING"
  | "PAID"
  | "FAILED"
  | "CANCELED"
  | "EXPIRED"
  | "REFUNDED"
  | "PARTIALLY_REFUNDED";

export type SubscriptionStatus =
  | "TRIALING"
  | "ACTIVE"
  | "PAST_DUE"
  | "CANCELED"
  | "EXPIRED";

export type BillingCycle = "MONTHLY" | "YEARLY";

// ── Topup Requests ─────────────────────────────────────────────────────────────

export interface CreateTopupOrderRequest {
  amountMinor: number;
  currency: string;
  provider: PaymentProvider;
  pointsAmount?: number;
  paymentMethod?: string;
  description?: string;
}

export interface CreateCheckoutSessionRequest {
  successUrl?: string;
  cancelUrl?: string;
  clientReferenceId?: string;
}

// ── Topup Responses ────────────────────────────────────────────────────────────

export interface CreateTopupOrderResponse {
  orderNo: string;
  status: OrderStatus;
  workspaceId: string;
  amountMinor: number;
  currency: string;
  provider: PaymentProvider;
  pointsAmount: number;
}

export interface CreateCheckoutSessionResponse {
  orderNo: string;
  providerSessionId: string;
  checkoutUrl: string;
  expiresAt: string;
}

export interface OrderDetailResponse {
  orderNo: string;
  workspaceId: string;
  userId: string;
  provider: PaymentProvider;
  orderType: string;
  status: OrderStatus;
  amountMinor: number;
  currency: string;
  pointsAmount: number;
  providerPaymentId: string | null;
  providerSessionId: string | null;
  failCode: string | null;
  failMessage: string | null;
  paidAt: string | null;
  createdAt: string;
  updatedAt: string;
  meta: Record<string, unknown>;
}

// ── Plan / Subscription ────────────────────────────────────────────────────────

export interface PlanPriceResponse {
  id: string;
  provider: PaymentProvider;
  planCode: string;
  workspacePlanType: string | null;
  billingCycle: BillingCycle | null;
  currency: string;
  amountMinor: number;
  metered: boolean | null;
  usageType: string | null;
  stripeProductId: string | null;
  stripePriceId: string | null;
  status: string;
  meta: Record<string, unknown> | null;
  createdAt: string;
  updatedAt: string;
}

export interface SubscriptionResponse {
  subscriptionId: string;
  workspaceId: string;
  provider: PaymentProvider;
  planCode: string;
  billingCycle: BillingCycle | null;
  status: SubscriptionStatus;
  currentPeriodStart: string | null;
  currentPeriodEnd: string | null;
  cancelAtPeriodEnd: boolean | null;
  gracePeriodEnd: string | null;
}

// ── Exchange Rate Config ───────────────────────────────────────────────────────

export interface TopupRateConfig {
  currency: string;
  pointsPerMajorUnit: number;
  minorPerMajorUnit: number;
}