/**
 * Authentication DTOs
 */

import type { WorkspaceRole } from "./workspace.dto";

// Token data
export interface TokenDTO {
  accessToken: string;
  refreshToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  refreshExpiresIn: number;
  sessionId: string;
  workspaceId: string | null;
}

// User data
export interface UserDTO {
  id: string;
  username: string;
  email: string | null;
  phone: string | null;
  nickname: string | null;
  avatar: string | null;
  emailVerified: boolean;
  phoneVerified: boolean;
  systemRole: WorkspaceRole | null;
  createdAt: string;
  oauthProviders: string[];
}

// Captcha data
export interface CaptchaDTO {
  captchaKey: string;
  captchaImage: string;
}

// Auth response (login/register)
export interface AuthResponseDTO {
  user: UserDTO;
  token: TokenDTO;
}

// Verify code types
export type VerifyCodeType = "register" | "login" | "reset_password" | "bind";

// Request DTOs
export interface SendVerifyCodeRequestDTO {
  target: string;
  type: VerifyCodeType;
  captcha: string;
  captchaKey: string;
}

export interface SendVerifyCodeResponseDTO {
  expireIn: number;
}

export interface RegisterRequestDTO {
  username: string;
  email?: string;
  phone?: string;
  password: string;
  nickname?: string;
  verifyCode: string;
  inviteCode?: string;
}

export interface LoginRequestDTO {
  account: string;
  password: string;
  captcha?: string;
  captchaKey?: string;
  rememberMe?: boolean;
}

export interface LoginWithCodeRequestDTO {
  target: string;
  verifyCode: string;
}

export interface RefreshTokenRequestDTO {
  refreshToken: string;
}
