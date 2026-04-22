"use client";

import { useState, useEffect, useCallback, useMemo, useRef } from "react";
import { useTranslations } from "next-intl";
import { Inbox, X, RotateCcw, Clock, Zap, ChevronUp } from "lucide-react";
import { Table, Chip, Button, Skeleton, Pagination, ProgressBar, Select, ListBox, cn } from "@heroui/react";
import type { SortDescriptor } from "@heroui/react";
import { taskService } from "@/lib/api/services/task.service";
import { useDebouncedEntityChanges } from "@/lib/websocket";
import { getTypeIcon, STATUS_COLOR_MAP, formatDuration, getOutputFileUrl, getMediaCategory, ErrorTooltip } from "./task-shared";
import type { TaskListItemDTO, TaskStatus } from "@/lib/api/dto/task.dto";
import Image from "@/components/ui/content-image";

// ============================================================================
// Constants
// ============================================================================

const PAGE_SIZE_OPTIONS = [10, 20, 50] as const;
type PageSize = (typeof PAGE_SIZE_OPTIONS)[number];

// ============================================================================
// Props
// ============================================================================

interface TaskListProps {
  statusFilter?: TaskStatus;
  typeFilter?: string;
  searchKeyword?: string;
  onViewDetail?: (taskId: string) => void;
  onCancel?: (taskId: string) => void;
  onRetry?: (taskId: string) => void;
}

// ============================================================================
// Sortable column header
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

export function TaskList({
  statusFilter,
  typeFilter,
  searchKeyword,
  onViewDetail,
  onCancel,
  onRetry,
}: TaskListProps) {
  const t = useTranslations("workspace.taskCenter");
  const [tasks, setTasks] = useState<TaskListItemDTO[]>([]);
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalItems, setTotalItems] = useState(0);
  const [pageSize, setPageSize] = useState<PageSize>(20);
  const [isLoading, setIsLoading] = useState(true);
  const [sortDescriptor, setSortDescriptor] = useState<SortDescriptor>({
    column: "createdAt",
    direction: "descending",
  });

  // Debounced keyword for API calls
  const [debouncedKeyword, setDebouncedKeyword] = useState(searchKeyword ?? "");
  const searchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    if (searchTimerRef.current) clearTimeout(searchTimerRef.current);
    searchTimerRef.current = setTimeout(() => {
      setDebouncedKeyword(searchKeyword ?? "");
    }, 400);
    return () => {
      if (searchTimerRef.current) clearTimeout(searchTimerRef.current);
    };
  }, [searchKeyword]);

  const loadTasks = useCallback(
    async (page: number) => {
      // Cancel any in-flight request
      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;

      setIsLoading(true);
      setTasks([]);
      try {
        const data = await taskService.getMyTasks(
          {
            pageNum: page,
            pageSize,
            status: statusFilter,
            type: typeFilter,
            keyword: debouncedKeyword || undefined,
          },
          { signal: controller.signal },
        );
        if (!controller.signal.aborted) {
          setTasks(data.records);
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
    [statusFilter, typeFilter, pageSize, debouncedKeyword],
  );

  // Reset to page 1 on filter/pageSize/keyword change
  useEffect(() => {
    setCurrentPage(1);
    loadTasks(1);
  }, [loadTasks]);

  // Auto-refresh when WS broadcasts task entity changes
  const reloadCurrentPage = useCallback(() => {
    loadTasks(currentPage);
  }, [loadTasks, currentPage]);

  useDebouncedEntityChanges("task", reloadCurrentPage, { delay: 500 }, [reloadCurrentPage]);

  const handlePageChange = (page: number) => {
    loadTasks(page);
  };

  // Client-side sort on loaded data (search is handled server-side via keyword param)
  const sortedTasks = useMemo(() => {
    if (!sortDescriptor.column) return tasks;
    return [...tasks].sort((a, b) => {
      const col = sortDescriptor.column as keyof TaskListItemDTO;
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
  }, [tasks, sortDescriptor]);

  // Page numbers with ellipsis
  const pageNumbers = useMemo((): (number | "ellipsis")[] => {
    if (totalPages <= 7) {
      return Array.from({ length: totalPages }, (_, i) => i + 1);
    }
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
          aria-label={t("pageTitle")}
          className="min-w-[1100px]"
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
            <Table.Column id="entityType" className="w-28">
              {t("column.entityType")}
            </Table.Column>
            <Table.Column id="type" className="w-36">
              {t("column.type")}
            </Table.Column>
            <Table.Column id="model" className="w-36">
              {t("column.model")}
            </Table.Column>
            <Table.Column id="source" className="w-24">
              {t("column.source")}
            </Table.Column>
            <Table.Column allowsSorting id="status" className="w-44">
              {({ sortDirection }) => (
                <SortableColumnHeader sortDirection={sortDirection}>
                  {t("column.status")}
                </SortableColumnHeader>
              )}
            </Table.Column>
            <Table.Column allowsSorting id="createdAt" className="w-36">
              {({ sortDirection }) => (
                <SortableColumnHeader sortDirection={sortDirection}>
                  {t("column.createdAt")}
                </SortableColumnHeader>
              )}
            </Table.Column>
            <Table.Column id="duration" className="w-28">
              {t("column.duration")}
            </Table.Column>
            <Table.Column className="w-20 text-end">
              {t("column.actions")}
            </Table.Column>
          </Table.Header>
          <Table.Body
            renderEmptyState={() =>
              isLoading ? (
                <div className="w-full space-y-2 py-4">
                  {Array.from({ length: 6 }).map((_, i) => (
                    <div key={i} className="flex items-center gap-4 px-4 py-2">
                      <Skeleton className="size-9 rounded-md" />
                      <div className="flex-1 space-y-2">
                        <Skeleton className="h-4 w-3/5 rounded" />
                        <Skeleton className="h-3 w-2/5 rounded" />
                      </div>
                      <Skeleton className="h-5 w-16 rounded-full" />
                      <Skeleton className="h-3 w-24 rounded" />
                      <Skeleton className="h-3 w-16 rounded" />
                    </div>
                  ))}
                </div>
              ) : (
                <div className="flex w-full flex-col items-center justify-center py-20 text-center">
                  <div className="flex size-16 items-center justify-center rounded-full bg-surface">
                    <Inbox className="size-8 text-foreground-3" />
                  </div>
                  <p className="mt-4 text-sm font-medium text-foreground">{t("emptyState")}</p>
                </div>
              )
            }
          >
            <Table.Collection items={sortedTasks}>
              {(task) => (
                <TaskRow
                  task={task}
                  t={t}
                  onViewDetail={onViewDetail}
                  onCancel={onCancel}
                  onRetry={onRetry}
                />
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
// Table row component
// ============================================================================

function TaskRow({
  task,
  t,
  onViewDetail,
  onCancel,
  onRetry,
}: {
  task: TaskListItemDTO;
  t: ReturnType<typeof useTranslations>;
  onViewDetail?: (taskId: string) => void;
  onCancel?: (taskId: string) => void;
  onRetry?: (taskId: string) => void;
}) {
  const Icon = getTypeIcon(task.type, task.title);
  const statusColor = STATUS_COLOR_MAP[task.status] || "default";
  const isActive = task.status === "PENDING" || task.status === "QUEUED" || task.status === "RUNNING";
  const isCompleted = task.status === "COMPLETED";
  const isFailed = task.status === "FAILED";
  const duration = formatDuration(task.startedAt, task.completedAt ?? task.updatedAt);
  const fileUrl = task.thumbnailUrl || (task.status === "COMPLETED" ? getOutputFileUrl(task) : null);
  const media = fileUrl ? getMediaCategory(task) : null;

  return (
    <Table.Row
      id={task.id}
      className="cursor-pointer"
      onAction={() => onViewDetail?.(task.id)}
    >
      {/* Title: thumbnail/icon + title + entityType + entityName/scriptName + error */}
      <Table.Cell className="max-w-[280px]">
        <div className="flex min-w-0 items-center gap-2.5">
          {fileUrl && media === "video" ? (
            <div className="relative size-9 shrink-0 overflow-hidden rounded-md border border-border/60">
              <video src={fileUrl} muted preload="metadata" className="size-full object-cover" />
            </div>
          ) : fileUrl && media !== "audio" ? (
            <div className="relative size-9 shrink-0 overflow-hidden rounded-md border border-border/60">
              <Image src={fileUrl} alt="" fill className="object-cover" sizes="(min-width: 768px) 20vw, 40vw" />
            </div>
          ) : (
            <div className="flex size-9 shrink-0 items-center justify-center rounded-md bg-surface">
              <Icon className="size-4 text-foreground-2" />
            </div>
          )}
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium text-foreground">{task.title}</p>
            <div className="mt-0.5 flex flex-wrap items-center gap-x-1.5 gap-y-0.5">
              {task.entityName && (
                <span className="truncate text-[11px] text-foreground-3">{task.entityName}</span>
              )}
              {task.scriptName && (
                <span className="truncate text-[11px] text-foreground-3">{task.scriptName}</span>
              )}
            </div>
            {isFailed && task.errorMessage && (
              <ErrorTooltip msg={task.errorMessage} />
            )}
          </div>
        </div>
      </Table.Cell>

      {/* Entity Type */}
      <Table.Cell>
        {task.entityType ? (
          <span className="text-xs text-foreground-2">{task.entityType}</span>
        ) : (
          <span className="text-xs text-foreground-3">—</span>
        )}
      </Table.Cell>

      {/* Task Type + Generation Type */}
      <Table.Cell>
        <div className="flex flex-col gap-0.5">
          {task.type ? (
            <span className="text-xs text-foreground-2">{task.type.replace(/_/g, " ")}</span>
          ) : (
            <span className="text-xs text-foreground-3">—</span>
          )}
          {task.generationType && (
            <span className="text-[10px] text-foreground-3">{task.generationType}</span>
          )}
        </div>
      </Table.Cell>

      {/* Model */}
      <Table.Cell>
        {task.providerName ? (
          <span className="truncate text-xs text-foreground-2">{task.providerName}</span>
        ) : (
          <span className="text-xs text-foreground-3">—</span>
        )}
      </Table.Cell>

      {/* Source */}
      <Table.Cell>
        {task.source ? (
          <Chip size="sm" variant="secondary" className="h-4 min-h-0 rounded px-1.5 text-[10px] font-normal">
            {task.source}
          </Chip>
        ) : (
          <span className="text-xs text-foreground-3">—</span>
        )}
      </Table.Cell>

      {/* Status + inline progress bar */}
      <Table.Cell>
        <div className="flex flex-col gap-1.5">
          <Chip size="sm" variant="soft" color={statusColor}>
            {t(`status.${task.status.toLowerCase()}`)}
          </Chip>
          {isActive && task.progress > 0 ? (
            <ProgressBar aria-label="Task progress" value={Math.min(task.progress, 100)} size="sm" color="accent">
              <ProgressBar.Output className="text-[10px] tabular-nums text-foreground-3" />
              <ProgressBar.Track className="min-w-12">
                <ProgressBar.Fill />
              </ProgressBar.Track>
            </ProgressBar>
          ) : isCompleted ? (
            <ProgressBar aria-label="Completed" value={100} size="sm" color="success">
              <ProgressBar.Output className="text-[10px] tabular-nums text-foreground-3" />
              <ProgressBar.Track className="min-w-12">
                <ProgressBar.Fill />
              </ProgressBar.Track>
            </ProgressBar>
          ) : null}
        </div>
      </Table.Cell>

      {/* Created at */}
      <Table.Cell>
        <span className="text-xs tabular-nums text-foreground-2">
          {new Date(task.createdAt).toLocaleString()}
        </span>
      </Table.Cell>

      {/* Duration + credit cost */}
      <Table.Cell>
        <div className="flex flex-col gap-0.5">
          {duration ? (
            <span className="flex items-center gap-1 text-xs text-foreground-2">
              <Clock className="size-3 shrink-0" />
              {duration}
            </span>
          ) : (
            <span className="text-xs text-foreground-3">—</span>
          )}
          {task.creditCost > 0 && (
            <span className="flex items-center gap-1 text-xs text-foreground-3">
              <Zap className="size-3 shrink-0" />
              {task.creditCost}
            </span>
          )}
        </div>
      </Table.Cell>

      {/* Actions */}
      <Table.Cell>
        <div className="flex items-center justify-end gap-1">
          {isFailed && onRetry && (
            <Button
              isIconOnly
              variant="ghost"
              size="sm"
              className="size-7 min-w-0"
              onPress={() => onRetry(task.id)}
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
              onPress={() => onCancel(task.id)}
              aria-label={t("cancel")}
            >
              <X className="size-3.5" />
            </Button>
          )}
        </div>
      </Table.Cell>
    </Table.Row>
  );
}
