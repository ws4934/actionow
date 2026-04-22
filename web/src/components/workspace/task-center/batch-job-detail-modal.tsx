"use client";

import { useState, useEffect, useRef, useCallback } from "react";
import { useTranslations } from "next-intl";
import {
  Loader2,
  Pause,
  Play,
  X as XIcon,
  RotateCcw,
  AlertTriangle,
  Package,
} from "lucide-react";
import { Modal, Button, Chip, Spinner, Select, ListBox, toast } from "@heroui/react";
import { batchJobService } from "@/lib/api/services/batch-job.service";
import { getErrorFromException } from "@/lib/api/errors";
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

interface BatchJobDetailModalProps {
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
  jobId: string | null;
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
// Component
// ============================================================================

export function BatchJobDetailModal({
  isOpen,
  onOpenChange,
  jobId,
  onRefreshList,
}: BatchJobDetailModalProps) {
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
    if (!isOpen || !jobId) {
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
  }, [isOpen, jobId]);

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
    [jobId, itemStatusFilter]
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
    if (!isOpen || !jobId || !detail) return;
    if (detail.status !== "RUNNING" && detail.status !== "PAUSED") return;

    const { abort } = batchJobService.streamProgress(jobId, {
      onEvent: (event: BatchJobSseEvent) => {
        // Update progress info from SSE events
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
        // Reload detail to get final state
        batchJobService.getDetail(jobId).then(setDetail).catch(() => {});
      },
    });

    sseRef.current = { abort };

    return () => {
      abort();
      sseRef.current = null;
    };
  }, [isOpen, jobId, detail?.status]); // eslint-disable-line react-hooks/exhaustive-deps

  // Cleanup SSE on close
  useEffect(() => {
    if (!isOpen && sseRef.current) {
      sseRef.current.abort();
      sseRef.current = null;
    }
  }, [isOpen]);

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
    <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange} isDismissable>
      <Modal.Container placement="center">
        <Modal.Dialog className="sm:max-w-2xl">
          <Modal.CloseTrigger />
          <Modal.Header>
            <Modal.Heading>
              <div className="flex items-center gap-3">
                <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-surface">
                  <Package className="size-4 text-foreground-2" />
                </div>
                <div className="min-w-0 flex-1">
                  <span className="block truncate">
                    {detail?.title || t("detail.title")}
                  </span>
                </div>
                {detail && (
                  <>
                    <Chip size="sm" variant="soft" color={statusColor}>
                      {t(`status.${detail.status}` as never)}
                    </Chip>
                    <Chip size="sm" variant="secondary">
                      {t(`batchType.${detail.batchType}` as never)}
                    </Chip>
                  </>
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
              <div className="space-y-4">
                {/* Progress */}
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
                      <Select.Trigger />
                      <Select.Popover>
                        <ListBox>
                          <ListBox.Item key="ALL" id="ALL" textValue={t("filter.allStatus")}>
                            {t("filter.allStatus")}
                          </ListBox.Item>
                          {ITEM_STATUS_OPTIONS.map((s) => (
                            <ListBox.Item key={s} id={s} textValue={t(`itemStatus.${s}` as never)}>
                              {t(`itemStatus.${s}` as never)}
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
                      </tbody>
                    </table>
                    {isLoadingItems && (
                      <div className="flex items-center justify-center py-3">
                        <Spinner size="sm" />
                      </div>
                    )}
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
          </Modal.Body>

          <Modal.Footer>
            {(isActive || isPaused) && (
              <>
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
                <Button variant="secondary" size="sm" isPending={actionPending === "cancel"} isDisabled={!!actionPending} onPress={() => handleAction("cancel")}>
                  <XIcon className="size-4" />
                  {t("action.cancel")}
                </Button>
              </>
            )}
            {isFailed && detail && detail.failedItems > 0 && (
              <Button size="sm" isPending={actionPending === "retryFailed"} isDisabled={!!actionPending} onPress={() => handleAction("retryFailed")}>
                <RotateCcw className="size-4" />
                {t("action.retryFailed")}
              </Button>
            )}
            <Button variant="secondary" slot="close" size="sm">
              Close
            </Button>
          </Modal.Footer>
        </Modal.Dialog>
      </Modal.Container>
    </Modal.Backdrop>
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
