/**
 * Task Service
 * Handles Task Center API endpoints.
 * Aligned with backend API doc: apis/09-任务调度服务.md
 */

import { api } from "../client";
import type {
  TaskPageDTO,
  TaskListItemDTO,
  TaskDetailDTO,
  TaskStatsSummaryDTO,
  RunningTaskCountDTO,
  QueueStatusDTO,
  QueuePositionDTO,
  TaskListParams,
  TaskListAllParams,
} from "../dto/task.dto";

const TASK_BASE = "/api/tasks";

export const taskService = {
  // ============ Task List (5.1.5 / 5.1.7 / 5.1.8) ============

  /** Paginated query — GET /tasks?... (5.1.5) */
  getTasks: (params?: TaskListParams) =>
    api.get<TaskPageDTO>(TASK_BASE, {
      params: params as Record<string, string | number | boolean | undefined>,
    }),

  /** My tasks, paginated — GET /tasks/my?... (5.1.7) */
  getMyTasks: (params?: TaskListParams, options?: { signal?: AbortSignal }) =>
    api.get<TaskPageDTO>(`${TASK_BASE}/my`, {
      params: params as Record<string, string | number | boolean | undefined>,
      signal: options?.signal,
    }),

  /** All my tasks, non-paginated — GET /tasks/my/all (5.1.8) */
  getMyTasksAll: (params?: TaskListAllParams) =>
    api.get<TaskListItemDTO[]>(`${TASK_BASE}/my/all`, {
      params: params as Record<string, string | number | boolean | undefined>,
    }),

  /** Business-related tasks — GET /tasks/business/{businessId}?businessType= (5.1.9) */
  getBusinessTasks: (businessId: string, businessType?: string) =>
    api.get<TaskListItemDTO[]>(`${TASK_BASE}/business/${businessId}`, {
      params: businessType ? { businessType } : undefined,
    }),

  // ============ Stats (5.1.10 / 5.4.1 / 5.4.2) ============

  /** Workspace stats summary — GET /tasks/stats/summary (5.4.1) */
  getStatsSummary: () =>
    api.get<TaskStatsSummaryDTO>(`${TASK_BASE}/stats/summary`),

  /** My stats summary — GET /tasks/stats/my-summary (5.4.2) */
  getMyStatsSummary: () =>
    api.get<TaskStatsSummaryDTO>(`${TASK_BASE}/stats/my-summary`),

  /** Running task count — GET /tasks/stats/running (5.1.10) */
  getRunningCount: () =>
    api.get<RunningTaskCountDTO>(`${TASK_BASE}/stats/running`),

  // ============ Queue (5.4.9 / 5.2.8) ============

  /** Queue status — GET /tasks/stats/queue (5.4.9) */
  getQueueStatus: () =>
    api.get<QueueStatusDTO>(`${TASK_BASE}/stats/queue`),

  /** Queue position — GET /tasks/{taskId}/queue-position (5.2.8) */
  getQueuePosition: (taskId: string) =>
    api.get<QueuePositionDTO>(`${TASK_BASE}/${taskId}/queue-position`),

  // ============ Task Detail (5.1.4) ============

  /** Get task detail — GET /tasks/{taskId} (5.1.4) */
  getTaskDetail: (taskId: string) =>
    api.get<TaskDetailDTO>(`${TASK_BASE}/${taskId}`),

  // ============ Task Actions (5.1.2 / 5.1.3) ============

  /** Cancel a task — POST /tasks/{taskId}/cancel (5.1.2) */
  cancelTask: (taskId: string) =>
    api.post<null>(`${TASK_BASE}/${taskId}/cancel`),

  /** Retry a failed task — POST /tasks/{taskId}/retry (5.1.3) */
  retryTask: (taskId: string) =>
    api.post<TaskListItemDTO>(`${TASK_BASE}/${taskId}/retry`),
};

export default taskService;
