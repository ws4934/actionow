/**
 * Collab Comment DTOs
 * Corresponds to backend Collab Comment API (/api/collab/comments)
 */

import type { PaginatedResponseDTO } from "./workspace.dto";

// ============================================================================
// Enums
// ============================================================================

export type CommentTargetType =
  | "SCRIPT"
  | "EPISODE"
  | "STORYBOARD"
  | "CHARACTER"
  | "SCENE"
  | "PROP";

export type CommentStatus = "OPEN" | "RESOLVED";

export type MentionType = "USER" | "CHARACTER" | "STORYBOARD" | "SCENE" | "PROP";

export type AttachmentAssetType = "IMAGE" | "VIDEO" | "AUDIO" | "DOCUMENT";

export type EmojiType = "thumbsup" | "heart" | "fire" | "eyes" | "tada" | "laugh";

// ============================================================================
// Sub-models
// ============================================================================

/** Mention position within comment content — used to reconstruct rich text */
export interface CommentMentionDTO {
  type: MentionType;
  id: string;
  name: string;
  /** Character offset in content */
  offset: number;
  /** Length of mention text in content */
  length: number;
}

/** Attachment metadata (asset must be uploaded via project service first) */
export interface CommentAttachmentDTO {
  assetId: string;
  assetType?: AttachmentAssetType;
  fileName?: string;
  fileUrl?: string;
  thumbnailUrl?: string;
  fileSize?: number;
  mimeType?: string;
  metaInfo?: Record<string, unknown>;
}

/** Emoji reaction summary for a comment */
export interface ReactionSummaryDTO {
  emoji: EmojiType;
  count: number;
  /** Whether the current user has reacted with this emoji */
  reacted: boolean;
}

/** Comment author info */
export interface CommentAuthorDTO {
  id: string;
  nickname: string | null;
  avatar: string | null;
}

// ============================================================================
// Response DTOs
// ============================================================================

export interface CommentResponseDTO {
  id: string;
  targetType: CommentTargetType;
  targetId: string;
  /** Script ID — determines WebSocket broadcast scope */
  scriptId: string | null;
  /** Parent comment ID; null for top-level comments */
  parentId: string | null;
  content: string;
  mentions: CommentMentionDTO[];
  status: CommentStatus;
  author: CommentAuthorDTO;
  attachments: CommentAttachmentDTO[];
  replyCount: number;
  reactions: ReactionSummaryDTO[];
  /** Preview of the 2 most recent replies */
  latestReplies: CommentResponseDTO[];
  resolvedBy: string | null;
  resolvedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export type CommentPageDTO = PaginatedResponseDTO<CommentResponseDTO>;

// ============================================================================
// Request DTOs
// ============================================================================

export interface CreateCommentDTO {
  targetType: CommentTargetType;
  targetId: string;
  /** Required for WebSocket broadcast scope */
  scriptId?: string;
  /** Set when replying to another comment */
  parentId?: string;
  content: string;
  mentions?: CommentMentionDTO[];
  attachments?: CommentAttachmentDTO[];
}

export interface UpdateCommentDTO {
  content: string;
  mentions?: CommentMentionDTO[];
}

export interface AddReactionDTO {
  emoji: EmojiType;
}

export interface CommentListParamsDTO {
  pageNum?: number;
  pageSize?: number;
}
