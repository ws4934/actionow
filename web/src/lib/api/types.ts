/**
 * Common API Types
 */

// Standard API response wrapper
export interface ApiResponse<T = unknown> {
  code: number | string;
  message: string;
  data: T;
  timestamp?: string | number;
  success?: boolean;
  requestId?: string;
}

// API Error class
export class ApiError extends Error {
  code: number | string;
  status?: number;
  authError?: string | null;

  constructor(code: number | string, message: string, status?: number, authError?: string | null) {
    super(message);
    this.code = code;
    this.status = status;
    this.authError = authError ?? null;
    this.name = "ApiError";
  }
}

// Request options extending fetch RequestInit
export interface RequestOptions extends Omit<RequestInit, "body"> {
  params?: Record<string, string | number | boolean | undefined>;
  /** Skip request deduplication */
  skipDedup?: boolean;
  /** GET response deduplication window in ms (default: 800) */
  dedupTtlMs?: number;
  /** Custom timeout in ms (default: 30000) */
  timeout?: number;
  /** Internal: Skip token refresh retry (to prevent infinite loops) */
  _skipTokenRefresh?: boolean;
  /** Internal: Skip one-time replay (to prevent infinite loops) */
  _skipReplay?: boolean;
  /** Internal: Post workspace-switch auth grace retry count */
  _workspaceGraceRetryCount?: number;
}

// Error codes mapping
export const ERROR_CODES = {
  SUCCESS: "0",
  NOT_AUTHENTICATED: "0001100",
  TOKEN_INVALID_V2: "0001102",
  WORKSPACE_NOT_MEMBER: "0201103",
  WORKSPACE_NO_PERMISSION: "0201104",
  // Parameter errors (400xx)
  PARAM_ERROR: 40001,
  VERIFY_CODE_ERROR: 40002,
  USERNAME_EXISTS: 40003,
  EMAIL_EXISTS: 40004,
  PHONE_EXISTS: 40005,
  INVITE_CODE_INVALID: 40006,
  CAPTCHA_ERROR: 40007,
  // Authentication errors (401xx)
  NOT_LOGGED_IN: 40101,
  TOKEN_INVALID: 40102,
  TOKEN_EXPIRED: 40103,
  ACCOUNT_LOCKED: 40104,
  PASSWORD_ERROR: 40105,
  // Authorization errors (403xx)
  NO_PERMISSION: 40301,
  // Not found errors (404xx)
  NOT_FOUND: 40401,
  // Resource errors (22xx - backend specific)
  RESOURCE_NOT_FOUND: 2200,
  // Server errors (500xx)
  SERVER_ERROR: 50001,
} as const;

export type ErrorCode = (typeof ERROR_CODES)[keyof typeof ERROR_CODES];
