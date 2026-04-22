"use client";

/**
 * CommentItem
 * Renders a single comment in a card layout with flat hierarchy.
 * Supports inline editing, reply input, reply preview, resolve/reopen, and delete.
 */

import { useState, useCallback } from "react";
import { Avatar, Button, Tooltip, Spinner, toast} from "@heroui/react";
import { Check, RotateCcw, Edit3, Trash2, FileVideo, FileText, Reply, Play } from "lucide-react";
import { useTranslations, useLocale} from "next-intl";
import { RichTextContent } from "./rich-text-content";
import { ReactionBar } from "./reaction-bar";
import { CommentInput } from "./comment-input";
import { ReplyThread } from "./reply-thread";
import { AssetPreviewModal } from "@/components/common/asset-preview-modal";
import type { AssetPreviewInfo } from "@/components/common/asset-preview-modal";
import { commentService } from "@/lib/api/services/comment.service";
import { useAuthStore } from "@/lib/stores/auth-store";
import { getErrorFromException } from "@/lib/api";
import type {
  CommentResponseDTO,
  CommentTargetType,
  CommentAttachmentDTO,
  EmojiType,
} from "@/lib/api/dto/comment.dto";

// ── Helpers ──────────────────────────────────────────────────────────────────

type TimeTranslator = (key: string, values?: Record<string, number>) => string;

function formatRelativeTime(dateStr: string, t: TimeTranslator): string {
  try {
    const diff = Date.now() - new Date(dateStr).getTime();
    const sec = Math.floor(diff / 1000);
    if (sec < 60) return t("justNow");
    const min = Math.floor(sec / 60);
    if (min < 60) return t("minutesAgo", { min });
    const hour = Math.floor(min / 60);
    if (hour < 24) return t("hoursAgo", { hour });
    const day = Math.floor(hour / 24);
    if (day < 30) return t("daysAgo", { day });
    const month = Math.floor(day / 30);
    if (month < 12) return t("monthsAgo", { month });
    return t("yearsAgo", { year: Math.floor(month / 12) });
  } catch {
    return dateStr;
  }
}

// ── Component ────────────────────────────────────────────────────────────────

export interface CommentItemProps {
  comment: CommentResponseDTO;
  scriptId: string;
  targetType?: CommentTargetType;
  targetId?: string;
  onUpdated?: (comment: CommentResponseDTO) => void;
  onDeleted?: (commentId: string) => void;
  /** True when rendered as a reply (hides reply thread and resolve button) */
  isReply?: boolean;
}

export function CommentItem({
  comment,
  scriptId,
  targetType,
  targetId,
  onUpdated,
  onDeleted,
  isReply = false,
}: CommentItemProps) {
  const locale = useLocale();
  const t = useTranslations("workspace.collab.item");
  const tTime = useTranslations("workspace.collab.item.time");
  const userId = useAuthStore((s) => s.userId);
  const isOwner = userId === comment.author.id;

  // Local copy so optimistic updates don't depend on parent re-renders
  const [local, setLocal] = useState(comment);
  const [prevUpdatedAt, setPrevUpdatedAt] = useState(comment.updatedAt);

  // Sync with prop when parent refreshes (e.g. WebSocket update)
  if (comment.updatedAt !== prevUpdatedAt) {
    setPrevUpdatedAt(comment.updatedAt);
    setLocal(comment);
  }

  // UI state
  const [editing, setEditing] = useState(false);
  const [editValue, setEditValue] = useState("");
  const [editSubmitting, setEditSubmitting] = useState(false);
  const [showReplyInput, setShowReplyInput] = useState(false);
  const [actionLoading, setActionLoading] = useState<"resolve" | "reopen" | "delete" | null>(null);
  const [previewAttachment, setPreviewAttachment] = useState<AssetPreviewInfo | null>(null);

  // ── Reaction handler ──────────────────────────────────────────────────────

  const handleReaction = useCallback(
    async (emoji: EmojiType) => {
      const existing = local.reactions.find((r) => r.emoji === emoji);
      const wasReacted = existing?.reacted ?? false;

      // Optimistic update
      setLocal((prev) => {
        const nextReactions = wasReacted
          ? prev.reactions
              .map((r) =>
                r.emoji === emoji ? { ...r, count: r.count - 1, reacted: false } : r
              )
              .filter((r) => r.count > 0)
          : prev.reactions.some((r) => r.emoji === emoji)
          ? prev.reactions.map((r) =>
              r.emoji === emoji ? { ...r, count: r.count + 1, reacted: true } : r
            )
          : [...prev.reactions, { emoji, count: 1, reacted: true }];

        return { ...prev, reactions: nextReactions };
      });

      try {
        if (wasReacted) {
          await commentService.removeReaction(local.id, emoji);
        } else {
          await commentService.addReaction(local.id, { emoji });
        }
      } catch {
        // Revert to last known state from parent prop
        setLocal(comment);
      }
    },
    [local, comment]
  );

  // ── Resolve / Reopen ──────────────────────────────────────────────────────

  const handleResolve = useCallback(async () => {
    setActionLoading("resolve");
    try {
      await commentService.resolveComment(local.id);
      const updated: CommentResponseDTO = {
        ...local,
        status: "RESOLVED",
        resolvedBy: userId ?? "",
        resolvedAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      setLocal(updated);
      onUpdated?.(updated);
    } catch (err) {
      console.error("Failed to resolve comment:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setActionLoading(null);
    }
  }, [local, onUpdated, userId]);

  const handleReopen = useCallback(async () => {
    setActionLoading("reopen");
    try {
      await commentService.reopenComment(local.id);
      const updated: CommentResponseDTO = {
        ...local,
        status: "OPEN",
        resolvedBy: null,
        resolvedAt: null,
        updatedAt: new Date().toISOString(),
      };
      setLocal(updated);
      onUpdated?.(updated);
    } catch (err) {
      console.error("Failed to reopen comment:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setActionLoading(null);
    }
  }, [local, onUpdated]);

  // ── Delete ────────────────────────────────────────────────────────────────

  const handleDelete = useCallback(async () => {
    if (!window.confirm(t("deleteConfirm"))) return;
    setActionLoading("delete");
    try {
      await commentService.deleteComment(local.id);
      onDeleted?.(local.id);
    } catch (err) {
      console.error("Failed to delete comment:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setActionLoading(null);
    }
  }, [local.id, onDeleted]);

  // ── Inline edit ───────────────────────────────────────────────────────────

  const startEditing = useCallback(() => {
    setEditValue(local.content);
    setEditing(true);
  }, [local.content]);

  const handleEditSubmit = useCallback(async () => {
    const trimmed = editValue.trim();
    if (!trimmed || editSubmitting) return;
    setEditSubmitting(true);
    try {
      const updated = await commentService.updateComment(local.id, { content: trimmed });
      setLocal(updated);
      onUpdated?.(updated);
      setEditing(false);
    } catch (err) {
      console.error("Failed to update comment:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setEditSubmitting(false);
    }
  }, [editValue, editSubmitting, local.id, onUpdated]);

  // ── Render ────────────────────────────────────────────────────────────────

  const hasAttachments = local.attachments && local.attachments.length > 0;

  /** Map CommentAttachmentDTO to AssetPreviewInfo for the shared modal */
  const openAttachmentPreview = useCallback((att: CommentAttachmentDTO) => {
    setPreviewAttachment({
      id: att.assetId,
      name: att.fileName,
      assetType: att.assetType,
      fileUrl: att.fileUrl,
      thumbnailUrl: att.thumbnailUrl,
      mimeType: att.mimeType,
      fileSize: att.fileSize,
    });
  }, []);

  return (
    <div className={`group ${isReply ? "" : "rounded-xl border border-muted/10 bg-muted/[0.03] p-3"} ${local.status === "RESOLVED" ? "opacity-60" : ""}`}>
      {/* Header: Avatar + name + time + badges + actions — single row */}
      <div className="flex items-center gap-2">
        <Avatar size="sm" className="size-6 shrink-0 text-[10px]">
          {local.author.avatar ? (
            <Avatar.Image src={local.author.avatar} alt={local.author.nickname ?? "user"} />
          ) : null}
          <Avatar.Fallback>
            {(local.author.nickname ?? "U").charAt(0).toUpperCase()}
          </Avatar.Fallback>
        </Avatar>

        <span className="truncate text-xs font-medium text-foreground">
          {local.author.nickname ?? "Unknown"}
        </span>
        <span className="shrink-0 text-[10px] text-muted">
          {formatRelativeTime(local.createdAt, tTime)}
        </span>
        {local.createdAt !== local.updatedAt && (
          <span className="shrink-0 text-[10px] text-muted/60">· {t("edited")}</span>
        )}
        {local.status === "RESOLVED" && (
          <span className="shrink-0 rounded-full bg-success/20 px-1.5 py-0.5 text-[10px] font-medium text-success">
            {t("statusResolved")}
          </span>
        )}

        {/* Action buttons — right side, visible on hover */}
        <div className="ml-auto flex shrink-0 items-center gap-0.5 opacity-0 transition-opacity group-hover:opacity-100">
          {!isReply && (
            <Tooltip delay={0}>
              <Tooltip.Trigger>
                <Button
                  isIconOnly size="sm" variant="ghost" className="size-6"
                  isPending={actionLoading === "resolve" || actionLoading === "reopen"}
                  onPress={() => { if (local.status === "OPEN") { handleResolve(); } else { handleReopen(); } }}
                >
                  {({isPending}) => isPending ? <Spinner color="current" size="sm" /> : (local.status === "OPEN"
                    ? <Check className="size-3 text-success" />
                    : <RotateCcw className="size-3 text-warning" />)}
                </Button>
              </Tooltip.Trigger>
              <Tooltip.Content>
                {local.status === "OPEN" ? t("resolveTooltip") : t("reopenTooltip")}
              </Tooltip.Content>
            </Tooltip>
          )}
          {isOwner && !editing && (
            <Tooltip delay={0}>
              <Tooltip.Trigger>
                <Button isIconOnly size="sm" variant="ghost" className="size-6" onPress={startEditing}>
                  <Edit3 className="size-3 text-muted" />
                </Button>
              </Tooltip.Trigger>
              <Tooltip.Content>{t("editTooltip")}</Tooltip.Content>
            </Tooltip>
          )}
          {isOwner && (
            <Tooltip delay={0}>
              <Tooltip.Trigger>
                <Button
                  isIconOnly size="sm" variant="ghost" className="size-6"
                  isPending={actionLoading === "delete"}
                  onPress={() => { handleDelete(); }}
                >
                  {({isPending}) => isPending ? <Spinner color="current" size="sm" /> : <Trash2 className="size-3 text-danger/70" />}
                </Button>
              </Tooltip.Trigger>
              <Tooltip.Content>{t("deleteTooltip")}</Tooltip.Content>
            </Tooltip>
          )}
        </div>
      </div>

      {/* Content — full width, no indent */}
      {editing ? (
        <div className="mt-2">
          <textarea
            value={editValue}
            onChange={(e) => setEditValue(e.target.value)}
            rows={2}
            autoFocus
            className="w-full resize-none rounded-lg bg-muted/10 px-2.5 py-1.5 text-sm outline-none focus:bg-muted/15 transition-colors"
          />
          <div className="mt-1 flex justify-end gap-1.5">
            <Button size="sm" variant="ghost" className="h-7 text-xs" onPress={() => setEditing(false)}>
              {t("cancel")}
            </Button>
            <Button
              size="sm" className="h-7 text-xs"
              isDisabled={!editValue.trim() || editSubmitting}
              isPending={editSubmitting}
              onPress={() => { handleEditSubmit(); }}
            >
              {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}{t("save")}</>)}
            </Button>
          </div>
        </div>
      ) : (
        <>
          <div className="mt-1">
            <RichTextContent content={local.content} mentions={local.mentions} />
          </div>

          {/* Attachments */}
          {hasAttachments && (
            <div className="mt-1.5 flex flex-wrap gap-1.5">
              {local.attachments.map((att) => {
                const isImage = att.assetType === "IMAGE" || att.mimeType?.startsWith("image/");
                const isVideo = att.assetType === "VIDEO" || att.mimeType?.startsWith("video/");
                const isAudio = att.assetType === "AUDIO" || att.mimeType?.startsWith("audio/");

                if (isImage) {
                  return (
                    <button
                      key={att.assetId}
                      type="button"
                      onClick={() => openAttachmentPreview(att)}
                      className="block overflow-hidden rounded-lg border border-muted/20 transition-opacity hover:opacity-80"
                    >
                      <img
                        src={att.thumbnailUrl ?? att.fileUrl ?? ""}
                        alt={att.fileName ?? "attachment"}
                        className="h-[100px] max-w-[150px] object-cover"
                      />
                    </button>
                  );
                }

                if (isVideo) {
                  return (
                    <button
                      key={att.assetId}
                      type="button"
                      onClick={() => openAttachmentPreview(att)}
                      className="group/video relative block overflow-hidden rounded-lg border border-muted/20 transition-opacity hover:opacity-90"
                    >
                      {att.thumbnailUrl ? (
                        <img
                          src={att.thumbnailUrl}
                          alt={att.fileName ?? "video"}
                          className="h-[100px] max-w-[150px] object-cover"
                        />
                      ) : att.fileUrl ? (
                        <video
                          src={att.fileUrl}
                          preload="metadata"
                          muted
                          className="h-[100px] max-w-[150px] object-cover"
                        />
                      ) : (
                        <div className="flex h-[100px] w-[150px] items-center justify-center bg-muted/10">
                          <FileVideo className="size-8 text-muted/30" />
                        </div>
                      )}
                      <div className="absolute inset-0 flex items-center justify-center bg-black/20 transition-colors group-hover/video:bg-black/30">
                        <div className="flex size-8 items-center justify-center rounded-full bg-white/90 shadow-sm">
                          <Play className="size-4 text-black" fill="black" />
                        </div>
                      </div>
                      <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/60 to-transparent px-1.5 pb-1 pt-3">
                        <span className="block truncate text-[10px] text-white">{att.fileName ?? "video"}</span>
                      </div>
                    </button>
                  );
                }

                if (isAudio) {
                  return (
                    <button
                      key={att.assetId}
                      type="button"
                      onClick={() => openAttachmentPreview(att)}
                      className="flex items-center gap-2 rounded-lg border border-muted/20 bg-muted/5 px-2.5 py-2 transition-colors hover:bg-muted/10"
                    >
                      <div className="flex size-7 shrink-0 items-center justify-center rounded-full bg-accent/10">
                        <Play className="size-3 text-accent" fill="currentColor" />
                      </div>
                      <div className="flex flex-col items-start gap-0.5">
                        <span className="max-w-[100px] truncate text-[11px] font-medium">{att.fileName ?? "audio"}</span>
                        {att.fileSize != null && (
                          <span className="text-[10px] text-muted/60">
                            {att.fileSize < 1024 * 1024
                              ? `${(att.fileSize / 1024).toFixed(0)}KB`
                              : `${(att.fileSize / (1024 * 1024)).toFixed(1)}MB`}
                          </span>
                        )}
                      </div>
                    </button>
                  );
                }

                return (
                  <button
                    key={att.assetId}
                    type="button"
                    onClick={() => window.open(att.fileUrl ?? "#", "_blank")}
                    className="flex items-center gap-1.5 rounded-lg border border-muted/20 bg-muted/5 px-2 py-1 transition-colors hover:bg-muted/10"
                  >
                    <FileText className="size-3.5 shrink-0 text-muted" />
                    <span className="max-w-[100px] truncate text-[11px]">{att.fileName ?? "file"}</span>
                    {att.fileSize != null && (
                      <span className="text-[10px] text-muted/60">
                        {att.fileSize < 1024 * 1024
                          ? `${(att.fileSize / 1024).toFixed(0)}KB`
                          : `${(att.fileSize / (1024 * 1024)).toFixed(1)}MB`}
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          )}

          {/* Footer: reactions + reply — always visible, no hover show/hide */}
          <div className="mt-1.5 flex items-center gap-1.5">
            <ReactionBar
              reactions={local.reactions}
              onToggle={handleReaction}
              onPickNew={handleReaction}
              showPicker
            />
            {!isReply && (
              <button
                className="inline-flex shrink-0 items-center gap-0.5 rounded-full border border-muted/15 px-1.5 py-0.5 text-[11px] text-muted/60 transition-colors hover:border-accent/30 hover:text-accent"
                onClick={() => setShowReplyInput((v) => !v)}
              >
                <Reply className="size-3" />
                {showReplyInput ? t("cancelReply") : t("reply")}
              </button>
            )}
          </div>
        </>
      )}

      {/* Reply input */}
      {showReplyInput && targetType && targetId && (
        <div className="mt-2">
          <CommentInput
            targetType={targetType}
            targetId={targetId}
            scriptId={scriptId}
            parentId={local.id}
            placeholder={t("replyPlaceholder")}
            autoFocus
            onCancel={() => setShowReplyInput(false)}
            onCreated={(reply) => {
              setShowReplyInput(false);
              setLocal((prev) => ({
                ...prev,
                replyCount: prev.replyCount + 1,
                latestReplies: [...prev.latestReplies, reply].slice(-2),
              }));
            }}
          />
        </div>
      )}

      {/* Reply thread (top-level only) */}
      {!isReply && !editing && (
        <ReplyThread
          commentId={local.id}
          scriptId={scriptId}
          targetType={targetType}
          targetId={targetId}
          replyCount={local.replyCount}
          latestReplies={local.latestReplies}
          onReplyCountChange={(delta) =>
            setLocal((prev) => ({
              ...prev,
              replyCount: Math.max(0, prev.replyCount + delta),
            }))
          }
        />
      )}

      {/* Attachment Preview Modal */}
      <AssetPreviewModal
        isOpen={!!previewAttachment}
        onOpenChange={(open) => { if (!open) setPreviewAttachment(null); }}
        asset={previewAttachment}
      />
    </div>
  );
}
