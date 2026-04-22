/**
 * OAuth Service
 * Handles OAuth-related API calls
 */

import { api } from "../client";
import type {
  OAuthProvider,
  OAuthAuthorizeResponseDTO,
  OAuthCallbackRequestDTO,
  OAuthCallbackResponseDTO,
  LoginConfigDTO,
} from "../dto";

const OAUTH_BASE = "/api/user/oauth";

export const oauthService = {
  /** Get OAuth authorize URL */
  getAuthorizeUrl: (
    provider: OAuthProvider,
    redirectUri: string,
    state?: string
  ) =>
    api.get<OAuthAuthorizeResponseDTO>(`${OAUTH_BASE}/${provider}/authorize`, {
      params: {
        redirectUri,
        ...(state && { state }),
      },
    }),

  /** Handle OAuth callback */
  handleCallback: (provider: OAuthProvider, data: OAuthCallbackRequestDTO) =>
    api.post<OAuthCallbackResponseDTO>(`${OAUTH_BASE}/${provider}/callback`, data),

  /** Get login configuration (public, no auth) */
  getLoginConfig: () =>
    api.get<LoginConfigDTO>(`${OAUTH_BASE}/login-config`),
};

export default oauthService;
