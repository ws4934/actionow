/**
 * User DTOs
 */

export interface ResetPasswordRequestDTO {
  target: string;
  verifyCode: string;
  newPassword: string;
}

export interface ChangePasswordRequestDTO {
  oldPassword: string;
  newPassword: string;
}

export interface UpdateProfileRequestDTO {
  nickname?: string;
  avatar?: string;
}

export interface CheckAvailabilityResponseDTO {
  available: boolean;
  message?: string;
}
