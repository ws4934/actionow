/**
 * Invite Code Service
 * Handles invitation code-related API calls
 */

import { api } from "../client";
import type {
  InviteCodeValidationDTO,
  RegistrationConfigDTO,
  PersonalInviteCodeDTO,
  InvitedUserDTO,
  PaginatedResponseDTO,
} from "../dto";

const INVITE_BASE = "/api/user/user/invitation-code";

export const inviteService = {
  /** Validate an invitation code */
  validate: (code: string) =>
    api.get<InviteCodeValidationDTO>(`${INVITE_BASE}/validate/${encodeURIComponent(code)}`),

  /** Get registration configuration */
  getRegistrationConfig: () =>
    api.get<RegistrationConfigDTO>(`${INVITE_BASE}/registration-config`),

  /** Get my personal invitation code */
  getMyInviteCode: () =>
    api.get<PersonalInviteCodeDTO>(INVITE_BASE),

  /** Refresh/regenerate my personal invitation code */
  regenerateMyInviteCode: () =>
    api.post<PersonalInviteCodeDTO>(`${INVITE_BASE}/refresh`),

  /** Get list of users I invited */
  getInvitedUsers: (params?: { page?: number; size?: number }) =>
    api.get<PaginatedResponseDTO<InvitedUserDTO>>(`${INVITE_BASE}/invitees`, { params }),

  /** Get my inviter info */
  getMyInviter: () =>
    api.get<InvitedUserDTO | null>(`${INVITE_BASE}/inviter`),
};

export default inviteService;
