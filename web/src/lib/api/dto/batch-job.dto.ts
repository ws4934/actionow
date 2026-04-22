/**
 * Batch Job DTOs
 * Aligned with backend Batch Job API: /api/tasks/batch-jobs
 */

import type { PaginatedResponseDTO } from "./workspace.dto";

// ============================================================================
// Enums (union types)
// ============================================================================

export type BatchJobStatus = "CREATED" | "RUNNING" | "PAUSED" | "COMPLETED" | "FAILED" | "CANCELLED";

export type BatchItemStatus =
  | "PENDING"
  | "SUBMITTED"
  | "RUNNING"
  | "COMPLETED"
  | "FAILED"
  | "SKIPPED"
  | "CANCELLED";

export type BatchType = "SIMPLE" | "PIPELINE" | "VARIATION" | "SCOPE" | "AB_TEST";

export type ErrorStrategy = "CONTINUE" | "STOP" | "RETRY_THEN_CONTINUE";

export type SkipCondition = "NONE" | "ASSET_EXISTS";

export type BatchSource = "API" | "AGENT" | "SCHEDULED";

export type ScopeEntityType = "EPISODE" | "SCRIPT" | "CHARACTER" | "SCENE" | "PROP";

export type PipelineStepType =
  | "GENERATE_TEXT"
  | "GENERATE_IMAGE"
  | "GENERATE_VIDEO"
  | "GENERATE_AUDIO"
  | "GENERATE_TTS";

export type PipelineTemplate =
  | "TEXT_TO_PROMPT_TO_IMAGE"
  | "TEXT_TO_PROMPT_TO_VIDEO"
  | "TEXT_TO_PROMPT_TO_AUDIO"
  | "TEXT_TO_IMAGE_TO_VIDEO"
  | "TEXT_TO_KEYFRAMES_TO_VIDEO"
  | "FULL_STORYBOARD";

// ============================================================================
// Response DTOs
// ============================================================================

export interface BatchJobResponseDTO {
  id: string;
  workspaceId: string;
  scriptId?: string | null;
  creatorId: string;
  title?: string | null;
  batchType: BatchType;
  status: BatchJobStatus;
  progress: number;
  totalItems: number;
  completedItems: number;
  failedItems: number;
  skippedItems: number;
  estimatedCredits: number;
  actualCredits: number;
  concurrency: number;
  errorStrategy: ErrorStrategy;
  skipCondition: SkipCondition;
  providerId?: string | null;
  generationType?: string | null;
  missionId?: string | null;
  source: BatchSource;
  pipelineTemplate?: PipelineTemplate | null;
  abTestProviderIds?: string[] | null;
  errorMessage?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface BatchJobItemDTO {
  id: string;
  batchJobId: string;
  sequenceNumber: number;
  entityType?: string | null;
  entityId?: string | null;
  entityName?: string | null;
  params?: Record<string, unknown> | null;
  providerId?: string | null;
  providerName?: string | null;
  generationType?: string | null;
  pipelineStepId?: string | null;
  pipelineStepNumber?: number | null;
  taskId?: string | null;
  assetId?: string | null;
  status: BatchItemStatus;
  errorMessage?: string | null;
  creditCost: number;
  startedAt?: string | null;
  completedAt?: string | null;
  createdAt: string;
  updatedAt: string;
}

// ============================================================================
// SSE Event
// ============================================================================

export interface BatchJobSseEvent {
  batchJobId: string;
  eventType: string;
  status?: BatchJobStatus;
  message?: string;
  progress?: number;
  completedItems?: number;
  failedItems?: number;
  skippedItems?: number;
  totalItems?: number;
  actualCredits?: number;
  itemId?: string;
  itemStatus?: BatchItemStatus;
  stepNumber?: number;
  data?: Record<string, unknown>;
  timestamp?: string;
}

// ============================================================================
// Request DTOs
// ============================================================================

export interface PipelineStepRequestDTO {
  stepNumber: number;
  stepType: PipelineStepType;
  providerId?: string;
  params?: Record<string, unknown>;
}

export interface BatchJobItemRequestDTO {
  entityType?: string;
  entityId?: string;
  entityName?: string;
  params?: Record<string, unknown>;
  providerId?: string;
  generationType?: string;
}

export interface CreateBatchJobRequestDTO {
  batchType: BatchType;
  title?: string;
  items?: BatchJobItemRequestDTO[];
  providerId?: string;
  generationType?: string;
  concurrency?: number;
  errorStrategy?: ErrorStrategy;
  skipCondition?: SkipCondition;
  scopeEntityType?: ScopeEntityType;
  scopeEntityId?: string;
  pipelineTemplate?: PipelineTemplate;
  pipelineSteps?: PipelineStepRequestDTO[];
  abTestProviderIds?: string[];
  stepProviderOverrides?: Record<string, string>;
  scriptId?: string;
  missionId?: string;
}

// ============================================================================
// Pagination & Params
// ============================================================================

export type BatchJobPageDTO = PaginatedResponseDTO<BatchJobResponseDTO>;
export type BatchJobItemPageDTO = PaginatedResponseDTO<BatchJobItemDTO>;

export interface BatchJobListParams {
  pageNum?: number;
  pageSize?: number;
  status?: BatchJobStatus;
  batchType?: BatchType;
  scriptId?: string;
}

export interface BatchJobItemListParams {
  pageNum?: number;
  pageSize?: number;
  status?: BatchItemStatus;
}
