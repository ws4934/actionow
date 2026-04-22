/**
 * Notification Service
 * Backend: actionow-collab NotificationController @ /collab/notifications
 */

import { api } from "../client";
import type {
  NotificationPageDTO,
  UnreadCountResponseDTO,
  BatchMarkReadRequestDTO,
} from "../dto/notification.dto";

const BASE = "/api/collab/notifications";

export interface ListNotificationsParams {
  workspaceId?: string;
  type?: string;
  isRead?: boolean;
  pageNum?: number;
  pageSize?: number;
}

function buildQuery(params: ListNotificationsParams): string {
  const usp = new URLSearchParams();
  if (params.workspaceId) usp.set("workspaceId", params.workspaceId);
  if (params.type) usp.set("type", params.type);
  if (typeof params.isRead === "boolean") usp.set("isRead", String(params.isRead));
  if (params.pageNum != null) usp.set("pageNum", String(params.pageNum));
  if (params.pageSize != null) usp.set("pageSize", String(params.pageSize));
  const qs = usp.toString();
  return qs ? `?${qs}` : "";
}

export const notificationService = {
  list: (params: ListNotificationsParams = {}) =>
    api.get<NotificationPageDTO>(`${BASE}${buildQuery(params)}`),

  getUnreadCount: () => api.get<UnreadCountResponseDTO>(`${BASE}/unread/count`),

  markAsRead: (id: string) => api.put<void>(`${BASE}/${id}/read`),

  batchMarkAsRead: (ids: string[]) =>
    api.put<void>(`${BASE}/read/batch`, { ids } satisfies BatchMarkReadRequestDTO),

  markAllAsRead: () => api.put<void>(`${BASE}/read/all`),

  delete: (id: string) => api.delete<void>(`${BASE}/${id}`),
};

export default notificationService;
