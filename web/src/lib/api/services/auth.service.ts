/**
 * Auth Service
 * Handles authentication-related API calls
 */

import {
  api,
  beginWorkspaceSwitchTransition,
  endWorkspaceSwitchTransition,
  resetApiClientSessionState,
} from "../client";
import { authTrace, summarizeToken } from "../auth-trace";
import { setAuthBundle, getUserId } from "@/lib/stores/auth-store";
import type {
  CaptchaDTO,
  TokenDTO,
  AuthResponseDTO,
  SendVerifyCodeRequestDTO,
  SendVerifyCodeResponseDTO,
  RegisterRequestDTO,
  LoginRequestDTO,
  LoginWithCodeRequestDTO,
  UserDTO,
  WorkspaceDTO,
} from "../dto";
import { normalizeToken } from "../token-normalizer";

const AUTH_BASE = "/api/user/auth";
let ensureWorkspaceBoundPromise: Promise<TokenDTO> | null = null;
const TOKEN_ACTIVATION_MAX_ATTEMPTS = 6;
const TOKEN_ACTIVATION_RETRY_DELAY_MS = 250;
const SWITCH_TOKEN_REISSUE_MAX_ATTEMPTS = 2;
const SWITCH_TOKEN_REISSUE_DELAY_MS = 1100;

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

async function waitForAccessTokenActivation(accessToken: string): Promise<boolean> {
  for (let attempt = 1; attempt <= TOKEN_ACTIVATION_MAX_ATTEMPTS; attempt += 1) {
    try {
      const response = await fetch(`${AUTH_BASE}/me`, {
        method: "GET",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });

      if (response.ok) {
        const result = (await response.json().catch(() => null)) as
          | { code?: string | number }
          | null;
        if (!result || String(result.code ?? "") === "0") {
          authTrace("token-activation:ready", { attempt });
          return true;
        }
      }

      authTrace("token-activation:retry", {
        attempt,
        status: response.status,
      });
    } catch {
      authTrace("token-activation:retry", {
        attempt,
        status: "network_error",
      });
    }

    if (attempt < TOKEN_ACTIVATION_MAX_ATTEMPTS) {
      await sleep(TOKEN_ACTIVATION_RETRY_DELAY_MS);
    }
  }

  authTrace("token-activation:timeout");
  return false;
}

async function requestSwitchWorkspaceToken(workspaceId: string): Promise<TokenDTO> {
  return normalizeToken(
    await api.post<unknown>(
      `${AUTH_BASE}/workspaces/${workspaceId}/switch`,
      undefined,
      { _skipTokenRefresh: true }
    )
  );
}

async function stabilizeSwitchedToken(token: TokenDTO, workspaceId: string): Promise<TokenDTO> {
  let candidate = token;

  for (let attempt = 1; attempt <= SWITCH_TOKEN_REISSUE_MAX_ATTEMPTS + 1; attempt += 1) {
    const activated = await waitForAccessTokenActivation(candidate.accessToken);
    if (activated) return candidate;

    authTrace("token-activation:retry-switch", {
      attempt,
      workspaceId,
      token: summarizeToken(candidate.accessToken),
    });

    if (attempt <= SWITCH_TOKEN_REISSUE_MAX_ATTEMPTS) {
      await sleep(SWITCH_TOKEN_REISSUE_DELAY_MS);
      candidate = await requestSwitchWorkspaceToken(workspaceId);
    }
  }

  throw new Error("Workspace switch token not yet active. Please try again later.");
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === "object";
}

function normalizeAuthResponse(payload: unknown): AuthResponseDTO {
  if (!isRecord(payload) || !isRecord(payload.user)) {
    throw new Error("Invalid auth response");
  }

  const user = payload.user as Record<string, unknown>;
  if (typeof user.id !== "string") {
    throw new Error("Invalid auth response");
  }

  return {
    user: user as unknown as UserDTO,
    token: normalizeToken(payload),
  };
}

export const authService = {
  /** Get captcha image and key */
  getCaptcha: () => api.get<CaptchaDTO>(`${AUTH_BASE}/captcha`),

  /** Send verification code to email/phone */
  sendVerifyCode: (data: SendVerifyCodeRequestDTO) =>
    api.post<SendVerifyCodeResponseDTO>(`${AUTH_BASE}/verify-code`, data),

  /** Register a new user */
  register: async (data: RegisterRequestDTO) =>
    normalizeAuthResponse(await api.post<unknown>(`${AUTH_BASE}/register`, data)),

  /** Login with account (username/email/phone) and password */
  login: async (data: LoginRequestDTO) =>
    normalizeAuthResponse(await api.post<unknown>(`${AUTH_BASE}/login`, data)),

  /** Login with verification code */
  loginWithCode: async (data: LoginWithCodeRequestDTO) =>
    normalizeAuthResponse(await api.post<unknown>(`${AUTH_BASE}/login/code`, data)),

  /** Refresh access token */
  refreshToken: async (refreshToken: string) =>
    normalizeToken(await api.post<unknown>(`${AUTH_BASE}/refresh`, { refreshToken })),

  /** Switch workspace and receive new token bundle */
  switchWorkspace: async (workspaceId: string) => {
    const token = await requestSwitchWorkspaceToken(workspaceId);
    return stabilizeSwitchedToken(token, workspaceId);
  },

  /** List workspaces and switch to the first one when token is unbound */
  ensureWorkspaceBound: async (token: TokenDTO, userId?: string | null): Promise<TokenDTO> => {
    if (token.workspaceId) return token;
    if (ensureWorkspaceBoundPromise) return ensureWorkspaceBoundPromise;
    authTrace("ensure-workspace-bound:start", {
      token: summarizeToken(token.accessToken),
    });

    ensureWorkspaceBoundPromise = (async () => {
      const workspaces = await api.get<WorkspaceDTO[]>("/api/workspaces", {
        _skipTokenRefresh: true,
      });
      authTrace("ensure-workspace-bound:workspaces-loaded", {
        count: workspaces.length,
      });
      if (!workspaces.length) return token;

      beginWorkspaceSwitchTransition();
      try {
        authTrace("ensure-workspace-bound:switch-start", {
          workspaceId: workspaces[0].id,
        });
        const switchedToken = await requestSwitchWorkspaceToken(workspaces[0].id);
        const readyToken = await stabilizeSwitchedToken(switchedToken, workspaces[0].id);
        // Must update token before releasing switch barrier.
        setAuthBundle(readyToken, userId ?? getUserId());
        resetApiClientSessionState();
        authTrace("ensure-workspace-bound:switch-success", {
          token: summarizeToken(readyToken.accessToken),
        });
        return readyToken;
      } finally {
        endWorkspaceSwitchTransition();
      }
    })();

    try {
      return await ensureWorkspaceBoundPromise;
    } finally {
      ensureWorkspaceBoundPromise = null;
    }
  },

  /** Public helper for callers that receive mixed token payload shapes */
  normalizeToken,

  /** Logout current user */
  logout: () => api.post<null>(`${AUTH_BASE}/logout`),

  /** Logout from all devices */
  logoutAll: () => api.post<null>(`${AUTH_BASE}/logout-all`),

  /** Get current user info */
  getCurrentUser: () => api.get<UserDTO>(`${AUTH_BASE}/me`),
};

export default authService;
