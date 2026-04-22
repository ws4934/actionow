"use client";

import { useState, useEffect, useRef, useCallback, useMemo } from "react";
import { useTranslations, useLocale} from "next-intl";
import { agentService, getErrorFromException} from "@/lib/api";
import type {
  SessionResponseDTO,
  MessageResponseDTO,
  StatusEventMetadata,
  StructuredDataMetadata,
  AskUserMetadata,
  UserAnswerDTO,
  HeartbeatMetadata,
  SendMessageRequestDTO,
} from "@/lib/api/dto";
import type { StreamCallbacks } from "@/lib/api/services/agent.service";
import type { RawMessage, ConversationTurn, TokenUsage, LinkedEntity, EntityCategoryKey, MessageAttachment, AskUserRuntime, TurnSegment } from "../types";
import { groupIntoTurns, isInternalTool, normalizeHistoryMetadata } from "../utils";
import { POLLING_INTERVAL_MS, MAX_POLLING_DURATION_MS } from "../constants";
import { uuid } from "@/lib/utils/uuid";
import { toast } from "@heroui/react";

export function useAgentMessages(
  scriptId: string,
  currentSession: SessionResponseDTO | null,
  onSessionsReload: () => void,
) {
  const locale = useLocale();
  const t = useTranslations("workspace.agent");
  const [rawMessages, setRawMessages] = useState<RawMessage[]>([]);
  const [inputValue, setInputValue] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [isGenerating, setIsGenerating] = useState(false);
  const [streamingTurn, setStreamingTurn] = useState<ConversationTurn | null>(null);
  const [syncError, setSyncError] = useState<string | null>(null);

  // Client-side message pagination
  const [visibleTurnCount, setVisibleTurnCount] = useState(20);

  const streamAbortRef = useRef<{ abort: () => void } | null>(null);
  // Tracks the current pending HITL ask (session-level uniqueness guaranteed by backend).
  const currentAskRef = useRef<string | null>(null);
  const pollingIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const pollingStartTimeRef = useRef<number | null>(null);
  const pollingFailureCountRef = useRef(0);

  // SSE liveness tracking (per frontend-integration.md §2.4 / §3.11)
  const lastEventIdRef = useRef<number | null>(null);
  const lastEventAtRef = useRef<number | null>(null);
  const staleWatcherRef = useRef<ReturnType<typeof setInterval> | null>(null);
  // Surfaces "server silent >15s" so UI can show a hint / trigger /state probe later
  const [isStaleGeneration, setIsStaleGeneration] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const messagesContainerRef = useRef<HTMLDivElement>(null);
  const loadRequestIdRef = useRef(0);
  const pendingStreamingTurnRef = useRef<ConversationTurn | null>(null);
  const streamingFrameRef = useRef<number | null>(null);
  // Shared reference to the active closure-local segments array inside attachStream.
  // Exposed so external handlers (e.g. markAskStateInMessages) can patch segments
  // in place — otherwise the next SSE-driven syncSegments() overwrites the patch.
  const streamingSegmentsRef = useRef<TurnSegment[] | null>(null);

  // Tracks whether we should auto-scroll to the bottom on new content.
  // Reads the user's scroll intent at event time (before render), not after.
  const stickToBottomRef = useRef(true);

  // Keep a ref to rawMessages for use in callbacks without stale closures
  const rawMessagesRef = useRef<RawMessage[]>(rawMessages);
  rawMessagesRef.current = rawMessages;

  // When sendMessageWithContent creates a brand-new session and immediately starts
  // streaming, the currentSession change would trigger loadMessages and wipe the
  // streaming turn.  This flag tells the effect to skip that one load.
  const skipNextSessionLoadRef = useRef(false);

  // Compute all conversation turns from raw messages
  const allConversationTurns = useMemo(() => {
    return groupIntoTurns(rawMessages);
  }, [rawMessages]);

  const flushStreamingTurn = useCallback(() => {
    if (streamingFrameRef.current != null) return;

    streamingFrameRef.current = requestAnimationFrame(() => {
      streamingFrameRef.current = null;
      const nextTurn = pendingStreamingTurnRef.current;
      setStreamingTurn(nextTurn ? { ...nextTurn, toolCalls: [...nextTurn.toolCalls] } : null);
    });
  }, []);

  const updateStreamingTurn = useCallback((updater: (current: ConversationTurn) => ConversationTurn) => {
    const current = pendingStreamingTurnRef.current;
    if (!current) return;
    pendingStreamingTurnRef.current = updater(current);
    flushStreamingTurn();
  }, [flushStreamingTurn]);

  // Visible turns (client-side pagination)
  const conversationTurns = useMemo(() => {
    if (allConversationTurns.length <= visibleTurnCount) {
      return allConversationTurns;
    }
    return allConversationTurns.slice(allConversationTurns.length - visibleTurnCount);
  }, [allConversationTurns, visibleTurnCount]);

  const hasMoreTurns = allConversationTurns.length > visibleTurnCount;

  const loadMoreTurns = useCallback(() => {
    setVisibleTurnCount(prev => prev + 20);
  }, []);

  // Called from the scroll container's onScroll — records intent BEFORE render.
  // This avoids the race where new content grows scrollHeight after the effect runs,
  // making the post-render distance-to-bottom appear large even though user was at bottom.
  const handleContainerScroll = useCallback(() => {
    const el = messagesContainerRef.current;
    if (!el) return;
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    stickToBottomRef.current = distanceFromBottom < 150;
  }, []);

  const forceScrollToBottom = useCallback((behavior: ScrollBehavior = "smooth") => {
    stickToBottomRef.current = true;
    messagesEndRef.current?.scrollIntoView({ behavior });
  }, []);

  // Auto-scroll: fires whenever turns or streaming content changes.
  // Uses stickToBottomRef (set by onScroll, before render) as the gate.
  useEffect(() => {
    if (!stickToBottomRef.current) return;
    if (streamingTurn) {
      messagesEndRef.current?.scrollIntoView({ behavior: "instant" });
    } else if (conversationTurns.length > 0) {
      messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
    }
  }, [conversationTurns, streamingTurn]);

  // Stop polling helper
  const stopPolling = useCallback(() => {
    if (pollingIntervalRef.current) {
      clearInterval(pollingIntervalRef.current);
      pollingIntervalRef.current = null;
    }
    pollingStartTimeRef.current = null;
  }, []);

  // Touch liveness on every SSE event (including heartbeat). Clears any stale flag.
  const markStreamAlive = useCallback((eventId?: number) => {
    lastEventAtRef.current = Date.now();
    if (typeof eventId === "number") lastEventIdRef.current = eventId;
    setIsStaleGeneration(false);
  }, []);

  const stopStaleWatcher = useCallback(() => {
    if (staleWatcherRef.current) {
      clearInterval(staleWatcherRef.current);
      staleWatcherRef.current = null;
    }
    setIsStaleGeneration(false);
  }, []);

  // Starts a 1-Hz watcher that flags the stream as stale if >15s elapse without any event.
  // Stale = no heartbeat and no other event since lastEventAt. Caller can chain a /state probe later.
  const startStaleWatcher = useCallback(() => {
    stopStaleWatcher();
    lastEventAtRef.current = Date.now();
    staleWatcherRef.current = setInterval(() => {
      const last = lastEventAtRef.current;
      if (last == null) return;
      const silentMs = Date.now() - last;
      setIsStaleGeneration(silentMs > 15_000);
    }, 1000);
  }, [stopStaleWatcher]);

  // Start polling for generating messages
  const startPolling = useCallback((sessionId: string) => {
    if (pollingIntervalRef.current) return;

    pollingStartTimeRef.current = Date.now();

    pollingIntervalRef.current = setInterval(async () => {
      if (pollingStartTimeRef.current &&
          Date.now() - pollingStartTimeRef.current > MAX_POLLING_DURATION_MS) {
        console.warn("Polling timeout reached, stopping");
        stopPolling();
        setIsGenerating(false);
        return;
      }

      try {
        const messageHistory = await agentService.getMessages(sessionId);
        pollingFailureCountRef.current = 0;
        setSyncError(null);
        const hasGenerating = messageHistory.some(
          (msg: MessageResponseDTO) => msg.status === "generating"
        );

        const sorted = [...messageHistory].sort(
          (a, b) => (a.sequence ?? 0) - (b.sequence ?? 0)
        );
        setRawMessages((sorted as RawMessage[]).map(normalizeHistoryMetadata));

        if (!hasGenerating) {
          stopPolling();
          setIsGenerating(false);
        }
      } catch (error) {
        console.error("Polling error:", error);
        toast.danger(getErrorFromException(error, locale));
        pollingFailureCountRef.current += 1;
        if (pollingFailureCountRef.current >= 2) {
          setSyncError(t("messageSyncError"));
        }
      }
    }, POLLING_INTERVAL_MS);
  }, [stopPolling, t]);

  // Cleanup on unmount: abort stream and clear timers.
  useEffect(() => {
    return () => {
      if (streamingFrameRef.current != null) {
        cancelAnimationFrame(streamingFrameRef.current);
      }
      // Abort any active stream
      streamAbortRef.current?.abort();
      // Clear polling
      if (pollingIntervalRef.current) {
        clearInterval(pollingIntervalRef.current);
        pollingIntervalRef.current = null;
      }
      if (staleWatcherRef.current) {
        clearInterval(staleWatcherRef.current);
        staleWatcherRef.current = null;
      }
    };
  }, []);

  // Ref to loadMessages so onResyncRequired can re-run recovery without closure cycles.
  const loadMessagesRef = useRef<((sessionId: string) => Promise<void>) | null>(null);

  // Parent-supplied callback may be unstable (re-created each render).  Keep it in a ref
  // so it doesn't force attachStream → loadMessages identity to churn and trigger the
  // session-change effect on every render (which would infinite-loop setRawMessages([])).
  const onSessionsReloadRef = useRef(onSessionsReload);
  onSessionsReloadRef.current = onSessionsReload;

  /**
   * Attach an SSE stream (send or resume) to the hook's streaming state.
   * Seeds a streaming turn for `userMessage`, wires accumulators, and returns abort handle.
   * On done/error/cancelled it pushes `[userMessage, ...streamingToolCalls, assistantMsg]` —
   * callers MUST ensure `userMessage` is NOT already in `rawMessages` before calling
   * (send: client-built; resume: pulled out of history first).
   */
  const attachStream = useCallback((
    session: SessionResponseDTO,
    userMessage: RawMessage,
    startingSequence: number,
    streamStarter: (callbacks: StreamCallbacks) => { abort: () => void },
    lastEventIdSeed: number | null,
    // Pre-existing tool/assistant messages for this in-flight turn, persisted before reconnect.
    // Included so the streaming turn renders them alongside new events, and they survive onDone.
    seedToolCalls?: RawMessage[],
  ) => {
    let accumulatedContent = "";
    const streamingToolCalls: RawMessage[] = seedToolCalls ? [...seedToolCalls] : [];
    let nextSequence = startingSequence;

    // Segment ledger — mirrors the visual order and drives ConversationTurnCard.
    // Mutations here require creating replacement segment objects (not in-place edits)
    // so React sees fresh references when we snapshot via syncSegments().
    const segments: TurnSegment[] = [];
    streamingSegmentsRef.current = segments;

    // Seed pre-existing tool / ask / structured messages (resume path) into segments,
    // merging consecutive tools into a single segment.
    for (const msg of streamingToolCalls) {
      if (msg.eventType === "ask_user") {
        segments.push({ kind: "ask", id: msg.id, message: msg });
      } else if (msg.eventType === "structured_data") {
        segments.push({ kind: "structured", id: msg.id, message: msg });
      } else {
        const last = segments.at(-1);
        if (last && last.kind === "tools") {
          last.messages.push(msg);
        } else {
          segments.push({ kind: "tools", id: msg.id, messages: [msg] });
        }
      }
    }

    const closeOpenStreamingSegment = () => {
      const last = segments.at(-1);
      if (!last) return;
      if (last.kind === "thinking" && !last.done) {
        segments[segments.length - 1] = { ...last, done: true };
      } else if (last.kind === "text" && !last.done) {
        segments[segments.length - 1] = { ...last, done: true };
      }
    };

    const syncSegments = () => {
      updateStreamingTurn((prev) => ({ ...prev, segments: [...segments] }));
    };

    const newTurn: ConversationTurn = {
      id: `streaming-turn-${uuid()}`,
      userMessage,
      segments: [...segments],
      toolCalls: [...streamingToolCalls],
      assistantMessage: null,
      isStreaming: true,
    };
    pendingStreamingTurnRef.current = newTurn;
    setStreamingTurn(newTurn);
    setIsGenerating(true);
    stickToBottomRef.current = true;

    lastEventIdRef.current = lastEventIdSeed;
    startStaleWatcher();

    const callbacks: StreamCallbacks = {
      onEventId: (eventId: number) => markStreamAlive(eventId),
      onHeartbeat: (eventId: number | undefined, _meta?: HeartbeatMetadata) => {
        // Liveness-only; do not add to message list per spec §3.11
        markStreamAlive(eventId);
      },
      onResyncRequired: () => {
        // Buffer gap — abort, drop streaming turn, re-run recovery through loadMessages.
        // loadMessages will re-probe /state and re-attach if still in-flight (§3.12).
        pendingStreamingTurnRef.current = null;
        setStreamingTurn(null);
        streamAbortRef.current?.abort();
        void loadMessagesRef.current?.(session.id);
      },
      onThinking: (thinkingContent: string) => {
        const last = segments.at(-1);
        if (last && last.kind === "thinking" && !last.done) {
          const updatedContent = last.content + thinkingContent;
          segments[segments.length - 1] = { ...last, content: updatedContent };
          // Mirror into the corresponding thinking RawMessage so it persists on finalize.
          const idx = streamingToolCalls.findIndex(m => m.id === last.id);
          if (idx >= 0) {
            streamingToolCalls[idx] = { ...streamingToolCalls[idx], content: updatedContent };
          }
        } else {
          closeOpenStreamingSegment();
          const thinkingMsg: RawMessage = {
            id: `thinking-${uuid()}`,
            sessionId: session.id,
            role: "assistant",
            content: thinkingContent,
            eventType: "thinking",
            sequence: nextSequence++,
            createdAt: new Date().toISOString(),
          } as RawMessage;
          streamingToolCalls.push(thinkingMsg);
          segments.push({
            kind: "thinking",
            id: thinkingMsg.id,
            content: thinkingContent,
            done: false,
          });
        }
        syncSegments();
      },
      onToolCall: (toolName: string, args: Record<string, unknown>, toolCallId: string) => {
        if (isInternalTool(toolName)) return;
        const toolCallMsg: RawMessage = {
          id: `tool-call-${toolCallId}`,
          sessionId: session.id,
          role: "tool",
          content: "",
          eventType: "tool_call",
          toolCallId,
          toolName,
          toolArguments: args,
          sequence: nextSequence++,
          createdAt: new Date().toISOString(),
        } as RawMessage;
        streamingToolCalls.push(toolCallMsg);
        const last = segments.at(-1);
        if (last && last.kind === "tools") {
          segments[segments.length - 1] = { ...last, messages: [...last.messages, toolCallMsg] };
        } else {
          closeOpenStreamingSegment();
          segments.push({ kind: "tools", id: toolCallMsg.id, messages: [toolCallMsg] });
        }
        updateStreamingTurn((prev) => ({
          ...prev,
          segments: [...segments],
          toolCalls: [...streamingToolCalls],
        }));
      },
      onToolResult: (toolName: string, result: Record<string, unknown>, toolCallId: string) => {
        if (isInternalTool(toolName)) return;
        const toolResultMsg: RawMessage = {
          id: `tool-result-${toolCallId}`,
          sessionId: session.id,
          role: "tool",
          content: "",
          eventType: "tool_result",
          toolCallId,
          toolName,
          toolResult: result,
          sequence: nextSequence++,
          createdAt: new Date().toISOString(),
        } as RawMessage;
        streamingToolCalls.push(toolResultMsg);
        const last = segments.at(-1);
        if (last && last.kind === "tools") {
          segments[segments.length - 1] = { ...last, messages: [...last.messages, toolResultMsg] };
        } else {
          closeOpenStreamingSegment();
          segments.push({ kind: "tools", id: toolResultMsg.id, messages: [toolResultMsg] });
        }
        updateStreamingTurn((prev) => ({
          ...prev,
          segments: [...segments],
          toolCalls: [...streamingToolCalls],
        }));
      },
      onMessage: (msgContent: string) => {
        accumulatedContent += msgContent;
        const last = segments.at(-1);
        if (last && last.kind === "text" && !last.done) {
          segments[segments.length - 1] = {
            ...last,
            streamingContent: (last.streamingContent ?? "") + msgContent,
          };
        } else {
          closeOpenStreamingSegment();
          segments.push({
            kind: "text",
            id: `text-${uuid()}`,
            message: null,
            streamingContent: msgContent,
            done: false,
          });
        }
        syncSegments();
      },
      onStatus: (status: StatusEventMetadata) => {
        updateStreamingTurn((prev) => ({
          ...prev,
          streamingStatus: status,
        }));
      },
      onStructuredData: (meta: StructuredDataMetadata) => {
        const sdMsg: RawMessage = {
          id: `structured-${uuid()}`,
          sessionId: session.id,
          role: "tool",
          content: "",
          eventType: "structured_data",
          sequence: nextSequence++,
          createdAt: new Date().toISOString(),
          metadata: { structuredData: meta },
        } as RawMessage;
        streamingToolCalls.push(sdMsg);
        closeOpenStreamingSegment();
        segments.push({ kind: "structured", id: sdMsg.id, message: sdMsg });
        updateStreamingTurn((prev) => ({
          ...prev,
          segments: [...segments],
          toolCalls: [...streamingToolCalls],
        }));
      },
      onAskUser: (meta: AskUserMetadata) => {
        currentAskRef.current = meta.askId;
        const runtime: AskUserRuntime = { ...meta, state: "pending" };
        const askMsg: RawMessage = {
          id: `ask-${meta.askId}`,
          sessionId: session.id,
          role: "tool",
          content: meta.question,
          eventType: "ask_user",
          sequence: nextSequence++,
          createdAt: new Date().toISOString(),
          metadata: { askUser: runtime },
        } as RawMessage;
        streamingToolCalls.push(askMsg);
        closeOpenStreamingSegment();
        segments.push({ kind: "ask", id: askMsg.id, message: askMsg });
        updateStreamingTurn((prev) => ({
          ...prev,
          segments: [...segments],
          toolCalls: [...streamingToolCalls],
        }));
      },
      onError: (error: string) => {
        stopStaleWatcher();
        closeOpenStreamingSegment();
        pendingStreamingTurnRef.current = null;
        currentAskRef.current = null;
        setRawMessages(prev => [
          ...prev,
          userMessage,
          ...streamingToolCalls,
          {
            id: uuid(),
            sessionId: session.id,
            role: "assistant" as const,
            content: error,
            status: "failed" as const,
            sequence: nextSequence++,
            createdAt: new Date().toISOString(),
          } as RawMessage,
        ]);
        setStreamingTurn(null);
        setIsGenerating(false);
        setSyncError(t("messageSyncError"));
      },
      onDone: (stats) => {
        stopStaleWatcher();
        closeOpenStreamingSegment();
        pendingStreamingTurnRef.current = null;
        currentAskRef.current = null;
        const finalContent = stats.content || accumulatedContent;

        // Thinking is now persisted as separate RawMessages (eventType="thinking"),
        // so groupIntoTurns will rebuild thinking segments from history.  We drop
        // metadata.thinking to avoid double-persistence.
        const assistantMsg: RawMessage = {
          id: uuid(),
          sessionId: session.id,
          role: "assistant",
          content: finalContent,
          status: "completed",
          sequence: nextSequence++,
          createdAt: new Date().toISOString(),
          metadata: {
            elapsedMs: stats.elapsedMs,
            totalToolCalls: stats.totalToolCalls,
            tokenUsage: stats.tokenUsage as TokenUsage | undefined,
          },
        } as RawMessage;

        setRawMessages(prev => [
          ...prev,
          userMessage,
          ...streamingToolCalls,
          assistantMsg,
        ]);

        setStreamingTurn(null);
        setIsGenerating(false);
        setSyncError(null);
        onSessionsReloadRef.current();
      },
      onCancelled: (cancelledContent: string) => {
        stopStaleWatcher();
        closeOpenStreamingSegment();
        pendingStreamingTurnRef.current = null;
        for (const m of streamingToolCalls) {
          if (m.eventType === "ask_user" && m.metadata?.askUser?.state === "pending") {
            m.metadata = {
              ...m.metadata,
              askUser: { ...m.metadata.askUser, state: "cancelled" },
            };
          }
        }
        currentAskRef.current = null;

        const assistantMsg: RawMessage = {
          id: uuid(),
          sessionId: session.id,
          role: "assistant",
          content: cancelledContent || accumulatedContent || "",
          status: "cancelled",
          sequence: nextSequence++,
          createdAt: new Date().toISOString(),
        } as RawMessage;

        setRawMessages(prev => [
          ...prev,
          userMessage,
          ...streamingToolCalls,
          assistantMsg,
        ]);

        setStreamingTurn(null);
        setIsGenerating(false);
      },
    };

    streamAbortRef.current = streamStarter(callbacks);
  }, [markStreamAlive, startStaleWatcher, stopStaleWatcher, t, updateStreamingTurn]);

  /**
   * Probe /state and attach the SSE stream if the server is still generating
   * or waiting on an ask.  Called after history loads to handle reconnect cases
   * (§2.3 + §2.4).  Idempotent: returns early if no active generation.
   */
  const attachResumeIfNeeded = useCallback(async (
    session: SessionResponseDTO,
    loadedMessages: RawMessage[],
  ) => {
    let state;
    try {
      state = await agentService.getSessionState(session.id);
    } catch (error) {
      // /state probe is best-effort — log and fall back to plain polling (already set up).
      console.warn("[agent-chat] getSessionState failed, skipping resume:", error);
      return;
    }

    if (state.resumeHint === "IDLE") return;

    // RESUME_STREAM / ANSWER_ASK: attach GET /stream to keep receiving events.
    // Find the last user message — that's the turn we're resuming.
    const lastUserMsg = [...loadedMessages].reverse().find(m => m.role === "user");
    if (!lastUserMsg) {
      console.warn("[agent-chat] resume requested but no user message in history, skipping");
      return;
    }

    // Messages already recorded AFTER the last user message are tool/assistant outputs
    // for this turn.  Pull them AND the user message out of the rendered history so the
    // streaming turn renders them as one live card (avoiding double render).
    const lastUserIdx = loadedMessages.findIndex(m => m.id === lastUserMsg.id);
    const historyBefore = loadedMessages.slice(0, lastUserIdx);
    // Tool messages persisted for the in-flight turn before reconnect — seed them into the
    // streaming turn so they stay visible and survive onDone finalization.  Assistant rows
    // (typically a generating placeholder) are skipped: onDone/onError/onCancelled will
    // create the definitive assistant message to avoid duplication.
    const inFlightSeed = loadedMessages
      .slice(lastUserIdx + 1)
      .filter(m => m.role === "tool" || m.role === "tool_call" || m.role === "tool_result");
    setRawMessages(historyBefore);

    const startingSequence = Math.max(
      lastUserMsg.sequence ?? 0,
      ...loadedMessages.map(m => m.sequence ?? 0),
    ) + 1;

    attachStream(
      session,
      lastUserMsg,
      startingSequence,
      (callbacks) => agentService.resumeMessageStream(session.id, state.lastEventId ?? null, callbacks),
      state.lastEventId ?? null,
      inFlightSeed,
    );
  }, [attachStream]);

  const loadMessages = useCallback(async (sessionId: string) => {
    const requestId = ++loadRequestIdRef.current;
    try {
      setIsLoading(true);
      setSyncError(null);
      stopPolling();
      stopStaleWatcher();
      streamAbortRef.current?.abort();
      pendingStreamingTurnRef.current = null;
      setStreamingTurn(null);
      setVisibleTurnCount(20);

      const messageHistory = await agentService.getMessages(sessionId);

      if (requestId !== loadRequestIdRef.current) {
        return;
      }

      const sorted = [...messageHistory].sort(
        (a, b) => (a.sequence ?? 0) - (b.sequence ?? 0)
      );

      // Map backend attachments into metadata for rendering
      for (const msg of sorted) {
        if (msg.attachments?.length) {
          const existing = (msg.metadata as Record<string, unknown> | undefined)?.attachments;
          if (!existing || !Array.isArray(existing) || existing.length === 0) {
            (msg as RawMessage).metadata = {
              ...(msg.metadata as Record<string, unknown>),
              attachments: msg.attachments.map((a) => ({
                url: a.url,
                fileName: a.fileName,
                mimeType: a.mimeType,
                mediaCategory: a.assetType,
              })),
            };
          }
        }
      }

      const normalized = (sorted as RawMessage[]).map(normalizeHistoryMetadata);
      setRawMessages(normalized);
      pollingFailureCountRef.current = 0;

      // Always scroll to bottom after loading a session
      stickToBottomRef.current = true;
      requestAnimationFrame(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: "instant" });
      });

      const hasGenerating = messageHistory.some(
        (msg: MessageResponseDTO) => msg.status === "generating"
      );

      // Recovery: probe /state to see whether we should attach a live SSE stream.
      // This covers the case where the user navigated away mid-generation and now
      // the backend is still producing events (§2.3).  getSessionState is authoritative;
      // the `hasGenerating` flag from /messages is only used as a fallback polling trigger.
      const session = { id: sessionId } as SessionResponseDTO;
      void attachResumeIfNeeded(session, normalized).then(() => {
        if (requestId !== loadRequestIdRef.current) return;
        // If attach didn't fire (IDLE) but /messages shows a generating placeholder,
        // fall back to polling (pre-/state behavior) so we still surface final content.
        if (!pendingStreamingTurnRef.current && hasGenerating) {
          setIsGenerating(true);
          startPolling(sessionId);
        } else if (!pendingStreamingTurnRef.current && !hasGenerating) {
          setIsGenerating(false);
        }
      });
    } catch (error) {
      if (requestId !== loadRequestIdRef.current) {
        return;
      }
      console.error("Failed to load messages:", error);
      toast.danger(getErrorFromException(error, locale));
      setSyncError(t("loadMessagesFailed"));
    } finally {
      if (requestId === loadRequestIdRef.current) {
        setIsLoading(false);
      }
    }
  }, [attachResumeIfNeeded, locale, startPolling, stopPolling, stopStaleWatcher, t]);

  // Keep the ref in sync so attachStream's onResyncRequired can re-run recovery.
  loadMessagesRef.current = loadMessages;

  // Load messages when session changes
  useEffect(() => {
    if (currentSession) {
      // Skip reload when sendMessageWithContent just created this session and is
      // about to start streaming — reloading would wipe the streaming turn.
      if (skipNextSessionLoadRef.current) {
        skipNextSessionLoadRef.current = false;
        return;
      }
      loadMessages(currentSession.id);
    } else {
      stopPolling();
      stopStaleWatcher();
      lastEventIdRef.current = null;
      lastEventAtRef.current = null;
      setSyncError(null);
      setRawMessages([]);
      pendingStreamingTurnRef.current = null;
      setStreamingTurn(null);
      setIsGenerating(false);
      setVisibleTurnCount(20);
    }
  }, [currentSession, loadMessages, stopPolling, stopStaleWatcher]);

  const sendMessageWithContent = useCallback(async (
    content: string,
    sessionToUse?: SessionResponseDTO | null,
    createSession?: () => Promise<SessionResponseDTO | null>,
    linkedEntities?: LinkedEntity[],
    attachmentIds?: string[],
    attachmentMetadata?: MessageAttachment[],
    skillNames?: string[] | null,
  ) => {
    if (!content.trim() || isGenerating) return false;

    let session = sessionToUse ?? currentSession;
    if (!session && createSession) {
      skipNextSessionLoadRef.current = true;
      session = await createSession();
      if (!session) {
        skipNextSessionLoadRef.current = false;
        return false;
      }
    }
    if (!session) return false;

    const userMessage: RawMessage = {
      id: uuid(),
      sessionId: session.id,
      role: "user",
      content: content.trim(),
      createdAt: new Date().toISOString(),
      sequence: (rawMessagesRef.current.length > 0 ? Math.max(...rawMessagesRef.current.map(m => m.sequence ?? 0)) : 0) + 1,
      ...(attachmentMetadata?.length ? { metadata: { attachments: attachmentMetadata } } : {}),
    } as RawMessage;

    setSyncError(null);
    setInputValue("");

    // Build entity payload from linked entities
    const entityPayload: Record<string, string> = {};
    linkedEntities?.forEach((entity) => {
      const fieldMap: Record<EntityCategoryKey, string> = {
        character: "characterId",
        scene: "sceneId",
        prop: "propId",
        style: "styleId",
        episode: "episodeId",
        storyboard: "storyboardId",
      };
      entityPayload[fieldMap[entity.category]] = entity.id;
    });

    const requestData: SendMessageRequestDTO = {
      message: content.trim(),
      scriptId,
      ...(attachmentIds?.length ? { attachmentIds } : {}),
      ...(skillNames !== undefined ? { skillNames } : {}),
      ...entityPayload,
    };

    attachStream(
      session,
      userMessage,
      userMessage.sequence! + 1,
      (callbacks) => agentService.sendMessageStream(session!.id, requestData, callbacks),
      null,
    );
    return true;
  }, [scriptId, currentSession, isGenerating, attachStream]);

  const retryMessage = useCallback((userMessageId: string, content: string) => {
    // Remove the failed turn's messages by user message ID
    setRawMessages(prev => {
      const userIdx = prev.findIndex(m => m.id === userMessageId);
      if (userIdx === -1) return prev;
      return prev.slice(0, userIdx);
    });
    // Re-send the same content
    sendMessageWithContent(content);
  }, [sendMessageWithContent]);

  const refreshMessages = useCallback(async () => {
    if (currentSession && !isGenerating) {
      await loadMessages(currentSession.id);
    }
  }, [currentSession, isGenerating, loadMessages]);

  const scrollToTurn = useCallback((turnId: string) => {
    const el = document.getElementById(turnId);
    if (el) {
      el.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  }, []);

  const markAskStateInMessages = useCallback((
    askId: string,
    nextState: "answered" | "cancelled" | "timeout",
    patch?: Partial<AskUserRuntime>,
  ) => {
    setRawMessages(prev => prev.map(m => {
      if (m.eventType !== "ask_user") return m;
      const ask = m.metadata?.askUser;
      if (!ask || ask.askId !== askId) return m;
      return {
        ...m,
        metadata: {
          ...m.metadata,
          askUser: { ...ask, ...patch, state: nextState },
        },
      };
    }));
    // Also reflect in the in-flight turn if still streaming
    const current = pendingStreamingTurnRef.current;
    if (current) {
      const patchMsg = (m: RawMessage): RawMessage => {
        if (m.eventType !== "ask_user") return m;
        const ask = m.metadata?.askUser;
        if (!ask || ask.askId !== askId) return m;
        return {
          ...m,
          metadata: {
            ...m.metadata,
            askUser: { ...ask, ...patch, state: nextState },
          },
        };
      };
      const updatedToolCalls = current.toolCalls.map(patchMsg);
      // Patch closure-local segments in place so the next syncSegments() won't
      // overwrite our update with a stale snapshot.
      const liveSegments = streamingSegmentsRef.current;
      if (liveSegments) {
        for (let i = 0; i < liveSegments.length; i++) {
          const seg = liveSegments[i];
          if (seg.kind !== "ask") continue;
          const ask = seg.message.metadata?.askUser;
          if (!ask || ask.askId !== askId) continue;
          liveSegments[i] = { ...seg, message: patchMsg(seg.message) };
        }
      }
      const updatedSegments = current.segments?.map((seg) => {
        if (seg.kind !== "ask") return seg;
        const ask = seg.message.metadata?.askUser;
        if (!ask || ask.askId !== askId) return seg;
        return { ...seg, message: patchMsg(seg.message) };
      });
      pendingStreamingTurnRef.current = {
        ...current,
        toolCalls: updatedToolCalls,
        segments: updatedSegments,
      };
      setStreamingTurn(pendingStreamingTurnRef.current);
    }
    if (currentAskRef.current === askId) currentAskRef.current = null;
  }, []);

  const submitAsk = useCallback(async (askId: string, answer: UserAnswerDTO) => {
    if (!currentSession) return;
    // Optimistic update
    markAskStateInMessages(askId, "answered", {
      answer,
      answeredAt: new Date().toISOString(),
    });
    try {
      await agentService.answerAsk(currentSession.id, askId, answer);
    } catch (error) {
      console.error("Failed to submit ask answer:", error);
      toast.danger(getErrorFromException(error, locale));
    }
  }, [currentSession, locale, markAskStateInMessages]);

  const dismissAsk = useCallback(async (askId: string, reason?: string) => {
    if (!currentSession) return;
    markAskStateInMessages(askId, "cancelled");
    try {
      await agentService.cancelAsk(currentSession.id, askId, reason);
    } catch (error) {
      console.error("Failed to cancel ask:", error);
      toast.danger(getErrorFromException(error, locale));
    }
  }, [currentSession, locale, markAskStateInMessages]);

  const cancelGeneration = useCallback(async () => {
    if (!currentSession || !isGenerating) return;

    try {
      streamAbortRef.current?.abort();
      stopStaleWatcher();
      pendingStreamingTurnRef.current = null;
      setStreamingTurn(null);
      setIsGenerating(false);
      // Fallback cascade: flag any pending ask as cancelled in case the cancelled SSE event is lost
      setRawMessages(prev => prev.map(m => {
        if (m.eventType !== "ask_user") return m;
        const ask = m.metadata?.askUser;
        if (!ask || ask.state !== "pending") return m;
        return {
          ...m,
          metadata: { ...m.metadata, askUser: { ...ask, state: "cancelled" } },
        };
      }));
      currentAskRef.current = null;
      const resp = await agentService.cancelGeneration(currentSession.id);
      if (resp?.cancelledAsks && resp.cancelledAsks > 0) {
        toast.info(t("cancelCascadedAsks", { count: resp.cancelledAsks }));
      }
    } catch (error) {
      console.error("Failed to cancel generation:", error);
      toast.danger(getErrorFromException(error, locale));
    }
  }, [currentSession, isGenerating, locale, stopStaleWatcher, t]);

  return {
    rawMessages,
    inputValue,
    setInputValue,
    isLoading,
    isGenerating,
    streamingTurn,
    allConversationTurns,
    conversationTurns,
    hasMoreTurns,
    loadMoreTurns,
    syncError,
    clearSyncError: () => setSyncError(null),
    messagesEndRef,
    messagesContainerRef,
    handleContainerScroll,
    sendMessageWithContent,
    retryMessage,
    refreshMessages,
    scrollToTurn,
    forceScrollToBottom,
    cancelGeneration,
    submitAsk,
    dismissAsk,
    // Liveness — consumers may render a hint if >15s silent during generation
    isStaleGeneration,
  };
}
