/**
 * Collab Comment Service
 * Corresponds to backend Collab Comment API (/api/collab/comments)
 */

import { api } from "../client";
import type {
  CommentResponseDTO,
  CommentPageDTO,
  CreateCommentDTO,
  UpdateCommentDTO,
  AddReactionDTO,
  CommentListParamsDTO,
  CommentTargetType,
  EmojiType,
} from "../dto/comment.dto";

const COMMENT_BASE = "/api/collab/comments";

export const commentService = {
  /** Create a comment (may include attachments and mentions) — POST /api/collab/comments */
  createComment: (data: CreateCommentDTO) =>
    api.post<CommentResponseDTO>(COMMENT_BASE, data),

  /** Paginated top-level comment list for an entity — GET /api/collab/comments/{targetType}/{targetId} */
  listComments: (
    targetType: CommentTargetType,
    targetId: string,
    params?: CommentListParamsDTO
  ) =>
    api.get<CommentPageDTO>(`${COMMENT_BASE}/${targetType}/${targetId}`, {
      params: params as Record<string, string | number | undefined>,
    }),

  /** Single comment detail — GET /api/collab/comments/{commentId} */
  getComment: (commentId: string) =>
    api.get<CommentResponseDTO>(`${COMMENT_BASE}/${commentId}`),

  /** Update comment content (author only) — PUT /api/collab/comments/{commentId} */
  updateComment: (commentId: string, data: UpdateCommentDTO) =>
    api.put<CommentResponseDTO>(`${COMMENT_BASE}/${commentId}`, data),

  /** Soft-delete a comment — DELETE /api/collab/comments/{commentId} */
  deleteComment: (commentId: string) =>
    api.delete<void>(`${COMMENT_BASE}/${commentId}`),

  /** Paginated replies for a comment (asc order) — GET /api/collab/comments/{commentId}/replies */
  listReplies: (commentId: string, params?: CommentListParamsDTO) =>
    api.get<CommentPageDTO>(`${COMMENT_BASE}/${commentId}/replies`, {
      params: params as Record<string, string | number | undefined>,
    }),

  /** Mark comment as resolved — POST /api/collab/comments/{commentId}/resolve */
  resolveComment: (commentId: string) =>
    api.post<void>(`${COMMENT_BASE}/${commentId}/resolve`),

  /** Reopen a resolved comment — POST /api/collab/comments/{commentId}/reopen */
  reopenComment: (commentId: string) =>
    api.post<void>(`${COMMENT_BASE}/${commentId}/reopen`),

  /** Add emoji reaction (idempotent per user/emoji) — POST /api/collab/comments/{commentId}/reactions */
  addReaction: (commentId: string, data: AddReactionDTO) =>
    api.post<void>(`${COMMENT_BASE}/${commentId}/reactions`, data),

  /** Remove emoji reaction — DELETE /api/collab/comments/{commentId}/reactions/{emoji} */
  removeReaction: (commentId: string, emoji: EmojiType) =>
    api.delete<void>(`${COMMENT_BASE}/${commentId}/reactions/${emoji}`),

  /** Get comment count for an entity (Redis-cached, 1hr TTL) — GET /api/collab/comments/count/{targetType}/{targetId} */
  getCommentCount: (targetType: CommentTargetType, targetId: string) =>
    api.get<number>(`${COMMENT_BASE}/count/${targetType}/${targetId}`),
};
