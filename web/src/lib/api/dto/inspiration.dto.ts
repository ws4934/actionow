/**
 * Inspiration Module DTOs
 * Free-form AI generation without entity/script binding.
 * Assets created here are workspace-scoped (scope="WORKSPACE", scriptId=null).
 */

import type { ProviderType } from "./ai.dto";
import type { AssetType } from "./project.dto";
import type { PaginatedResponseDTO } from "./workspace.dto";

// ============================================================================
// Session
// ============================================================================

export type InspirationSessionStatus = "ACTIVE" | "ARCHIVED";

export interface InspirationSessionDTO {
  id: string;
  workspaceId: string;
  title: string;
  coverUrl: string | null;
  recordCount: number;
  totalCredits: number;
  status: InspirationSessionStatus;
  createdAt: string;
  updatedAt: string;
  lastActiveAt: string;
}

export interface CreateInspirationSessionRequestDTO {
  title?: string;
}

// ============================================================================
// Record
// ============================================================================

export type InspirationRecordStatus = "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";

export interface InspirationAssetDTO {
  id: string;
  url: string;
  thumbnailUrl: string | null;
  assetType: AssetType;
  width: number | null;
  height: number | null;
  duration: number | null;
  mimeType: string | null;
  fileSize: number | null;
}

export interface InspirationRecordDTO {
  id: string;
  sessionId: string;
  /** User prompt */
  prompt: string;
  negativePrompt: string | null;
  generationType: Exclude<ProviderType, "TEXT">;
  providerId: string;
  providerName: string | null;
  providerIconUrl: string | null;
  params: Record<string, unknown>;
  status: InspirationRecordStatus;
  assets: InspirationAssetDTO[];
  /** Reference assets used as input for this generation */
  refAssets: InspirationAssetDTO[];
  taskId: string | null;
  creditCost: number;
  progress: number;
  errorMessage: string | null;
  createdAt: string;
  completedAt: string | null;
}

// ============================================================================
// Generation Request / Response
// ============================================================================

export interface FreeGenerationRequestDTO {
  sessionId: string;
  generationType: Exclude<ProviderType, "TEXT">;
  prompt: string;
  negativePrompt?: string;
  providerId: string;
  params?: Record<string, unknown>;
  count?: number;
}

export interface FreeGenerationResponseDTO {
  recordId: string;
  assetIds: string[];
  taskId: string;
  taskStatus: string;
  creditCost: number;
  success: boolean;
  errorMessage: string | null;
}

// ============================================================================
// Pagination (re-export shared type for convenience)
// ============================================================================

export type InspirationSessionsPageDTO = PaginatedResponseDTO<InspirationSessionDTO>;
export type InspirationRecordsPageDTO = PaginatedResponseDTO<InspirationRecordDTO>;
