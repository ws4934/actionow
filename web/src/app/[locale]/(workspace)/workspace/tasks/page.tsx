"use client";

import { useState, useEffect, useCallback } from "react";
import { useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { RefreshCw } from "lucide-react";
import { Button, Tabs, Separator, SearchField, Select, ListBox, Spinner } from "@heroui/react";
import { taskService } from "@/lib/api/services/task.service";
import { missionService } from "@/lib/api/services/mission.service";
import { useTaskStore } from "@/lib/stores/task-store";
import { TaskList } from "@/components/workspace/task-center/task-list";
import { TaskDetailPanel } from "@/components/workspace/task-center/task-detail-panel";
import { MissionList } from "@/components/workspace/task-center/mission-list";
import { MissionDetailPanel } from "@/components/workspace/task-center/mission-detail-panel";
import { BatchJobList } from "@/components/workspace/task-center/batch-job-list";
import { BatchJobDetailPanel } from "@/components/workspace/task-center/batch-job-detail-panel";
import type { TaskStatsSummaryDTO, TaskStatus } from "@/lib/api/dto/task.dto";
import type { MissionStatus } from "@/lib/api/dto/mission.dto";
import type { BatchJobStatus } from "@/lib/api/dto/batch-job.dto";

// ============================================================================
// Constants
// ============================================================================

type ViewMode = "tasks" | "missions" | "batchJobs";

const TASK_STATUS_FILTERS: Array<{ key: string; value: TaskStatus | undefined }> = [
  { key: "all", value: undefined },
  { key: "running", value: "RUNNING" },
  { key: "completed", value: "COMPLETED" },
  { key: "failed", value: "FAILED" },
  { key: "cancelled", value: "CANCELLED" },
];

const MISSION_STATUS_FILTERS: Array<{ key: string; value: MissionStatus | undefined }> = [
  { key: "all", value: undefined },
  { key: "executing", value: "EXECUTING" },
  { key: "waiting", value: "WAITING" },
  { key: "completed", value: "COMPLETED" },
  { key: "failed", value: "FAILED" },
  { key: "cancelled", value: "CANCELLED" },
];

const BATCH_STATUS_FILTERS: Array<{ key: string; value: BatchJobStatus | undefined }> = [
  { key: "all", value: undefined },
  { key: "running", value: "RUNNING" },
  { key: "paused", value: "PAUSED" },
  { key: "completed", value: "COMPLETED" },
  { key: "failed", value: "FAILED" },
  { key: "cancelled", value: "CANCELLED" },
];

// ============================================================================
// Page
// ============================================================================

export default function TasksPage() {
  const t = useTranslations("workspace.taskCenter");
  const tM = useTranslations("workspace.missions");
  const tB = useTranslations("workspace.batchJobs");
  const searchParams = useSearchParams();

  // View mode: tasks | missions
  const [viewMode, setViewMode] = useState<ViewMode>("tasks");

  // Unified detail panel state
  const [selectedItem, setSelectedItem] = useState<{ type: ViewMode; id: string } | null>(null);

  // Task state
  const [stats, setStats] = useState<TaskStatsSummaryDTO | null>(null);
  const [isLoadingStats, setIsLoadingStats] = useState(true);
  const [taskStatusFilter, setTaskStatusFilter] = useState<TaskStatus | undefined>(undefined);
  const [searchKeyword, setSearchKeyword] = useState("");
  const [taskRefreshKey, setTaskRefreshKey] = useState(0);

  // Auto-open task detail from query param (e.g. from header popover)
  useEffect(() => {
    const taskIdParam = searchParams.get("taskId");
    if (taskIdParam) {
      setSelectedItem({ type: "tasks", id: taskIdParam });
    }
  }, [searchParams]);

  // Mission state
  const [missionStatusFilter, setMissionStatusFilter] = useState<MissionStatus | undefined>(undefined);
  const [missionRefreshKey, setMissionRefreshKey] = useState(0);

  // Batch job state
  const [batchStatusFilter, setBatchStatusFilter] = useState<BatchJobStatus | undefined>(undefined);
  const [batchRefreshKey, setBatchRefreshKey] = useState(0);

  const [isRefreshing, setIsRefreshing] = useState(false);
  const cancelTaskInStore = useTaskStore((s) => s.cancelTask);

  const loadStats = useCallback(async () => {
    setIsLoadingStats(true);
    try {
      const data = await taskService.getMyStatsSummary();
      setStats(data);
      useTaskStore.getState().setStatsSummary(data);
    } catch {
      // silent
    } finally {
      setIsLoadingStats(false);
    }
  }, []);

  useEffect(() => {
    loadStats();
  }, [loadStats]);

  const handleRefresh = () => {
    setIsRefreshing(true);
    if (viewMode === "tasks") {
      loadStats().finally(() => setIsRefreshing(false));
      setTaskRefreshKey((k) => k + 1);
    } else if (viewMode === "missions") {
      setMissionRefreshKey((k) => k + 1);
      setIsRefreshing(false);
    } else {
      setBatchRefreshKey((k) => k + 1);
      setIsRefreshing(false);
    }
  };

  const handleCloseDetail = () => setSelectedItem(null);

  // Task handlers
  const handleViewTaskDetail = (taskId: string) => {
    setSelectedItem({ type: "tasks", id: taskId });
  };

  const handleCancelTask = async (taskId: string) => {
    cancelTaskInStore(taskId);
    try {
      await taskService.cancelTask(taskId);
    } catch {
      // silent
    }
    setTaskRefreshKey((k) => k + 1);
    loadStats();
  };

  const handleRetryTask = async (taskId: string) => {
    try {
      await taskService.retryTask(taskId);
    } catch {
      // silent
    }
    setTaskRefreshKey((k) => k + 1);
    loadStats();
  };

  // Mission handlers
  const handleViewMissionDetail = (missionId: string) => {
    setSelectedItem({ type: "missions", id: missionId });
  };

  const handleCancelMission = async (missionId: string) => {
    try {
      await missionService.cancelMission(missionId);
    } catch {
      // silent
    }
    setMissionRefreshKey((k) => k + 1);
  };

  // Batch job handlers
  const handleViewBatchJobDetail = (jobId: string) => {
    setSelectedItem({ type: "batchJobs", id: jobId });
  };

  const handleBatchRefreshList = () => {
    setBatchRefreshKey((k) => k + 1);
  };

  const activeTaskStatusKey =
    TASK_STATUS_FILTERS.find((f) => f.value === taskStatusFilter)?.key || "all";

  const activeMissionStatusKey =
    MISSION_STATUS_FILTERS.find((f) => f.value === missionStatusFilter)?.key || "all";

  // List content for current view mode
  const listContent =
    viewMode === "tasks" ? (
      <TaskList
        key={taskRefreshKey}
        statusFilter={taskStatusFilter}
        searchKeyword={searchKeyword}
        onViewDetail={handleViewTaskDetail}
        onCancel={handleCancelTask}
        onRetry={handleRetryTask}
      />
    ) : viewMode === "missions" ? (
      <MissionList
        key={missionRefreshKey}
        statusFilter={missionStatusFilter}
        onViewDetail={handleViewMissionDetail}
        onCancel={handleCancelMission}
      />
    ) : (
      <BatchJobList
        key={batchRefreshKey}
        statusFilter={batchStatusFilter}
        onViewDetail={handleViewBatchJobDetail}
      />
    );

  // Detail panel for current selection
  const detailPanel = selectedItem ? (
    selectedItem.type === "tasks" ? (
      <TaskDetailPanel
        taskId={selectedItem.id}
        onClose={handleCloseDetail}
        onCancel={handleCancelTask}
        onRetry={handleRetryTask}
      />
    ) : selectedItem.type === "missions" ? (
      <MissionDetailPanel
        missionId={selectedItem.id}
        onClose={handleCloseDetail}
        onCancel={handleCancelMission}
      />
    ) : (
      <BatchJobDetailPanel
        jobId={selectedItem.id}
        onClose={handleCloseDetail}
        onRefreshList={handleBatchRefreshList}
      />
    )
  ) : null;

  return (
    <>
      <div className="flex h-full flex-col bg-background">
        {/* Sticky toolbar */}
        <div className="flex shrink-0 items-center justify-between gap-4 border-b border-border bg-surface px-6 py-2.5">
          <div className="flex items-center gap-4 min-w-0 flex-1">
            {/* View mode tabs */}
            <Tabs
              selectedKey={viewMode}
              onSelectionChange={(key) => {
                setViewMode(key as ViewMode);
                setSelectedItem(null);
              }}
            >
              <Tabs.ListContainer>
                <Tabs.List aria-label="View mode">
                  <Tabs.Tab id="tasks">
                    {t("pageTitle")}
                    <Tabs.Indicator />
                  </Tabs.Tab>
                  <Tabs.Tab id="missions">
                    {tM("tab")}
                    <Tabs.Indicator />
                  </Tabs.Tab>
                  <Tabs.Tab id="batchJobs">
                    {tB("tab")}
                    <Tabs.Indicator />
                  </Tabs.Tab>
                </Tabs.List>
              </Tabs.ListContainer>
            </Tabs>

            <Separator orientation="vertical" className="h-5" />

            {/* Status filter dropdown */}
            {viewMode === "tasks" ? (
              <>
                <Select
                  aria-label={t("filter.all")}
                  selectedKey={activeTaskStatusKey}
                  onSelectionChange={(key) => {
                    const filter = TASK_STATUS_FILTERS.find((f) => f.key === key);
                    setTaskStatusFilter(filter?.value);
                  }}
                  variant="secondary"
                >
                  <Select.Trigger className="h-7 w-28 text-xs">
                    <Select.Value />
                    <Select.Indicator />
                  </Select.Trigger>
                  <Select.Popover>
                    <ListBox>
                      {TASK_STATUS_FILTERS.map((filter) => (
                        <ListBox.Item key={filter.key} id={filter.key} textValue={t(`filter.${filter.key}`)}>
                          {t(`filter.${filter.key}`)}
                          {!isLoadingStats && stats && filter.key !== "all" && (
                            <span className="ml-auto text-[10px] text-foreground-3">
                              {filter.key === "running" ? stats.runningTasks : null}
                              {filter.key === "completed" ? stats.completedTasks : null}
                              {filter.key === "failed" ? stats.failedTasks : null}
                              {filter.key === "cancelled" ? stats.cancelledTasks : null}
                            </span>
                          )}
                          <ListBox.ItemIndicator />
                        </ListBox.Item>
                      ))}
                    </ListBox>
                  </Select.Popover>
                </Select>
                {!isLoadingStats && stats && (
                  <div className="flex items-center gap-3 text-xs text-foreground-2">
                    <span>
                      {t("stats.total")}:{" "}
                      <span className="font-semibold text-foreground">{stats.totalTasks}</span>
                    </span>
                    <span>
                      {t("stats.successRate")}:{" "}
                      <span className="font-semibold text-foreground">
                        {stats.successRate.toFixed(1)}%
                      </span>
                    </span>
                  </div>
                )}
              </>
            ) : viewMode === "missions" ? (
              <Select
                aria-label={t("filter.all")}
                selectedKey={activeMissionStatusKey}
                onSelectionChange={(key) => {
                  const filter = MISSION_STATUS_FILTERS.find((f) => f.key === key);
                  setMissionStatusFilter(filter?.value);
                }}
                variant="secondary"
              >
                <Select.Trigger className="h-7 w-28 text-xs">
                  <Select.Value />
                  <Select.Indicator />
                </Select.Trigger>
                <Select.Popover>
                  <ListBox>
                    {MISSION_STATUS_FILTERS.map((filter) => (
                      <ListBox.Item key={filter.key} id={filter.key} textValue={
                        filter.key === "all" ? t("filter.all") : tM(`status.${filter.value as MissionStatus}`)
                      }>
                        {filter.key === "all" ? t("filter.all") : tM(`status.${filter.value as MissionStatus}`)}
                        <ListBox.ItemIndicator />
                      </ListBox.Item>
                    ))}
                  </ListBox>
                </Select.Popover>
              </Select>
            ) : (
              <Select
                aria-label={tB("filter.allStatus")}
                selectedKey={BATCH_STATUS_FILTERS.find((f) => f.value === batchStatusFilter)?.key ?? "all"}
                onSelectionChange={(key) => {
                  const filter = BATCH_STATUS_FILTERS.find((f) => f.key === key);
                  setBatchStatusFilter(filter?.value);
                }}
                variant="secondary"
              >
                <Select.Trigger className="h-7 w-28 text-xs">
                  <Select.Value />
                  <Select.Indicator />
                </Select.Trigger>
                <Select.Popover>
                  <ListBox>
                    {BATCH_STATUS_FILTERS.map((filter) => (
                      <ListBox.Item key={filter.key} id={filter.key} textValue={
                        filter.key === "all" ? tB("filter.allStatus") : tB(`status.${filter.value as BatchJobStatus}`)
                      }>
                        {filter.key === "all" ? tB("filter.allStatus") : tB(`status.${filter.value as BatchJobStatus}`)}
                        <ListBox.ItemIndicator />
                      </ListBox.Item>
                    ))}
                  </ListBox>
                </Select.Popover>
              </Select>
            )}
          </div>

          {/* Right: Search (tasks only) + Refresh */}
          <div className="flex items-center gap-2">
            {viewMode === "tasks" && (
              <SearchField
                aria-label={t("searchPlaceholder")}
                value={searchKeyword}
                onChange={setSearchKeyword}
                variant="secondary"
              >
                <SearchField.Group>
                  <SearchField.SearchIcon />
                  <SearchField.Input className="w-44" placeholder={t("searchPlaceholder")} />
                  <SearchField.ClearButton />
                </SearchField.Group>
              </SearchField>
            )}
            <Button
              variant="ghost"
              size="sm"
              isIconOnly
              isPending={isRefreshing}
              onPress={handleRefresh}
              aria-label={t("refresh")}
            >
              {({ isPending }) =>
                isPending ? <Spinner size="sm" color="current" /> : <RefreshCw className="size-4" />
              }
            </Button>
          </div>
        </div>

        {/* Content area — fills remaining viewport */}
        <div className="flex min-h-0 flex-1 overflow-hidden pt-3">
          {/* Left: list — always mounted */}
          <div className={`h-full shrink-0 overflow-hidden px-4 transition-[width] duration-300 ease-in-out ${selectedItem ? "w-[60%]" : "w-full"}`}>
            {listContent}
          </div>
          {/* Right: detail panel — slides in/out */}
          <div className={`h-full overflow-hidden transition-[width,opacity] duration-300 ease-in-out ${selectedItem ? "w-[40%] opacity-100" : "w-0 opacity-0"}`}>
            {detailPanel}
          </div>
        </div>
      </div>
    </>
  );
}
