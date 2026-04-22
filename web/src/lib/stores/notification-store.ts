/**
 * Notification Store
 * Tracks unread notification counts and notification list.
 * Updated by WebSocket NOTIFICATION / NOTIFICATION_COUNT messages.
 *
 * State is intentionally NOT persisted — counts are re-fetched from
 * Redis on reconnect via the NOTIFICATION_COUNT WebSocket message.
 */

import { create } from "zustand";

// ============================================================================
// Types
// ============================================================================

export type NotificationType =
  | "COMMENT_MENTION"
  | "COMMENT_REPLY"
  | "TASK_COMPLETED"
  | "TASK_FAILED"
  | "TASK_STATUS_CHANGED"
  | "GENERATION_RESULT"
  | "SYSTEM_ALERT"
  | "SYSTEM_ANNOUNCEMENT";

export interface NotificationPayload {
  commentId?: string;
  targetType?: string;
  targetId?: string;
  scriptId?: string;
}

export interface NotificationDTO {
  id: string;
  type: NotificationType;
  title: string;
  sender: {
    id: string;
    name: string;
  };
  payload: NotificationPayload;
  /** ISO datetime string */
  receivedAt: string;
  read: boolean;
}

export interface NotificationStoreState {
  unreadCount: number;
  notifications: NotificationDTO[];
}

export interface NotificationStoreActions {
  /** Replace total unread count (from server sync) */
  setUnreadCount: (count: number) => void;
  /** Apply a delta from NOTIFICATION_COUNT WebSocket message */
  incrementUnread: (delta: number) => void;
  /** Prepend a new notification from NOTIFICATION WebSocket message */
  addNotification: (notification: NotificationDTO) => void;
  /** Replace the notifications list (after REST history load) */
  setNotifications: (items: NotificationDTO[]) => void;
  /** Mark a single notification as read */
  markRead: (notificationId: string) => void;
  /** Mark all as read and reset count */
  markAllRead: () => void;
  /** Full reset on workspace change / logout */
  reset: () => void;
}

export type NotificationStore = NotificationStoreState & NotificationStoreActions;

// ============================================================================
// Constants
// ============================================================================

const INITIAL_STATE: NotificationStoreState = {
  unreadCount: 0,
  notifications: [],
};

/** Keep at most this many notifications in memory */
const MAX_NOTIFICATIONS = 50;

// ============================================================================
// Store
// ============================================================================

export const useNotificationStore = create<NotificationStore>((set) => ({
  ...INITIAL_STATE,

  setUnreadCount: (count) => set({ unreadCount: Math.max(0, count) }),

  incrementUnread: (delta) =>
    set((state) => ({ unreadCount: Math.max(0, state.unreadCount + delta) })),

  addNotification: (notification) =>
    set((state) => {
      const exists = state.notifications.some((n) => n.id === notification.id);
      if (exists) return state;
      const notifications = [notification, ...state.notifications].slice(
        0,
        MAX_NOTIFICATIONS
      );
      return { notifications };
    }),

  setNotifications: (items) =>
    set(() => ({ notifications: items.slice(0, MAX_NOTIFICATIONS) })),

  markRead: (notificationId) =>
    set((state) => {
      const notifications = state.notifications.map((n) =>
        n.id === notificationId ? { ...n, read: true } : n
      );
      const readNow = state.notifications.find(
        (n) => n.id === notificationId && !n.read
      );
      return {
        notifications,
        unreadCount: readNow
          ? Math.max(0, state.unreadCount - 1)
          : state.unreadCount,
      };
    }),

  markAllRead: () =>
    set((state) => ({
      unreadCount: 0,
      notifications: state.notifications.map((n) => ({ ...n, read: true })),
    })),

  reset: () => set(INITIAL_STATE),
}));

// ============================================================================
// Selectors
// ============================================================================

export const notificationSelectors = {
  unreadCount: (state: NotificationStore) => state.unreadCount,
  notifications: (state: NotificationStore) => state.notifications,
  hasUnread: (state: NotificationStore) => state.unreadCount > 0,
};

// ============================================================================
// Convenience Hooks
// ============================================================================

export function useNotificationState() {
  const unreadCount = useNotificationStore(notificationSelectors.unreadCount);
  const notifications = useNotificationStore(notificationSelectors.notifications);
  const hasUnread = useNotificationStore(notificationSelectors.hasUnread);
  return { unreadCount, notifications, hasUnread };
}

export function useNotificationActions() {
  const setUnreadCount = useNotificationStore((s) => s.setUnreadCount);
  const incrementUnread = useNotificationStore((s) => s.incrementUnread);
  const addNotification = useNotificationStore((s) => s.addNotification);
  const setNotifications = useNotificationStore((s) => s.setNotifications);
  const markRead = useNotificationStore((s) => s.markRead);
  const markAllRead = useNotificationStore((s) => s.markAllRead);
  const reset = useNotificationStore((s) => s.reset);
  return {
    setUnreadCount,
    incrementUnread,
    addNotification,
    setNotifications,
    markRead,
    markAllRead,
    reset,
  };
}

// ============================================================================
// Non-hook helpers (for use outside React components, e.g. WebSocket provider)
// ============================================================================

export function getNotificationStore() {
  return useNotificationStore.getState();
}
