/**
 * Wallet Store
 * Tracks real-time wallet balance via WebSocket WALLET_BALANCE_CHANGED messages.
 *
 * State is NOT persisted — the initial balance is fetched via REST API,
 * then kept in sync by WebSocket pushes.
 */

import { create } from "zustand";
import type { WalletTransactionType } from "@/lib/websocket/types";
import type { WalletDTO, QuotaDTO } from "@/lib/api/dto";

// ============================================================================
// Types
// ============================================================================

export interface WalletStoreState {
  /** 可用余额 */
  balance: number;
  /** 冻结金额 */
  frozen: number;
  /** Full wallet DTO from REST (for header display) */
  wallet: WalletDTO | null;
  /** Quota DTO from REST (for header display) */
  quota: QuotaDTO | null;
  /** Whether the store has been initialized with data from REST API */
  initialized: boolean;
}

export interface WalletStoreActions {
  /** Set balance from REST API (initial load) */
  setBalance: (balance: number, frozen: number) => void;
  /** Set full wallet + quota from REST API (header initial load) */
  setWalletAndQuota: (wallet: WalletDTO, quota: QuotaDTO) => void;
  /** Apply a real-time update from WebSocket */
  applyBalanceChanged: (balance: number, frozen: number, delta: number, transactionType: WalletTransactionType) => void;
  /** Full reset on workspace change / logout */
  reset: () => void;
}

export type WalletStore = WalletStoreState & WalletStoreActions;

// ============================================================================
// Constants
// ============================================================================

const INITIAL_STATE: WalletStoreState = {
  balance: 0,
  frozen: 0,
  wallet: null,
  quota: null,
  initialized: false,
};

// ============================================================================
// Store
// ============================================================================

export const useWalletStore = create<WalletStore>((set) => ({
  ...INITIAL_STATE,

  setBalance: (balance, frozen) =>
    set({ balance, frozen, initialized: true }),

  setWalletAndQuota: (wallet, quota) =>
    set({ wallet, quota, balance: wallet.available, frozen: wallet.frozen, initialized: true }),

  applyBalanceChanged: (balance, frozen, delta) =>
    set((state) => {
      // Update the stored wallet/quota objects to keep header in sync
      const updatedWallet = state.wallet
        ? { ...state.wallet, available: balance, frozen }
        : state.wallet;
      // When credits are consumed (delta < 0), reduce quota remaining accordingly
      const updatedQuota = state.quota && delta < 0
        ? { ...state.quota, remainingAmount: Math.max(0, state.quota.remainingAmount + delta) }
        : state.quota;
      return { balance, frozen, wallet: updatedWallet, quota: updatedQuota, initialized: true };
    }),

  reset: () => set(INITIAL_STATE),
}));

// ============================================================================
// Selectors
// ============================================================================

export const walletSelectors = {
  balance: (state: WalletStore) => state.balance,
  frozen: (state: WalletStore) => state.frozen,
  totalBalance: (state: WalletStore) => state.balance + state.frozen,
  initialized: (state: WalletStore) => state.initialized,
};

// ============================================================================
// Convenience Hooks
// ============================================================================

export function useWalletBalance() {
  const balance = useWalletStore(walletSelectors.balance);
  const frozen = useWalletStore(walletSelectors.frozen);
  const totalBalance = useWalletStore(walletSelectors.totalBalance);
  const initialized = useWalletStore(walletSelectors.initialized);
  return { balance, frozen, totalBalance, initialized };
}

export function useWalletActions() {
  const setBalance = useWalletStore((s) => s.setBalance);
  const applyBalanceChanged = useWalletStore((s) => s.applyBalanceChanged);
  const reset = useWalletStore((s) => s.reset);
  return { setBalance, applyBalanceChanged, reset };
}

// ============================================================================
// Non-hook helpers (for use outside React components, e.g. WebSocket provider)
// ============================================================================

export function getWalletStore() {
  return useWalletStore.getState();
}
