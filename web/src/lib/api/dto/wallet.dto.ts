/**
 * Wallet DTOs
 */

export type TransactionType =
  | "TOPUP"
  | "CONSUME"
  | "REFUND"
  | "FREEZE"
  | "UNFREEZE"
  | "GIFT"
  | "TRANSFER_IN"
  | "TRANSFER_OUT";

export type ResetCycle = "DAILY" | "WEEKLY" | "MONTHLY" | "NEVER";

export interface WalletDTO {
  id: string;
  workspaceId: string;
  balance: number;
  available: number;
  frozen: number;
  totalBalance: number;
  totalRecharged: number;
  totalConsumed: number;
  createdAt: string;
  updatedAt: string;
}

export interface TransactionDTO {
  id: string;
  workspaceId: string;
  userId: string | null;
  operatorId: string;
  transactionType: TransactionType;
  amount: number;
  balanceBefore: number;
  balanceAfter: number;
  description: string | null;
  relatedTaskId: string | null;
  meta: Record<string, unknown>;
  createdAt: string;
}

export interface QuotaDTO {
  id: string;
  workspaceId: string;
  userId: string;
  limitAmount: number;
  usedAmount: number;
  remainingAmount: number;
  usagePercentage: number;
  resetCycle: ResetCycle;
  lastResetAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface TopupRequestDTO {
  amount: number;
  description?: string;
  paymentOrderId?: string;
  paymentMethod?: string;
}

export interface SetQuotaRequestDTO {
  limitAmount: number;
  resetCycle?: ResetCycle;
}

export interface WalletStatisticsDTO {
  balance: number;
  frozen: number;
  totalRecharged: number;
  totalConsumed: number;
  startDate: string;
  endDate: string;
  byType: Record<TransactionType, number>;
}
