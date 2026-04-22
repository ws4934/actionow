/**
 * Workspace Service
 * Handles workspace-related API calls
 */

import { api } from "../client";
import type {
  WorkspaceDTO,
  CreateWorkspaceRequestDTO,
  UpdateWorkspaceRequestDTO,
  WorkspaceMemberDTO,
  WorkspaceInvitationDTO,
  CreateInvitationRequestDTO,
  PaginatedResponseDTO,
} from "../dto";

const BASE = "/api/workspaces";

export const workspaceService = {
  /** Get user's workspaces list */
  getWorkspaces: () =>
    api.get<WorkspaceDTO[]>(BASE, {
      dedupTtlMs: 1500,
    }),

  /** Get current workspace details */
  getCurrentWorkspace: () =>
    api.get<WorkspaceDTO>(`${BASE}/current`),

  /** Create a new workspace */
  createWorkspace: (data: CreateWorkspaceRequestDTO) =>
    api.post<WorkspaceDTO>(BASE, data),

  /** Update workspace */
  updateWorkspace: (data: UpdateWorkspaceRequestDTO) =>
    api.patch<WorkspaceDTO>(BASE, data, {
      params: {},
    }),

  /** Delete workspace */
  deleteWorkspace: () =>
    api.delete<null>(BASE, undefined, {
      params: {},
    }),

  /** Transfer ownership */
  transferOwnership: (newOwnerId: string) =>
    api.post<null>(
      `${BASE}/transfer`,
      undefined,
      {
        params: { newOwnerId },
      }
    ),

  /** Get workspace members */
  getMembers: (
    params?: { current?: number; size?: number; role?: string }
  ) =>
    api.get<PaginatedResponseDTO<WorkspaceMemberDTO>>(
      `${BASE}/members`,
      {
        params: { ...params },
      }
    ),

  /** Remove a member */
  removeMember: (memberId: string) =>
    api.delete<null>(`${BASE}/members/${memberId}`, undefined, {
      params: {},
    }),

  /** Leave workspace */
  leaveWorkspace: () =>
    api.post<null>(`${BASE}/leave`, undefined, {
      params: {},
    }),

  /** Update member role */
  updateMemberRole: (memberId: string, role: string) =>
    api.patch<null>(
      `${BASE}/members/${memberId}/role`,
      { role },
      { params: {} }
    ),

  /** Create invitation */
  createInvitation: (data: CreateInvitationRequestDTO) =>
    api.post<WorkspaceInvitationDTO>(
      `${BASE}/invitations`,
      data,
      { params: {} }
    ),

  /** Get invitations list */
  getInvitations: (
    params?: { current?: number; size?: number }
  ) =>
    api.get<PaginatedResponseDTO<WorkspaceInvitationDTO>>(
      `${BASE}/invitations`,
      {
        params: { ...params },
      }
    ),

  /** Disable invitation */
  disableInvitation: (invitationId: string) =>
    api.delete<null>(
      `${BASE}/invitations/${invitationId}`,
      undefined,
      { params: {} }
    ),

  /** Get invitation details (public) */
  getInvitationByCode: (code: string) =>
    api.get<WorkspaceInvitationDTO>(`${BASE}/invite/${code}`),

  /** Accept invitation */
  acceptInvitation: (code: string) =>
    api.post<WorkspaceMemberDTO>(`${BASE}/invite/${code}/accept`),

  /** Toggle member script creation permission */
  toggleScriptCreation: (enabled: boolean) =>
    api.patch<null>(`/api/workspace/workspaces/settings/script-creation`, undefined, {
      params: { enabled },
    }),
};

export default workspaceService;
