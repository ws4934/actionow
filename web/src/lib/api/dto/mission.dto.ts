/**
 * Mission DTOs
 * Aligned with backend Agent Mission API
 */

import type { PaginatedResponseDTO } from "./workspace.dto";

// ============================================================================
// Enums (union types)
// ============================================================================

export type MissionStatus =
  | "CREATED"
  | "EXECUTING"
  | "WAITING"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED";

export type StepType = "AGENT_INVOKE" | "WAIT_TASKS";

export type StepStatus = "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";

// ============================================================================
// Core Response DTOs
// ============================================================================

export interface MissionResponseDTO {
  id: string;
  workspaceId: string;
  sessionId: string;
  creatorId: string;
  title: string;
  goal: string;
  plan: string | null;
  status: MissionStatus;
  currentStep: number;
  progress: number;
  totalSteps: number;
  totalCreditCost: number;
  errorMessage: string | null;
  pendingTaskIds: string[];
  completedTaskIds: string[];
  failedTaskIds: string[];
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PendingTaskStats {
  total: number;
  completed: number;
  failed: number;
  running: number;
}

export interface StepSummary {
  number: number;
  type: StepType;
  status: StepStatus;
  summary: string | null;
}

export interface MissionProgressDTO {
  id: string;
  title: string;
  status: MissionStatus;
  progress: number;
  currentStep: number;
  totalSteps: number;
  currentActivity: string | null;
  pendingTasks: PendingTaskStats;
  steps: StepSummary[];
  totalCreditCost: number;
  startedAt: string | null;
}

export interface MissionStepDTO {
  id: string;
  missionId: string;
  stepNumber: number;
  stepType: StepType;
  inputSummary: string | null;
  outputSummary: string | null;
  toolCalls: Record<string, unknown>[] | null;
  delegatedTaskIds: string[];
  status: StepStatus;
  durationMs: number | null;
  creditCost: number;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
}

export interface MissionSseEvent {
  missionId: string;
  eventType: string;
  status?: MissionStatus;
  message?: string;
  progress?: number;
  currentStep?: number;
  data?: Record<string, unknown>;
  timestamp?: string;
}

// ============================================================================
// Pagination
// ============================================================================

export type MissionPageDTO = PaginatedResponseDTO<MissionResponseDTO>;

// ============================================================================
// Params
// ============================================================================

export interface MissionListParams {
  current?: number;
  size?: number;
  status?: MissionStatus;
}
