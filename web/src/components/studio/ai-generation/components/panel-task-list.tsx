"use client";

import { useState, useEffect, useCallback, useMemo } from "react";
import { useTranslations, useLocale} from "next-intl";
import { Spinner, toast} from "@heroui/react";
import { ListChecks } from "lucide-react";
import { useTaskStore } from "@/lib/stores/task-store";
import { taskService } from "@/lib/api/services/task.service";
import { TaskCard } from "@/components/workspace/task-center/task-card";
import { TaskDetailModal } from "@/components/workspace/task-center/task-detail-modal";
import type { TaskListItemDTO } from "@/lib/api/dto/task.dto";
import { getErrorFromException } from "@/lib/api";

interface PanelTaskListProps {
  scriptId: string;
}

export function PanelTaskList({ scriptId }: PanelTaskListProps) {
  const locale = useLocale();
  const t = useTranslations("workspace.aiGeneration.tabs");
  const { activeTasksList } = useTaskStore();

  const [historyTasks, setHistoryTasks] = useState<TaskListItemDTO[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);
  const [isDetailOpen, setIsDetailOpen] = useState(false);

  // Load initial history tasks
  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    taskService
      .getMyTasks({ scriptId, pageSize: 20 })
      .then((data) => {
        if (!cancelled) setHistoryTasks(data.records ?? []);
      })
      .catch((err) => { console.error("PanelTaskList: failed to load tasks", err); toast.danger(getErrorFromException(err, locale)); })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [scriptId]);

  // Merge active tasks (from WS store) + history tasks, dedup by id
  // activeTasksList entries (live) take priority over history
  const mergedTasks = useMemo(() => {
    const activeForScript = activeTasksList.filter(
      (t) => t.scriptId === scriptId,
    );
    const activeIds = new Set(activeForScript.map((t) => t.id));
    const historyFiltered = historyTasks.filter(
      (t) => t.scriptId === scriptId && !activeIds.has(t.id),
    );

    // Combine: active first, then history
    const combined = [...activeForScript, ...historyFiltered];

    // Sort: active statuses on top, then by createdAt desc
    const ACTIVE_STATUSES = new Set(["PENDING", "QUEUED", "RUNNING"]);
    return combined.sort((a, b) => {
      const aActive = ACTIVE_STATUSES.has(a.status) ? 1 : 0;
      const bActive = ACTIVE_STATUSES.has(b.status) ? 1 : 0;
      if (aActive !== bActive) return bActive - aActive;
      return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
    });
  }, [activeTasksList, historyTasks, scriptId]);

  const handleCancel = useCallback(async (taskId: string) => {
    try {
      await taskService.cancelTask(taskId);
    } catch (err) {
      console.error("Failed to cancel task", err);
      toast.danger(getErrorFromException(err, locale));
    }
  }, []);

  const handleRetry = useCallback(async (taskId: string) => {
    try {
      const updated = await taskService.retryTask(taskId);
      // Optimistically update history list
      setHistoryTasks((prev) =>
        prev.map((t) => (t.id === taskId ? updated : t)),
      );
    } catch (err) {
      console.error("Failed to retry task", err);
      toast.danger(getErrorFromException(err, locale));
    }
  }, []);

  const handleViewDetail = useCallback((taskId: string) => {
    setSelectedTaskId(taskId);
    setIsDetailOpen(true);
  }, []);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-10">
        <Spinner size="sm" />
      </div>
    );
  }

  if (mergedTasks.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center gap-2 py-10 text-center">
        <ListChecks className="size-8 text-foreground-3" />
        <p className="text-sm text-foreground-3">{t("empty")}</p>
      </div>
    );
  }

  return (
    <>
      <div className="flex flex-col gap-1 p-2">
        {mergedTasks.map((task) => (
          <TaskCard
            key={task.id}
            task={task}
            variant="compact"
            onCancel={handleCancel}
            onRetry={handleRetry}
            onViewDetail={handleViewDetail}
          />
        ))}
      </div>

      <TaskDetailModal
        isOpen={isDetailOpen}
        onOpenChange={setIsDetailOpen}
        taskId={selectedTaskId}
        onCancel={handleCancel}
        onRetry={handleRetry}
      />
    </>
  );
}
