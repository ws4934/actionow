/**
 * Error Messages
 * Localized error messages for API error codes
 */

import { ERROR_CODES } from "./types";

type ErrorMessages = Record<string, { zh: string; en: string }>;

const errorMessages: ErrorMessages = {
  [ERROR_CODES.PARAM_ERROR]: {
    zh: "参数错误",
    en: "Invalid parameters",
  },
  [ERROR_CODES.VERIFY_CODE_ERROR]: {
    zh: "验证码错误或已过期",
    en: "Invalid or expired verification code",
  },
  [ERROR_CODES.USERNAME_EXISTS]: {
    zh: "用户名已被使用",
    en: "Username already taken",
  },
  [ERROR_CODES.EMAIL_EXISTS]: {
    zh: "邮箱已被使用",
    en: "Email already in use",
  },
  [ERROR_CODES.PHONE_EXISTS]: {
    zh: "手机号已被使用",
    en: "Phone number already in use",
  },
  [ERROR_CODES.INVITE_CODE_INVALID]: {
    zh: "邀请码无效",
    en: "Invalid invitation code",
  },
  [ERROR_CODES.CAPTCHA_ERROR]: {
    zh: "验证码错误",
    en: "Captcha verification failed",
  },
  [ERROR_CODES.NOT_LOGGED_IN]: {
    zh: "请先登录",
    en: "Please login first",
  },
  [ERROR_CODES.NOT_AUTHENTICATED]: {
    zh: "请先登录",
    en: "Please login first",
  },
  [ERROR_CODES.TOKEN_INVALID]: {
    zh: "登录已过期，请重新登录",
    en: "Session expired, please login again",
  },
  [ERROR_CODES.TOKEN_INVALID_V2]: {
    zh: "登录已过期，请重新登录",
    en: "Session expired, please login again",
  },
  [ERROR_CODES.TOKEN_EXPIRED]: {
    zh: "登录已过期，请重新登录",
    en: "Session expired, please login again",
  },
  [ERROR_CODES.ACCOUNT_LOCKED]: {
    zh: "账号已被锁定，请30分钟后再试",
    en: "Account locked, please try again in 30 minutes",
  },
  [ERROR_CODES.PASSWORD_ERROR]: {
    zh: "密码错误",
    en: "Incorrect password",
  },
  [ERROR_CODES.NO_PERMISSION]: {
    zh: "无权限访问",
    en: "Access denied",
  },
  [ERROR_CODES.WORKSPACE_NOT_MEMBER]: {
    zh: "你已不在当前工作空间中",
    en: "You are not a member of the current workspace",
  },
  [ERROR_CODES.WORKSPACE_NO_PERMISSION]: {
    zh: "当前工作空间权限不足",
    en: "Insufficient permissions in current workspace",
  },
  [ERROR_CODES.NOT_FOUND]: {
    zh: "资源不存在",
    en: "Resource not found",
  },
  [ERROR_CODES.SERVER_ERROR]: {
    zh: "服务器错误，请稍后再试",
    en: "Server error, please try again later",
  },
};

/**
 * Get localized error message for an error code
 */
export function getErrorMessage(code: number | string, locale: string = "zh"): string {
  const msg = errorMessages[String(code)];
  if (msg) {
    return locale === "en" ? msg.en : msg.zh;
  }
  return locale === "en" ? "Unknown error" : "未知错误";
}

/**
 * Get error message from ApiError or generic Error
 */
export function getErrorFromException(
  error: unknown,
  locale: string = "zh"
): string {
  if (error && typeof error === "object" && "code" in error) {
    const apiError = error as { code: number | string; message?: string };
    // First try to get localized message, fallback to error message
    const localizedMsg = getErrorMessage(apiError.code, locale);
    if (localizedMsg !== (locale === "en" ? "Unknown error" : "未知错误")) {
      return localizedMsg;
    }
    return apiError.message || localizedMsg;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return locale === "en" ? "Unknown error" : "未知错误";
}
