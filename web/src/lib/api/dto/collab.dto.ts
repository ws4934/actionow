/**
 * Collab DTOs - Based on collab-api.md v1.0
 */

/** User presence status */
export type UserPresenceStatus = "ONLINE" | "AWAY" | "OFFLINE";

/** Collaboration status */
export type CollabStatusType = "VIEWING" | "EDITING";

/** Tab types in script */
export type CollabTabType = "DETAIL" | "EPISODES" | "STORYBOARDS" | "CHARACTERS" | "SCENES" | "PROPS";

/** User presence info */
export interface UserPresenceDTO {
  userId: string;
  nickname: string;
  avatar: string;
  status: UserPresenceStatus;
  lastActiveAt: string;
}

/** User location info for collaboration */
export interface UserLocationDTO {
  userId: string;
  nickname: string;
  avatar: string;
  page?: string;
  scriptId?: string;
  tab?: CollabTabType;
  focusedEntityType?: string | null;
  focusedEntityId?: string | null;
  collabStatus: CollabStatusType;
}

/** Script collaboration state */
export interface ScriptCollaborationDTO {
  scriptId: string;
  totalUsers: number;
  users: UserLocationDTO[];
  tabUserCounts: Partial<Record<CollabTabType, number>>;
}

/** Collaborator info */
export interface CollaboratorDTO {
  userId: string;
  nickname: string;
  avatar: string;
  status: CollabStatusType;
  joinedAt: string;
}

/** Entity collaboration state */
export interface EntityCollaborationDTO {
  entityType: string;
  entityId: string;
  viewers: CollaboratorDTO[];
  editor: CollaboratorDTO | null;
}

/** Entity editing status */
export interface EntityEditingStatusDTO {
  editing: boolean;
  editor: {
    userId: string;
    nickname: string;
    avatar: string;
  } | null;
}
