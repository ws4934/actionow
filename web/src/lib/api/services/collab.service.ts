/**
 * Collab Service - Based on collab-api.md v1.0
 * Handles collaboration-related API calls
 */

import { api } from "../client";
import type {
  UserPresenceDTO,
  ScriptCollaborationDTO,
  EntityCollaborationDTO,
  EntityEditingStatusDTO,
} from "../dto/collab.dto";

const COLLAB_BASE = "/api/collab";

export const collabService = {
  // =============================================================================
  // Workspace Online Status
  // =============================================================================

  /** Get online users in workspace */
  getWorkspaceOnlineUsers: (workspaceId: string) =>
    api.get<UserPresenceDTO[]>(`${COLLAB_BASE}/workspace/${workspaceId}/online`),

  /** Get online user count in workspace */
  getWorkspaceOnlineCount: (workspaceId: string) =>
    api.get<number>(`${COLLAB_BASE}/workspace/${workspaceId}/online/count`),

  /** Check if user is online in workspace */
  isUserOnline: (workspaceId: string, userId: string) =>
    api.get<boolean>(`${COLLAB_BASE}/workspace/${workspaceId}/user/${userId}/online`),

  // =============================================================================
  // Script Collaboration
  // =============================================================================

  /** Get script collaboration state */
  getScriptCollaboration: (scriptId: string) =>
    api.get<ScriptCollaborationDTO>(`${COLLAB_BASE}/script/${scriptId}/collaboration`),

  /** Get online user count in script */
  getScriptOnlineCount: (scriptId: string) =>
    api.get<number>(`${COLLAB_BASE}/script/${scriptId}/online/count`),

  /** Check if user is present in script */
  isUserInScript: (scriptId: string, userId: string) =>
    api.get<boolean>(`${COLLAB_BASE}/script/${scriptId}/user/${userId}/present`),

  /** Batch get script collaboration states */
  batchGetScriptCollaborations: (scriptIds: string[]) =>
    api.post<Record<string, ScriptCollaborationDTO>>(
      `${COLLAB_BASE}/script/collaboration/batch`,
      scriptIds
    ),

  // =============================================================================
  // Entity Collaboration
  // =============================================================================

  /** Get entity collaboration state */
  getEntityCollaboration: (entityType: string, entityId: string) =>
    api.get<EntityCollaborationDTO>(
      `${COLLAB_BASE}/entity/${entityType}/${entityId}/collaboration`
    ),

  /** Batch get entity collaboration states */
  batchGetEntityCollaborations: (entityType: string, entityIds: string[]) =>
    api.post<Record<string, EntityCollaborationDTO>>(
      `${COLLAB_BASE}/entity/${entityType}/collaboration/batch`,
      entityIds
    ),

  /** Check entity editing status */
  getEntityEditingStatus: (entityType: string, entityId: string) =>
    api.get<EntityEditingStatusDTO>(
      `${COLLAB_BASE}/entity/${entityType}/${entityId}/editing`
    ),
};

export default collabService;
