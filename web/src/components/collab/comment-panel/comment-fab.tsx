"use client";

import { MessageSquare } from "lucide-react";
import { Button, Tooltip } from "@heroui/react";
import { useTranslations } from "next-intl";
import { useCommentPanelStore } from "@/lib/stores/comment-panel-store";
import { useNotificationStore, notificationSelectors } from "@/lib/stores/notification-store";

export function CommentFAB() {
  const t = useTranslations("workspace.collab.fab");
  const open = useCommentPanelStore((s) => s.open);
  const unreadCount = useNotificationStore(notificationSelectors.unreadCount);

  return (
    <Tooltip delay={300}>
      <Tooltip.Trigger>
        <div className="fixed bottom-6 right-6 z-50">
          <Button
            isIconOnly
            size="lg"
            className="relative size-12 rounded-full shadow-lg"
            onPress={() => open()}
            aria-label={t("ariaLabel")}
          >
            <MessageSquare className="size-5" />
            {unreadCount > 0 && (
              <span className="absolute -right-1 -top-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-danger px-1 text-[10px] font-semibold text-white">
                {unreadCount > 99 ? "99+" : unreadCount}
              </span>
            )}
          </Button>
        </div>
      </Tooltip.Trigger>
      <Tooltip.Content placement="left">{t("tooltip")}</Tooltip.Content>
    </Tooltip>
  );
}
