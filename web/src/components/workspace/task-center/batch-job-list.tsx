"use client";

import { useState, useEffect, useCallback, useMemo, useRef } from "react";
import { useTranslations } from "next-intl";
import { Inbox, ChevronUp, Package } from "lucide-react";
import { Table, Chip, Skeleton, Pagination, ProgressBar, Select, ListBox, cn } from "@heroui/react";
import type { SortDescriptor } from "@heroui/react";
import { batchJobService } from "@/lib/api/services/batch-job.service";
import { useDebouncedEntityChanges } from "@/lib/websocket";
import type {
  BatchJobResponseDTO,
  BatchJobStatus,
  BatchType,
} from "@/lib/api/dto/batch-job.dto";

// ============================================================================
// Constants
// ============================================================================

const PAGE_SIZE_OPTIONS = [10, 20, 50] as const;
type PageSize = (typeof PAGE_SIZE_OPTIONS)[number];

const STATUS_COLOR_MAP: Record<BatchJobStatus, "default" | "accent" | "success" | "danger" | "warning"> = {
  CREATED: "default",
  RUNNING: "accent",
  PAUSED: "warning",
  COMPLETED: "success",
  FAILED: "danger",
  CANCELLED: "warning",
};

const BATCH_TYPE_OPTIONS: BatchType[] = ["SIMPLE", "PIPELINE", "VARIATION", "SCOPE", "AB_TEST"];

// ============================================================================
// Props
// ============================================================================

interface BatchJobListProps {
  statusFilter?: BatchJobStatus;
  onViewDetail: (jobId: string) => void;
}

// ============================================================================
// Helpers
// ============================================================================

function SortableColumnHeader({
  children,
  sortDirection,
}: {
  children: React.ReactNode;
  sortDirection?: "ascending" | "descending";
}) {
  return (
    <span className="flex items-center justify-between">
      {children}
      {!!sortDirection && (
        <ChevronUp
          className={cn(
            "size-3 transform transition-transform duration-100 ease-out",
            sortDirection === "descending" ? "rotate-180" : "",
          )}
        />
      )}
    </span>
  );
}

// ============================================================================
// Component
// ============================================================================

export function BatchJobList({ statusFilter, onViewDetail }: BatchJobListProps) {
  const t = useTranslations("workspace.batchJobs");

  const [jobs, setJobs] = useState<BatchJobResponseDTO[]>([]);
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalItems, setTotalItems] = useState(0);
  const [pageSize, setPageSize] = useState<PageSize>(20);
  const [isLoading, setIsLoading] = useState(true);
  const [typeFilter, setTypeFilter] = useState<BatchType | undefined>(undefined);
  const [sortDescriptor, setSortDescriptor] = useState<SortDescriptor>({
    column: "createdAt",
    direction: "descending",
  });
  const abortRef = useRef<AbortController | null>(null);

  const loadJobs = useCallback(
    async (page: number) => {
      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;

      setIsLoading(true);
      setJobs([]);
      try {
        const data = await batchJobService.list({
          pageNum: page,
          pageSize,
          status: statusFilter,
          batchType: typeFilter,
        });
        if (!controller.signal.aborted) {
          setJobs(data.records ?? []);
          setCurrentPage(data.current);
          setTotalPages(data.pages);
          setTotalItems(data.total);
        }
      } catch {
        if (!controller.signal.aborted) {
          setTotalPages(1);
          setTotalItems(0);
        }
      } finally {
        if (!controller.signal.aborted) {
          setIsLoading(false);
        }
      }
    },
    [statusFilter, typeFilter, pageSize],
  );

  useEffect(() => {
    setCurrentPage(1);
    loadJobs(1);
  }, [loadJobs]);

  // Auto-refresh on WS entity changes
  const reloadCurrentPage = useCallback(() => {
    loadJobs(currentPage);
  }, [loadJobs, currentPage]);

  useDebouncedEntityChanges("batch_job", reloadCurrentPage, { delay: 500 }, [reloadCurrentPage]);

  const handlePageChange = (page: number) => {
    loadJobs(page);
  };

  const sortedJobs = useMemo(() => {
    if (!sortDescriptor.column) return jobs;
    return [...jobs].sort((a, b) => {
      const col = sortDescriptor.column as keyof BatchJobResponseDTO;
      const aVal = a[col];
      const bVal = b[col];
      let cmp = 0;
      if (aVal == null && bVal == null) cmp = 0;
      else if (aVal == null) cmp = -1;
      else if (bVal == null) cmp = 1;
      else if (typeof aVal === "number" && typeof bVal === "number") cmp = aVal - bVal;
      else cmp = String(aVal).localeCompare(String(bVal));
      return sortDescriptor.direction === "descending" ? -cmp : cmp;
    });
  }, [jobs, sortDescriptor]);

  const pageNumbers = useMemo((): (number | "ellipsis")[] => {
    if (totalPages <= 7) return Array.from({ length: totalPages }, (_, i) => i + 1);
    const pages: (number | "ellipsis")[] = [1];
    if (currentPage > 3) pages.push("ellipsis");
    const start = Math.max(2, currentPage - 1);
    const end = Math.min(totalPages - 1, currentPage + 1);
    for (let i = start; i <= end; i++) pages.push(i);
    if (currentPage < totalPages - 2) pages.push("ellipsis");
    pages.push(totalPages);
    return pages;
  }, [currentPage, totalPages]);

  const startItem = totalItems === 0 ? 0 : (currentPage - 1) * pageSize + 1;
  const endItem = Math.min(currentPage * pageSize, totalItems);

  return (
    <Table className="h-full [grid-template-rows:1fr_auto]">
      <Table.ScrollContainer className="min-h-0 overflow-y-auto">
        <Table.Content
          aria-label={t("tab")}
          className="min-w-[900px]"
          sortDescriptor={sortDescriptor}
          onSortChange={setSortDescriptor}
        >
          <Table.Header className="sticky top-0 z-10">
            <Table.Column isRowHeader allowsSorting id="title" className="max-w-[280px]">
              {({ sortDirection }) => (
                <SortableColumnHeader sortDirection={sortDirection}>
                  {t("column.title")}
                </SortableColumnHeader>
              )}
            </Table.Column>
          <Table.Column id="batchType" className="w-36">
              {/* Type filter inline */}
              <Select
                aria-label={t("filter.allType")}
                selectedKey={typeFilter ?? "ALL"}
                onSelectionChange={(key) => setTypeFilter(key === "ALL" ? undefined : (key as BatchType))}
                variant="secondary"
                className="w-full"
              >
                <Select.Trigger className="h-6 text-xs">
                  <Select.Value />
                  <Select.Indicator />
                </Select.Trigger>
                <Select.Popover>
                  <ListBox>
                    <ListBox.Item key="ALL" id="ALL" textValue={t("filter.allType")}>
                      {t("filter.allType")}
                      <ListBox.ItemIndicator />
                    </ListBox.Item>
                    {BATCH_TYPE_OPTIONS.map((bt) => (
                      <ListBox.Item key={bt} id={bt} textValue={t(`batchType.${bt}` as never)}>
                        {t(`batchType.${bt}` as never)}
                        <ListBox.ItemIndicator />
                      </ListBox.Item>
                    ))}
                  </ListBox>
                </Select.Popover>
              </Select>
            </Table.Column>
            <Table.Column allowsSorting id="status" className="w-28">
              {({ sortDirection }) => (
                <SortableColumnHeader sortDirection={sortDirection}>
                  {t("column.status")}
                </SortableColumnHeader>
              )}
            </Table.Column>
            <Table.Column id="progress" className="w-36">
              {t("column.progress")}
            </Table.Column>
            <Table.Column id="items" className="w-32">
              {t("column.items")}
            </Table.Column>
            <Table.Column allowsSorting id="actualCredits" className="w-24">
              {({ sortDirection }) => (
                <SortableColumnHeader sortDirection={sortDirection}>
                  {t("column.credits")}
                </SortableColumnHeader>
              )}
            </Table.Column>
            <Table.Column allowsSorting id="createdAt" className="w-40">
              {({ sortDirection }) => (
                <SortableColumnHeader sortDirection={sortDirection}>
                  {t("column.createdAt")}
                </SortableColumnHeader>
              )}
            </Table.Column>
          </Table.Header>
          <Table.Body
            renderEmptyState={() =>
              isLoading ? (
                <div className="w-full space-y-2 py-4">
                  {Array.from({ length: 6 }).map((_, i) => (
                    <div key={i} className="flex items-center gap-4 px-4 py-2">
                      <Skeleton className="size-9 rounded-lg" />
                      <div className="flex-1 space-y-2">
                        <Skeleton className="h-4 w-3/5 rounded" />
                        <Skeleton className="h-3 w-2/5 rounded" />
                      </div>
                      <Skeleton className="h-5 w-16 rounded-full" />
                      <Skeleton className="h-3 w-20 rounded" />
                      <Skeleton className="h-3 w-16 rounded" />
                    </div>
                  ))}
                </div>
              ) : (
                <div className="flex w-full flex-col items-center justify-center py-20 text-center">
                  <div className="flex size-16 items-center justify-center rounded-full bg-surface">
                    <Inbox className="size-8 text-foreground-3" />
                  </div>
                  <p className="mt-4 text-sm font-medium text-foreground">{t("empty")}</p>
                </div>
              )
            }
          >
            <Table.Collection items={sortedJobs}>
              {(job) => (
                <BatchJobRow job={job} t={t} onViewDetail={onViewDetail} />
              )}
            </Table.Collection>
          </Table.Body>
        </Table.Content>
      </Table.ScrollContainer>

      <Table.Footer>
        <Pagination size="sm">
          <Pagination.Summary>
            <div className="flex items-center gap-2.5">
              {totalItems > 0 && (
                <span className="tabular-nums text-xs text-foreground-2">
                  {startItem}–{endItem} / {totalItems}
                </span>
              )}
              <Select
                variant="secondary"
                value={String(pageSize)}
                onChange={(value) => setPageSize(Number(value) as PageSize)}
              >
                <Select.Trigger className="h-7 w-24 text-xs">
                  <Select.Value />
                  <Select.Indicator />
                </Select.Trigger>
                <Select.Popover>
                  <ListBox>
                    {PAGE_SIZE_OPTIONS.map((s) => (
                      <ListBox.Item key={s} id={String(s)} textValue={`${s} 条/页`}>
                        {s} 条/页
                        <ListBox.ItemIndicator />
                      </ListBox.Item>
                    ))}
                  </ListBox>
                </Select.Popover>
              </Select>
            </div>
          </Pagination.Summary>
          {totalPages > 1 && (
            <Pagination.Content>
              <Pagination.Item>
                <Pagination.Previous
                  isDisabled={currentPage === 1}
                  onPress={() => handlePageChange(currentPage - 1)}
                >
                  <Pagination.PreviousIcon />
                </Pagination.Previous>
              </Pagination.Item>
              {pageNumbers.map((p, i) =>
                p === "ellipsis" ? (
                  <Pagination.Item key={`e-${i}`}>
                    <Pagination.Ellipsis />
                  </Pagination.Item>
                ) : (
                  <Pagination.Item key={p}>
                    <Pagination.Link
                      isActive={p === currentPage}
                      onPress={() => handlePageChange(p as number)}
                    >
                      {p}
                    </Pagination.Link>
                  </Pagination.Item>
                ),
              )}
              <Pagination.Item>
                <Pagination.Next
                  isDisabled={currentPage >= totalPages}
                  onPress={() => handlePageChange(currentPage + 1)}
                >
                  <Pagination.NextIcon />
                </Pagination.Next>
              </Pagination.Item>
            </Pagination.Content>
          )}
        </Pagination>
      </Table.Footer>
    </Table>
  );
}

// ============================================================================
// Table row
// ============================================================================

function BatchJobRow({
  job,
  t,
  onViewDetail,
}: {
  job: BatchJobResponseDTO;
  t: ReturnType<typeof useTranslations>;
  onViewDetail: (jobId: string) => void;
}) {
  const statusColor = STATUS_COLOR_MAP[job.status] || "default";
  const isCompleted = job.status === "COMPLETED";

  return (
    <Table.Row
      id={job.id}
      className="cursor-pointer"
      onAction={() => onViewDetail(job.id)}
    >
      {/* Title */}
      <Table.Cell className="max-w-[280px]">
        <div className="flex min-w-0 items-center gap-2.5">
          <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-surface">
            <Package className="size-4 text-foreground-2" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium text-foreground">
              {job.title || job.id.slice(0, 8)}
            </p>
            {job.errorMessage && (
              <p className="mt-0.5 truncate text-xs text-danger">{job.errorMessage}</p>
            )}
          </div>
        </div>
      </Table.Cell>

      {/* Batch Type */}
      <Table.Cell>
        <Chip size="sm" variant="soft">
          {t(`batchType.${job.batchType}` as never)}
        </Chip>
      </Table.Cell>

      {/* Status */}
      <Table.Cell>
        <Chip size="sm" variant="soft" color={statusColor}>
          {t(`status.${job.status}` as never)}
        </Chip>
      </Table.Cell>

      {/* Progress */}
      <Table.Cell>
        <ProgressBar
          aria-label="Job progress"
          value={Math.min(job.progress, 100)}
          size="sm"
          color={isCompleted ? "success" : "accent"}
        >
          <ProgressBar.Output className="text-[10px] tabular-nums text-foreground-3" />
          <ProgressBar.Track className="min-w-12">
            <ProgressBar.Fill />
          </ProgressBar.Track>
        </ProgressBar>
      </Table.Cell>

      {/* Items */}
      <Table.Cell>
        <span className="text-xs text-foreground-2">
          {job.completedItems}/{job.totalItems}
          {job.failedItems > 0 && (
            <span className="text-danger"> ({job.failedItems})</span>
          )}
        </span>
      </Table.Cell>

      {/* Credits */}
      <Table.Cell>
        <span className="text-xs tabular-nums text-foreground-2">
          {job.actualCredits}/{job.estimatedCredits}
        </span>
      </Table.Cell>

      {/* Created At */}
      <Table.Cell>
        <span className="text-xs tabular-nums text-foreground-2">
          {new Date(job.createdAt).toLocaleString()}
        </span>
      </Table.Cell>
    </Table.Row>
  );
}
