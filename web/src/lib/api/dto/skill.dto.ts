/**
 * Workspace Skill DTOs
 * Corresponds to backend WorkspaceSkillController (/agent/skills)
 */

import type { PaginatedResponseDTO } from "./workspace.dto";

// ============================================================================
// Enums
// ============================================================================

export type SkillScope = "SYSTEM" | "WORKSPACE";

// ============================================================================
// Response DTOs
// ============================================================================

export interface SkillResponseDTO {
  id: string;
  name: string;
  displayName?: string;
  description: string;
  /** Full instruction content — only returned by the detail endpoint */
  content?: string;
  groupedToolIds?: string[];
  outputSchema?: Record<string, unknown>;
  tags?: string[];
  references?: Array<Record<string, unknown>>;
  examples?: Array<Record<string, unknown>>;
  enabled: boolean;
  version: number;
  scope: SkillScope;
  workspaceId?: string;
  createdAt: string;
  updatedAt?: string;
}

export type SkillPageDTO = PaginatedResponseDTO<SkillResponseDTO>;

// ============================================================================
// Request DTOs
// ============================================================================

export interface SkillCreateRequestDTO {
  /** Format: [a-z][a-z0-9_]{1,63} */
  name: string;
  displayName?: string;
  description: string;
  content: string;
  groupedToolIds?: string[];
  outputSchema?: Record<string, unknown>;
  tags?: string[];
  references?: Array<Record<string, unknown>>;
  examples?: Array<Record<string, unknown>>;
}

export interface SkillUpdateRequestDTO {
  displayName?: string;
  description?: string;
  content?: string;
  groupedToolIds?: string[];
  outputSchema?: Record<string, unknown>;
  tags?: string[];
  references?: Array<Record<string, unknown>>;
  examples?: Array<Record<string, unknown>>;
}

export interface SkillListParamsDTO {
  page?: number;
  size?: number;
  keyword?: string;
}

// ============================================================================
// Import / Upload
// ============================================================================

/** Returned by POST /api/agent/skills/import (ZIP batch import) */
export interface SkillImportResult {
  total: number;
  success: number;
  failed: number;
  errors: string[];
}
