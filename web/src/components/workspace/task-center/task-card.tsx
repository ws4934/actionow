"use client";

import { useTranslations } from "next-intl";
import { X, RotateCcw, Clock } from "lucide-react";
import { Button, Chip, ProgressBar } from "@heroui/react";
import { getTypeIcon, STATUS_COLOR_MAP, formatDuration, ErrorTooltip } from "./task-shared";
import type { TaskListItemDTO } from "@/lib/api/dto/task.dto";

// ============================================================================
// Props
// ============================================================================

interface TaskCardProps {
  task: TaskListItemDTO;
  variant: "compact" | "full";
  onCancel?: (taskId: string) => void;
  onRetry?: (taskId: string) => void;
  onViewDetail?: (taskId: string) => void;
}

// ============================================================================
// Component
// ============================================================================

export function TaskCard({ task, variant, onCancel, onRetry, onViewDetail }: TaskCardProps) {
  const t = useTranslations("workspace.taskCenter");
  const Icon = getTypeIcon(task.type, task.title);
  const statusColor = STATUS_COLOR_MAP[task.status] || "default";
  const isActive = task.status === "PENDING" || task.status === "QUEUED" || task.status === "RUNNING";
  const isFailed = task.status === "FAILED";
  const duration = formatDuration(task.startedAt, task.completedAt ?? task.updatedAt);

  if (variant === "compact") {
    return (
      <div
        className="group flex items-center gap-2 rounded-lg px-2 py-1.5 transition-colors hover:bg-surface"
        role="button"
        tabIndex={0}
        onClick={() => onViewDetail?.(task.id)}
        onKeyDown={(e) => e.key === "Enter" && onViewDetail?.(task.id)}
      >
        <Icon className="size-4 shrink-0 text-foreground-2" />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm text-foreground">{task.title}</p>
          {task.providerName && (
            <p className="truncate text-[11px] text-foreground-3">{task.providerName}</p>
          )}
          {isActive && task.progress > 0 && (
            <ProgressBar aria-label="Task progress" value={Math.min(task.progress, 100)} size="sm" color="accent" className="mt-0.5">
              <ProgressBar.Track>
                <ProgressBar.Fill />
              </ProgressBar.Track>
            </ProgressBar>
          )}
        </div>
        <div className="flex shrink-0 flex-col items-end gap-0.5">
          <Chip size="sm" variant="soft" color={statusColor} className="text-[10px]">
            {t(`status.${task.status.toLowerCase()}`)}
          </Chip>
          {task.source && (
            <span className="text-[10px] text-foreground-3">{task.source}</span>
          )}
        </div>
        {isActive && onCancel && (
          <Button
            isIconOnly
            variant="ghost"
            size="sm"
            className="size-6 min-w-0 opacity-0 group-hover:opacity-100"
            onPress={() => onCancel(task.id)}
            aria-label={t("cancel")}
          >
            <X className="size-3" />
          </Button>
        )}
      </div>
    );
  }

  // ========== Full variant ==========
  return (
    <div
      className="group rounded-lg border border-border bg-background p-4 transition-colors hover:border-accent/40 hover:bg-surface"
      role="button"
      tabIndex={0}
      onClick={() => onViewDetail?.(task.id)}
      onKeyDown={(e) => e.key === "Enter" && onViewDetail?.(task.id)}
    >
      {/* Row 1: Icon + Title + Status chip + Actions */}
      <div className="flex items-center gap-3">
        <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-surface">
          <Icon className="size-4 text-foreground-2" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium text-foreground">{task.title}</p>
        </div>
        <Chip size="sm" variant="soft" color={statusColor}>
          {t(`status.${task.status.toLowerCase()}`)}
        </Chip>
        {/* Action buttons — visible on hover */}
        <div className="flex items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100">
          {isFailed && onRetry && (
            <Button
              isIconOnly
              variant="ghost"
              size="sm"
              className="size-7 min-w-0"
              onPress={(e) => {
                e.continuePropagation?.();
                onRetry(task.id);
              }}
              aria-label={t("retry")}
            >
              <RotateCcw className="size-3.5" />
            </Button>
          )}
          {isActive && onCancel && (
            <Button
              isIconOnly
              variant="ghost"
              size="sm"
              className="size-7 min-w-0"
              onPress={(e) => {
                e.continuePropagation?.();
                onCancel(task.id);
              }}
              aria-label={t("cancel")}
            >
              <X className="size-3.5" />
            </Button>
          )}
        </div>
      </div>

      {/* Row 2: Progress bar (only for active tasks with progress) */}
      {isActive && task.progress > 0 && (
        <ProgressBar aria-label="Task progress" value={Math.min(task.progress, 100)} size="sm" color="accent" className="mt-2.5">
          <ProgressBar.Output />
          <ProgressBar.Track>
            <ProgressBar.Fill />
          </ProgressBar.Track>
        </ProgressBar>
      )}

      {/* Row 3: Meta info */}
      <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-foreground-2">
        {task.providerName && (
          <span className="rounded bg-surface px-1.5 py-0.5 text-[11px] font-medium text-foreground-2">
            {task.providerName}
          </span>
        )}
        {task.source && (
          <Chip size="sm" variant="secondary" className="h-4 min-h-0 rounded px-1.5 text-[10px] font-normal">
            {task.source}
          </Chip>
        )}
        {task.entityType && (
          <Chip size="sm" variant="secondary" className="h-4 min-h-0 rounded px-1.5 text-[10px] font-normal">
            {task.entityType}
          </Chip>
        )}
        {task.scriptName && (
          <span className="text-[11px] text-foreground-3">{task.scriptName}</span>
        )}
        <span>{new Date(task.createdAt).toLocaleString()}</span>
        {duration && (
          <span className="flex items-center gap-1">
            <Clock className="size-3" />
            {duration}
          </span>
        )}
        {task.retryCount > 0 && (
          <span>
            {t("retryCount", { count: task.retryCount, max: task.maxRetries })}
          </span>
        )}
      </div>

      {/* Row 4: Error message — truncated, hover to see full + copy */}
      {isFailed && task.errorMessage && (
        <div className="mt-2 rounded-md bg-danger/5 px-2.5 py-1.5">
          <ErrorTooltip msg={task.errorMessage} />
        </div>
      )}
    </div>
  );
}
