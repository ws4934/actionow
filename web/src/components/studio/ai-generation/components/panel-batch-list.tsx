"use client";

import { useState, useEffect, useCallback } from "react";
import { useTranslations } from "next-intl";
import { Chip, Spinner, toast } from "@heroui/react";
import { Package, Pause, X as XIcon } from "lucide-react";
import { batchJobService } from "@/lib/api/services/batch-job.service";
import type { BatchJobResponseDTO, BatchJobStatus } from "@/lib/api/dto/batch-job.dto";

// ============================================================================
// Constants
// ============================================================================

const STATUS_COLOR: Record<BatchJobStatus, "default" | "accent" | "success" | "danger" | "warning"> = {
  CREATED: "default",
  RUNNING: "accent",
  PAUSED: "warning",
  COMPLETED: "success",
  FAILED: "danger",
  CANCELLED: "warning",
};

const ACTIVE_STATUSES = new Set<BatchJobStatus>(["CREATED", "RUNNING", "PAUSED"]);

// ============================================================================
// Props
// ============================================================================

interface PanelBatchListProps {
  scriptId: string;
}

// ============================================================================
// Component
// ============================================================================

export function PanelBatchList({ scriptId }: PanelBatchListProps) {
  const t = useTranslations("workspace.aiGeneration.tabs");
  const tB = useTranslations("workspace.batchJobs");

  const [jobs, setJobs] = useState<BatchJobResponseDTO[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    batchJobService
      .list({ pageSize: 20, scriptId })
      .then((data) => {
        if (!cancelled) {
          // Only show active batch jobs
          const active = (data.records ?? []).filter((j) => ACTIVE_STATUSES.has(j.status));
          setJobs(active);
        }
      })
      .catch(() => {})
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [scriptId]);

  const handlePause = useCallback(async (jobId: string) => {
    try {
      await batchJobService.pause(jobId);
      toast.success(tB("toast.pauseSuccess"));
      setJobs((prev) => prev.map((j) => (j.id === jobId ? { ...j, status: "PAUSED" as const } : j)));
    } catch {
      toast.danger(tB("toast.pauseFailed"));
    }
  }, [tB]);

  const handleCancel = useCallback(async (jobId: string) => {
    try {
      await batchJobService.cancel(jobId);
      toast.success(tB("toast.cancelSuccess"));
      setJobs((prev) => prev.filter((j) => j.id !== jobId));
    } catch {
      toast.danger(tB("toast.cancelFailed"));
    }
  }, [tB]);

  // Don't render anything if loading or no active jobs
  if (isLoading) return null;
  if (jobs.length === 0) return null;

  return (
    <div className="border-b border-border p-2">
      <div className="mb-1 px-1 text-[10px] font-medium uppercase tracking-wider text-foreground-3">
        {t("batchRunning")}
      </div>
      <div className="flex flex-col gap-1">
        {jobs.map((job) => (
          <div
            key={job.id}
            className="group flex items-center gap-2 rounded-md px-2 py-1.5 hover:bg-surface-2"
          >
            <Package className="size-3.5 shrink-0 text-foreground-3" />
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-1.5">
                <span className="truncate text-xs font-medium text-foreground">
                  {job.title || job.id.slice(0, 8)}
                </span>
                <Chip size="sm" variant="soft" color={STATUS_COLOR[job.status]}>
                  {tB(`status.${job.status}` as never)}
                </Chip>
              </div>
              <div className="mt-0.5 flex items-center gap-2">
                <div className="h-1 flex-1 overflow-hidden rounded-full bg-surface-2">
                  <div
                    className="h-full rounded-full bg-accent transition-all"
                    style={{ width: `${Math.min(job.progress, 100)}%` }}
                  />
                </div>
                <span className="shrink-0 text-[10px] tabular-nums text-foreground-3">
                  {job.completedItems}/{job.totalItems}
                </span>
              </div>
            </div>

            {/* Action buttons (visible on hover) */}
            <div className="flex shrink-0 items-center gap-0.5 opacity-0 transition-opacity group-hover:opacity-100">
              {job.status === "RUNNING" && (
                <button
                  type="button"
                  onClick={(e) => { e.stopPropagation(); handlePause(job.id); }}
                  className="rounded p-0.5 text-foreground-3 hover:bg-surface hover:text-foreground"
                  title={tB("action.pause")}
                >
                  <Pause className="size-3" />
                </button>
              )}
              <button
                type="button"
                onClick={(e) => { e.stopPropagation(); handleCancel(job.id); }}
                className="rounded p-0.5 text-foreground-3 hover:bg-surface hover:text-danger"
                title={tB("action.cancel")}
              >
                <XIcon className="size-3" />
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
