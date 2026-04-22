/**
 * Task Center DTOs
 * Aligned with backend API doc: apis/09-任务调度服务.md
 */

import type { PaginatedResponseDTO } from "./workspace.dto";

// ============================================================================
// Enums (union types)
// ============================================================================

export type TaskStatus =
  | "PENDING"
  | "QUEUED"
  | "RUNNING"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED";

/** Task type — matches 2.3 任务类型 */
export type TaskType =
  | "IMAGE_GENERATION"
  | "VIDEO_GENERATION"
  | "AUDIO_GENERATION"
  | "TEXT_GENERATION"
  | "TTS_GENERATION"
  | "BATCH_EXPORT"
  | "FILE_PROCESSING"
  | string
  | null;

/** Business entity that triggered this task — matches 2.5 实体类型 */
export type TaskBusinessType = "ASSET" | "SCRIPT" | string;

/** Entity type — matches 2.5 */
export type TaskEntityType =
  | "SCRIPT"
  | "EPISODE"
  | "STORYBOARD"
  | "CHARACTER"
  | "SCENE"
  | "PROP"
  | "STYLE"
  | "ASSET"
  | string;

/** Generation type — matches 5.2.1 */
export type GenerationType = "IMAGE" | "VIDEO" | "AUDIO" | "TEXT" | string;

/** Task source — matches 2.6 */
export type TaskSource = "MANUAL" | "BATCH" | "RETRY" | "SCHEDULED" | string;

// ============================================================================
// Task Response DTO (shared by list & detail)
// Matches t_task table (2.1) + enriched fields (section 8)
// ============================================================================

export interface TaskListItemDTO {
  id: string;
  workspaceId: string;
  creatorId: string;
  type: TaskType;
  title: string;
  status: TaskStatus;
  businessType: TaskBusinessType | null;
  businessId: string | null;
  scriptId: string | null;
  entityId: string | null;
  entityType: TaskEntityType | null;
  entityName: string | null;
  providerId: string | null;
  generationType: GenerationType | null;
  thumbnailUrl: string | null;
  creditCost: number;
  source: TaskSource | null;
  priority: number;
  progress: number;
  retryCount: number;
  maxRetries: number;
  timeoutSeconds: number;
  inputParams: Record<string, unknown> | null;
  outputResult: Record<string, unknown> | null;
  errorMessage: string | null;
  errorDetail: Record<string, unknown> | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
  // Enriched fields (section 8 — 响应名称富化)
  creatorName?: string | null;
  scriptName?: string | null;
  providerName?: string | null;
}

/** Detail response is the same shape, enriched names always present */
export type TaskDetailDTO = TaskListItemDTO;

// ============================================================================
// Stats DTOs — matches 5.4.1 TaskStatsSummary
// ============================================================================

export interface TaskStatsSummaryDTO {
  totalTasks: number;
  pendingTasks: number;
  runningTasks: number;
  completedTasks: number;
  failedTasks: number;
  cancelledTasks: number;
  successRate: number;
  avgDurationMs: number;
  totalCreditsUsed: number;
}

// ============================================================================
// Queue DTOs
// ============================================================================

/** Running task count — GET /tasks/stats/running */
export interface RunningTaskCountDTO {
  count: number;
}

/** Queue status — matches 5.4.9 QueueStatus */
export interface QueueStatusDTO {
  totalQueued: number;
  highPriorityQueued: number;
  normalPriorityQueued: number;
  lowPriorityQueued: number;
  currentlyRunning: number;
  maxConcurrent: number;
  utilizationRate: number;
}

/** Queue position — matches 5.2.8 (position: -1 means not in queue) */
export interface QueuePositionDTO {
  position: number;
}

// ============================================================================
// Pagination
// ============================================================================

export type TaskPageDTO = PaginatedResponseDTO<TaskListItemDTO>;

// ============================================================================
// Params
// ============================================================================

export interface TaskListParams {
  pageNum?: number;
  pageSize?: number;
  status?: TaskStatus;
  type?: string;
  keyword?: string;
  scriptId?: string;
  entityType?: string;
}

export interface TaskListAllParams {
  status?: TaskStatus;
  type?: string;
}
