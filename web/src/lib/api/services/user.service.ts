/**
 * User Service
 * Handles user-related API calls
 */

import { api } from "../client";
import type {
  ResetPasswordRequestDTO,
  ChangePasswordRequestDTO,
  UpdateProfileRequestDTO,
  CheckAvailabilityResponseDTO,
} from "../dto";
import type { UserDTO } from "../dto/auth.dto";

const USER_BASE = "/api/user/users";

export const userService = {
  /** Reset password with verification code */
  resetPassword: (data: ResetPasswordRequestDTO) =>
    api.post<null>(`${USER_BASE}/password/reset`, data),

  /** Change password (requires current password) */
  changePassword: (data: ChangePasswordRequestDTO) =>
    api.post<null>(`${USER_BASE}/password/change`, data),

  /** Update user profile */
  updateProfile: (data: UpdateProfileRequestDTO) =>
    api.patch<UserDTO>(`${USER_BASE}/profile`, data),

  /** Check username availability */
  checkUsername: (username: string) =>
    api.get<CheckAvailabilityResponseDTO>(`${USER_BASE}/check/username`, {
      params: { username },
    }),

  /** Check email availability */
  checkEmail: (email: string) =>
    api.get<CheckAvailabilityResponseDTO>(`${USER_BASE}/check/email`, {
      params: { email },
    }),

  /** Check phone availability */
  checkPhone: (phone: string) =>
    api.get<CheckAvailabilityResponseDTO>(`${USER_BASE}/check/phone`, {
      params: { phone },
    }),
};

export default userService;
