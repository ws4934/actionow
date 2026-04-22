"use client";

/**
 * Comment Panel
 *
 * Right-side panel displayed alongside the Script Panel.
 * When collapsed (isOpen=false), renders a narrow icon strip (w-10).
 * When expanded (isOpen=true), renders the full comment panel (w-80).
 */

import { MessageSquare, ChevronRight, ChevronLeft, CheckCheck, RefreshCw } from "lucide-react";
import { Button, ScrollShadow, Tooltip } from "@heroui/react";
import { useTranslations } from "next-intl";
import { useCommentPanelStore } from "@/lib/stores/comment-panel-store";
import { useNotificationStore, notificationSelectors } from "@/lib/stores/notification-store";
import { CommentList } from "@/components/collab/comments/comment-list";
import { CommentInput } from "@/components/collab/comments/comment-input";
import type { CommentResponseDTO } from "@/lib/api/dto/comment.dto";
import { useCallback, useRef, useState } from "react";

// ── Helpers ──────────────────────────────────────────────────────────────────

function targetTypeLabel(
  type: string,
  t: (key: string) => string
): string {
  const key = type as "CHARACTER" | "STORYBOARD" | "SCENE" | "PROP" | "SCRIPT" | "EPISODE";
  try {
    return t(key);
  } catch {
    return type;
  }
}

// ── Component ────────────────────────────────────────────────────────────────

export function CommentPanel() {
  const t = useTranslations("workspace.collab.panel");
  const isOpen = useCommentPanelStore((s) => s.isOpen);
  const open = useCommentPanelStore((s) => s.open);
  const close = useCommentPanelStore((s) => s.close);
  const target = useCommentPanelStore((s) => s.target);
  const unreadCount = useNotificationStore(notificationSelectors.unreadCount);
  const [showResolved, setShowResolved] = useState(true);

  // Ref to CommentList's addComment function
  const addCommentRef = useRef<((c: CommentResponseDTO) => void) | null>(null);
  const refreshRef = useRef<(() => void) | null>(null);

  const handleAddCommentRef = useCallback((fn: (c: CommentResponseDTO) => void) => {
    addCommentRef.current = fn;
  }, []);

  const handleRefreshRef = useCallback((fn: () => void) => {
    refreshRef.current = fn;
  }, []);

  const handleCommentCreated = useCallback((comment: CommentResponseDTO) => {
    // Push the full API response (with attachments) directly into CommentList
    addCommentRef.current?.(comment);
  }, []);

  /* ── Collapsed icon strip ── */
  if (!isOpen) {
    return (
      <div className="flex h-full w-10 flex-col items-center border-l border-muted/10 bg-background pt-3">
        <Tooltip delay={0}>
          <Tooltip.Trigger>
            <Button
              isIconOnly
              size="sm"
              variant="ghost"
              onPress={() => open()}
              aria-label={t("title")}
              className="relative"
            >
              <MessageSquare className="size-4" />
              {unreadCount > 0 && (
                <span className="absolute -right-1 -top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-danger px-0.5 text-[10px] font-semibold text-white">
                  {unreadCount > 99 ? "99+" : unreadCount}
                </span>
              )}
            </Button>
          </Tooltip.Trigger>
          <Tooltip.Content placement="left">{t("title")}</Tooltip.Content>
        </Tooltip>
      </div>
    );
  }

  return (
    <div className="flex h-full w-full flex-col border-l border-muted/10 bg-background">
      {/* ── Header ── */}
      <div className="flex shrink-0 items-center justify-between gap-2 border-b border-muted/10 px-4 py-3">
        <div className="flex items-center gap-2">
          <MessageSquare className="size-4 text-muted" />
          <span className="text-sm font-medium">{t("title")}</span>
        </div>
        <div className="flex items-center gap-1">
          <Tooltip delay={0}>
            <Tooltip.Trigger>
              <Button
                isIconOnly
                size="sm"
                variant="ghost"
                onPress={() => refreshRef.current?.()}
                aria-label={t("refresh")}
                className="size-7"
              >
                <RefreshCw className="size-3.5" />
              </Button>
            </Tooltip.Trigger>
            <Tooltip.Content>{t("refresh")}</Tooltip.Content>
          </Tooltip>
          <Tooltip delay={0}>
            <Tooltip.Trigger>
              <Button
                isIconOnly
                size="sm"
                variant="ghost"
                onPress={() => setShowResolved((v) => !v)}
                aria-label={t("filterResolved")}
                className={`size-7 ${!showResolved ? "text-accent" : ""}`}
              >
                <CheckCheck className="size-4" />
              </Button>
            </Tooltip.Trigger>
            <Tooltip.Content>
              {showResolved ? t("hideResolved") : t("showResolved")}
            </Tooltip.Content>
          </Tooltip>
          <Button
            isIconOnly
            size="sm"
            variant="ghost"
            onPress={close}
            aria-label={t("closeAriaLabel")}
          >
            <ChevronRight className="size-4" />
          </Button>
        </div>
      </div>

      {/* ── Entity breadcrumb ── */}
      {target && (
        <div className="flex shrink-0 items-center gap-1.5 border-b border-muted/10 px-4 py-2">
          <span className="text-xs text-muted">
            {targetTypeLabel(target.type, (key) => t(`entityTypes.${key}`))}
          </span>
          <ChevronLeft className="size-2.5 text-muted/50" />
          <span className="truncate text-xs font-medium text-foreground">
            {target.name}
          </span>
        </div>
      )}

      {/* ── Body ── */}
      {target ? (
        <>
          <ScrollShadow className="min-h-0 flex-1">
            <CommentList
              targetType={target.type}
              targetId={target.id}
              scriptId={target.scriptId}
              showResolved={showResolved}
              onAddCommentRef={handleAddCommentRef}
              onRefreshRef={handleRefreshRef}
            />
          </ScrollShadow>

          {/* ── Footer input ── */}
          <div className="shrink-0 border-t border-muted/10 p-3">
            <CommentInput
              targetType={target.type}
              targetId={target.id}
              scriptId={target.scriptId}
              onCreated={handleCommentCreated}
            />
          </div>
        </>
      ) : (
        /* No entity selected */
        <div className="flex flex-1 flex-col items-center justify-center gap-3 p-6 text-center">
          <MessageSquare className="size-10 text-muted/30" />
          <p className="text-sm text-muted">{t("noTarget")}</p>
          <p className="text-xs text-muted/60">
            {t("noTargetHint")}
          </p>
        </div>
      )}
    </div>
  );
}
