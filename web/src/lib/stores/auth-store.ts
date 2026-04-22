/**
 * Auth Token Store
 * Manages authentication session using Zustand + persist
 */

import { create } from "zustand";
import { persist } from "zustand/middleware";
import { createPersistStorage } from "./persist-storage";

export interface TokenBundle {
  accessToken: string;
  refreshToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  refreshExpiresIn: number;
  sessionId: string;
  workspaceId: string | null;
}

interface TokenClaims {
  userId?: string;
  workspaceId?: string | null;
}

interface AuthState {
  tokenBundle: TokenBundle | null;
  userId: string | null;
  setTokenBundle: (bundle: TokenBundle | null) => void;
  setUserId: (userId: string | null) => void;
  setAuthData: (bundle: TokenBundle, userId?: string | null) => void;
  clearAuthTokens: () => void;
}

type PersistedAuthState = Pick<AuthState, "tokenBundle" | "userId">;

function parseJwtClaims(accessToken: string): TokenClaims | null {
  const segments = accessToken.split(".");
  if (segments.length < 2) return null;

  try {
    const base64 = segments[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64.padEnd(
      base64.length + ((4 - (base64.length % 4)) % 4),
      "="
    );

    if (typeof atob !== "function") return null;
    return JSON.parse(atob(padded)) as TokenClaims;
  } catch {
    return null;
  }
}

function normalizeTokenBundle(input: TokenBundle): TokenBundle {
  const claims = parseJwtClaims(input.accessToken);
  const workspaceIdFromClaim =
    claims && Object.prototype.hasOwnProperty.call(claims, "workspaceId")
      ? claims.workspaceId ?? null
      : undefined;

  return {
    accessToken: input.accessToken,
    refreshToken: input.refreshToken,
    tokenType: "Bearer",
    expiresIn: input.expiresIn ?? 7200,
    refreshExpiresIn: input.refreshExpiresIn ?? 604800,
    sessionId: input.sessionId,
    workspaceId:
      workspaceIdFromClaim === undefined
        ? (input.workspaceId ?? null)
        : workspaceIdFromClaim,
  };
}

function resolveUserId(bundle: TokenBundle, userId?: string | null): string | null {
  if (userId) return userId;
  const claims = parseJwtClaims(bundle.accessToken);
  return claims?.userId ?? null;
}

function mergePersistedAuthState(
  persistedState: unknown,
  currentState: AuthState
): AuthState {
  const persisted = (persistedState as Partial<PersistedAuthState> | null) ?? null;
  const runtimeToken = currentState.tokenBundle;

  // Runtime token (e.g. just-login/switch/refresh) must win over stale persisted token.
  if (runtimeToken) {
    return {
      ...currentState,
      tokenBundle: runtimeToken,
      userId: currentState.userId ?? resolveUserId(runtimeToken),
    };
  }

  const persistedToken =
    persisted?.tokenBundle ? normalizeTokenBundle(persisted.tokenBundle) : null;
  const persistedUserId =
    typeof persisted?.userId === "string" || persisted?.userId === null
      ? persisted.userId
      : null;

  return {
    ...currentState,
    tokenBundle: persistedToken,
    userId: persistedUserId ?? (persistedToken ? resolveUserId(persistedToken) : null),
  };
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      tokenBundle: null,
      userId: null,
      setTokenBundle: (bundle) =>
        set({
          tokenBundle: bundle ? normalizeTokenBundle(bundle) : null,
        }),
      setUserId: (userId) => set({ userId }),
      setAuthData: (bundle, userId) =>
        set(() => {
          const normalizedBundle = normalizeTokenBundle(bundle);
          return {
            tokenBundle: normalizedBundle,
            userId: resolveUserId(normalizedBundle, userId),
          };
        }),
      clearAuthTokens: () =>
        set({
          tokenBundle: null,
          userId: null,
        }),
    }),
    {
      name: "actionow_auth_store",
      storage: createPersistStorage<PersistedAuthState>(),
      partialize: (state) => ({
        tokenBundle: state.tokenBundle,
        userId: state.userId,
      }),
      merge: mergePersistedAuthState,
    }
  )
);

export function getTokenBundle(): TokenBundle | null {
  return useAuthStore.getState().tokenBundle;
}

export function getAuthToken(): string | null {
  return useAuthStore.getState().tokenBundle?.accessToken ?? null;
}

export function getRefreshToken(): string | null {
  return useAuthStore.getState().tokenBundle?.refreshToken ?? null;
}

export function getUserId(): string | null {
  return useAuthStore.getState().userId;
}

export function getTokenWorkspaceId(): string | null {
  return useAuthStore.getState().tokenBundle?.workspaceId ?? null;
}

export function getTokenSessionId(): string | null {
  return useAuthStore.getState().tokenBundle?.sessionId ?? null;
}

export function setUserId(userId: string): void {
  useAuthStore.getState().setUserId(userId);
}

export function setAuthBundle(bundle: TokenBundle, userId?: string | null): void {
  useAuthStore.getState().setAuthData(bundle, userId);
}

export function clearAuthTokens(): void {
  useAuthStore.getState().clearAuthTokens();
}

export function isAuthenticated(): boolean {
  return !!useAuthStore.getState().tokenBundle?.accessToken;
}
