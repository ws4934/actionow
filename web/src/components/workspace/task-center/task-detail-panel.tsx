"use client";

import { useState, useEffect, useRef } from "react";
import { useTranslations } from "next-intl";
import {
  Clock,
  RotateCcw,
  X as XIcon,
  CalendarClock,
  AlertTriangle,
} from "lucide-react";
import ContentImage from "@/components/ui/content-image";
import { Button, Card, Chip, Skeleton, ScrollShadow, ProgressBar } from "@heroui/react";
import { getTypeIcon, STATUS_COLOR_MAP, formatDuration, getOutputFileUrl, getMediaCategory } from "./task-shared";
import type { MediaCategory } from "./task-shared";
import { taskService } from "@/lib/api/services/task.service";
import type { TaskDetailDTO, TaskStatus } from "@/lib/api/dto/task.dto";

// ============================================================================
// Props
// ============================================================================

interface TaskDetailPanelProps {
  taskId: string | null;
  onClose: () => void;
  onCancel?: (taskId: string) => void;
  onRetry?: (taskId: string) => void;
}

// ============================================================================
// Helpers
// ============================================================================

function extractUserParams(inputParams: Record<string, unknown> | null): Record<string, unknown> | null {
  if (!inputParams) return null;
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

function DetailRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-4 py-1.5">
      <span className="shrink-0 text-xs text-foreground-2">{label}</span>
      <span className="text-right text-xs font-medium text-foreground">{children}</span>
    </div>
  );
}

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

// ============================================================================
// Skeleton
// ============================================================================

function DetailSkeleton() {
  return (
    <div className="space-y-4 p-4">
      <Skeleton className="h-40 w-full rounded-lg" />
      <div className="space-y-3">
        {Array.from({ length: 6 }).map((_, i) => (
          <div key={i} className="flex justify-between gap-4">
            <Skeleton className="h-3 w-20 rounded" />
            <Skeleton className="h-3 w-28 rounded" />
          </div>
        ))}
      </div>
      <Skeleton className="h-24 w-full rounded-lg" />
    </div>
  );
}

// ============================================================================
// Component
// ============================================================================

export function TaskDetailPanel({
  taskId,
  onClose,
  onCancel,
  onRetry,
}: TaskDetailPanelProps) {
  const t = useTranslations("workspace.taskCenter");
  const [detail, setDetail] = useState<TaskDetailDTO | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [queuePosition, setQueuePosition] = useState<number | null>(null);
  const queuePollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (!taskId) {
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
  }, [taskId]);

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
  const TypeIcon = detail ? getTypeIcon(detail.type, detail.title) : null;
  const outputFileUrl = isCompleted && detail ? getOutputFileUrl(detail) : null;
  const mediaCategory = detail && outputFileUrl ? getMediaCategory(detail) : null;

  return (
    <Card className="flex h-full flex-col overflow-hidden">
      {/* Header */}
      <div className="flex shrink-0 items-center gap-3 border-b border-border px-4 py-3">
        {TypeIcon && (
          <div className="flex size-7 shrink-0 items-center justify-center rounded-md bg-surface">
            <TypeIcon className="size-3.5 text-foreground-2" />
          </div>
        )}
        <span className="min-w-0 flex-1 truncate text-sm font-medium text-foreground">
          {detail?.title || t("detail.title")}
        </span>
        {detail && (
          <Chip size="sm" variant="soft" color={statusColor} className="shrink-0">
            {t(`status.${detail.status.toLowerCase()}`)}
          </Chip>
        )}
        <Button isIconOnly variant="ghost" size="sm" onPress={onClose} aria-label={t("close")} className="ml-1 shrink-0">
          <XIcon className="size-4" />
        </Button>
      </div>

      {/* Body */}
      <ScrollShadow className="min-h-0 flex-1 overflow-y-auto">
        {isLoading ? (
          <DetailSkeleton />
        ) : detail ? (
          <div className="space-y-4 p-4">
            {/* Media preview (image / video / audio) */}
            {outputFileUrl && (
              <OutputMediaPreview
                url={outputFileUrl}
                media={mediaCategory}
                title={detail.title}
                label={t("detail.openImage")}
              />
            )}

            {/* Progress bar */}
            {isActive && detail.progress > 0 && (
              <ProgressBar aria-label="Task progress" value={Math.min(detail.progress, 100)} size="md" color="accent">
                <ProgressBar.Output />
                <ProgressBar.Track>
                  <ProgressBar.Fill />
                </ProgressBar.Track>
              </ProgressBar>
            )}

            {/* Queue position */}
            {detail.status === "QUEUED" && queuePosition != null && queuePosition >= 0 && (
              <div className="flex items-center gap-2 rounded-lg bg-surface p-3 text-sm">
                <Clock className="size-4 text-foreground-2" />
                <span>{t("detail.queuePosition", { position: queuePosition })}</span>
              </div>
            )}

            {/* Key info */}
            <div className="rounded-lg bg-surface px-3">
              {detail.type && (
                <DetailRow label={t("detail.type")}>{detail.type.replace(/_/g, " ")}</DetailRow>
              )}
              {detail.generationType && (
                <DetailRow label={t("detail.generationType")}>{detail.generationType}</DetailRow>
              )}
              {detail.creditCost > 0 && (
                <DetailRow label={t("detail.creditCost")}>{detail.creditCost} {t("credits")}</DetailRow>
              )}
              {duration && (
                <DetailRow label={t("detail.duration")}>{duration}</DetailRow>
              )}
              {detail.entityType && (
                <DetailRow label={t("detail.entityType")}>
                  {detail.entityType}{detail.entityName ? ` · ${detail.entityName}` : ""}
                </DetailRow>
              )}
              {detail.scriptName && (
                <DetailRow label={t("detail.script")}>{detail.scriptName}</DetailRow>
              )}
              {detail.providerName && (
                <DetailRow label={t("detail.provider")}>{detail.providerName}</DetailRow>
              )}
              {detail.source && (
                <DetailRow label={t("detail.source")}>{detail.source}</DetailRow>
              )}
              {detail.creatorName && (
                <DetailRow label={t("detail.creator")}>{detail.creatorName}</DetailRow>
              )}
              {detail.retryCount > 0 && (
                <DetailRow label={t("detail.retryInfo")}>{detail.retryCount} / {detail.maxRetries}</DetailRow>
              )}
            </div>

            {/* Input params */}
            {userParams && Object.keys(userParams).length > 0 && (
              <div>
                <p className="mb-1.5 text-xs font-medium text-foreground">{t("detail.inputParams")}</p>
                <div className="rounded-lg bg-surface px-3">
                  {Object.entries(userParams).map(([key, value]) => (
                    <DetailRow key={key} label={key}>{String(value)}</DetailRow>
                  ))}
                </div>
              </div>
            )}

            {/* Output result (non-image) */}
            {detail.outputResult && Object.keys(detail.outputResult).length > 0 && !outputFileUrl && (
              <div>
                <p className="mb-1.5 text-xs font-medium text-foreground">{t("detail.outputResult")}</p>
                <pre className="max-h-40 overflow-auto rounded-lg bg-surface p-3 text-xs text-foreground-2">
                  {JSON.stringify(detail.outputResult, null, 2)}
                </pre>
              </div>
            )}

            {/* Error */}
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

            {/* Timeline */}
            <div className="space-y-1.5">
              <div className="flex items-center gap-1.5 text-xs font-medium text-foreground">
                <CalendarClock className="size-3.5" />
                {t("detail.timeline")}
              </div>
              <div className="rounded-lg border border-border pl-4">
                <div className="relative space-y-0 border-l border-border pl-4">
                  <TimelineItem label={t("detail.createdAt")} time={detail.createdAt} />
                  {detail.startedAt && <TimelineItem label={t("detail.startedAt")} time={detail.startedAt} />}
                  {detail.completedAt && <TimelineItem label={t("detail.completedAt")} time={detail.completedAt} />}
                </div>
              </div>
            </div>
          </div>
        ) : (
          <p className="py-10 text-center text-foreground-2">{t("detail.notFound")}</p>
        )}
      </ScrollShadow>

      {/* Footer */}
      {detail && (isActive || isFailed) && (
        <div className="flex shrink-0 items-center gap-2 border-t border-border px-4 py-2.5">
          {isActive && onCancel && taskId && (
            <Button variant="secondary" size="sm" onPress={() => onCancel(taskId)}>
              <XIcon className="size-4" />
              {t("cancel")}
            </Button>
          )}
          {isFailed && onRetry && taskId && (
            <Button size="sm" onPress={() => onRetry(taskId)}>
              <RotateCcw className="size-4" />
              {t("retry")}
            </Button>
          )}
        </div>
      )}
    </Card>
  );
}
