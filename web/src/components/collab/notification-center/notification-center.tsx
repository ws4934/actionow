"use client";

/**
 * NotificationCenter
 *
 * Bell icon button (with unread badge) + Popover notification list.
 * Reads from useNotificationStore, which is updated in real-time by the
 * WebSocket provider (NOTIFICATION / NOTIFICATION_COUNT messages).
 *
 * On item click: marks the notification as read and navigates to the
 * relevant script studio page (if scriptId is in the payload).
 */

import { useState, useCallback, useEffect } from "react";
import { useRouter } from "next/navigation";
import { useLocale } from "next-intl";
import { useTranslations } from "next-intl";
import { Button, Popover, ScrollShadow, Tooltip, Avatar } from "@heroui/react";
import {
  Bell,
  AtSign,
  MessageSquare,
  CheckCheck,
  BellOff,
  Loader2,
  CheckCircle2,
  AlertTriangle,
  Sparkles,
  Megaphone,
} from "lucide-react";
import {
  useNotificationStore,
  type NotificationDTO,
  type NotificationType,
} from "@/lib/stores/notification-store";
import { notificationService } from "@/lib/api/services";
import type { NotificationResponseDTO } from "@/lib/api/dto/notification.dto";

// ── Helpers ──────────────────────────────────────────────────────────────────

type TimeTranslator = (key: string, values?: Record<string, number>) => string;

function formatRelativeTime(dateStr: string, tTime: TimeTranslator): string {
  try {
    const diff = Date.now() - new Date(dateStr).getTime();
    const sec = Math.floor(diff / 1000);
    if (sec < 60) return tTime("justNow");
    const min = Math.floor(sec / 60);
    if (min < 60) return tTime("minutesAgo", { min });
    const hour = Math.floor(min / 60);
    if (hour < 24) return tTime("hoursAgo", { hour });
    const day = Math.floor(hour / 24);
    if (day < 30) return tTime("daysAgo", { day });
    const month = Math.floor(day / 30);
    if (month < 12) return tTime("monthsAgo", { month });
    return tTime("yearsAgo", { year: Math.floor(month / 12) });
  } catch {
    return "";
  }
}

const NOTIFICATION_ICONS: Record<string, typeof AtSign> = {
  COMMENT_MENTION: AtSign,
  COMMENT_REPLY: MessageSquare,
  TASK_COMPLETED: CheckCircle2,
  TASK_FAILED: AlertTriangle,
  TASK_STATUS_CHANGED: Loader2,
  GENERATION_RESULT: Sparkles,
  SYSTEM_ALERT: AlertTriangle,
  SYSTEM_ANNOUNCEMENT: Megaphone,
};

function mapBackendDtoToNotification(dto: NotificationResponseDTO): NotificationDTO {
  const payload = (dto.payload ?? {}) as NotificationDTO["payload"];
  return {
    id: dto.id,
    type: dto.type as NotificationType,
    title: dto.title ?? dto.type,
    sender: {
      id: dto.senderId ?? "",
      name: dto.senderName ?? "",
    },
    payload: {
      commentId: (payload as Record<string, unknown>)?.commentId as string | undefined,
      targetType: dto.entityType,
      targetId: dto.entityId,
      scriptId: (payload as Record<string, unknown>)?.scriptId as string | undefined,
    },
    receivedAt: dto.createdAt,
    read: dto.isRead,
  };
}

// ── NotificationItem ─────────────────────────────────────────────────────────

function NotificationItem({
  notification,
  onRead,
  tTime,
}: {
  notification: NotificationDTO;
  onRead: (n: NotificationDTO) => void;
  tTime: TimeTranslator;
}) {
  const TypeIcon =
    NOTIFICATION_ICONS[notification.type] ?? MessageSquare;

  return (
    <button
      className={`flex w-full items-start gap-3 rounded-lg px-3 py-2.5 text-left transition-colors hover:bg-muted/10 ${
        notification.read ? "opacity-60" : ""
      }`}
      onClick={() => onRead(notification)}
    >
      {/* Sender avatar */}
      <div className="relative mt-0.5 shrink-0">
        <Avatar size="sm">
          <Avatar.Fallback className="bg-accent/20 text-xs text-accent">
            {notification.sender.name.charAt(0).toUpperCase()}
          </Avatar.Fallback>
        </Avatar>
        {/* Type badge */}
        <span className="absolute -bottom-0.5 -right-0.5 flex size-4 items-center justify-center rounded-full bg-background ring-1 ring-muted/20">
          <TypeIcon className="size-2.5 text-muted" />
        </span>
      </div>

      {/* Content */}
      <div className="min-w-0 flex-1">
        <p
          className={`text-xs leading-snug ${
            notification.read
              ? "text-muted"
              : "font-medium text-foreground"
          }`}
        >
          {notification.title}
        </p>
        <p className="mt-0.5 text-[10px] text-muted">
          {formatRelativeTime(notification.receivedAt, tTime)}
        </p>
      </div>

      {/* Unread dot */}
      {!notification.read && (
        <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-accent" />
      )}
    </button>
  );
}

// ── NotificationCenter ───────────────────────────────────────────────────────

export function NotificationCenter() {
  const router = useRouter();
  const locale = useLocale();
  const t = useTranslations("workspace.collab.notifications");
  const tTime = useTranslations("workspace.collab.item.time");

  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);

  const unreadCount = useNotificationStore((s) => s.unreadCount);
  const notifications = useNotificationStore((s) => s.notifications);
  const setNotifications = useNotificationStore((s) => s.setNotifications);
  const setUnreadCount = useNotificationStore((s) => s.setUnreadCount);
  const markRead = useNotificationStore((s) => s.markRead);
  const markAllRead = useNotificationStore((s) => s.markAllRead);

  const loadHistory = useCallback(async () => {
    setLoading(true);
    try {
      const page = await notificationService.list({ pageNum: 1, pageSize: 30 });
      setNotifications((page.records ?? []).map(mapBackendDtoToNotification));
    } catch (err) {
      console.error("[NotificationCenter] Failed to load history:", err);
    } finally {
      setLoading(false);
    }
  }, [setNotifications]);

  // 打开下拉时按需拉取历史未读，解决"徽标数 N / 列表空"的断层
  useEffect(() => {
    if (open) {
      loadHistory();
    }
  }, [open, loadHistory]);

  const handleRead = useCallback(
    (n: NotificationDTO) => {
      markRead(n.id);
      setOpen(false);
      // 持久化已读状态到服务端（失败不回滚，由下次打开时的 REST 同步纠偏）
      notificationService.markAsRead(n.id).catch((err) => {
        console.error("[NotificationCenter] markAsRead failed:", err);
      });

      // Navigate to the relevant script studio page
      const { scriptId } = n.payload;
      if (scriptId) {
        router.push(`/${locale}/workspace/projects/${scriptId}`);
      }
    },
    [markRead, router, locale]
  );

  const handleMarkAllRead = useCallback(() => {
    markAllRead();
    setUnreadCount(0);
    notificationService.markAllAsRead().catch((err) => {
      console.error("[NotificationCenter] markAllAsRead failed:", err);
    });
  }, [markAllRead, setUnreadCount]);

  const displayCount = unreadCount > 99 ? "99+" : unreadCount > 0 ? String(unreadCount) : null;

  return (
    <Popover isOpen={open} onOpenChange={setOpen}>
      <Popover.Trigger>
        <Button
          isIconOnly
          variant="ghost"
          size="sm"
          aria-label={t("ariaLabel")}
          className="relative"
        >
          <Bell className="size-4" />
          {displayCount && (
            <span className="absolute -right-0.5 -top-0.5 flex min-w-[14px] items-center justify-center rounded-full bg-danger px-0.5 text-[8px] font-bold leading-[14px] text-white">
              {displayCount}
            </span>
          )}
        </Button>
      </Popover.Trigger>

      <Popover.Content placement="bottom end" className="w-80 p-0">
        <Popover.Dialog className="p-0">
          {/* Header */}
          <div className="flex items-center justify-between border-b border-border px-4 py-2.5">
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-foreground">{t("title")}</span>
              {unreadCount > 0 && (
                <span className="inline-flex size-5 items-center justify-center rounded-full bg-surface-2 text-[10px] font-medium text-foreground-2">
                  {unreadCount > 99 ? "99+" : unreadCount}
                </span>
              )}
            </div>
            {unreadCount > 0 && (
              <Tooltip delay={0}>
                <Tooltip.Trigger>
                  <Button
                    isIconOnly
                    variant="ghost"
                    size="sm"
                    className="size-7"
                    onPress={handleMarkAllRead}
                  >
                    <CheckCheck className="size-3.5 text-muted" />
                  </Button>
                </Tooltip.Trigger>
                <Tooltip.Content>{t("markAllRead")}</Tooltip.Content>
              </Tooltip>
            )}
          </div>

          {/* Notification list */}
          {loading && notifications.length === 0 ? (
            <div className="flex flex-col items-center justify-center gap-2 px-4 py-10 text-center">
              <Loader2 className="size-6 animate-spin text-muted/50" />
            </div>
          ) : notifications.length === 0 ? (
            <div className="flex flex-col items-center justify-center gap-2 px-4 py-10 text-center">
              <BellOff className="size-8 text-muted/30" />
              <p className="text-xs text-muted">{t("empty")}</p>
            </div>
          ) : (
            <ScrollShadow className="max-h-80 overflow-y-auto p-1.5">
              <div className="space-y-0.5">
                {notifications.map((n) => (
                  <NotificationItem
                    key={n.id}
                    notification={n}
                    onRead={handleRead}
                    tTime={tTime}
                  />
                ))}
              </div>
            </ScrollShadow>
          )}
        </Popover.Dialog>
      </Popover.Content>
    </Popover>
  );
}
