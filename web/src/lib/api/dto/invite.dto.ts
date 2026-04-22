/**
 * Invitation Code DTOs
 */

export type InviteCodeType = "System" | "User";

export interface InviteCodeValidationDTO {
  valid: boolean;
  type: InviteCodeType;
  inviterName: string | null;
  remainingUses: number;
  validUntil: string | null;
  message: string | null;
}

export interface RegistrationConfigDTO {
  invitationCodeRequired: boolean;
  allowUserCode: boolean;
}

/** Personal invitation code for inviting new users to register */
export interface PersonalInviteCodeDTO {
  code: string;
  totalInvited: number;
  remainingUses: number;
  maxUses: number;
  validUntil: string | null;
  createdAt: string;
}

/** Invited user record */
export interface InvitedUserDTO {
  id: string;
  username: string;
  nickname: string | null;
  avatar: string | null;
  registeredAt: string;
}
