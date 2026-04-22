"use client";

import { useState, useEffect, useRef } from "react";
import { useTranslations } from "next-intl";
import {
  Loader2,
  Clock,
  RotateCcw,
  X as XIcon,
  Image,
  Video,
  AudioLines,
  MessageSquare,
  FileDown,
  FileCode,
  Clapperboard,
  CalendarClock,
  AlertTriangle,
  Type,
  type LucideIcon,
} from "lucide-react";
import ContentImage from "@/components/ui/content-image";
import { Modal, Button, Chip } from "@heroui/react";
import { getOutputFileUrl, getMediaCategory } from "./task-shared";
import type { MediaCategory } from "./task-shared";
import { taskService } from "@/lib/api/services/task.service";
import type { TaskDetailDTO, TaskStatus } from "@/lib/api/dto/task.dto";

interface TaskDetailModalProps {
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
  taskId: string | null;
  onCancel?: (taskId: string) => void;
  onRetry?: (taskId: string) => void;
}

// ============================================================================
// Constants
// ============================================================================

const STATUS_COLOR_MAP: Record<TaskStatus, "default" | "accent" | "success" | "danger" | "warning"> = {
  PENDING: "default",
  QUEUED: "default",
  RUNNING: "accent",
  COMPLETED: "success",
  FAILED: "danger",
  CANCELLED: "warning",
};

const TYPE_ICON_MAP: Record<string, LucideIcon> = {
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

// ============================================================================
// Helpers
// ============================================================================

function getTypeIcon(type: string | null): LucideIcon {
  if (type && TYPE_ICON_MAP[type]) return TYPE_ICON_MAP[type];
  return Clapperboard;
}

function formatDuration(start: string | null, end: string | null): string | null {
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

/** Extract user-facing input params from the nested structure. */
function extractUserParams(inputParams: Record<string, unknown> | null): Record<string, unknown> | null {
  if (!inputParams) return null;
  // Backend nests actual params under "params" key
  const params = inputParams.params;
  if (params && typeof params === "object") return params as Record<string, unknown>;
  return inputParams;
}

function OutputMediaPreview({ url, media, title, label }: {
  url: string;
  media: MediaCategory;
  title: string;
  label: string;
}) {
  if (media === "video") {
    return (
      <div className="overflow-hidden rounded-lg border border-border">
        <video src={url} controls className="w-full" />
        <div className="flex items-center justify-between border-t border-border px-3 py-1.5">
          <span className="text-xs text-foreground-2">video</span>
          <a href={url} target="_blank" rel="noopener noreferrer" className="text-xs text-accent hover:underline">{label}</a>
        </div>
      </div>
    );
  }
  if (media === "audio") {
    return (
      <div className="overflow-hidden rounded-lg border border-border p-3">
        <audio src={url} controls className="w-full" />
        <div className="mt-2 flex items-center justify-between">
          <span className="text-xs text-foreground-2">audio</span>
          <a href={url} target="_blank" rel="noopener noreferrer" className="text-xs text-accent hover:underline">{label}</a>
        </div>
      </div>
    );
  }
  return (
    <div className="overflow-hidden rounded-lg border border-border">
      <ContentImage src={url} alt={title} width={800} height={450} className="w-full h-auto object-cover" />
      <div className="flex items-center justify-between border-t border-border px-3 py-1.5">
        <span className="text-xs text-foreground-2">image</span>
        <a href={url} target="_blank" rel="noopener noreferrer" className="text-xs text-accent hover:underline">{label}</a>
      </div>
    </div>
  );
}

// ============================================================================
// Sub-components
// ============================================================================

function DetailRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-4 py-1.5">
      <span className="shrink-0 text-xs text-foreground-2">{label}</span>
      <span className="text-right text-xs font-medium text-foreground">{children}</span>
    </div>
  );
}

// ============================================================================
// Component
// ============================================================================

export function TaskDetailModal({
  isOpen,
  onOpenChange,
  taskId,
  onCancel,
  onRetry,
}: TaskDetailModalProps) {
  const t = useTranslations("workspace.taskCenter");
  const [detail, setDetail] = useState<TaskDetailDTO | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [queuePosition, setQueuePosition] = useState<number | null>(null);
  const queuePollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Load detail when modal opens
  useEffect(() => {
    if (!isOpen || !taskId) {
      setDetail(null);
      setQueuePosition(null);
      return;
    }

    const load = async () => {
      setIsLoading(true);
      try {
        const data = await taskService.getTaskDetail(taskId);
        setDetail(data);
      } catch {
        // silent
      } finally {
        setIsLoading(false);
      }
    };
    load();
  }, [isOpen, taskId]);

  // Poll queue position for QUEUED tasks
  useEffect(() => {
    if (queuePollRef.current) {
      clearInterval(queuePollRef.current);
      queuePollRef.current = null;
    }

    if (!detail || detail.status !== "QUEUED" || !taskId) return;

    const pollQueue = async () => {
      try {
        const pos = await taskService.getQueuePosition(taskId);
        setQueuePosition(pos.position);
      } catch {
        // silent
      }
    };

    pollQueue();
    queuePollRef.current = setInterval(pollQueue, 5000);

    return () => {
      if (queuePollRef.current) {
        clearInterval(queuePollRef.current);
        queuePollRef.current = null;
      }
    };
  }, [detail?.status, taskId]); // eslint-disable-line react-hooks/exhaustive-deps

  const isActive = detail?.status === "PENDING" || detail?.status === "QUEUED" || detail?.status === "RUNNING";
  const isCompleted = detail?.status === "COMPLETED";
  const isFailed = detail?.status === "FAILED";
  const statusColor = detail ? STATUS_COLOR_MAP[detail.status] || "default" : "default";
  const duration = detail ? formatDuration(detail.startedAt, detail.completedAt ?? detail.updatedAt) : null;
  const userParams = detail ? extractUserParams(detail.inputParams) : null;
  const TypeIcon = detail ? getTypeIcon(detail.type) : Clapperboard;
  const outputFileUrl = isCompleted && detail ? getOutputFileUrl(detail) : null;
  const mediaCategory = detail && outputFileUrl ? getMediaCategory(detail) : null;

  return (
    <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange} isDismissable>
      <Modal.Container placement="center">
        <Modal.Dialog className="sm:max-w-3xl">
          <Modal.Header>
            <Modal.Heading>
              <div className="flex items-center gap-3">
                <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-surface">
                  <TypeIcon className="size-4 text-foreground-2" />
                </div>
                <div className="min-w-0 flex-1">
                  <span className="block truncate">{detail?.title || t("detail.title")}</span>
                </div>
                {detail && (
                  <Chip size="sm" variant="soft" color={statusColor} className="shrink-0">
                    {t(`status.${detail.status.toLowerCase()}`)}
                  </Chip>
                )}
              </div>
            </Modal.Heading>
          </Modal.Header>
          <Modal.Body>
            {isLoading ? (
              <div className="flex items-center justify-center py-10">
                <Loader2 className="size-6 animate-spin text-accent" />
              </div>
            ) : detail ? (
              <div className="grid grid-cols-[1fr_220px] gap-5">

                {/* ── Left: content area ── */}
                <div className="min-w-0 space-y-4">
                  {/* Media preview (image / video / audio) */}
                  {outputFileUrl && (
                    <OutputMediaPreview
                      url={outputFileUrl}
                      media={mediaCategory}
                      title={detail.title}
                      label={t("detail.openImage")}
                    />
                  )}

                  {/* Progress bar (active tasks) */}
                  {isActive && detail.progress > 0 && (
                    <div>
                      <div className="mb-1.5 flex items-center justify-between text-xs text-foreground-2">
                        <span>{t("detail.progress")}</span>
                        <span className="tabular-nums">{detail.progress}%</span>
                      </div>
                      <div className="h-2 w-full overflow-hidden rounded-full bg-surface-2">
                        <div
                          className="h-full rounded-full bg-accent transition-all duration-500"
                          style={{ width: `${Math.min(detail.progress, 100)}%` }}
                        />
                      </div>
                    </div>
                  )}

                  {/* Queue position */}
                  {detail.status === "QUEUED" && queuePosition != null && queuePosition >= 0 && (
                    <div className="flex items-center gap-2 rounded-lg bg-surface p-3 text-sm">
                      <Clock className="size-4 text-foreground-2" />
                      <span>{t("detail.queuePosition", { position: queuePosition })}</span>
                    </div>
                  )}

                  {/* Input params */}
                  {userParams && Object.keys(userParams).length > 0 && (
                    <div>
                      <p className="mb-1.5 text-xs font-medium text-foreground">
                        {t("detail.inputParams")}
                      </p>
                      <div className="rounded-lg bg-surface px-3">
                        {Object.entries(userParams).map(([key, value]) => (
                          <DetailRow key={key} label={key}>
                            {String(value)}
                          </DetailRow>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Output result (non-image tasks) */}
                  {detail.outputResult && Object.keys(detail.outputResult).length > 0 && !outputFileUrl && (
                    <div>
                      <p className="mb-1.5 text-xs font-medium text-foreground">{t("detail.outputResult")}</p>
                      <pre className="max-h-40 overflow-auto rounded-lg bg-surface p-3 text-xs text-foreground-2">
                        {JSON.stringify(detail.outputResult, null, 2)}
                      </pre>
                    </div>
                  )}

                  {/* Error message */}
                  {isFailed && detail.errorMessage && (
                    <div className="rounded-lg border border-danger/20 bg-danger/5 p-3">
                      <div className="mb-1 flex items-center gap-1.5 text-xs font-medium text-danger">
                        <AlertTriangle className="size-3.5" />
                        {t("detail.errorTitle")}
                      </div>
                      <pre className="max-h-28 overflow-auto whitespace-pre-wrap break-all text-xs text-danger/80">
                        {detail.errorMessage}
                      </pre>
                      {detail.errorDetail && Object.keys(detail.errorDetail).length > 0 && (
                        <pre className="mt-2 max-h-20 overflow-auto whitespace-pre-wrap break-all rounded bg-danger/5 p-2 text-[11px] text-danger/60">
                          {JSON.stringify(detail.errorDetail, null, 2)}
                        </pre>
                      )}
                    </div>
                  )}
                </div>

                {/* ── Right: metadata sidebar ── */}
                <div className="space-y-4">
                  {/* Key info grid */}
                  <div className="rounded-lg bg-surface px-3">
                    {detail.type && (
                      <DetailRow label={t("detail.type")}>
                        {detail.type.replace(/_/g, " ")}
                      </DetailRow>
                    )}
                    {detail.generationType && (
                      <DetailRow label={t("detail.generationType")}>
                        {detail.generationType}
                      </DetailRow>
                    )}
                    {detail.creditCost > 0 && (
                      <DetailRow label={t("detail.creditCost")}>
                        {detail.creditCost} {t("credits")}
                      </DetailRow>
                    )}
                    {duration && (
                      <DetailRow label={t("detail.duration")}>
                        {duration}
                      </DetailRow>
                    )}
                    {detail.entityType && (
                      <DetailRow label={t("detail.entityType")}>
                        {detail.entityType}
                        {detail.entityName ? ` · ${detail.entityName}` : ""}
                      </DetailRow>
                    )}
                    {detail.scriptName && (
                      <DetailRow label={t("detail.script")}>
                        {detail.scriptName}
                      </DetailRow>
                    )}
                    {detail.providerName && (
                      <DetailRow label={t("detail.provider")}>
                        {detail.providerName}
                      </DetailRow>
                    )}
                    {detail.source && (
                      <DetailRow label={t("detail.source")}>
                        {detail.source}
                      </DetailRow>
                    )}
                    {detail.creatorName && (
                      <DetailRow label={t("detail.creator")}>
                        {detail.creatorName}
                      </DetailRow>
                    )}
                    {detail.retryCount > 0 && (
                      <DetailRow label={t("detail.retryInfo")}>
                        {detail.retryCount} / {detail.maxRetries}
                      </DetailRow>
                    )}
                    {detail.priority > 0 && (
                      <DetailRow label={t("detail.priority")}>
                        {detail.priority}
                      </DetailRow>
                    )}
                  </div>

                  {/* Timeline */}
                  <div className="space-y-1.5">
                    <div className="flex items-center gap-1.5 text-xs font-medium text-foreground">
                      <CalendarClock className="size-3.5" />
                      {t("detail.timeline")}
                    </div>
                    <div className="rounded-lg border border-border pl-4">
                      <div className="relative space-y-0 border-l border-border pl-4">
                        <TimelineItem label={t("detail.createdAt")} time={detail.createdAt} />
                        {detail.startedAt && (
                          <TimelineItem label={t("detail.startedAt")} time={detail.startedAt} />
                        )}
                        {detail.completedAt && (
                          <TimelineItem label={t("detail.completedAt")} time={detail.completedAt} />
                        )}
                      </div>
                    </div>
                  </div>
                </div>

              </div>
            ) : (
              <p className="py-10 text-center text-foreground-2">{t("detail.notFound")}</p>
            )}
          </Modal.Body>
          <Modal.Footer>
            {isActive && onCancel && taskId && (
              <Button
                variant="secondary"
                size="sm"
                onPress={() => {
                  onCancel(taskId);
                  onOpenChange(false);
                }}
              >
                <XIcon className="size-4" />
                {t("cancel")}
              </Button>
            )}
            {isFailed && onRetry && taskId && (
              <Button
                size="sm"
                onPress={() => {
                  onRetry(taskId);
                  onOpenChange(false);
                }}
              >
                <RotateCcw className="size-4" />
                {t("retry")}
              </Button>
            )}
            <Button variant="secondary" slot="close" size="sm">
              {t("close")}
            </Button>
          </Modal.Footer>
        </Modal.Dialog>
      </Modal.Container>
    </Modal.Backdrop>
  );
}

// ============================================================================
// Timeline item
// ============================================================================

function TimelineItem({ label, time }: { label: string; time: string }) {
  return (
    <div className="relative py-2">
      <div className="absolute -left-[calc(1rem+0.25rem)] top-1/2 size-2 -translate-y-1/2 rounded-full bg-accent" />
      <div className="flex items-center justify-between gap-4">
        <span className="text-xs text-foreground-2">{label}</span>
        <span className="text-xs tabular-nums text-foreground">{new Date(time).toLocaleString()}</span>
      </div>
    </div>
  );
}
