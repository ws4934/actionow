"use client";

import { useState, useRef, useCallback, useEffect } from "react";
import { toast } from "@heroui/react";
import { useTranslations, useLocale } from "next-intl";
import { getErrorFromException } from "@/lib/api";
import { inspirationService } from "@/lib/api/services/inspiration.service";
import { useInspirationStore } from "@/lib/stores/inspiration-store";
import type { InspirationSessionDTO } from "@/lib/api/dto/inspiration.dto";

const SESSION_STORAGE_KEY = "inspiration:current-session";

export function useInspirationSessions() {
  const t = useTranslations("workspace.inspiration");
  const locale = useLocale();
  const sessions = useInspirationStore((s) => s.sessions);
  const currentSessionId = useInspirationStore((s) => s.currentSessionId);
  const setSessions = useInspirationStore((s) => s.setSessions);
  const prependSession = useInspirationStore((s) => s.prependSession);
  const removeSession = useInspirationStore((s) => s.removeSession);
  const setCurrentSessionId = useInspirationStore((s) => s.setCurrentSessionId);
  const setLoadingSessions = useInspirationStore((s) => s.setLoadingSessions);
  const isLoadingSessions = useInspirationStore((s) => s.isLoadingSessions);

  const [isSessionSelectorOpen, setIsSessionSelectorOpen] = useState(false);
  const [hasMoreSessions, setHasMoreSessions] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);
  const [isActionPending, setIsActionPending] = useState(false);

  const sessionPageRef = useRef(1);
  const loadRequestIdRef = useRef(0);
  const sessionListRef = useRef<HTMLDivElement>(null);

  const currentSession = sessions.find((s) => s.id === currentSessionId) ?? null;

  // Restore last selected session from localStorage
  useEffect(() => {
    const stored = localStorage.getItem(SESSION_STORAGE_KEY);
    if (stored && !currentSessionId) {
      setCurrentSessionId(stored);
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Persist current session selection
  useEffect(() => {
    if (currentSessionId) {
      localStorage.setItem(SESSION_STORAGE_KEY, currentSessionId);
    }
  }, [currentSessionId]);

  const loadSessions = useCallback(
    async (reset: boolean = false) => {
      const requestId = ++loadRequestIdRef.current;
      try {
        setLoadingSessions(true);
        const page = reset ? 1 : sessionPageRef.current;
        const result = await inspirationService.getSessions({ page, size: 20 });

        if (requestId !== loadRequestIdRef.current) return;

        if (reset) {
          // Auto-create a default session if none exist
          if (result.records.length === 0) {
            const newSession = await inspirationService.createSession({
              title: t("session.autoTitle"),
            });
            if (requestId !== loadRequestIdRef.current) return;
            setSessions([newSession]);
            setCurrentSessionId(newSession.id);
          } else {
            setSessions(result.records);
            // Auto-select first session if none selected or current not found
            if (!currentSessionId || !result.records.some((s) => s.id === currentSessionId)) {
              setCurrentSessionId(result.records[0]?.id ?? null);
            }
          }
          sessionPageRef.current = 1;
        } else {
          const merged = [...sessions, ...result.records];
          const deduped = Array.from(
            new Map(merged.map((s) => [s.id, s])).values()
          );
          setSessions(deduped);
          sessionPageRef.current = page;
        }

        setHasMoreSessions(result.current < result.pages);
      } catch (error) {
        if (requestId !== loadRequestIdRef.current) return;
        console.error("Failed to load inspiration sessions:", error);
        toast.danger(getErrorFromException(error, locale));
        setHasMoreSessions(false);
      } finally {
        if (requestId === loadRequestIdRef.current) {
          setLoadingSessions(false);
        }
      }
    },
    [sessions, currentSessionId, setSessions, setCurrentSessionId, setLoadingSessions, t]
  );

  const loadMoreSessions = useCallback(() => {
    if (!isLoadingSessions && hasMoreSessions) {
      sessionPageRef.current += 1;
      loadSessions(false);
    }
  }, [isLoadingSessions, hasMoreSessions, loadSessions]);

  const handleSessionListScroll = useCallback(
    (e: React.UIEvent<HTMLDivElement>) => {
      const target = e.currentTarget;
      const isNearBottom =
        target.scrollHeight - target.scrollTop - target.clientHeight < 50;
      if (isNearBottom && hasMoreSessions && !isLoadingSessions) {
        loadMoreSessions();
      }
    },
    [hasMoreSessions, isLoadingSessions, loadMoreSessions]
  );

  const createNewSession = useCallback(async () => {
    try {
      const session = await inspirationService.createSession();
      prependSession(session);
      setCurrentSessionId(session.id);
      setIsSessionSelectorOpen(false);
      return session;
    } catch (error) {
      console.error("Failed to create inspiration session:", error);
      toast.danger(t("session.new") + " failed");
      return null;
    }
  }, [prependSession, setCurrentSessionId, t]);

  const selectSession = useCallback(
    (session: InspirationSessionDTO) => {
      setCurrentSessionId(session.id);
      setIsSessionSelectorOpen(false);
    },
    [setCurrentSessionId]
  );

  const confirmDeleteSession = useCallback(async () => {
    if (!deleteTarget) return;
    try {
      setIsActionPending(true);
      await inspirationService.deleteSession(deleteTarget);
      removeSession(deleteTarget);
      // If we deleted the current session, select the next one
      if (currentSessionId === deleteTarget) {
        const remaining = sessions.filter((s) => s.id !== deleteTarget);
        setCurrentSessionId(remaining[0]?.id ?? null);
      }
    } catch (error) {
      console.error("Failed to delete session:", error);
      toast.danger(t("session.delete") + " failed");
    } finally {
      setIsActionPending(false);
      setDeleteTarget(null);
    }
  }, [deleteTarget, currentSessionId, sessions, removeSession, setCurrentSessionId, t]);

  return {
    sessions,
    currentSession,
    currentSessionId,
    selectSession,
    isSessionSelectorOpen,
    setIsSessionSelectorOpen,
    isLoadingSessions,
    hasMoreSessions,
    sessionListRef,
    loadSessions,
    loadMoreSessions,
    handleSessionListScroll,
    createNewSession,
    deleteTarget,
    setDeleteTarget,
    isActionPending,
    confirmDeleteSession,
  };
}
