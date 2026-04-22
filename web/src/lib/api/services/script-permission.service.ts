/**
 * Script Permission Service
 * Handles script-level permission management API calls
 */

import { api } from "../client";
import type {
  ScriptPermissionDTO,
  GrantScriptPermissionRequest,
  InviteCollaboratorRequest,
} from "../dto";

const BASE = "/api/project/scripts";

export const scriptPermissionService = {
  /** Grant a user permission to a script */
  grantPermission: (scriptId: string, data: GrantScriptPermissionRequest) =>
    api.post<ScriptPermissionDTO>(`${BASE}/${scriptId}/permissions`, data),

  /** Revoke a user's permission to a script */
  revokePermission: (scriptId: string, userId: string) =>
    api.delete<null>(`${BASE}/${scriptId}/permissions/${userId}`),

  /** Get all permissions for a script */
  getPermissions: (scriptId: string) =>
    api.get<ScriptPermissionDTO[]>(`${BASE}/${scriptId}/permissions`),

  /** Invite a collaborator (can invite non-workspace members, auto-adds as Guest) */
  inviteCollaborator: (scriptId: string, data: InviteCollaboratorRequest) =>
    api.post<ScriptPermissionDTO>(`${BASE}/${scriptId}/collaborators`, data),

  /** Remove a collaborator from a script */
  removeCollaborator: (scriptId: string, userId: string) =>
    api.delete<null>(`${BASE}/${scriptId}/collaborators/${userId}`),
};
