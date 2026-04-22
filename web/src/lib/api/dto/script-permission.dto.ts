export type ScriptPermissionType = "VIEW" | "EDIT" | "ADMIN";
export type GrantSource = "WORKSPACE_ADMIN" | "SCRIPT_OWNER";

export interface ScriptPermissionDTO {
  id: string;
  scriptId: string;
  userId: string;
  permissionType: ScriptPermissionType;
  grantSource: GrantSource;
  grantedBy: string;
  grantedAt: string;
  expiresAt: string | null;
  username: string | null;
  nickname: string | null;
  avatar: string | null;
}

export interface GrantScriptPermissionRequest {
  userId: string;
  permissionType: ScriptPermissionType;
  expiresAt?: string;
}

export interface InviteCollaboratorRequest {
  userId: string;
  permissionType: ScriptPermissionType;
  expiresAt?: string;
}
