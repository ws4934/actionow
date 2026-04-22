"use client";

import { useState } from "react";
import {
  Image,
  Video,
  AudioLines,
  MessageSquare,
  FileDown,
  FileCode,
  Clapperboard,
  Type,
  Copy,
  Check,
  type LucideIcon,
} from "lucide-react";
import { Button, Tooltip } from "@heroui/react";
import type { TaskListItemDTO, TaskStatus } from "@/lib/api/dto/task.dto";

// ============================================================================
// Constants
// ============================================================================

export const TYPE_ICON_MAP: Record<string, LucideIcon> = {
  IMAGE_GENERATION: Image,
  IMAGE: Image,
  VIDEO_GENERATION: Video,
  VIDEO: Video,
  AUDIO_GENERATION: AudioLines,
  AUDIO: AudioLines,
  TEXT_GENERATION: Type,
  TEXT: Type,
  TTS_GENERATION: MessageSquare,
  TTS: MessageSquare,
  BATCH_EXPORT: FileDown,
  FILE_PROCESSING: FileCode,
};

export const STATUS_COLOR_MAP: Record<TaskStatus, "default" | "accent" | "success" | "danger" | "warning"> = {
  PENDING: "default",
  QUEUED: "default",
  RUNNING: "accent",
  COMPLETED: "success",
  FAILED: "danger",
  CANCELLED: "warning",
};

// ============================================================================
// Helpers
// ============================================================================

export function getTypeIcon(type: string | null, title: string): LucideIcon {
  if (type && TYPE_ICON_MAP[type]) return TYPE_ICON_MAP[type];
  const t = title.toUpperCase();
  if (t.includes("IMAGE")) return Image;
  if (t.includes("VIDEO")) return Video;
  if (t.includes("AUDIO")) return AudioLines;
  if (t.includes("TTS")) return MessageSquare;
  if (t.includes("EXPORT")) return FileDown;
  return Clapperboard;
}

/** Format a duration between two date strings as "Xm Ys" or "Xh Ym". */
export function formatDuration(start: string | null, end: string | null): string | null {
  if (!start || !end) return null;
  const ms = new Date(end).getTime() - new Date(start).getTime();
  if (ms < 0) return null;
  const totalSec = Math.round(ms / 1000);
  if (totalSec < 60) return `${totalSec}s`;
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  if (min < 60) return `${min}m ${sec}s`;
  const hr = Math.floor(min / 60);
  return `${hr}h ${min % 60}m`;
}

/** Extract a short, human-readable error from a potentially long error string. */
export function shortenError(msg: string, maxLen = 120): string {
  const msgMatch = msg.match(/"message"\s*:\s*"([^"]+)"/);
  if (msgMatch) return msgMatch[1];
  if (msg.length > maxLen) return msg.slice(0, maxLen) + "…";
  return msg;
}

/** Detect media category from task fields. */
export type MediaCategory = "image" | "video" | "audio" | null;

export function getMediaCategory(task: {
  type?: string | null;
  generationType?: string | null;
  outputResult?: Record<string, unknown> | null;
}): MediaCategory {
  // Check mimeType first (most reliable)
  const mime = (task.outputResult?.mimeType as string) || "";
  if (mime.startsWith("video/") || mime.endsWith(".mp4")) return "video";
  if (mime.startsWith("audio/") || mime.endsWith(".mp3") || mime.endsWith(".wav")) return "audio";
  if (mime.startsWith("image/")) return "image";

  // Fall back to generationType / type
  const gt = (task.generationType || task.type || "").toUpperCase();
  if (gt.includes("VIDEO")) return "video";
  if (gt.includes("AUDIO") || gt.includes("TTS")) return "audio";
  if (gt.includes("IMAGE")) return "image";

  // Check URL extension
  const url = getOutputFileUrl(task);
  if (url) {
    const path = url.split("?")[0].toLowerCase();
    if (path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".mov")) return "video";
    if (path.endsWith(".mp3") || path.endsWith(".wav") || path.endsWith(".ogg") || path.endsWith(".aac")) return "audio";
  }

  return "image"; // default for tasks that have a fileUrl
}

/** Extract output file URL from a task (image, video, or audio). */
export function getOutputFileUrl(task: {
  status?: string;
  outputResult?: Record<string, unknown> | null;
}): string | null {
  const r = task.outputResult;
  if (!r) return null;
  const url =
    (r.fileUrl as string) ||
    ((r.outputs as Record<string, unknown>)?.fileUrl as string);
  return url || null;
}

/** @deprecated Use getOutputFileUrl instead */
export function getOutputImageUrl(task: TaskListItemDTO): string | null {
  if (task.status !== "COMPLETED") return null;
  return getOutputFileUrl(task);
}

// ============================================================================
// Error Tooltip (shared between task-card and task-list)
// ============================================================================

export function ErrorTooltip({ msg }: { msg: string }) {
  const [copied, setCopied] = useState(false);
  const short = shortenError(msg);

  const handleCopy = async (e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await navigator.clipboard.writeText(msg);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      /* ignore */
    }
  };

  return (
    <Tooltip delay={300}>
      <Tooltip.Trigger className="cursor-help text-left">
        <p className="truncate text-xs text-danger">{short}</p>
      </Tooltip.Trigger>
      <Tooltip.Content className="max-w-[320px] p-2">
        <p className="max-h-24 overflow-y-auto whitespace-pre-wrap break-all text-xs leading-relaxed text-danger/90">
          {msg}
        </p>
        <Button
          variant="ghost"
          size="sm"
          className="mt-1.5 h-auto min-w-0 gap-1 px-0 py-0 text-[11px] text-foreground-3 hover:text-foreground"
          onPress={() => {
            navigator.clipboard.writeText(msg).then(() => {
              setCopied(true);
              setTimeout(() => setCopied(false), 2000);
            });
          }}
        >
          {copied ? (
            <Check className="size-3 text-success" />
          ) : (
            <Copy className="size-3" />
          )}
          {copied ? "已复制" : "复制"}
        </Button>
      </Tooltip.Content>
    </Tooltip>
  );
}

// ============================================================================
// WS Data Parser
// ============================================================================

/**
 * Parse a WebSocket task data payload into a TaskListItemDTO.
 * Centralises the field extraction logic to keep it in sync with the DTO.
 */
export function parseTaskFromWsData(
  taskId: string,
  status: string,
  data: Record<string, unknown>,
): TaskListItemDTO {
  return {
    id: (data.id as string) || taskId,
    workspaceId: (data.workspaceId as string) || "",
    creatorId: (data.creatorId as string) || "",
    type: (data.type as string) ?? null,
    title: (data.title as string) || "",
    status: (data.status as string) || status,
    businessType: (data.businessType as string) ?? null,
    businessId: (data.businessId as string) ?? null,
    scriptId: (data.scriptId as string) ?? null,
    entityId: (data.entityId as string) ?? null,
    entityType: (data.entityType as string) ?? null,
    entityName: (data.entityName as string) ?? null,
    providerId: (data.providerId as string) ?? null,
    generationType: (data.generationType as string) ?? null,
    thumbnailUrl: (data.thumbnailUrl as string) ?? null,
    creditCost: (data.creditCost as number) ?? 0,
    source: (data.source as string) ?? null,
    priority: (data.priority as number) ?? 5,
    progress: (data.progress as number) ?? 0,
    retryCount: (data.retryCount as number) ?? 0,
    maxRetries: (data.maxRetries as number) ?? 3,
    timeoutSeconds: (data.timeoutSeconds as number) ?? 300,
    inputParams: (data.inputParams as Record<string, unknown>) ?? null,
    outputResult: (data.outputResult as Record<string, unknown>) ?? null,
    errorMessage: (data.errorMessage as string) ?? null,
    errorDetail: (data.errorDetail as Record<string, unknown>) ?? null,
    startedAt: (data.startedAt as string) ?? null,
    completedAt: (data.completedAt as string) ?? null,
    createdAt: (data.createdAt as string) || new Date().toISOString(),
    updatedAt: (data.updatedAt as string) || new Date().toISOString(),
  } as TaskListItemDTO;
}
