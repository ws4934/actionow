"use client";

import { useState, useRef, useCallback, useEffect } from "react";
import { agentService, getErrorFromException } from "@/lib/api";
import { toast } from "@heroui/react";
import { useTranslations, useLocale } from "next-intl";
import type { AgentCatalogItemDTO, SessionResponseDTO } from "@/lib/api/dto";

const SELECTED_AGENT_STORAGE_PREFIX = "agent-chat:selected-agent:";
const SESSION_STORAGE_PREFIX = "agent-chat:current-session:";

export function useAgentSessions(scriptId: string) {
  const t = useTranslations("workspace.agent");
  const locale = useLocale();
  const [sessions, setSessions] = useState<SessionResponseDTO[]>([]);
  const [currentSession, setCurrentSession] = useState<SessionResponseDTO | null>(null);
  const [isSessionSelectorOpen, setIsSessionSelectorOpen] = useState(false);
  const [sessionPage, setSessionPage] = useState(1);
  const [sessionTotal, setSessionTotal] = useState(0);
  const [isLoadingSessions, setIsLoadingSessions] = useState(false);
  const [hasMoreSessions, setHasMoreSessions] = useState(false);
  const [availableAgents, setAvailableAgents] = useState<AgentCatalogItemDTO[]>([]);
  const [selectedAgentType, setSelectedAgentType] = useState("");
  const [isLoadingAvailableAgents, setIsLoadingAvailableAgents] = useState(false);
  const sessionListRef = useRef<HTMLDivElement>(null);
  const sessionPageRef = useRef(sessionPage);

  // Confirm modal targets
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);
  const [archiveTarget, setArchiveTarget] = useState<string | null>(null);
  const [isActionPending, setIsActionPending] = useState(false);
  const loadRequestIdRef = useRef(0);

  const storageKey = `${SELECTED_AGENT_STORAGE_PREFIX}${scriptId}`;
  const sessionStorageKey = `${SESSION_STORAGE_PREFIX}${scriptId}`;

  useEffect(() => {
    loadRequestIdRef.current += 1;
    setSessions([]);
    setCurrentSession(null);
    setSessionPage(1);
    setSessionTotal(0);
    setHasMoreSessions(false);
    setAvailableAgents([]);
    setSelectedAgentType("");
    setIsLoadingAvailableAgents(false);
    setIsSessionSelectorOpen(false);
    setDeleteTarget(null);
    setArchiveTarget(null);
  }, [scriptId]);

  useEffect(() => {
    sessionPageRef.current = sessionPage;
  }, [sessionPage]);

  useEffect(() => {
    let cancelled = false;

    const loadAvailableAgents = async () => {
      try {
        setIsLoadingAvailableAgents(true);
        const [coordinators, standaloneAgents] = await Promise.all([
          agentService.getCoordinatorAgents(),
          agentService.getStandaloneAgents(),
        ]);

        if (cancelled) return;

        const merged = [...coordinators, ...standaloneAgents];
        const deduped = Array.from(new Map(merged.map((item) => [item.agentType, item])).values());
        setAvailableAgents(deduped);
        setSelectedAgentType((prev) => {
          const persisted =
            typeof window !== "undefined" ? window.localStorage.getItem(storageKey) ?? "" : "";
          if (prev && deduped.some((item) => item.agentType === prev)) return prev;
          if (persisted && deduped.some((item) => item.agentType === persisted)) return persisted;
          return deduped[0]?.agentType ?? "";
        });
      } catch (error) {
        console.error("Failed to load available agents:", error);
        toast.danger(getErrorFromException(error, locale));
        if (!cancelled) {
          setAvailableAgents([]);
          setSelectedAgentType("");
        }
      } finally {
        if (!cancelled) setIsLoadingAvailableAgents(false);
      }
    };

    void loadAvailableAgents();

    return () => {
      cancelled = true;
    };
  }, [scriptId, storageKey]);

  useEffect(() => {
    if (typeof window === "undefined") return;
    if (!selectedAgentType) {
      window.localStorage.removeItem(storageKey);
      return;
    }
    window.localStorage.setItem(storageKey, selectedAgentType);
  }, [selectedAgentType, storageKey]);

  // Persist current session ID to localStorage
  useEffect(() => {
    if (typeof window === "undefined") return;
    if (!currentSession) {
      window.localStorage.removeItem(sessionStorageKey);
      return;
    }
    window.localStorage.setItem(sessionStorageKey, currentSession.id);
  }, [currentSession, sessionStorageKey]);

  const loadSessions = useCallback(async (reset: boolean = false, explicitPage?: number) => {
    const requestId = ++loadRequestIdRef.current;
    try {
      setIsLoadingSessions(true);
      const page = reset ? 1 : (explicitPage ?? sessionPageRef.current);

      const result = await agentService.querySessions({
        page,
        size: 20,
        scriptId,
      });

      if (requestId !== loadRequestIdRef.current) {
        return;
      }

      if (reset) {
        setSessions(result.records);
        setSessionPage(1);
        setCurrentSession((prev) => {
          // Keep current session if still in results
          if (prev) {
            const matched = result.records.find((session) => session.id === prev.id);
            if (matched) return matched;
          }
          // Restore persisted session from localStorage
          const persistedId =
            typeof window !== "undefined" ? window.localStorage.getItem(sessionStorageKey) : null;
          if (persistedId) {
            const persisted = result.records.find((session) => session.id === persistedId);
            if (persisted) return persisted;
          }
          return result.records[0] || null;
        });
      } else {
        setSessions((prev) => {
          const merged = [...prev, ...result.records];
          return Array.from(new Map(merged.map((session) => [session.id, session])).values());
        });
        setSessionPage(page);
      }

      setSessionTotal(result.total);
      setHasMoreSessions(result.page < result.pages);
    } catch (error) {
      if (requestId !== loadRequestIdRef.current) {
        return;
      }
      console.error("Failed to load sessions:", error);
      toast.danger(getErrorFromException(error, locale));
      // Prevent further scroll-triggered loads on error
      setHasMoreSessions(false);
    } finally {
      if (requestId === loadRequestIdRef.current) {
        setIsLoadingSessions(false);
      }
    }
  }, [scriptId, sessionStorageKey]);

  const loadMoreSessions = useCallback(() => {
    if (!isLoadingSessions && hasMoreSessions) {
      loadSessions(false, sessionPageRef.current + 1);
    }
  }, [isLoadingSessions, hasMoreSessions, loadSessions]);

  const handleSessionListScroll = useCallback((e: React.UIEvent<HTMLDivElement>) => {
    const target = e.currentTarget;
    const isNearBottom = target.scrollHeight - target.scrollTop - target.clientHeight < 50;
    if (isNearBottom && hasMoreSessions && !isLoadingSessions) {
      loadMoreSessions();
    }
  }, [hasMoreSessions, isLoadingSessions, loadMoreSessions]);

  const createNewSession = useCallback(async () => {
    const nextAgentType = selectedAgentType || availableAgents[0]?.agentType;
    if (!nextAgentType) {
      toast.danger(t("sessionCreateNoAgent"));
      return null;
    }

    try {
      const session = await agentService.createSession({
        agentType: nextAgentType,
        scope: "script",
        scriptId,
      });
      setSessions((prev) => [session, ...prev]);
      setCurrentSession(session);
      return session;
    } catch (error) {
      console.error("Failed to create session:", error);
      toast.danger(t("sessionCreateFailed"));
      return null;
    }
  }, [availableAgents, scriptId, selectedAgentType, t]);

  const confirmDeleteSession = useCallback(async () => {
    if (!deleteTarget) return;
    try {
      setIsActionPending(true);
      await agentService.deleteSession(deleteTarget);
      let nextSessions: SessionResponseDTO[] = [];
      setSessions((prev) => {
        nextSessions = prev.filter((session) => session.id !== deleteTarget);
        return nextSessions;
      });
      setCurrentSession((prev) => (
        prev?.id === deleteTarget ? nextSessions[0] || null : prev
      ));
    } catch (error) {
      console.error("Failed to delete session:", error);
      toast.danger(t("sessionDeleteFailed"));
    } finally {
      setIsActionPending(false);
      setDeleteTarget(null);
    }
  }, [deleteTarget, t]);

  const confirmArchiveSession = useCallback(async () => {
    if (!archiveTarget) return;
    try {
      setIsActionPending(true);
      await agentService.archiveSession(archiveTarget);
      let nextSessions: SessionResponseDTO[] = [];
      setSessions((prev) => {
        nextSessions = prev.filter((session) => session.id !== archiveTarget);
        return nextSessions;
      });
      setCurrentSession((prev) => (
        prev?.id === archiveTarget ? nextSessions[0] || null : prev
      ));
    } catch (error) {
      console.error("Failed to archive session:", error);
      toast.danger(t("sessionArchiveFailed"));
    } finally {
      setIsActionPending(false);
      setArchiveTarget(null);
    }
  }, [archiveTarget, t]);

  return {
    sessions,
    currentSession,
    setCurrentSession,
    isSessionSelectorOpen,
    setIsSessionSelectorOpen,
    sessionTotal,
    isLoadingSessions,
    hasMoreSessions,
    availableAgents,
    selectedAgentType,
    setSelectedAgentType,
    isLoadingAvailableAgents,
    sessionListRef,
    loadSessions,
    loadMoreSessions,
    handleSessionListScroll,
    createNewSession,
    // Confirm modal state
    deleteTarget,
    setDeleteTarget,
    archiveTarget,
    setArchiveTarget,
    isActionPending,
    confirmDeleteSession,
    confirmArchiveSession,
  };
}
