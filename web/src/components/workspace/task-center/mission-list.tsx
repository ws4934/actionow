"use client";

import { useState, useEffect, useCallback, useMemo, useRef } from "react";
import { useTranslations } from "next-intl";
import { Inbox, X, ChevronUp, Clock, Layers } from "lucide-react";
import { Table, Chip, Button, Skeleton, Pagination, ProgressBar, Select, ListBox, cn } from "@heroui/react";
import type { SortDescriptor } from "@heroui/react";
import { missionService } from "@/lib/api/services/mission.service";
import { useDebouncedEntityChanges } from "@/lib/websocket";
import type { MissionResponseDTO, MissionStatus } from "@/lib/api/dto/mission.dto";

// ============================================================================
// Constants
// ============================================================================

const PAGE_SIZE_OPTIONS = [10, 20, 50] as const;
type PageSize = (typeof PAGE_SIZE_OPTIONS)[number];

const STATUS_COLOR_MAP: Record<MissionStatus, "default" | "accent" | "success" | "danger" | "warning"> = {
  CREATED: "default",
  EXECUTING: "accent",
  WAITING: "warning",
  COMPLETED: "success",
  FAILED: "danger",
  CANCELLED: "warning",
};

// ============================================================================
// Props
// ============================================================================

interface MissionListProps {
  statusFilter?: MissionStatus;
  onViewDetail?: (missionId: string) => void;
  onCancel?: (missionId: string) => void;
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

export function MissionList({ statusFilter, onViewDetail, onCancel }: MissionListProps) {
  const t = useTranslations("workspace.missions");
  const [missions, setMissions] = useState<MissionResponseDTO[]>([]);
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalItems, setTotalItems] = useState(0);
  const [pageSize, setPageSize] = useState<PageSize>(20);
  const [isLoading, setIsLoading] = useState(true);
  const [sortDescriptor, setSortDescriptor] = useState<SortDescriptor>({
    column: "createdAt",
    direction: "descending",
  });
  const abortRef = useRef<AbortController | null>(null);

  const loadMissions = useCallback(
    async (page: number) => {
      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;

      setIsLoading(true);
      setMissions([]);
      try {
        const data = await missionService.queryMissions({
          current: page,
          size: pageSize,
          status: statusFilter,
        });
        if (!controller.signal.aborted) {
          setMissions(data.records);
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
    [statusFilter, pageSize],
  );

  useEffect(() => {
    setCurrentPage(1);
    loadMissions(1);
  }, [loadMissions]);

  // Auto-refresh on WS entity changes
  const reloadCurrentPage = useCallback(() => {
    loadMissions(currentPage);
  }, [loadMissions, currentPage]);

  useDebouncedEntityChanges("mission", reloadCurrentPage, { delay: 500 }, [reloadCurrentPage]);

  const handlePageChange = (page: number) => {
    loadMissions(page);
  };

  const sortedMissions = useMemo(() => {
    if (!sortDescriptor.column) return missions;
    return [...missions].sort((a, b) => {
      const col = sortDescriptor.column as keyof MissionResponseDTO;
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
  }, [missions, sortDescriptor]);

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
          className="min-w-[800px]"
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
            <Table.Column id="steps" className="w-24">
              {t("column.steps")}
            </Table.Column>
            <Table.Column allowsSorting id="totalCreditCost" className="w-24">
              {({ sortDirection }) => (
                <SortableColumnHeader sortDirection={sortDirection}>
                  {t("column.credit")}
                </SortableColumnHeader>
              )}
            </Table.Column>
            <Table.Column allowsSorting id="startedAt" className="w-40">
              {({ sortDirection }) => (
                <SortableColumnHeader sortDirection={sortDirection}>
                  {t("column.startedAt")}
                </SortableColumnHeader>
              )}
            </Table.Column>
            <Table.Column className="w-20 text-end">{""}</Table.Column>
          </Table.Header>
          <Table.Body
            renderEmptyState={() =>
              isLoading ? (
                <div className="w-full space-y-2 py-4">
                  {Array.from({ length: 6 }).map((_, i) => (
                    <div key={i} className="flex items-center gap-4 px-4 py-2">
                      <Skeleton className="size-7 rounded-md" />
                      <div className="flex-1 space-y-2">
                        <Skeleton className="h-4 w-3/5 rounded" />
                        <Skeleton className="h-3 w-1/4 rounded" />
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
            <Table.Collection items={sortedMissions}>
              {(mission) => (
                <MissionRow
                  mission={mission}
                  t={t}
                  onViewDetail={onViewDetail}
                  onCancel={onCancel}
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

function MissionRow({
  mission,
  t,
  onViewDetail,
  onCancel,
}: {
  mission: MissionResponseDTO;
  t: ReturnType<typeof useTranslations>;
  onViewDetail?: (missionId: string) => void;
  onCancel?: (missionId: string) => void;
}) {
  const statusColor = STATUS_COLOR_MAP[mission.status] || "default";
  const isActive = mission.status === "EXECUTING" || mission.status === "WAITING" || mission.status === "CREATED";
  const progress = mission.progress ?? 0;

  return (
    <Table.Row
      id={mission.id}
      className="cursor-pointer"
      onAction={() => onViewDetail?.(mission.id)}
    >
      <Table.Cell className="max-w-[280px]">
        <div className="flex min-w-0 items-center gap-2.5">
          <div className="flex size-7 shrink-0 items-center justify-center rounded-md bg-surface">
            <Layers className="size-3.5 text-foreground-2" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium text-foreground">{mission.title}</p>
            {mission.errorMessage && mission.status === "FAILED" && (
              <p className="mt-0.5 truncate text-xs text-danger">{mission.errorMessage}</p>
            )}
          </div>
        </div>
      </Table.Cell>

      <Table.Cell>
        <Chip size="sm" variant="soft" color={statusColor}>
          {t(`status.${mission.status}`)}
        </Chip>
      </Table.Cell>

      <Table.Cell>
        {isActive && progress > 0 ? (
          <ProgressBar aria-label="Mission progress" value={Math.min(progress, 100)} size="sm" color="accent">
            <ProgressBar.Output className="text-[10px] tabular-nums text-foreground-3" />
            <ProgressBar.Track className="min-w-12">
              <ProgressBar.Fill />
            </ProgressBar.Track>
          </ProgressBar>
        ) : progress === 100 ? (
          <ProgressBar aria-label="Completed" value={100} size="sm" color="success">
            <ProgressBar.Output className="text-[10px] tabular-nums text-foreground-3" />
            <ProgressBar.Track className="min-w-12">
              <ProgressBar.Fill />
            </ProgressBar.Track>
          </ProgressBar>
        ) : (
          <span className="text-xs text-foreground-2">—</span>
        )}
      </Table.Cell>

      <Table.Cell>
        <span className="text-xs text-foreground-2">
          {mission.currentStep}/{mission.totalSteps}
        </span>
      </Table.Cell>

      <Table.Cell>
        {mission.totalCreditCost > 0 ? (
          <span className="text-xs text-foreground-2">{mission.totalCreditCost}</span>
        ) : (
          <span className="text-xs text-foreground-2">—</span>
        )}
      </Table.Cell>

      <Table.Cell>
        {mission.startedAt ? (
          <span className="flex items-center gap-1 text-xs tabular-nums text-foreground-2">
            <Clock className="size-3" />
            {new Date(mission.startedAt).toLocaleString()}
          </span>
        ) : (
          <span className="text-xs text-foreground-2">—</span>
        )}
      </Table.Cell>

      <Table.Cell>
        <div className="flex items-center justify-end gap-1">
          {isActive && onCancel && (
            <Button
              isIconOnly
              variant="ghost"
              size="sm"
              className="size-7 min-w-0"
              onPress={() => onCancel(mission.id)}
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
