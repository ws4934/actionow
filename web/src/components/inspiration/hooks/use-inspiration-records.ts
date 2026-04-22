"use client";

import { useEffect, useRef, useCallback } from "react";
import { toast } from "@heroui/react";
import { useTranslations, useLocale } from "next-intl";
import { getErrorFromException } from "@/lib/api";
import { useInspirationStore } from "@/lib/stores/inspiration-store";
import { inspirationService } from "@/lib/api/services/inspiration.service";
import { useTaskUpdates, useWebSocketContext } from "@/lib/websocket/provider";
import type { InspirationRecordStatus } from "@/lib/api/dto/inspiration.dto";

export function useInspirationRecords(sessionId: string | null) {
  const t = useTranslations("workspace.inspiration");
  const locale = useLocale();
  const recordsList = useInspirationStore((s) => s.recordsList);
  const taskRecordMap = useInspirationStore((s) => s.taskRecordMap);
  const setRecords = useInspirationStore((s) => s.setRecords);
  const updateRecordByTaskId = useInspirationStore((s) => s.updateRecordByTaskId);
  const updateRecordStatus = useInspirationStore((s) => s.updateRecordStatus);
  const isLoadingRecords = useInspirationStore((s) => s.isLoadingRecords);
  const setLoadingRecords = useInspirationStore((s) => s.setLoadingRecords);

  const { reconnectCount } = useWebSocketContext();

  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const userScrolledRef = useRef(false);
  const loadRequestIdRef = useRef(0);
  const sessionIdRef = useRef(sessionId);
  sessionIdRef.current = sessionId;

  // ── Load records when session changes ──
  const loadRecords = useCallback(async () => {
    const sid = sessionIdRef.current;
    if (!sid) {
      setRecords([]);
      return;
    }

    const requestId = ++loadRequestIdRef.current;
    try {
      setLoadingRecords(true);
      const result = await inspirationService.getRecords(sid, {
        page: 1,
        size: 100,
      });
      if (requestId !== loadRequestIdRef.current) return;
      setRecords(result.records);
    } catch (error) {
      if (requestId !== loadRequestIdRef.current) return;
      console.error("Failed to load inspiration records:", error);
      toast.danger(getErrorFromException(error, locale));
    } finally {
      if (requestId === loadRequestIdRef.current) {
        setLoadingRecords(false);
      }
    }
  }, [setRecords, setLoadingRecords]);

  useEffect(() => {
    loadRecords();
  }, [sessionId]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── WebSocket: task updates → record status ──
  const handleTaskUpdate = useCallback(
    (taskId: string, status: string, data: Record<string, unknown>) => {
      // Only process events for records we're tracking
      if (!taskRecordMap.has(taskId)) return;

      switch (status) {
        case "TASK_PROGRESS": {
          const progress = (data.progress as number) ?? 0;
          updateRecordByTaskId(taskId, {
            progress,
            status: "RUNNING" as InspirationRecordStatus,
          });
          break;
        }

        case "TASK_COMPLETED":
        case "COMPLETED": {
          // Update optimistic state immediately
          updateRecordByTaskId(taskId, {
            status: "COMPLETED" as InspirationRecordStatus,
            progress: 100,
            completedAt: new Date().toISOString(),
          });

          // Reload records from server to get full asset data
          loadRecords();
          break;
        }

        case "TASK_FAILED":
        case "FAILED": {
          const errorMessage =
            (data.errorMessage as string) || t("record.failed");
          updateRecordByTaskId(taskId, {
            status: "FAILED" as InspirationRecordStatus,
            errorMessage,
          });
          toast.danger(errorMessage);
          break;
        }

        default: {
          // TASK_STATUS_CHANGED — check the actual status
          const newStatus = (data.status as string) || "";
          if (newStatus === "RUNNING") {
            updateRecordByTaskId(taskId, {
              status: "RUNNING" as InspirationRecordStatus,
              progress: (data.progress as number) ?? 0,
            });
          }
          break;
        }
      }
    },
    [taskRecordMap, updateRecordByTaskId, loadRecords, t]
  );

  useTaskUpdates(handleTaskUpdate, [handleTaskUpdate]);

  // ── Re-sync on WS reconnect ──
  useEffect(() => {
    if (reconnectCount === 0) return;
    loadRecords();
  }, [reconnectCount]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Auto-scroll to bottom when new records are added ──
  useEffect(() => {
    if (!userScrolledRef.current && bottomRef.current) {
      bottomRef.current.scrollIntoView({ behavior: "smooth" });
    }
  }, [recordsList.length]);

  const handleScroll = useCallback(() => {
    const container = scrollContainerRef.current;
    if (!container) return;
    const isAtBottom =
      container.scrollHeight - container.scrollTop - container.clientHeight < 80;
    userScrolledRef.current = !isAtBottom;
  }, []);

  const scrollToBottom = useCallback(() => {
    userScrolledRef.current = false;
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, []);

  return {
    records: recordsList,
    isLoadingRecords,
    scrollContainerRef,
    bottomRef,
    handleScroll,
    scrollToBottom,
    userScrolledUp: userScrolledRef.current,
    updateRecordByTaskId,
  };
}
