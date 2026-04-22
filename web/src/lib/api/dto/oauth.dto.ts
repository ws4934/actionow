/**
 * OAuth DTOs
 */

import type { AuthResponseDTO } from "./auth.dto";

export type OAuthProvider = string;

export interface OAuthAuthorizeResponseDTO {
  authorizeUrl: string;
  state: string;
}

export interface OAuthCallbackRequestDTO {
  code: string;
  state?: string;
  redirectUri?: string;
  inviteCode?: string;
}

export interface OAuthCallbackResponseDTO extends AuthResponseDTO {
  isNewUser: boolean;
}

export interface OAuthProviderConfigDTO {
  provider: string;
  displayName: string;
  icon: string;
  authorizeUrl: string;
  scope: string;
  clientId: string;
}

export interface LoginConfigDTO {
  passwordLoginEnabled: boolean;
  codeLoginEnabled: boolean;
  invitationCodeRequired: boolean;
  allowUserCode: boolean;
  oauthProviders: OAuthProviderConfigDTO[];
}
