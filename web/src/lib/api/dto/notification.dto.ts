/**
 * Notification DTOs — aligned with backend actionow-collab NotificationController
 * Backend: GET/PUT /collab/notifications
 */

export type NotificationType =
  | "COMMENT_MENTION"
  | "COMMENT_REPLY"
  | "TASK_COMPLETED"
  | "TASK_FAILED"
  | "TASK_STATUS_CHANGED"
  | "GENERATION_RESULT"
  | "SYSTEM_ALERT"
  | "SYSTEM_ANNOUNCEMENT";

export interface NotificationResponseDTO {
  id: string;
  workspaceId?: string;
  userId: string;
  type: NotificationType | string;
  title: string;
  content?: string;
  payload?: Record<string, unknown>;
  entityType?: string;
  entityId?: string;
  senderId?: string;
  senderName?: string;
  isRead: boolean;
  readAt?: string;
  priority?: number;
  createdAt: string;
}

export interface NotificationPageDTO {
  pageNum: number;
  pageSize: number;
  total: number;
  records: NotificationResponseDTO[];
}

export interface UnreadCountResponseDTO {
  total: number;
  byType?: Record<string, number>;
}

export interface BatchMarkReadRequestDTO {
  ids: string[];
}
