/**
 * Wallet Service
 * Handles wallet-related API calls
 */

import { api } from "../client";
import type {
  WalletDTO,
  TransactionDTO,
  QuotaDTO,
  TopupRequestDTO,
  SetQuotaRequestDTO,
  WalletStatisticsDTO,
  PaginatedResponseDTO,
} from "../dto";

const WALLET_BASE = "/api/wallet";

export const walletService = {
  /** Get wallet balance */
  getWallet: () => api.get<WalletDTO>(`${WALLET_BASE}`),

  /** Top up wallet */
  topup: (data: TopupRequestDTO) =>
    api.post<TransactionDTO>(`${WALLET_BASE}/topup`, data),

  /** Check if balance is enough */
  checkBalance: (amount: number) =>
    api.get<{ enough: boolean; amount: number }>(
      `${WALLET_BASE}/check`,
      {
        params: { amount },
      }
    ),

  /** Get recent transactions */
  getRecentTransactions: (limit?: number) =>
    api.get<TransactionDTO[]>(`${WALLET_BASE}/transactions`, {
      params: { ...(limit ? { limit } : {}) },
    }),

  /** Get paginated transactions */
  getTransactions: (
    params?: { current?: number; size?: number; type?: string }
  ) =>
    api.get<PaginatedResponseDTO<TransactionDTO>>(
      `${WALLET_BASE}/transactions/page`,
      {
        params: { ...params },
      }
    ),

  /** Get wallet statistics */
  getStatistics: (
    params?: { startDate?: string; endDate?: string }
  ) =>
    api.get<WalletStatisticsDTO>(`${WALLET_BASE}/statistics`, {
      params: { ...params },
    }),

  /** Get my quota */
  getMyQuota: () => api.get<QuotaDTO>(`${WALLET_BASE}/quotas/my`),

  /** Get all member quotas */
  getAllQuotas: () => api.get<QuotaDTO[]>(`${WALLET_BASE}/quotas`),

  /** Set member quota */
  setQuota: (userId: string, data: SetQuotaRequestDTO) =>
    api.put<QuotaDTO>(`${WALLET_BASE}/quotas/${userId}`, data),

  /** Reset member quota */
  resetQuota: (userId: string) =>
    api.post<QuotaDTO>(
      `${WALLET_BASE}/quotas/${userId}/reset`,
      undefined
    ),

  /** Delete member quota */
  deleteQuota: (userId: string) =>
    api.delete<null>(`${WALLET_BASE}/quotas/${userId}`),
};

export default walletService;
