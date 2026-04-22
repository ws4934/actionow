"use client";

import { useState, useEffect, useRef, useCallback } from "react";
import { missionService } from "@/lib/api/services/mission.service";
import type { MissionResponseDTO, MissionProgressDTO, MissionSseEvent } from "@/lib/api/dto/mission.dto";

const TERMINAL_STATUSES = new Set(["COMPLETED", "FAILED", "CANCELLED"]);

interface UseMissionResult {
  mission: MissionResponseDTO | null;
  progress: MissionProgressDTO | null;
  isConnected: boolean;
  cancelMission: () => Promise<void>;
}

/**
 * Finds the most recent non-terminal mission for the given sessionId and
 * connects SSE for live progress updates. Once the mission reaches a
 * terminal state it stops the SSE connection.
 */
export function useMission(sessionId: string | null | undefined): UseMissionResult {
  const [mission, setMission] = useState<MissionResponseDTO | null>(null);
  const [progress, setProgress] = useState<MissionProgressDTO | null>(null);
  const [isConnected, setIsConnected] = useState(false);
  const sseRef = useRef<{ abort: () => void } | null>(null);
  const activeSessionRef = useRef<string | null>(null);

  const stopSse = useCallback(() => {
    sseRef.current?.abort();
    sseRef.current = null;
    setIsConnected(false);
  }, []);

  const connectSse = useCallback((missionId: string) => {
    stopSse();
    setIsConnected(true);

    sseRef.current = missionService.streamProgress(missionId, {
      onEvent: (event: MissionSseEvent) => {
        // Update progress on any event
        if (event.progress !== undefined) {
          setProgress((prev) =>
            prev
              ? {
                  ...prev,
                  progress: event.progress ?? prev.progress,
                  currentStep: event.currentStep ?? prev.currentStep,
                  status: event.status ?? prev.status,
                  currentActivity: (event.data as { currentActivity?: string } | undefined)?.currentActivity ?? prev.currentActivity,
                }
              : prev
          );
        }
        if (event.status) {
          setMission((prev) => (prev ? { ...prev, status: event.status! } : prev));
        }
      },
      onError: (err: string) => {
        console.warn("[useMission] SSE error:", err);
        setIsConnected(false);
      },
      onClose: () => {
        setIsConnected(false);
        // Reload mission to get final state
        missionService.getMission(missionId).then((m) => {
          setMission(m);
        }).catch(() => {});
      },
    });
  }, [stopSse]);

  useEffect(() => {
    if (!sessionId) {
      activeSessionRef.current = null;
      setMission(null);
      setProgress(null);
      stopSse();
      return;
    }

    if (activeSessionRef.current === sessionId) return;
    activeSessionRef.current = sessionId;

    // Stop any existing SSE for previous session
    stopSse();
    setMission(null);
    setProgress(null);

    let cancelled = false;

    const findMission = async () => {
      try {
        // Fetch recent missions and filter by sessionId client-side
        const page = await missionService.queryMissions({ current: 1, size: 20 });
        if (cancelled) return;

        const found = page.records.find((m) => m.sessionId === sessionId);
        if (!found) return;

        setMission(found);

        // Load progress
        try {
          const prog = await missionService.getProgress(found.id);
          if (!cancelled) setProgress(prog);
        } catch {
          // Not fatal — SSE will update progress
        }

        // Connect SSE if non-terminal
        if (!cancelled && !TERMINAL_STATUSES.has(found.status)) {
          connectSse(found.id);
        }
      } catch {
        // silent — no mission found or API unavailable
      }
    };

    findMission();

    return () => {
      cancelled = true;
    };
  }, [sessionId, connectSse, stopSse]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      stopSse();
    };
  }, [stopSse]);

  const cancelMission = useCallback(async () => {
    if (!mission) return;
    try {
      await missionService.cancelMission(mission.id);
      setMission((prev) => (prev ? { ...prev, status: "CANCELLED" } : prev));
      stopSse();
    } catch {
      // silent
    }
  }, [mission, stopSse]);

  return { mission, progress, isConnected, cancelMission };
}
