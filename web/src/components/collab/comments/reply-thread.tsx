"use client";

/**
 * ReplyThread
 *
 * Renders the reply preview for a top-level comment:
 * - Initially shows `latestReplies` (up to 2 items) from the parent CommentResponse.
 * - When the user clicks "View all N replies", fetches the full list via
 *   GET /api/collab/comments/{commentId}/replies and renders all replies.
 * - Supports pagination ("Load more replies") if the server returns multiple pages.
 * - Re-subscribes to COMMENT_CREATED via useWebSocketMessage so newly submitted
 *   replies appear immediately without re-fetching.
 */

import { useState, useCallback, useRef } from "react";
import { Button, Spinner } from "@heroui/react";
import { useTranslations } from "next-intl";
import { commentService } from "@/lib/api/services/comment.service";
import { useWebSocketMessage } from "@/lib/websocket";
import { CommentItem } from "./comment-item";
import type { CommentResponseDTO, CommentTargetType } from "@/lib/api/dto/comment.dto";
import type { CommentCreatedData } from "@/lib/websocket/types";

// ── Component ────────────────────────────────────────────────────────────────

interface ReplyThreadProps {
  /** Parent comment ID */
  commentId: string;
  scriptId: string;
  targetType?: CommentTargetType;
  targetId?: string;
  /** Total reply count (from parent CommentResponse.replyCount) */
  replyCount: number;
  /** Initial preview replies from parent CommentResponse.latestReplies */
  latestReplies: CommentResponseDTO[];
  /** Called when the thread's local reply count changes (e.g. new reply via WS) */
  onReplyCountChange?: (delta: number) => void;
}

export function ReplyThread({
  commentId,
  scriptId,
  targetType,
  targetId,
  replyCount,
  latestReplies,
  onReplyCountChange,
}: ReplyThreadProps) {
  const t = useTranslations("workspace.collab.thread");

  // When expanded=false we show latestReplies (2-item preview).
  // When expanded=true we show the full `replies` list.
  const [expanded, setExpanded] = useState(false);
  const [replies, setReplies] = useState<CommentResponseDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Track local count so WS-added replies reflect correctly
  const [localCount, setLocalCount] = useState(replyCount);

  // Stable ref for dedup inside WS handler
  const repliesRef = useRef(replies);
  repliesRef.current = replies;

  // ── Fetch ────────────────────────────────────────────────────────────────

  const fetchReplies = useCallback(async (pageNum = 1) => {
    try {
      const res = await commentService.listReplies(commentId, {
        pageNum,
        pageSize: 20,
      });
      const records = res.records ?? [];
      if (pageNum === 1) {
        setReplies(records);
      } else {
        setReplies((prev) => [...prev, ...records]);
      }
      setHasMore((res.current ?? pageNum) < (res.pages ?? pageNum));
      setPage(res.current ?? pageNum);
      setError(null);
    } catch {
      setError(t("error"));
    }
  }, [commentId, t]);

  // ── Expand / collapse ────────────────────────────────────────────────────

  const handleExpand = useCallback(async () => {
    setExpanded(true);
    setLoading(true);
    await fetchReplies(1);
    setLoading(false);
  }, [fetchReplies]);

  const handleCollapse = useCallback(() => {
    setExpanded(false);
    // Keep replies list in state so re-expand is instant
  }, []);

  const handleLoadMore = useCallback(async () => {
    if (loadingMore) return;
    setLoadingMore(true);
    await fetchReplies(page + 1);
    setLoadingMore(false);
  }, [fetchReplies, loadingMore, page]);

  const handleRetry = useCallback(async () => {
    setError(null);
    setLoading(true);
    await fetchReplies(1);
    setLoading(false);
  }, [fetchReplies]);

  // ── WebSocket: listen for new replies ────────────────────────────────────

  useWebSocketMessage(
    (message) => {
      if (message.type !== "COMMENT_CREATED") return;
      const data = message.data as CommentCreatedData;
      const c = data.comment;
      // Only care about replies to THIS comment
      if (c.parentId !== commentId) return;

      // Increment local count
      setLocalCount((n) => n + 1);
      onReplyCountChange?.(1);

      // If thread is expanded, prepend the new reply
      if (expanded) {
        const full: CommentResponseDTO = {
          id: c.id,
          targetType: c.targetType as CommentTargetType,
          targetId: c.targetId,
          scriptId: c.scriptId,
          parentId: c.parentId,
          content: c.content,
          mentions: [],
          status: c.status as "OPEN" | "RESOLVED",
          author: c.author,
          attachments: [],
          replyCount: 0,
          reactions: c.reactions as CommentResponseDTO["reactions"],
          latestReplies: [],
          resolvedBy: null,
          resolvedAt: null,
          createdAt: c.createdAt,
          updatedAt: c.createdAt,
        };
        setReplies((prev) => {
          if (prev.some((r) => r.id === full.id)) return prev;
          return [...prev, full];
        });
      }
    },
    [commentId, expanded, onReplyCountChange]
  );

  // ── Render ────────────────────────────────────────────────────────────────

  if (localCount === 0) return null;

  // Collapsed: show preview list + "View all N replies" button
  if (!expanded) {
    return (
      <div className="mt-2">
        {/* Latest replies preview */}
        {latestReplies.length > 0 && (
          <div className="space-y-2 border-l-2 border-muted/20 pl-3">
            {latestReplies.map((reply) => (
              <CommentItem
                key={reply.id}
                comment={reply}
                scriptId={scriptId}
                isReply
              />
            ))}
          </div>
        )}

        {/* Expand button — shown when there are more replies than the preview */}
        {localCount > latestReplies.length && (
          <button
            className="mt-1.5 text-[11px] text-muted transition-colors hover:text-accent"
            onClick={handleExpand}
          >
            {t("viewAll", { count: localCount })}
          </button>
        )}
      </div>
    );
  }

  // Expanded: full reply list
  return (
    <div className="mt-2">
      {/* Collapse button */}
      <button
        className="mb-2 text-[11px] text-muted transition-colors hover:text-accent"
        onClick={handleCollapse}
      >
        {t("collapse")}
      </button>

      {loading ? (
        <div className="flex items-center justify-center py-4">
          <Spinner size="sm" />
          <span className="ml-2 text-xs text-muted">{t("loading")}</span>
        </div>
      ) : error ? (
        <div className="rounded-lg bg-danger/10 px-3 py-2 text-center">
          <p className="text-xs text-danger">{error}</p>
          <Button
            size="sm"
            variant="ghost"
            className="mt-1 h-6 text-[11px]"
            onPress={handleRetry}
          >
            {t("retry")}
          </Button>
        </div>
      ) : (
        <div className="space-y-2 border-l-2 border-muted/20 pl-3">
          {replies.length === 0 ? (
            <p className="text-xs text-muted">{t("empty")}</p>
          ) : (
            replies.map((reply) => (
              <CommentItem
                key={reply.id}
                comment={reply}
                scriptId={scriptId}
                targetType={targetType}
                targetId={targetId}
                isReply
              />
            ))
          )}

          {/* Load more */}
          {hasMore && (
            <div className="pt-1">
              <Button
                size="sm"
                variant="ghost"
                className="h-6 text-[11px] text-muted"
                isPending={loadingMore}
                onPress={() => { handleLoadMore(); }}
              >
                {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}{t("loadMore")}</>)}
              </Button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
