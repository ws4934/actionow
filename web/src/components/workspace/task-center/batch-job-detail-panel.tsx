"use client";

import { useState, useEffect, useRef, useCallback } from "react";
import { useTranslations } from "next-intl";
import {
  Pause,
  Play,
  X as XIcon,
  RotateCcw,
  AlertTriangle,
  Package,
} from "lucide-react";
import { Button, Card, Chip, Skeleton, ScrollShadow, ProgressBar, Select, ListBox, toast } from "@heroui/react";
import { batchJobService } from "@/lib/api/services/batch-job.service";
import type {
  BatchJobResponseDTO,
  BatchJobItemDTO,
  BatchJobStatus,
  BatchItemStatus,
  BatchJobSseEvent,
} from "@/lib/api/dto/batch-job.dto";

// ============================================================================
// Props
// ============================================================================

interface BatchJobDetailPanelProps {
  jobId: string | null;
  onClose: () => void;
  onRefreshList?: () => void;
}

// ============================================================================
// Constants
// ============================================================================

const JOB_STATUS_COLOR: Record<BatchJobStatus, "default" | "accent" | "success" | "danger" | "warning"> = {
  CREATED: "default",
  RUNNING: "accent",
  PAUSED: "warning",
  COMPLETED: "success",
  FAILED: "danger",
  CANCELLED: "warning",
};

const ITEM_STATUS_COLOR: Record<BatchItemStatus, "default" | "accent" | "success" | "danger" | "warning"> = {
  PENDING: "default",
  SUBMITTED: "default",
  RUNNING: "accent",
  COMPLETED: "success",
  FAILED: "danger",
  SKIPPED: "warning",
  CANCELLED: "warning",
};

const ITEM_STATUS_OPTIONS: BatchItemStatus[] = [
  "PENDING", "SUBMITTED", "RUNNING", "COMPLETED", "FAILED", "SKIPPED", "CANCELLED",
];

const ITEMS_PAGE_SIZE = 20;

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

function DetailSkeleton() {
  return (
    <div className="space-y-4 p-4">
      <Skeleton className="h-6 w-full rounded-lg" />
      <Skeleton className="h-24 w-full rounded-lg" />
      <div className="space-y-3">
        {Array.from({ length: 6 }).map((_, i) => (
          <div key={i} className="flex justify-between gap-4">
            <Skeleton className="h-3 w-24 rounded" />
            <Skeleton className="h-3 w-20 rounded" />
          </div>
        ))}
      </div>
      <Skeleton className="h-32 w-full rounded-lg" />
    </div>
  );
}

// ============================================================================
// Component
// ============================================================================

export function BatchJobDetailPanel({
  jobId,
  onClose,
  onRefreshList,
}: BatchJobDetailPanelProps) {
  const t = useTranslations("workspace.batchJobs");

  const [detail, setDetail] = useState<BatchJobResponseDTO | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  // Items
  const [items, setItems] = useState<BatchJobItemDTO[]>([]);
  const [isLoadingItems, setIsLoadingItems] = useState(false);
  const [itemsPage, setItemsPage] = useState(1);
  const [hasMoreItems, setHasMoreItems] = useState(true);
  const [itemStatusFilter, setItemStatusFilter] = useState<BatchItemStatus | undefined>(undefined);

  // Action loading
  const [actionPending, setActionPending] = useState<string | null>(null);

  // SSE
  const sseRef = useRef<{ abort: () => void } | null>(null);

  // Load detail
  useEffect(() => {
    if (!jobId) {
      setDetail(null);
      setItems([]);
      return;
    }

    const load = async () => {
      setIsLoading(true);
      try {
        const data = await batchJobService.getDetail(jobId);
        setDetail(data);
      } catch {
        // silent
      } finally {
        setIsLoading(false);
      }
    };
    load();
  }, [jobId]);

  // Load items
  const loadItems = useCallback(
    async (pageNum: number, reset = false) => {
      if (!jobId) return;
      setIsLoadingItems(true);
      try {
        const data = await batchJobService.getItems(jobId, {
          pageNum,
          pageSize: ITEMS_PAGE_SIZE,
          status: itemStatusFilter,
        });
        const records = data.records ?? [];
        if (reset) {
          setItems(records);
        } else {
          setItems((prev) => [...prev, ...records]);
        }
        setHasMoreItems(records.length >= ITEMS_PAGE_SIZE);
      } catch {
        // silent
      } finally {
        setIsLoadingItems(false);
      }
    },
    [jobId, itemStatusFilter],
  );

  // Reset items on filter change or detail load
  useEffect(() => {
    if (detail) {
      setItemsPage(1);
      loadItems(1, true);
    }
  }, [detail, loadItems]);

  // SSE: connect when RUNNING or PAUSED
  useEffect(() => {
    if (!jobId || !detail) return;
    if (detail.status !== "RUNNING" && detail.status !== "PAUSED") return;

    const { abort } = batchJobService.streamProgress(jobId, {
      onEvent: (event: BatchJobSseEvent) => {
        setDetail((prev) => {
          if (!prev) return prev;
          const updated = { ...prev };
          if (event.progress != null) updated.progress = event.progress;
          if (event.completedItems != null) updated.completedItems = event.completedItems;
          if (event.failedItems != null) updated.failedItems = event.failedItems;
          if (event.skippedItems != null) updated.skippedItems = event.skippedItems;
          if (event.totalItems != null) updated.totalItems = event.totalItems;
          if (event.actualCredits != null) updated.actualCredits = event.actualCredits;
          if (event.status) updated.status = event.status;
          return updated;
        });
      },
      onError: (error) => {
        console.warn("[BatchJob SSE] Error:", error);
      },
      onClose: () => {
        batchJobService.getDetail(jobId).then(setDetail).catch(() => {});
      },
    });

    sseRef.current = { abort };

    return () => {
      abort();
      sseRef.current = null;
    };
  }, [jobId, detail?.status]); // eslint-disable-line react-hooks/exhaustive-deps

  // Actions
  const handleAction = async (action: "cancel" | "pause" | "resume" | "retryFailed") => {
    if (!jobId) return;
    setActionPending(action);
    try {
      await batchJobService[action](jobId);
      toast.success(t(`toast.${action}Success` as never));
      const updated = await batchJobService.getDetail(jobId);
      setDetail(updated);
      onRefreshList?.();
    } catch {
      toast.danger(t(`toast.${action}Failed` as never));
    } finally {
      setActionPending(null);
    }
  };

  const handleLoadMoreItems = () => {
    const next = itemsPage + 1;
    setItemsPage(next);
    loadItems(next);
  };

  const isActive = detail?.status === "RUNNING" || detail?.status === "CREATED";
  const isPaused = detail?.status === "PAUSED";
  const isFailed = detail?.status === "FAILED";
  const statusColor = detail ? JOB_STATUS_COLOR[detail.status] ?? "default" : "default";

  return (
    <Card className="flex h-full flex-col overflow-hidden">
      {/* Header */}
      <div className="flex shrink-0 items-center gap-3 border-b border-border px-4 py-3">
        <div className="flex size-7 shrink-0 items-center justify-center rounded-md bg-surface">
          <Package className="size-3.5 text-foreground-2" />
        </div>
        <span className="min-w-0 flex-1 truncate text-sm font-medium text-foreground">
          {detail?.title || t("detail.title")}
        </span>
        {detail && (
          <>
            <Chip size="sm" variant="soft" color={statusColor} className="shrink-0">
              {t(`status.${detail.status}` as never)}
            </Chip>
            <Chip size="sm" variant="secondary" className="shrink-0">
              {t(`batchType.${detail.batchType}` as never)}
            </Chip>
          </>
        )}
        <Button isIconOnly variant="ghost" size="sm" onPress={onClose} aria-label="Close" className="ml-1 shrink-0">
          <XIcon className="size-4" />
        </Button>
      </div>

      {/* Body */}
      <ScrollShadow className="min-h-0 flex-1 overflow-y-auto">
        {isLoading ? (
          <DetailSkeleton />
        ) : detail ? (
          <div className="space-y-4 p-4">
            {/* Progress bar */}
            <ProgressBar
              aria-label="Job progress"
              value={Math.min(detail.progress, 100)}
              size="md"
              color={detail.status === "COMPLETED" ? "success" : "accent"}
            >
              <div className="flex items-center justify-between text-xs text-foreground-2">
                <span>{detail.completedItems}/{detail.totalItems} items</span>
              </div>
              <ProgressBar.Output />
              <ProgressBar.Track>
                <ProgressBar.Fill />
              </ProgressBar.Track>
            </ProgressBar>

            {/* Summary grid */}
            <div className="rounded-lg bg-surface px-3">
              <DetailRow label={t("detail.totalItems")}>{detail.totalItems}</DetailRow>
              <DetailRow label={t("detail.completedItems")}>{detail.completedItems}</DetailRow>
              <DetailRow label={t("detail.failedItems")}>
                <span className={detail.failedItems > 0 ? "text-danger" : ""}>
                  {detail.failedItems}
                </span>
              </DetailRow>
              <DetailRow label={t("detail.skippedItems")}>{detail.skippedItems}</DetailRow>
              <DetailRow label={t("detail.estimatedCredits")}>{detail.estimatedCredits}</DetailRow>
              <DetailRow label={t("detail.actualCredits")}>{detail.actualCredits}</DetailRow>
              <DetailRow label={t("detail.concurrency")}>{detail.concurrency}</DetailRow>
              <DetailRow label={t("detail.errorStrategy")}>
                {t(`errorStrategy.${detail.errorStrategy}` as never)}
              </DetailRow>
              <DetailRow label={t("detail.source")}>
                {t(`source.${detail.source}` as never)}
              </DetailRow>
              {detail.startedAt && (
                <DetailRow label={t("detail.startedAt")}>
                  {new Date(detail.startedAt).toLocaleString()}
                </DetailRow>
              )}
              {detail.completedAt && (
                <DetailRow label={t("detail.completedAt")}>
                  {new Date(detail.completedAt).toLocaleString()}
                </DetailRow>
              )}
            </div>

            {/* Error */}
            {detail.errorMessage && (
              <div className="rounded-lg border border-danger/20 bg-danger/5 p-3">
                <div className="mb-1 flex items-center gap-1.5 text-xs font-medium text-danger">
                  <AlertTriangle className="size-3.5" />
                  {detail.errorMessage}
                </div>
              </div>
            )}

            {/* Items table */}
            <div>
              <div className="mb-2 flex items-center justify-between">
                <span className="text-xs font-medium text-foreground">{t("detail.items")}</span>
                <Select
                  aria-label={t("filter.allStatus")}
                  selectedKey={itemStatusFilter ?? "ALL"}
                  onSelectionChange={(key) =>
                    setItemStatusFilter(key === "ALL" ? undefined : (key as BatchItemStatus))
                  }
                  className="w-32"
                >
                  <Select.Trigger className="h-7 text-xs">
                    <Select.Value />
                    <Select.Indicator />
                  </Select.Trigger>
                  <Select.Popover>
                    <ListBox>
                      <ListBox.Item key="ALL" id="ALL" textValue={t("filter.allStatus")}>
                        {t("filter.allStatus")}
                        <ListBox.ItemIndicator />
                      </ListBox.Item>
                      {ITEM_STATUS_OPTIONS.map((s) => (
                        <ListBox.Item key={s} id={s} textValue={t(`itemStatus.${s}` as never)}>
                          {t(`itemStatus.${s}` as never)}
                          <ListBox.ItemIndicator />
                        </ListBox.Item>
                      ))}
                    </ListBox>
                  </Select.Popover>
                </Select>
              </div>

              <div className="max-h-60 overflow-auto rounded-lg border border-border">
                <table className="w-full text-xs">
                  <thead className="sticky top-0 bg-surface text-foreground-2">
                    <tr>
                      <th className="px-2 py-1.5 text-left font-medium">#</th>
                      <th className="px-2 py-1.5 text-left font-medium">Entity</th>
                      <th className="px-2 py-1.5 text-left font-medium">Status</th>
                      <th className="px-2 py-1.5 text-left font-medium">Provider</th>
                      <th className="px-2 py-1.5 text-right font-medium">Credits</th>
                      <th className="px-2 py-1.5 text-left font-medium">Error</th>
                    </tr>
                  </thead>
                  <tbody>
                    {items.map((item) => (
                      <tr key={item.id} className="border-t border-border">
                        <td className="px-2 py-1.5 tabular-nums">{item.sequenceNumber}</td>
                        <td className="max-w-[120px] truncate px-2 py-1.5">
                          {item.entityName || item.entityId || "-"}
                        </td>
                        <td className="px-2 py-1.5">
                          <Chip
                            size="sm"
                            variant="soft"
                            color={ITEM_STATUS_COLOR[item.status]}
                          >
                            {t(`itemStatus.${item.status}` as never)}
                          </Chip>
                        </td>
                        <td className="max-w-[100px] truncate px-2 py-1.5">
                          {item.providerName || item.providerId || "-"}
                        </td>
                        <td className="px-2 py-1.5 text-right tabular-nums">
                          {item.creditCost}
                        </td>
                        <td className="max-w-[140px] truncate px-2 py-1.5 text-danger">
                          {item.errorMessage || ""}
                        </td>
                      </tr>
                    ))}
                    {items.length === 0 && !isLoadingItems && (
                      <tr>
                        <td colSpan={6} className="px-2 py-6 text-center text-foreground-3">
                          {t("empty")}
                        </td>
                      </tr>
                    )}
                    {isLoadingItems && (
                      <tr>
                        <td colSpan={6}>
                          <div className="space-y-1 py-2">
                            {Array.from({ length: 3 }).map((_, i) => (
                              <div key={i} className="flex items-center gap-2 px-2">
                                <Skeleton className="h-3 w-6 rounded" />
                                <Skeleton className="h-3 w-20 rounded" />
                                <Skeleton className="h-4 w-14 rounded-full" />
                                <Skeleton className="h-3 w-16 rounded" />
                                <Skeleton className="h-3 w-10 rounded" />
                              </div>
                            ))}
                          </div>
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
                {hasMoreItems && items.length > 0 && !isLoadingItems && (
                  <button
                    type="button"
                    onClick={handleLoadMoreItems}
                    className="w-full py-2 text-center text-xs text-accent hover:underline"
                  >
                    Load more...
                  </button>
                )}
              </div>
            </div>
          </div>
        ) : (
          <p className="py-10 text-center text-foreground-2">{t("empty")}</p>
        )}
      </ScrollShadow>

      {/* Footer */}
      {detail && (isActive || isPaused || isFailed) && (
        <div className="flex shrink-0 items-center gap-2 border-t border-border px-4 py-2.5">
          {isActive && (
            <Button variant="secondary" size="sm" isPending={actionPending === "pause"} isDisabled={!!actionPending} onPress={() => handleAction("pause")}>
              <Pause className="size-4" />
              {t("action.pause")}
            </Button>
          )}
          {isPaused && (
            <Button variant="secondary" size="sm" isPending={actionPending === "resume"} isDisabled={!!actionPending} onPress={() => handleAction("resume")}>
              <Play className="size-4" />
              {t("action.resume")}
            </Button>
          )}
          {(isActive || isPaused) && (
            <Button variant="secondary" size="sm" isPending={actionPending === "cancel"} isDisabled={!!actionPending} onPress={() => handleAction("cancel")}>
              <XIcon className="size-4" />
              {t("action.cancel")}
            </Button>
          )}
          {isFailed && detail.failedItems > 0 && (
            <Button size="sm" isPending={actionPending === "retryFailed"} isDisabled={!!actionPending} onPress={() => handleAction("retryFailed")}>
              <RotateCcw className="size-4" />
              {t("action.retryFailed")}
            </Button>
          )}
        </div>
      )}
    </Card>
  );
}
