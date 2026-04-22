/**
 * Generic API Client
 * Features:
 * - Token refresh single-flight with request queue replay
 * - Request deduplication for GET requests
 * - Timeout handling
 * - Business response code handling (code === "0")
 */

import {
  useAuthStore,
  getAuthToken,
  getRefreshToken,
  getUserId,
  setAuthBundle,
  clearAuthTokens,
} from "@/lib/stores/auth-store";
import { ApiResponse, ApiError, RequestOptions, ERROR_CODES } from "./types";
import { normalizeToken } from "./token-normalizer";
import { authTrace, summarizeToken } from "./auth-trace";

// Use empty string for client-side (goes through Next.js proxy route)
const API_BASE_URL = "";

let isRefreshing = false;

const failedRequestsQueue: Array<{
  resolve: (value: boolean) => void;
  reject: (reason: unknown) => void;
}> = [];

const pendingRequests = new Map<string, Promise<unknown>>();
const recentGetResponses = new Map<
  string,
  {
    expiresAt: number;
    value: unknown;
  }
>();
const DEFAULT_GET_DEDUP_TTL_MS = 800;
const WORKSPACE_SWITCH_AUTH_GRACE_MS = 2500;
const WORKSPACE_SWITCH_AUTH_GRACE_RETRY_MAX = 5;
const WORKSPACE_SWITCH_AUTH_GRACE_RETRY_DELAY_MS = 200;
let isWorkspaceSwitching = false;
let workspaceSwitchBarrier: Promise<void> | null = null;
let releaseWorkspaceSwitchBarrier: (() => void) | null = null;
let workspaceSwitchVersion = 0;
let lastWorkspaceSwitchEndedAt = 0;
let authHydrationPromise: Promise<void> | null = null;
let requestTraceSequence = 0;

const PUBLIC_AUTH_PATTERNS: RegExp[] = [
  /^\/api\/user\/auth\/captcha$/,
  /^\/api\/user\/auth\/verify-code$/,
  /^\/api\/user\/auth\/register$/,
  /^\/api\/user\/auth\/login$/,
  /^\/api\/user\/auth\/login\/code$/,
  /^\/api\/user\/auth\/refresh$/,
  /^\/api\/user\/oauth\/[^/]+\/authorize$/,
  /^\/api\/user\/oauth\/[^/]+\/callback$/,
  /^\/api\/user\/oauth\/login-config$/,
];

function isPublicAuthEndpoint(endpoint: string): boolean {
  return PUBLIC_AUTH_PATTERNS.some((pattern) => pattern.test(endpoint));
}

function isRefreshEndpoint(endpoint: string): boolean {
  return endpoint === "/api/user/auth/refresh";
}

function isWorkspaceSwitchEndpoint(endpoint: string): boolean {
  return /^\/api\/user\/auth\/workspaces\/[^/]+\/switch$/.test(endpoint);
}

function shouldBypassWorkspaceSwitchBarrier(endpoint: string): boolean {
  return isPublicAuthEndpoint(endpoint) || isWorkspaceSwitchEndpoint(endpoint);
}

async function waitForWorkspaceSwitchIfNeeded(endpoint: string): Promise<void> {
  if (!isWorkspaceSwitching) return;
  if (shouldBypassWorkspaceSwitchBarrier(endpoint)) return;
  if (!workspaceSwitchBarrier) return;
  authTrace("workspace-switch:wait", { endpoint, workspaceSwitchVersion });
  await workspaceSwitchBarrier;
  authTrace("workspace-switch:resume", { endpoint, workspaceSwitchVersion });
}

async function waitForAuthHydrationIfNeeded(endpoint: string): Promise<void> {
  if (typeof window === "undefined") return;
  if (isPublicAuthEndpoint(endpoint)) return;
  if (useAuthStore.persist.hasHydrated()) return;

  if (!authHydrationPromise) {
    authHydrationPromise = new Promise<void>((resolve) => {
      const unsubscribe = useAuthStore.persist.onFinishHydration(() => {
        unsubscribe();
        authHydrationPromise = null;
        resolve();
      });

      // Handle race: hydration may finish between hasHydrated() check and listener registration.
      if (useAuthStore.persist.hasHydrated()) {
        unsubscribe();
        authHydrationPromise = null;
        resolve();
      }
    });
  }

  await authHydrationPromise;
}

function normalizeCode(code: string | number | undefined): string {
  return String(code ?? "");
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

function isBusinessAuthFailureCode(code: string): boolean {
  return (
    code === ERROR_CODES.NOT_AUTHENTICATED ||
    code === ERROR_CODES.TOKEN_INVALID_V2 ||
    code === "401" ||
    code === "40101" ||
    code === "40102" ||
    code === "40103"
  );
}

function isWorkspaceAuthError(code: string, authError: string | null): boolean {
  if (code === ERROR_CODES.WORKSPACE_NOT_MEMBER || code === ERROR_CODES.WORKSPACE_NO_PERMISSION) {
    return true;
  }

  const normalizedAuthError = (authError || "").toLowerCase();
  return normalizedAuthError.includes("workspace") && normalizedAuthError.includes("mismatch");
}

function notifyWorkspaceSelectionRequired(): void {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new Event("workspaceAuthMismatch"));
}

/**
 * Reset in-memory API client session state.
 */
export function resetApiClientSessionState(): void {
  authTrace("api-session:reset", {
    pendingRequests: pendingRequests.size,
    recentGetResponses: recentGetResponses.size,
    failedQueue: failedRequestsQueue.length,
    isWorkspaceSwitching,
    workspaceSwitchVersion,
  });
  pendingRequests.clear();
  recentGetResponses.clear();
  failedRequestsQueue.length = 0;
  isRefreshing = false;
}

export function beginWorkspaceSwitchTransition(): void {
  if (isWorkspaceSwitching) return;
  isWorkspaceSwitching = true;
  workspaceSwitchVersion += 1;
  authTrace("workspace-switch:begin", { workspaceSwitchVersion });
  workspaceSwitchBarrier = new Promise<void>((resolve) => {
    releaseWorkspaceSwitchBarrier = resolve;
  });
}

export function endWorkspaceSwitchTransition(): void {
  if (!isWorkspaceSwitching) return;
  isWorkspaceSwitching = false;
  lastWorkspaceSwitchEndedAt = Date.now();
  authTrace("workspace-switch:end", {
    workspaceSwitchVersion,
    endedAt: lastWorkspaceSwitchEndedAt,
  });
  releaseWorkspaceSwitchBarrier?.();
  releaseWorkspaceSwitchBarrier = null;
  workspaceSwitchBarrier = null;
}

function processQueue(success: boolean, error?: unknown): void {
  authTrace("refresh:queue:drain", {
    success,
    queueLength: failedRequestsQueue.length,
    error: error instanceof Error ? error.message : String(error ?? ""),
  });
  failedRequestsQueue.forEach(({ resolve, reject }) => {
    if (success) {
      resolve(true);
    } else {
      reject(error);
    }
  });
  failedRequestsQueue.length = 0;
}

interface RefreshAccessTokenResult {
  success: boolean;
  shouldLogout: boolean;
  error?: ApiError;
}

function createRefreshFailureResult(
  shouldLogout: boolean,
  status: number,
  message: string,
  code: number | string = status
): RefreshAccessTokenResult {
  return {
    success: false,
    shouldLogout,
    error: new ApiError(code, message, status),
  };
}

async function refreshAccessToken(): Promise<RefreshAccessTokenResult> {
  const currentRefreshToken = getRefreshToken();
  if (!currentRefreshToken) {
    return createRefreshFailureResult(true, 401, "登录已过期，请重新登录", 401);
  }
  authTrace("refresh:start", {
    refreshTokenPrefix: currentRefreshToken.slice(0, 16),
    currentAccess: summarizeToken(getAuthToken()),
  });

  try {
    const response = await fetch(`${API_BASE_URL}/api/user/auth/refresh`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ refreshToken: currentRefreshToken }),
    });

    if (!response.ok) {
      const shouldLogout = response.status === 400 || response.status === 401 || response.status === 403;
      return createRefreshFailureResult(
        shouldLogout,
        response.status,
        shouldLogout ? "登录已过期，请重新登录" : `令牌刷新失败（HTTP ${response.status}）`
      );
    }

    const result = (await response.json().catch(() => null)) as ApiResponse<unknown> | null;
    if (!result || normalizeCode(result.code) !== ERROR_CODES.SUCCESS) {
      const code = normalizeCode(result?.code);
      const shouldLogout = isBusinessAuthFailureCode(code);
      authTrace("refresh:business-failed", {
        code,
      });
      return createRefreshFailureResult(
        shouldLogout,
        response.status,
        shouldLogout ? "登录已过期，请重新登录" : "令牌刷新失败，请稍后重试",
        code || response.status
      );
    }

    const token = normalizeToken(result.data);
    setAuthBundle(token, getUserId());
    authTrace("refresh:success", {
      newAccess: summarizeToken(token.accessToken),
      newRefreshPrefix: token.refreshToken.slice(0, 16),
    });
    return { success: true, shouldLogout: false };
  } catch (error) {
    authTrace("refresh:error");
    const message = error instanceof Error ? error.message : "令牌刷新失败，请稍后重试";
    return createRefreshFailureResult(false, -1, message, -1);
  }
}

async function isCurrentAccessTokenStillValid(): Promise<boolean> {
  const accessToken = getAuthToken();
  if (!accessToken) return false;

  try {
    const response = await fetch(`${API_BASE_URL}/api/user/auth/me`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        Accept: "application/json",
      },
    });

    if (!response.ok) return false;
    const result = (await response.json().catch(() => null)) as ApiResponse<unknown> | null;
    return !!result && normalizeCode(result.code) === ERROR_CODES.SUCCESS;
  } catch {
    return false;
  }
}

async function handleTokenRefresh(): Promise<boolean> {
  if (isRefreshing) {
    authTrace("refresh:join-single-flight", {
      queueLength: failedRequestsQueue.length + 1,
    });
    return new Promise((resolve, reject) => {
      failedRequestsQueue.push({ resolve, reject });
    });
  }

  isRefreshing = true;
  const refreshResultPromise = refreshAccessToken();
  let refreshSucceeded = false;

  try {
    const result = await refreshResultPromise;
    const success = result.success;
    refreshSucceeded = success;

    if (success) {
      processQueue(true);
    } else if (result.shouldLogout) {
      const accessStillValid = await isCurrentAccessTokenStillValid();
      if (!accessStillValid) {
        // Token is truly expired — clear auth state to trigger redirect to login
        clearAuthTokens();
      }
      const error = accessStillValid
        ? (result.error ?? new ApiError(-1, "令牌刷新失败，请稍后重试"))
        : new ApiError(401, "登录已过期，请重新登录", 401);
      processQueue(false, error);
    } else {
      processQueue(false, result.error ?? new ApiError(-1, "令牌刷新失败，请稍后重试"));
    }

    return success;
  } finally {
    authTrace("refresh:finish", { success: refreshSucceeded });
    isRefreshing = false;
  }
}

function getRequestKey(url: string, options: RequestInit, accessToken?: string | null): string {
  let tokenKey = "anonymous";
  if (accessToken) {
    const tokenSummary = summarizeToken(accessToken) as {
      jti?: string;
      sessionId?: string;
      workspaceId?: string | null;
    };
    if (tokenSummary.jti) {
      tokenKey = `jti:${tokenSummary.jti}`;
    } else if (tokenSummary.sessionId) {
      tokenKey = `sid:${tokenSummary.sessionId}`;
    } else {
      tokenKey = `len:${accessToken.length}:tail:${accessToken.slice(-24)}`;
    }
  }
  return `${options.method || "GET"}:${url}:${String(options.body || "")}:token=${tokenKey}`;
}

function shouldAttemptRefresh(endpoint: string, skipTokenRefresh: boolean): boolean {
  if (skipTokenRefresh) return false;
  if (isPublicAuthEndpoint(endpoint)) return false;
  if (isRefreshEndpoint(endpoint)) return false;
  if (isWorkspaceSwitchEndpoint(endpoint)) return false;
  return !!getRefreshToken();
}

function sanitizeRequestParams(
  params?: Record<string, string | number | boolean | undefined>
): Record<string, string | number | boolean | undefined> | undefined {
  if (!params) return params;
  const { workspaceId: _workspaceId, ...rest } = params;
  void _workspaceId;
  return Object.keys(rest).length > 0 ? rest : undefined;
}

async function request<T>(
  endpoint: string,
  options: RequestOptions & { body?: string } = {}
): Promise<T> {
  await waitForAuthHydrationIfNeeded(endpoint);
  await waitForWorkspaceSwitchIfNeeded(endpoint);
  const requestWorkspaceSwitchVersion = workspaceSwitchVersion;
  const requestTraceId = ++requestTraceSequence;

  const {
    params,
    headers: customHeaders,
    skipDedup = false,
    dedupTtlMs = DEFAULT_GET_DEDUP_TTL_MS,
    timeout = 30000,
    _skipTokenRefresh = false,
    _skipReplay = false,
    _workspaceGraceRetryCount = 0,
    ...restOptions
  } = options;

  let url = `${API_BASE_URL}${endpoint}`;

  const effectiveParams = sanitizeRequestParams(params);
  if (effectiveParams) {
    const searchParams = new URLSearchParams();
    Object.entries(effectiveParams).forEach(([key, value]) => {
      if (value !== undefined) {
        searchParams.append(key, String(value));
      }
    });
    const queryString = searchParams.toString();
    if (queryString) {
      url += `?${queryString}`;
    }
  }

  const headers = new Headers(customHeaders);

  if (restOptions.body !== undefined && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const requestAccessToken = getAuthToken();
  if (requestAccessToken && !isPublicAuthEndpoint(endpoint)) {
    headers.set("Authorization", `Bearer ${requestAccessToken}`);
  }

  const fetchOptions: RequestInit = {
    ...restOptions,
    headers,
  };

  const requestKey = getRequestKey(url, fetchOptions, requestAccessToken);
  const isGetRequest = fetchOptions.method === "GET";
  const now = Date.now();
  authTrace("request:start", {
    requestTraceId,
    endpoint,
    method: fetchOptions.method || "GET",
    requestWorkspaceSwitchVersion,
    isWorkspaceSwitching,
    token: summarizeToken(requestAccessToken),
    dedupKey: requestKey,
  });

  if (!skipDedup && isGetRequest) {
    const cached = recentGetResponses.get(requestKey);
    if (cached && cached.expiresAt > now) {
      authTrace("request:dedup:cache-hit", {
        requestTraceId,
        endpoint,
      });
      return Promise.resolve(cached.value as T);
    }
    if (cached && cached.expiresAt <= now) {
      recentGetResponses.delete(requestKey);
    }

    const pending = pendingRequests.get(requestKey);
    if (pending) {
      authTrace("request:dedup:pending-hit", {
        requestTraceId,
        endpoint,
      });
      return pending as Promise<T>;
    }
  }

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeout);
  fetchOptions.signal = controller.signal;

  const fetchPromise = (async () => {
    try {
      const response = await fetch(url, fetchOptions);
      clearTimeout(timeoutId);

      const authErrorHeader = response.headers.get("x-auth-error");
      const isGatewayAuthFailure = response.status === 401 || response.status === 403;
      const isWorkspaceMismatchGatewayAuthFailure = isWorkspaceAuthError("", authErrorHeader);
      const isInPostSwitchAuthGraceWindow =
        Date.now() - lastWorkspaceSwitchEndedAt <= WORKSPACE_SWITCH_AUTH_GRACE_MS;
      const switchAdvancedSinceRequest =
        workspaceSwitchVersion !== requestWorkspaceSwitchVersion;
      const shouldRefreshGateway401 =
        response.status === 401 &&
        !isWorkspaceMismatchGatewayAuthFailure &&
        !switchAdvancedSinceRequest &&
        !isWorkspaceSwitching &&
        !isInPostSwitchAuthGraceWindow &&
        shouldAttemptRefresh(endpoint, _skipTokenRefresh);
      authTrace("request:response", {
        requestTraceId,
        endpoint,
        status: response.status,
        switchAdvancedSinceRequest,
        isWorkspaceSwitching,
        isInPostSwitchAuthGraceWindow,
        authErrorHeader,
      });

      if (
        isGatewayAuthFailure &&
        !_skipReplay &&
        requestAccessToken &&
        getAuthToken() &&
        requestAccessToken !== getAuthToken()
      ) {
        authTrace("request:retry:new-token-detected", {
          requestTraceId,
          endpoint,
          requestToken: summarizeToken(requestAccessToken),
          latestToken: summarizeToken(getAuthToken()),
        });
        return request<T>(endpoint, {
          ...options,
          _skipReplay: true,
        });
      }

      if (isGatewayAuthFailure && !_skipReplay && switchAdvancedSinceRequest) {
        authTrace("request:retry:post-switch", {
          requestTraceId,
          endpoint,
        });
        await waitForWorkspaceSwitchIfNeeded(endpoint);
        return request<T>(endpoint, {
          ...options,
          _skipReplay: true,
          _skipTokenRefresh: true,
        });
      }

      if (
        response.status === 401 &&
        isInPostSwitchAuthGraceWindow &&
        !isPublicAuthEndpoint(endpoint) &&
        !isRefreshEndpoint(endpoint) &&
        _workspaceGraceRetryCount < WORKSPACE_SWITCH_AUTH_GRACE_RETRY_MAX
      ) {
        authTrace("request:retry:grace-window", {
          requestTraceId,
          endpoint,
          retryCount: _workspaceGraceRetryCount + 1,
        });
        await waitForWorkspaceSwitchIfNeeded(endpoint);
        await sleep(WORKSPACE_SWITCH_AUTH_GRACE_RETRY_DELAY_MS);
        return request<T>(endpoint, {
          ...options,
          _skipReplay: true,
          _skipTokenRefresh: true,
          _workspaceGraceRetryCount: _workspaceGraceRetryCount + 1,
        });
      }

      if (shouldRefreshGateway401) {
        authTrace("request:refresh-triggered", {
          requestTraceId,
          endpoint,
        });
        const refreshed = await handleTokenRefresh();
        if (refreshed) {
          authTrace("request:refresh-replay", {
            requestTraceId,
            endpoint,
            tokenAfterRefresh: summarizeToken(getAuthToken()),
          });
          return request<T>(endpoint, {
            ...options,
            _skipTokenRefresh: true,
            _skipReplay: true,
          });
        }
      }

      const contentType = response.headers.get("content-type") || "";
      if (!contentType.includes("application/json")) {
        if (response.ok) {
          return undefined as T;
        }

        if (isWorkspaceAuthError("", authErrorHeader)) {
          notifyWorkspaceSelectionRequired();
          throw new ApiError(
            ERROR_CODES.WORKSPACE_NOT_MEMBER,
            "工作空间已切换或无权限，请重新选择工作空间",
            response.status,
            authErrorHeader
          );
        }

        throw new ApiError(response.status, `HTTP ${response.status}`, response.status, authErrorHeader);
      }

      const result = (await response.json()) as ApiResponse<T>;
      const businessCode = normalizeCode(result.code);

      if (businessCode !== ERROR_CODES.SUCCESS) {
        authTrace("request:business-failure", {
          requestTraceId,
          endpoint,
          businessCode,
          message: result.message,
          authErrorHeader,
        });
        const isWorkspaceAuthFailure = isWorkspaceAuthError(businessCode, authErrorHeader);

        if (isWorkspaceAuthFailure && !_skipReplay && switchAdvancedSinceRequest) {
          await waitForWorkspaceSwitchIfNeeded(endpoint);
          return request<T>(endpoint, {
            ...options,
            _skipReplay: true,
            _skipTokenRefresh: true,
          });
        }

        if (
          isBusinessAuthFailureCode(businessCode) &&
          !_skipReplay &&
          isInPostSwitchAuthGraceWindow
        ) {
          await waitForWorkspaceSwitchIfNeeded(endpoint);
          return request<T>(endpoint, {
            ...options,
            _skipReplay: true,
          });
        }

        if (
          isBusinessAuthFailureCode(businessCode) &&
          !_skipReplay &&
          switchAdvancedSinceRequest
        ) {
          await waitForWorkspaceSwitchIfNeeded(endpoint);
          return request<T>(endpoint, {
            ...options,
            _skipReplay: true,
            _skipTokenRefresh: true,
          });
        }

        if (
          isBusinessAuthFailureCode(businessCode) &&
          !_skipReplay &&
          requestAccessToken &&
          getAuthToken() &&
          requestAccessToken !== getAuthToken()
        ) {
          return request<T>(endpoint, {
            ...options,
            _skipReplay: true,
          });
        }

        if (
          isBusinessAuthFailureCode(businessCode) &&
          !switchAdvancedSinceRequest &&
          !isWorkspaceSwitching &&
          !isInPostSwitchAuthGraceWindow &&
          shouldAttemptRefresh(endpoint, _skipTokenRefresh)
        ) {
          const refreshed = await handleTokenRefresh();
          if (refreshed) {
            return request<T>(endpoint, {
              ...options,
              _skipTokenRefresh: true,
              _skipReplay: true,
            });
          }
        }

        if (isWorkspaceAuthFailure) {
          notifyWorkspaceSelectionRequired();
          throw new ApiError(
            businessCode,
            result.message || "工作空间已切换或无权限，请重新选择工作空间",
            response.status,
            authErrorHeader
          );
        }

        throw new ApiError(businessCode, result.message || "请求失败", response.status, authErrorHeader);
      }

      if (!skipDedup && isGetRequest && dedupTtlMs > 0) {
        recentGetResponses.set(requestKey, {
          expiresAt: Date.now() + dedupTtlMs,
          value: result.data,
        });

        if (recentGetResponses.size > 500) {
          const firstKey = recentGetResponses.keys().next().value as string | undefined;
          if (firstKey) {
            recentGetResponses.delete(firstKey);
          }
        }
      }
      authTrace("request:success", {
        requestTraceId,
        endpoint,
      });

      return result.data;
    } catch (error) {
      clearTimeout(timeoutId);
      authTrace("request:error", {
        requestTraceId,
        endpoint,
        message: error instanceof Error ? error.message : String(error),
      });

      if (error instanceof ApiError) {
        throw error;
      }

      if (error instanceof Error) {
        if (error.name === "AbortError") {
          throw new ApiError(-1, "请求超时");
        }
        throw new ApiError(-1, error.message || "网络错误");
      }

      throw new ApiError(-1, "未知错误");
    } finally {
      pendingRequests.delete(requestKey);
    }
  })();

  if (!skipDedup && isGetRequest) {
    pendingRequests.set(requestKey, fetchPromise);
  }

  return fetchPromise;
}

export const api = {
  get: <T>(endpoint: string, options?: RequestOptions) =>
    request<T>(endpoint, { ...options, method: "GET" }),

  post: <T>(endpoint: string, data?: unknown, options?: RequestOptions) =>
    request<T>(endpoint, {
      ...options,
      method: "POST",
      body: data !== undefined ? JSON.stringify(data) : undefined,
      skipDedup: true,
    }),

  put: <T>(endpoint: string, data?: unknown, options?: RequestOptions) =>
    request<T>(endpoint, {
      ...options,
      method: "PUT",
      body: data !== undefined ? JSON.stringify(data) : undefined,
      skipDedup: true,
    }),

  patch: <T>(endpoint: string, data?: unknown, options?: RequestOptions) =>
    request<T>(endpoint, {
      ...options,
      method: "PATCH",
      body: data !== undefined ? JSON.stringify(data) : undefined,
      skipDedup: true,
    }),

  delete: <T>(endpoint: string, data?: unknown, options?: RequestOptions) =>
    request<T>(endpoint, {
      ...options,
      method: "DELETE",
      body: data !== undefined ? JSON.stringify(data) : undefined,
      skipDedup: true,
    }),
};

/**
 * Create a debounced API call
 */
export function createDebouncedFetch<TArgs extends unknown[], TResult>(
  fn: (...args: TArgs) => Promise<TResult>,
  wait: number = 300
) {
  let timeoutId: ReturnType<typeof setTimeout> | null = null;
  let latestResolve: ((value: TResult) => void) | null = null;
  let latestReject: ((reason: unknown) => void) | null = null;

  return (...args: TArgs): Promise<TResult> => {
    if (timeoutId) {
      clearTimeout(timeoutId);
    }

    const promise = new Promise<TResult>((resolve, reject) => {
      latestResolve = resolve;
      latestReject = reject;
    });

    timeoutId = setTimeout(async () => {
      try {
        const result = await fn(...args);
        latestResolve?.(result);
      } catch (error) {
        latestReject?.(error);
      }
    }, wait);

    return promise;
  };
}

export default api;
