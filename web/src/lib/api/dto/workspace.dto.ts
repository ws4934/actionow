/**
 * Workspace DTOs
 */

export type WorkspaceRole = "CREATOR" | "ADMIN" | "MEMBER" | "GUEST";
export type WorkspaceStatus = "Active" | "Suspended" | "Deleted";
export type PlanType = "Free" | "Basic" | "Pro" | "Enterprise";

export interface WorkspaceOwnerDTO {
  id: string;
  username: string;
  nickname: string | null;
  avatar: string | null;
}

export interface WorkspaceDTO {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  logoUrl: string | null;
  ownerId: string;
  owner: WorkspaceOwnerDTO;
  schemaName: string;
  status: WorkspaceStatus;
  planType: PlanType;
  maxMembers: number;
  memberCount: number;
  config: Record<string, unknown>;
  myRole: WorkspaceRole;
  isSystem: boolean;
  createdAt: string;
}

export interface CreateWorkspaceRequestDTO {
  name: string;
  slug?: string;
  description?: string;
  logoUrl?: string;
}

export interface UpdateWorkspaceRequestDTO {
  name?: string;
  description?: string;
  logoUrl?: string;
  config?: Record<string, unknown>;
}

export interface WorkspaceMemberDTO {
  id: string;
  userId: string;
  username: string;
  avatar: string | null;
  role: WorkspaceRole;
  status: "Active" | "Inactive" | "Invited";
  nickname: string | null;
  invitedBy: string | null;
  joinedAt: string;
}

export interface WorkspaceInvitationDTO {
  id: string;
  code: string;
  workspaceId: string;
  inviterId: string;
  inviteeEmail: string | null;
  role: WorkspaceRole;
  expiresAt: string;
  maxUses: number;
  usedCount: number;
  status: number;
  createdAt: string;
  inviteLink: string;
}

export interface CreateInvitationRequestDTO {
  role: Exclude<WorkspaceRole, "CREATOR">;
  emails?: string[];
  message?: string;
  expireHours?: number;
  maxUses?: number;
}

export interface PaginatedResponseDTO<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
  pages: number;
  first?: boolean;
  last?: boolean;
}
