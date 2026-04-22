"use client";

/**
 * CommentList
 * Paginated comment list for an entity target, with real-time WebSocket updates.
 * Subscribes to COMMENT_CREATED / UPDATED / DELETED / RESOLVED / REOPENED / REACTION.
 */

import { useState, useEffect, useCallback, useRef } from "react";
import { Button, Spinner, toast} from "@heroui/react";
import { MessageSquare } from "lucide-react";
import { useTranslations, useLocale} from "next-intl";
import { CommentItem } from "./comment-item";
import { commentService } from "@/lib/api/services/comment.service";
import { useWebSocketMessage } from "@/lib/websocket";
import type {
  CommentResponseDTO,
  CommentTargetType,
  EmojiType,
} from "@/lib/api/dto/comment.dto";
import { getErrorFromException } from "@/lib/api";
import type {
  CommentCreatedData,
  CommentUpdatedData,
  CommentDeletedData,
  CommentResolvedData,
  CommentReopenedData,
  CommentReactionData,
} from "@/lib/websocket/types";

// ── Component ────────────────────────────────────────────────────────────────

interface CommentListProps {
  targetType: CommentTargetType;
  targetId: string;
  scriptId: string;
  showResolved?: boolean;
  /** Called once on mount with a function to imperatively add a comment */
  onAddCommentRef?: (fn: (c: CommentResponseDTO) => void) => void;
  /** Called once on mount with a function to imperatively refresh the list */
  onRefreshRef?: (fn: () => void) => void;
}

export function CommentList({ targetType, targetId, scriptId, showResolved = true, onAddCommentRef, onRefreshRef }: CommentListProps) {
  const locale = useLocale();
  const t = useTranslations("workspace.collab.list");
  const [comments, setComments] = useState<CommentResponseDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Keep a stable ref to avoid stale closure in WS handler
  const commentsRef = useRef(comments);
  commentsRef.current = comments;

  // ── Initial + target change fetch ────────────────────────────────────────

  useEffect(() => {
    let cancelled = false;
    setComments([]);
    setPage(1);
    setHasMore(false);
    setError(null);
    setLoading(true);

    commentService
      .listComments(targetType, targetId, { pageNum: 1, pageSize: 20 })
      .then((res) => {
        if (cancelled) return;
        // Reverse: API returns newest-first, display chronological (oldest top)
        setComments((res.records ?? []).reverse());
        setHasMore((res.current ?? 1) < (res.pages ?? 1));
        setPage(res.current ?? 1);
      })
      .catch(() => {
        if (!cancelled) setError(t("error"));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [targetType, targetId]);

  // ── Load more ─────────────────────────────────────────────────────────────

  const handleLoadMore = useCallback(async () => {
    if (loadingMore) return;
    setLoadingMore(true);
    const nextPage = page + 1;
    try {
      const res = await commentService.listComments(targetType, targetId, {
        pageNum: nextPage,
        pageSize: 20,
      });
      setComments((prev) => [...(res.records ?? []).reverse(), ...prev]);
      setHasMore((res.current ?? nextPage) < (res.pages ?? nextPage));
      setPage(res.current ?? nextPage);
    } catch (err) {
      console.error("Failed to load more comments:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setLoadingMore(false);
    }
  }, [loadingMore, page, targetType, targetId]);

  // ── Local state updaters (also used by WS handlers) ───────────────────────

  const addComment = useCallback((c: CommentResponseDTO) => {
    // Only add top-level comments to the list; replies are inside their parent
    if (c.parentId) return;
    setComments((prev) => {
      const existingIdx = prev.findIndex((existing) => existing.id === c.id);
      if (existingIdx !== -1) {
        // Merge: prefer non-null author fields to handle API vs WS race condition
        const existing = prev[existingIdx];
        const merged: CommentResponseDTO = {
          ...existing,
          ...c,
          author: {
            ...existing.author,
            ...c.author,
            nickname: c.author.nickname ?? existing.author.nickname,
            avatar: c.author.avatar ?? existing.author.avatar,
          },
          attachments: (c.attachments && c.attachments.length > 0) ? c.attachments : existing.attachments,
          mentions: (c.mentions && c.mentions.length > 0) ? c.mentions : existing.mentions,
        };
        const updated = [...prev];
        updated[existingIdx] = merged;
        return updated;
      }
      return [...prev, c];
    });
  }, []);

  // Expose addComment to parent so it can push the full API response
  useEffect(() => {
    onAddCommentRef?.(addComment);
  }, [addComment, onAddCommentRef]);

  // Expose refresh to parent
  const refresh = useCallback(() => {
    setError(null);
    setLoading(true);
    commentService
      .listComments(targetType, targetId, { pageNum: 1, pageSize: 20 })
      .then((res) => {
        setComments((res.records ?? []).reverse());
        setHasMore((res.current ?? 1) < (res.pages ?? 1));
        setPage(res.current ?? 1);
      })
      .catch(() => setError(t("error")))
      .finally(() => setLoading(false));
  }, [targetType, targetId, t]);

  useEffect(() => {
    onRefreshRef?.(refresh);
  }, [refresh, onRefreshRef]);

  const updateComment = useCallback((updated: CommentResponseDTO) => {
    setComments((prev) =>
      prev.map((c) => (c.id === updated.id ? updated : c))
    );
  }, []);

  const deleteComment = useCallback((commentId: string) => {
    setComments((prev) => prev.filter((c) => c.id !== commentId));
  }, []);

  const patchCommentStatus = useCallback(
    (commentId: string, status: "OPEN" | "RESOLVED", resolvedBy?: string) => {
      setComments((prev) =>
        prev.map((c) =>
          c.id === commentId
            ? {
                ...c,
                status,
                resolvedBy: resolvedBy ?? null,
                resolvedAt: status === "RESOLVED" ? new Date().toISOString() : null,
                updatedAt: new Date().toISOString(),
              }
            : c
        )
      );
    },
    []
  );

  const patchCommentReaction = useCallback(
    (commentId: string, emoji: string, action: "ADDED" | "REMOVED", actorUserId: string) => {
      setComments((prev) =>
        prev.map((c) => {
          if (c.id !== commentId) return c;
          const emojiKey = emoji as EmojiType;
          const existing = c.reactions.find((r) => r.emoji === emojiKey);
          let nextReactions;

          if (action === "ADDED") {
            if (existing) {
              nextReactions = c.reactions.map((r) =>
                r.emoji === emojiKey ? { ...r, count: r.count + 1 } : r
              );
            } else {
              nextReactions = [...c.reactions, { emoji: emojiKey, count: 1, reacted: false }];
            }
          } else {
            nextReactions = c.reactions
              .map((r) =>
                r.emoji === emojiKey ? { ...r, count: Math.max(0, r.count - 1) } : r
              )
              .filter((r) => r.count > 0);
          }

          return { ...c, reactions: nextReactions };
        })
      );
    },
    []
  );

  // ── WebSocket subscriptions ───────────────────────────────────────────────

  useWebSocketMessage(
    (message) => {
      switch (message.type) {
        case "COMMENT_CREATED": {
          const data = message.data as CommentCreatedData;
          if (
            data.comment.targetType === targetType &&
            data.comment.targetId === targetId
          ) {
            // Build a minimal CommentResponseDTO from the WS payload
            const c = data.comment;
            const full: CommentResponseDTO = {
              id: c.id,
              targetType: c.targetType as CommentTargetType,
              targetId: c.targetId,
              scriptId: c.scriptId,
              parentId: c.parentId,
              content: c.content,
              mentions: (c.mentions ?? []) as CommentResponseDTO["mentions"],
              status: c.status as "OPEN" | "RESOLVED",
              author: c.author,
              attachments: (c.attachments ?? []) as CommentResponseDTO["attachments"],
              replyCount: c.replyCount,
              reactions: c.reactions as CommentResponseDTO["reactions"],
              latestReplies: [],
              resolvedBy: null,
              resolvedAt: null,
              createdAt: c.createdAt,
              updatedAt: c.createdAt,
            };
            addComment(full);
          }
          break;
        }

        case "COMMENT_UPDATED": {
          const data = message.data as CommentUpdatedData;
          if (
            data.comment.targetType === targetType &&
            data.comment.targetId === targetId
          ) {
            const c = data.comment;
            const full: CommentResponseDTO = {
              id: c.id,
              targetType: c.targetType as CommentTargetType,
              targetId: c.targetId,
              scriptId: c.scriptId,
              parentId: c.parentId,
              content: c.content,
              mentions: (c.mentions ?? []) as CommentResponseDTO["mentions"],
              status: c.status as "OPEN" | "RESOLVED",
              author: c.author,
              attachments: (c.attachments ?? []) as CommentResponseDTO["attachments"],
              replyCount: c.replyCount,
              reactions: c.reactions as CommentResponseDTO["reactions"],
              latestReplies: [],
              resolvedBy: null,
              resolvedAt: null,
              createdAt: c.createdAt,
              updatedAt: c.updatedAt,
            };
            updateComment(full);
          }
          break;
        }

        case "COMMENT_DELETED": {
          const data = message.data as CommentDeletedData;
          if (data.targetType === targetType && data.targetId === targetId) {
            deleteComment(data.commentId);
          }
          break;
        }

        case "COMMENT_RESOLVED": {
          const data = message.data as CommentResolvedData;
          patchCommentStatus(data.commentId, "RESOLVED", data.resolvedBy);
          break;
        }

        case "COMMENT_REOPENED": {
          const data = message.data as CommentReopenedData;
          patchCommentStatus(data.commentId, "OPEN");
          break;
        }

        case "COMMENT_REACTION": {
          const data = message.data as CommentReactionData;
          patchCommentReaction(data.commentId, data.emoji, data.action, data.userId);
          break;
        }
      }
    },
    [targetType, targetId, addComment, updateComment, deleteComment, patchCommentStatus, patchCommentReaction]
  );

  // ── Filtered comments ────────────────────────────────────────────────────

  const visibleComments = showResolved
    ? comments
    : comments.filter((c) => c.status !== "RESOLVED");
  const hiddenCount = comments.length - visibleComments.length;

  // ── Render ────────────────────────────────────────────────────────────────

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center gap-3 py-12">
        <Spinner size="sm" />
        <p className="text-xs text-muted">{t("loading")}</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center gap-3 py-12 text-center">
        <p className="text-sm text-danger">{error}</p>
        <Button
          size="sm"
          variant="ghost"
          onPress={() => {
            setError(null);
            setLoading(true);
            commentService
              .listComments(targetType, targetId, { pageNum: 1, pageSize: 20 })
              .then((res) => {
                setComments((res.records ?? []).reverse());
                setHasMore((res.current ?? 1) < (res.pages ?? 1));
                setPage(res.current ?? 1);
              })
              .catch(() => setError(t("error")))
              .finally(() => setLoading(false));
          }}
        >
          {t("retry")}
        </Button>
      </div>
    );
  }

  if (comments.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center gap-3 py-12 text-center">
        <MessageSquare className="size-8 text-muted/30" />
        <p className="text-sm text-muted">{t("empty")}</p>
        <p className="text-xs text-muted/60">{t("emptyHint")}</p>
      </div>
    );
  }

  return (
    <div className="space-y-2.5 p-3">
      {/* Load older comments */}
      {hasMore && (
        <div className="flex justify-center pb-2">
          <Button
            size="sm"
            variant="ghost"
            className="text-xs text-muted"
            isPending={loadingMore}
            onPress={() => { handleLoadMore(); }}
          >
            {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}{t("loadMore")}</>)}
          </Button>
        </div>
      )}

      {visibleComments.map((comment) => (
        <CommentItem
          key={comment.id}
          comment={comment}
          scriptId={scriptId}
          targetType={targetType}
          targetId={targetId}
          onUpdated={updateComment}
          onDeleted={deleteComment}
        />
      ))}

      {/* Hidden resolved count */}
      {hiddenCount > 0 && (
        <p className="text-center text-[11px] text-muted/60">
          {t("hiddenResolved", { count: hiddenCount })}
        </p>
      )}
    </div>
  );
}
