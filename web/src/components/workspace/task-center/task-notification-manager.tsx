"use client";

import { useEffect, useCallback, useRef } from "react";
import { toast } from "@heroui/react";
import { useTranslations } from "next-intl";
import { taskService } from "@/lib/api/services/task.service";
import { useTaskStore } from "@/lib/stores/task-store";
import { useTaskUpdates, useWebSocketContext } from "@/lib/websocket/provider";
import { parseTaskFromWsData } from "./task-shared";

/**
 * TaskNotificationManager
 * Headless component that manages task state initialization and WebSocket event handling.
 * Renders null — mounted inside workspace layout for side effects only.
 */
export function TaskNotificationManager() {
  const t = useTranslations("workspace.taskCenter");
  const {
    setActiveTasks,
    updateTaskProgress,
    completeTask,
    failTask,
    upsertTask,
    setInitialized,
  } = useTaskStore.getState();
  const isInitialized = useTaskStore((s) => s.isInitialized);
  const initRef = useRef(false);
  const { reconnectCount } = useWebSocketContext();

  // Initialize: fetch active tasks on mount
  useEffect(() => {
    if (initRef.current) return;
    initRef.current = true;

    const init = async () => {
      try {
        const tasks = await taskService.getMyTasksAll();
        setActiveTasks(tasks);
      } catch {
        // Silently fail — tracker just won't show tasks
      } finally {
        setInitialized(true);
      }
    };

    init();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // WebSocket task updates
  const handleTaskUpdate = useCallback(
    (taskId: string, status: string, data: Record<string, unknown>) => {
      switch (status) {
        case "TASK_PROGRESS": {
          const progress = (data.progress as number) ?? 0;
          updateTaskProgress(taskId, progress);
          break;
        }
        case "TASK_COMPLETED":
        case "COMPLETED": {
          completeTask(taskId);
          const title = (data.title as string) || t("toast.taskCompleted");
          toast.success(title);
          break;
        }
        case "TASK_FAILED":
        case "FAILED": {
          const errorMsg = (data.errorMessage as string) || undefined;
          failTask(taskId, errorMsg);
          const title = (data.title as string) || t("toast.taskFailed");
          toast.danger(title);
          break;
        }
        default: {
          // TASK_STATUS_CHANGED or other status values
          if (data.id || taskId) {
            upsertTask(parseTaskFromWsData(taskId, status, data));
          }
          break;
        }
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t]
  );

  useTaskUpdates(handleTaskUpdate, [handleTaskUpdate]);

  // Re-sync active tasks on WS reconnect (covers messages missed during disconnect)
  useEffect(() => {
    if (reconnectCount === 0) return; // skip initial
    const sync = async () => {
      try {
        const tasks = await taskService.getMyTasksAll();
        setActiveTasks(tasks);
      } catch {
        // silent
      }
    };
    sync();
  }, [reconnectCount]); // eslint-disable-line react-hooks/exhaustive-deps

  // Lightweight polling fallback: re-sync every 60s while tasks are active.
  // Covers WS messages that were silently dropped without a disconnect.
  const hasActiveTasks = useTaskStore((s) => s.activeTasksList.length > 0);
  useEffect(() => {
    if (!hasActiveTasks || !isInitialized) return;
    const interval = setInterval(async () => {
      try {
        const tasks = await taskService.getMyTasksAll();
        setActiveTasks(tasks);
      } catch {
        // silent
      }
    }, 60_000);
    return () => clearInterval(interval);
  }, [hasActiveTasks, isInitialized]); // eslint-disable-line react-hooks/exhaustive-deps

  // Re-initialize when isInitialized is reset (e.g., workspace switch)
  useEffect(() => {
    if (!isInitialized && initRef.current) {
      initRef.current = false;
      // Allow re-initialization
      const timer = setTimeout(() => {
        initRef.current = true;
        const reinit = async () => {
          try {
            const tasks = await taskService.getMyTasksAll();
            setActiveTasks(tasks);
          } catch {
            // silent
          } finally {
            setInitialized(true);
          }
        };
        reinit();
      }, 100);
      return () => clearTimeout(timer);
    }
  }, [isInitialized]); // eslint-disable-line react-hooks/exhaustive-deps

  return null;
}
