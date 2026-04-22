"use client";

import { useEffect, useCallback, useRef, useState, useMemo } from "react";
import { useTranslations, useLocale } from "next-intl";
import {
  Button,
  Card,
  Spinner,
  ScrollShadow,
  Tooltip,
  Popover,
  Input,
  TextField,
  toast,
} from "@heroui/react";
import {
  MentionPromptEditor,
  type MentionItem,
  type EntityMentionItem,
  type FileMentionItem,
  type DropdownRenderProps,
} from "@/components/ui/mention-prompt-editor";
import {
  Send,
  Plus,
  StopCircle,
  Bot,
  Sparkles,
  ChevronDown,
  Loader2,
  ChevronUp,
  RefreshCw,
  List,
  Paperclip,
  Check,
  Trash2,
  Archive,
  Zap,
  X,
} from "lucide-react";
import { useAIGeneration } from "@/components/providers/ai-generation-provider";
import { useDragDropActions, useDragDropState, ASSET_DRAG_TYPE } from "@/lib/stores/drag-drop-store";
import type { SessionResponseDTO } from "@/lib/api/dto";
import type { AgentChatProps, LinkedEntity } from "./types";
import { getQuickInputs, ENTITY_CATEGORIES } from "./constants";
import { formatDate } from "./utils";
import { useAgentSessions } from "./hooks/use-agent-sessions";
import { useAgentMessages } from "./hooks/use-agent-messages";
import { useEntityMention } from "./hooks/use-entity-mention";
import { useSkills } from "./hooks/use-skills";
import { useFileUpload } from "./hooks/use-file-upload";
import { ConversationTurnCard, ConfirmModal, MentionPopup, AttachmentPreviewBar, MissionPanel } from "./components";

export function AgentChat({ scriptId }: AgentChatProps) {
  const t = useTranslations("workspace.agent");
  const locale = useLocale();
  const { switchToAIGenerationPanel } = useAIGeneration();
  const editorContainerRef = useRef<HTMLDivElement>(null);

  const {
    sessions: sessionList,
    currentSession,
    setCurrentSession,
    isSessionSelectorOpen,
    setIsSessionSelectorOpen,
    sessionTotal,
    isLoadingSessions,
    availableAgents,
    selectedAgentType,
    setSelectedAgentType,
    isLoadingAvailableAgents,
    sessionListRef,
    loadSessions,
    handleSessionListScroll,
    createNewSession,
    deleteTarget,
    setDeleteTarget,
    archiveTarget,
    setArchiveTarget,
    isActionPending,
    confirmDeleteSession,
    confirmArchiveSession,
  } = useAgentSessions(scriptId);
  const {
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
    clearSyncError,
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
  } = useAgentMessages(
    scriptId,
    currentSession,
    () => loadSessions(true),
  );

  const quickInputs = getQuickInputs(t);
  const [isNavOpen, setIsNavOpen] = useState(false);
  const [sessionSearchQuery, setSessionSearchQuery] = useState("");
  const {
    isOpen: isMentionOpen,
    activeCategory,
    highlightIndex,
    linkedEntities,
    isLoadingEntities,
    selectEntity,
    getFilteredEntities,
    openMention,
    updateSearchFromInput,
    clearAllEntities,
    changeCategory,
    closeMention,
  } = useEntityMention(scriptId);
  const {
    attachments,
    addFiles,
    addAsset,
    removeAttachment,
    clearAttachments,
    retryAttachment,
    getAttachmentIds,
    getAttachmentMetadata,
    hasUploading,
    acceptString,
  } = useFileUpload(scriptId);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isDragOver, setIsDragOver] = useState(false);
  const { isDragging: isAssetDragging } = useDragDropState();
  const { endDrag } = useDragDropActions();

  // Skill picker — session-level multi-select (persists across messages)
  const { skills, isLoading: isLoadingSkills, workspaceSkills, systemSkills, overriddenSystemNames, toolToSkillMap } = useSkills();
  const [sessionSkills, setSessionSkills] = useState<string[]>([]);

  // Derive user message list for navigation
  const userMessageNav = useMemo(() => {
    return allConversationTurns.map((turn) => ({
      turnId: turn.id,
      content: turn.userMessage.content,
      time: turn.userMessage.createdAt,
    }));
  }, [allConversationTurns]);

  // Filter sessions by search query
  const filteredSessions = useMemo(() => {
    if (!sessionSearchQuery.trim()) return sessionList;
    const q = sessionSearchQuery.toLowerCase();
    return sessionList.filter(
      (s) => (s.title || t("defaultTitle")).toLowerCase().includes(q),
    );
  }, [sessionList, sessionSearchQuery, t]);

  const selectedAgent = useMemo(
    () => availableAgents.find((item) => item.agentType === selectedAgentType) ?? null,
    [availableAgents, selectedAgentType]
  );

  // Load sessions on mount
  useEffect(() => {
    void loadSessions(true);
  }, [scriptId, loadSessions]);

  const composerStatus = useMemo(() => {
    if (hasUploading) {
      return {
        tone: "text-warning" as const,
        text: t("sendBlockedUploading"),
      };
    }
    if (isGenerating) {
      return {
        tone: "text-accent" as const,
        text: t("draftWhileGenerating"),
      };
    }
    return {
      tone: "text-muted" as const,
      text: t("enterToSend"),
    };
  }, [hasUploading, isGenerating, t]);

  const focusComposer = useCallback(() => {
    // Focus the contentEditable editor inside the container
    const editor = editorContainerRef.current?.querySelector<HTMLDivElement>("[contenteditable]");
    editor?.focus();
  }, []);

  const closeTransientPanels = useCallback(() => {
    closeMention();
    setIsNavOpen(false);
    setIsSessionSelectorOpen(false);
  }, [closeMention, setIsNavOpen, setIsSessionSelectorOpen]);

  const announceBackgroundGeneration = useCallback(() => {
    toast(t("sessionContinuesInBackground"));
  }, [t]);

  const handleSessionSelect = useCallback((session: SessionResponseDTO) => {
    if (isGenerating && currentSession?.id && currentSession.id !== session.id) {
      announceBackgroundGeneration();
    }
    setCurrentSession(session);
    setIsSessionSelectorOpen(false);
    setSessionSearchQuery("");
    setSessionSkills([]);
  }, [
    isGenerating,
    currentSession,
    announceBackgroundGeneration,
    setCurrentSession,
    setIsSessionSelectorOpen,
    setSessionSkills,
  ]);

  const [isCreatingSession, setIsCreatingSession] = useState(false);
  const handleCreateSession = useCallback(async () => {
    if (isGenerating && currentSession) {
      announceBackgroundGeneration();
    }
    setIsCreatingSession(true);
    try {
      await createNewSession();
      setIsSessionSelectorOpen(false);
      setSessionSkills([]);
    } finally {
      setIsCreatingSession(false);
    }
  }, [
    isGenerating,
    currentSession,
    announceBackgroundGeneration,
    createNewSession,
    setIsSessionSelectorOpen,
    setSessionSkills,
  ]);

  // (Auto-resize handled by contentEditable MentionPromptEditor)

  useEffect(() => {
    const handleGlobalKeyDown = (event: KeyboardEvent) => {
      if (event.defaultPrevented) return;
      const target = event.target as HTMLElement | null;
      const isEditable = !!target && (
        target.tagName === "INPUT"
        || target.tagName === "TEXTAREA"
        || target.isContentEditable
      );

      if (event.key === "Escape") {
        closeTransientPanels();
        return;
      }

      const isMetaShortcut = (event.metaKey || event.ctrlKey) && !event.altKey;
      if (isMetaShortcut && event.key.toLowerCase() === "k") {
        event.preventDefault();
        focusComposer();
        return;
      }

      if (isMetaShortcut && event.shiftKey && event.key.toLowerCase() === "j") {
        event.preventDefault();
        setIsNavOpen(true);
        return;
      }

      if (isEditable) return;
      if (event.key === "/") {
        event.preventDefault();
        focusComposer();
      }
    };

    document.addEventListener("keydown", handleGlobalKeyDown);
    return () => document.removeEventListener("keydown", handleGlobalKeyDown);
  }, [closeTransientPanels, focusComposer]);

  const handleSendMessage = useCallback(async () => {
    if (!inputValue.trim() || hasUploading) return;
    const content = inputValue.trim();
    const attachmentIds = getAttachmentIds();
    const attachmentMetadata = getAttachmentMetadata();
    const sent = await sendMessageWithContent(
      content,
      currentSession,
      createNewSession,
      linkedEntities,
      attachmentIds.length > 0 ? attachmentIds : undefined,
      attachmentMetadata.length > 0 ? attachmentMetadata : undefined,
      sessionSkills.length > 0 ? sessionSkills : undefined,
    );
    if (!sent) return;
    clearAllEntities();
    clearAttachments();
  }, [
    inputValue,
    hasUploading,
    getAttachmentIds,
    getAttachmentMetadata,
    sendMessageWithContent,
    currentSession,
    createNewSession,
    linkedEntities,
    sessionSkills,
    clearAllEntities,
    clearAttachments,
  ]);

  const handleQuickInput = useCallback((message: string) => {
    setInputValue(message);
    focusComposer();
  }, [focusComposer, setInputValue]);

  // ── Build mention items: linked entities + attachments (for chip rendering & hover preview) ──
  const mentionItems = useMemo<MentionItem[]>(() => {
    const items: MentionItem[] = [];
    // Linked entities — already selected via @ mention
    for (const entity of linkedEntities) {
      items.push({
        kind: "entity",
        name: entity.name,
        entityId: entity.id,
        category: entity.category,
        thumbnailUrl: entity.coverUrl,
        thumbnailType: entity.coverUrl ? "image" : "icon",
        iconFallback: entity.name[0],
      } satisfies EntityMentionItem);
    }
    // Completed attachments
    let imgCount = 0;
    let vidCount = 0;
    let audCount = 0;
    for (const att of attachments) {
      if (att.status !== "completed") continue;
      let mediaKind: "IMAGE" | "VIDEO" | "AUDIO" = "IMAGE";
      let label: string;
      if (att.assetType === "VIDEO" || att.mimeType.startsWith("video/")) {
        mediaKind = "VIDEO";
        vidCount++;
        label = `视频${vidCount}`;
      } else if (att.assetType === "AUDIO" || att.mimeType.startsWith("audio/")) {
        mediaKind = "AUDIO";
        audCount++;
        label = `音频${audCount}`;
      } else {
        imgCount++;
        label = `图片${imgCount}`;
      }
      items.push({
        kind: "file",
        name: label,
        mediaKind,
        thumbnailUrl: att.url,
        thumbnailType: mediaKind === "IMAGE" ? "image" : mediaKind === "VIDEO" ? "video" : "icon",
        fileUrl: att.url,
        mimeType: att.mimeType,
        iconFallback: mediaKind === "AUDIO" ? "\u266B" : undefined,
      } satisfies FileMentionItem);
    }
    return items;
  }, [linkedEntities, attachments]);

  const mentionItemMap = useMemo(
    () => new Map(mentionItems.map((a) => [a.name, a])),
    [mentionItems],
  );

  // ── Custom dropdown for @ mentions: renders MentionPopup with entity categories ──
  const renderMentionDropdown = useCallback((props: DropdownRenderProps) => (
    <div
      style={{
        position: "fixed",
        top: props.position.top - 4,
        left: props.position.left,
        transform: "translateY(-100%)",
        zIndex: 9999,
        width: Math.min(400, window.innerWidth - 32),
      }}
    >
      <MentionPopup
        isOpen
        anchorRef={editorContainerRef as React.RefObject<HTMLTextAreaElement | null>}
        searchQuery={props.query}
        activeCategory={activeCategory}
        categories={ENTITY_CATEGORIES}
        filteredEntities={getFilteredEntities()}
        highlightIndex={highlightIndex}
        linkedEntities={linkedEntities}
        isLoading={isLoadingEntities}
        onCategoryChange={changeCategory}
        onSelect={(entity) => {
          // Track entity in linkedEntities AND let MentionPromptEditor insert chip
          selectEntity(entity);
          props.onSelect({
            kind: "entity",
            name: entity.name,
            entityId: entity.id,
            category: entity.category,
            thumbnailUrl: entity.coverUrl,
            thumbnailType: entity.coverUrl ? "image" : "icon",
            iconFallback: entity.name[0],
          });
        }}
        onClose={props.onClose}
      />
    </div>
  ), [activeCategory, getFilteredEntities, highlightIndex,
      linkedEntities, isLoadingEntities, changeCategory, selectEntity, editorContainerRef]);

  // Sync @ query to entity mention hook for filtering
  const handleMentionTrigger = useCallback((query: string) => {
    if (!isMentionOpen) {
      openMention(0);
    }
    // Update the search in the hook for entity filtering
    updateSearchFromInput(`@${query}`, query.length + 1);
  }, [isMentionOpen, openMention, updateSearchFromInput]);

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(true);
  }, []);

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(false);
  }, []);

  // Paste image from clipboard (Ctrl/Cmd+V)
  // Track whether user has scrolled away from bottom
  const [showScrollToBottom, setShowScrollToBottom] = useState(false);
  const handleMessagesScroll = useCallback(() => {
    const el = messagesContainerRef.current;
    if (!el) return;
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    setShowScrollToBottom(distanceFromBottom > 200);
    // Update stick-to-bottom intent in the hook (must happen at scroll-event time)
    handleContainerScroll();
  }, [messagesContainerRef, handleContainerScroll]);

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(false);

    // Check for asset drag data from script panel
    const assetDataStr = e.dataTransfer.getData(ASSET_DRAG_TYPE);
    if (assetDataStr) {
      try {
        const assetData = JSON.parse(assetDataStr);
        if (assetData?.assetId && assetData?.url) {
          addAsset(assetData);
          endDrag();
          return;
        }
      } catch {
        // Invalid JSON, fall through to file drop
      }
    }

    // Regular file drop
    const files = Array.from(e.dataTransfer.files);
    if (files.length > 0) {
      addFiles(files);
    }
    endDrag();
  }, [addAsset, addFiles, endDrag]);

  return (
    <Card className="relative flex h-full flex-col overflow-hidden pt-0">
      {/* Header */}
      <div className="flex min-h-14 shrink-0 flex-wrap items-center justify-between gap-2 px-1 py-2 sm:h-14 sm:flex-nowrap sm:py-0">
        {/* Session Selector */}
        <Popover
          isOpen={isSessionSelectorOpen}
          onOpenChange={(open) => {
            setIsSessionSelectorOpen(open);
            if (!open) setSessionSearchQuery("");
          }}
        >
          <Popover.Trigger>
            <Button
              variant="ghost"
              className="h-9 max-w-[min(16rem,78vw)] justify-start gap-2 px-2.5 sm:max-w-52"
            >
              <span className="relative inline-flex size-4 shrink-0 items-center justify-center">
                <Bot className="size-4 text-accent/70" />
                {(currentSession?.generating || isGenerating) && (
                  <span className="absolute -right-0.5 -top-0.5 size-1.5 animate-pulse rounded-full bg-accent" />
                )}
              </span>
              <span className="min-w-0 flex-1 truncate text-sm font-medium">
                {currentSession?.title || t("defaultTitle")}
              </span>
              <ChevronDown className={`size-3.5 shrink-0 text-muted transition-transform ${isSessionSelectorOpen ? "rotate-180" : ""}`} />
            </Button>
          </Popover.Trigger>
          <Popover.Content className="w-[min(18rem,calc(100vw-2rem))] overflow-hidden p-0 sm:w-72">
            {/* Search */}
            <div className="px-3 pt-3 pb-2">
              <TextField aria-label={t("searchSessions")}>
                <Input
                  value={sessionSearchQuery}
                  onChange={(e) => setSessionSearchQuery(e.target.value)}
                  placeholder={t("searchSessions")}
                  autoFocus
                />
              </TextField>
            </div>

            {/* Session List */}
            <ScrollShadow
              ref={sessionListRef}
              className="max-h-72"
              hideScrollBar
              onScroll={handleSessionListScroll}
            >
              {isLoadingSessions && sessionList.length === 0 ? (
                <div className="flex items-center justify-center py-10">
                  <Loader2 className="size-5 animate-spin text-muted" />
                </div>
              ) : filteredSessions.length === 0 ? (
                <div className="py-10 text-center text-sm text-muted">
                  {t("noSessions")}
                </div>
              ) : (
                <div className="px-2 pb-2">
                  {filteredSessions.map((session) => {
                    const isActive = currentSession?.id === session.id;
                    return (
                      <div
                        key={session.id}
                        className={`group/session relative flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 transition-colors cursor-pointer select-none ${
                          isActive ? "bg-accent/8 hover:bg-accent/10" : "hover:bg-muted/8"
                        }`}
                        role="button"
                        tabIndex={0}
                        onClick={() => handleSessionSelect(session)}
                        onKeyDown={(e) => {
                          if (e.key === "Enter" || e.key === " ") {
                            e.preventDefault();
                            handleSessionSelect(session);
                          }
                        }}
                      >
                        {/* Active left bar */}
                        {isActive && (
                          <div className="absolute left-0 top-1/2 h-5 w-0.5 -translate-y-1/2 rounded-full bg-accent" />
                        )}

                        {/* Icon */}
                        <div className={`flex size-8 shrink-0 items-center justify-center rounded-lg transition-colors ${
                          isActive ? "bg-accent/15 text-accent" : "bg-muted/10 text-foreground-3"
                        }`}>
                          {session.generating ? (
                            <Loader2 className="size-3.5 animate-spin" />
                          ) : (
                            <Bot className="size-3.5" />
                          )}
                        </div>

                        {/* Content */}
                        <div className="min-w-0 flex-1">
                          <div className="flex items-center gap-1.5">
                            <span className={`truncate text-xs font-medium leading-tight ${
                              isActive ? "text-foreground" : "text-foreground/80"
                            }`}>
                              {session.title || t("defaultTitle")}
                            </span>
                            {session.generating && (
                              <span className="size-1.5 shrink-0 animate-pulse rounded-full bg-accent" />
                            )}
                          </div>
                          <div className="mt-0.5 flex items-center gap-1 text-[11px] text-foreground-3">
                            <span>{t("messageCount", { count: session.messageCount })}</span>
                            {session.lastActiveAt && (
                              <>
                                <span className="opacity-50">·</span>
                                <span>{formatDate(new Date(session.lastActiveAt), locale)}</span>
                              </>
                            )}
                          </div>
                        </div>

                        {/* Right: check or action buttons */}
                        {isActive ? (
                          <Check className="shrink-0 size-3.5 text-accent" />
                        ) : (
                          <div
                            className="flex shrink-0 items-center gap-0.5 opacity-0 transition-opacity group-hover/session:opacity-100"
                            onClick={(e) => e.stopPropagation()}
                          >
                            <Tooltip delay={0}>
                              <Button
                                variant="ghost"
                                size="sm"
                                isIconOnly
                                aria-label={t("archiveSession")}
                                className="size-6 text-foreground-3 hover:text-foreground"
                                onPress={() => setArchiveTarget(session.id)}
                              >
                                <Archive className="size-3" />
                              </Button>
                              <Tooltip.Content>{t("archiveSession")}</Tooltip.Content>
                            </Tooltip>
                            <Tooltip delay={0}>
                              <Button
                                variant="ghost"
                                size="sm"
                                isIconOnly
                                aria-label={t("deleteSession")}
                                className="size-6 text-foreground-3 hover:text-danger"
                                onPress={() => setDeleteTarget(session.id)}
                              >
                                <Trash2 className="size-3" />
                              </Button>
                              <Tooltip.Content>{t("deleteSession")}</Tooltip.Content>
                            </Tooltip>
                          </div>
                        )}
                      </div>
                    );
                  })}
                  {isLoadingSessions && sessionList.length > 0 && (
                    <div className="flex items-center justify-center py-3">
                      <Loader2 className="size-4 animate-spin text-muted" />
                    </div>
                  )}
                </div>
              )}
            </ScrollShadow>

            {/* Footer */}
            <div className="flex items-center justify-between border-t border-border/40 px-3 py-2.5">
              <span className="text-[11px] text-muted">
                {t("totalSessions", { count: sessionTotal })}
              </span>
              <Button
                size="sm"
                variant="ghost"
                className="h-7 gap-1.5 px-2.5 text-xs"
                isPending={isCreatingSession}
                onPress={handleCreateSession}
              >
                {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : <Plus className="size-3.5" />}{t("newSession")}</>)}
              </Button>
            </div>
          </Popover.Content>
        </Popover>

        {/* Right: Actions */}
        <div className="ml-auto flex items-center gap-1">
          <Popover>
            <Tooltip delay={0}>
              <Popover.Trigger>
                <Button
                  variant="ghost"
                  size="sm"
                  className="h-8 max-w-44 gap-1.5 px-2.5 text-xs"
                  aria-label={t("selectAgent")}
                >
                  <Bot className="size-3.5 shrink-0" />
                  <span className="truncate">
                    {selectedAgent?.agentName || selectedAgent?.displayName || selectedAgentType || t("selectAgent")}
                  </span>
                  <ChevronDown className="size-3.5 shrink-0" />
                </Button>
              </Popover.Trigger>
              <Tooltip.Content>{t("selectedAgent")}</Tooltip.Content>
            </Tooltip>
            <Popover.Content className="w-[min(18rem,calc(100vw-2rem))] p-0 sm:w-72">
              <div className="border-b border-border/50 px-3 py-2 text-xs font-medium text-muted">
                {t("selectAgent")}
              </div>
              <ScrollShadow className="max-h-72" hideScrollBar>
                {isLoadingAvailableAgents ? (
                  <div className="flex items-center justify-center py-8 text-sm text-muted">
                    <Loader2 className="size-4 animate-spin" />
                  </div>
                ) : availableAgents.length === 0 ? (
                  <div className="py-8 text-center text-sm text-muted">
                    {t("noAvailableAgents")}
                  </div>
                ) : (
                  <div className="p-2">
                    {availableAgents.map((agent) => {
                      const selected = agent.agentType === selectedAgentType;
                      return (
                        <button
                          key={agent.agentType}
                          type="button"
                          className={`flex w-full items-start gap-2 rounded-lg px-2.5 py-2 text-left transition-colors ${
                            selected ? "bg-accent/8" : "hover:bg-muted/10"
                          }`}
                          onClick={() => setSelectedAgentType(agent.agentType)}
                        >
                          <div className={`mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-md ${
                            selected ? "bg-accent/15 text-accent" : "bg-muted/10 text-foreground-3"
                          }`}>
                            <Bot className="size-3.5" />
                          </div>
                          <div className="min-w-0 flex-1">
                            <div className="flex items-center gap-1.5">
                              <span className="truncate text-xs font-medium text-foreground">
                                {agent.agentName || agent.displayName || agent.agentType}
                              </span>
                              {selected ? <Check className="size-3 text-accent" /> : null}
                            </div>
                            <div className="mt-0.5 font-mono text-[11px] text-muted">
                              {agent.agentType}
                            </div>
                            {agent.description ? (
                              <div className="mt-0.5 line-clamp-2 text-[11px] text-muted">
                                {agent.description}
                              </div>
                            ) : null}
                          </div>
                        </button>
                      );
                    })}
                  </div>
                )}
              </ScrollShadow>
            </Popover.Content>
          </Popover>

          {/* Refresh Messages */}
          <Tooltip delay={0}>
            <Button
              variant="ghost"
              size="sm"
              isIconOnly
              aria-label={t("refreshMessages")}
              className="size-8"
              isDisabled={isLoading || isGenerating}
              onPress={async () => {
                await refreshMessages();
                clearSyncError();
              }}
            >
              <RefreshCw className={`size-4 ${isLoading ? "animate-spin" : ""}`} />
            </Button>
            <Tooltip.Content>{t("refreshMessages")}</Tooltip.Content>
          </Tooltip>

          {/* Message Navigation */}
          {userMessageNav.length > 0 && (
            <Popover isOpen={isNavOpen} onOpenChange={setIsNavOpen}>
              <Tooltip delay={0}>
                <Popover.Trigger>
                  <Button variant="ghost" size="sm" isIconOnly aria-label={t("messageNav")} className="size-8">
                    <List className="size-4" />
                  </Button>
                </Popover.Trigger>
                <Tooltip.Content>{`${t("messageNav")} · ${t("messageNavShortcut")}`}</Tooltip.Content>
              </Tooltip>
              <Popover.Content className="w-[min(16rem,calc(100vw-2rem))] p-0 sm:w-64">
                <div className="border-b border-border/50 px-3 py-2 text-xs font-medium text-muted">
                  {t("messageNav")} ({userMessageNav.length})
                </div>
                <ScrollShadow className="max-h-60" hideScrollBar>
                  <div className="p-1">
                    {userMessageNav.map((item, idx) => (
                      <button
                        key={item.turnId}
                        className="flex w-full items-start gap-2 rounded-lg px-2.5 py-2 text-left transition-colors hover:bg-muted/10"
                        onClick={() => {
                          scrollToTurn(item.turnId);
                          setIsNavOpen(false);
                        }}
                      >
                        <span className="mt-0.5 shrink-0 text-[10px] font-medium text-muted">
                          {idx + 1}
                        </span>
                        <div className="min-w-0 flex-1">
                          <span className="line-clamp-2 text-xs leading-relaxed">
                            {item.content}
                          </span>
                          {item.time && (
                            <span className="text-[10px] text-muted">
                              {formatDate(new Date(item.time), locale)}
                            </span>
                          )}
                        </div>
                      </button>
                    ))}
                  </div>
                </ScrollShadow>
              </Popover.Content>
            </Popover>
          )}

          {/* Switch to AI Generation */}
          <div className="ml-1 hidden h-5 w-px bg-border/50 sm:block" />
          <Button
            variant="ghost"
            size="sm"
            className="h-8 gap-1.5 px-2 text-xs sm:px-2.5"
            aria-label={t("aiGeneration")}
            onPress={switchToAIGenerationPanel}
          >
            <Sparkles className="size-3.5" />
            <span className="hidden sm:inline">{t("aiGeneration")}</span>
          </Button>
        </div>
      </div>

      {/*{(currentSession?.generating || isGenerating) && (*/}
      {/*  <div className="mx-2 mb-2 flex items-center gap-2 rounded-xl border border-accent/15 bg-accent/6 px-3 py-2 text-xs text-accent/90">*/}
      {/*    <Loader2 className="size-3.5 shrink-0 animate-spin" />*/}
      {/*    <span className="min-w-0 flex-1 truncate">{t("sessionGeneratingHint")}</span>*/}
      {/*    {isGenerating && (*/}
      {/*      <Button*/}
      {/*        variant="ghost"*/}
      {/*        size="sm"*/}
      {/*        className="h-6 shrink-0 gap-1 px-2 text-[11px] text-accent"*/}
      {/*        onPress={cancelGeneration}*/}
      {/*      >*/}
      {/*        <StopCircle className="size-3" />*/}
      {/*        <span className="hidden sm:inline">{t("cancel")}</span>*/}
      {/*      </Button>*/}
      {/*    )}*/}
      {/*  </div>*/}
      {/*)}*/}

      {/* Messages Area */}
      <div className="relative min-h-0 flex-1">
      {/* Active Skills Status Bar */}
      {sessionSkills.length > 0 && (
        <div className="flex items-center gap-2 border-b border-border/50 bg-surface/50 px-4 py-1.5">
          <span className="text-[10px] font-medium uppercase tracking-wider text-muted">{t("skill.activeSkills")}</span>
          <div className="flex flex-wrap items-center gap-1">
            {sessionSkills.map((name) => {
              const skill = skills.find((s) => s.name === name);
              return (
                <span
                  key={name}
                  className="inline-flex items-center gap-1 rounded-full bg-accent/8 px-2 py-0.5 text-[10px] font-medium text-accent"
                >
                  <Zap className="size-2.5" />
                  {skill?.displayName ?? name}
                  <button
                    type="button"
                    onClick={() => setSessionSkills((prev) => prev.filter((s) => s !== name))}
                    className="rounded-full p-0.5 transition-colors hover:bg-accent/15"
                  >
                    <X className="size-2" />
                  </button>
                </span>
              );
            })}
          </div>
        </div>
      )}
      <ScrollShadow
        ref={messagesContainerRef}
        className="h-full"
        hideScrollBar
        onScroll={handleMessagesScroll}
      >
        {syncError && (
          <div className="mb-3 flex items-center justify-between gap-3 rounded-xl border border-warning/30 bg-warning/10 px-3 py-2">
            <span className="text-xs text-warning">{syncError}</span>
            <Button
              variant="ghost"
              size="sm"
              className="h-7 gap-1 px-2 text-xs text-warning"
              onPress={async () => {
                await refreshMessages();
                clearSyncError();
              }}
            >
              <RefreshCw className="size-3" />
              {t("refreshMessages")}
            </Button>
          </div>
        )}
        {isLoading ? (
          <div className="flex h-full items-center justify-center">
            <Spinner size="md" />
          </div>
        ) : conversationTurns.length === 0 && !streamingTurn ? (
          <div className="flex h-full flex-col items-center justify-center px-6">
            {/* Empty State */}
            <div className="rounded-2xl bg-linear-to-br from-accent/5 to-accent/10 p-6">
              <Bot className="size-10 text-accent/60" />
            </div>
            <p className="mt-6 text-base font-medium">{t("empty")}</p>
            <p className="mt-2 max-w-sm text-center text-sm text-muted">{t("emptyHint")}</p>
            <div className="mt-4 flex flex-wrap justify-center gap-2">
              {quickInputs.slice(0, 3).map((preset, idx) => (
                <button
                  key={idx}
                  onClick={() => handleQuickInput(preset.message)}
                  className="flex items-center gap-1.5 rounded-full border border-border/60 bg-muted/5 px-3 py-1.5 text-xs text-muted transition-colors hover:border-accent/40 hover:bg-accent/5 hover:text-accent"
                >
                  <span>{preset.icon}</span>
                  <span>{preset.label}</span>
                </button>
              ))}
            </div>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Load earlier messages button */}
            {hasMoreTurns && (
              <div className="flex justify-center pt-2">
                <Button
                  variant="ghost"
                  size="sm"
                  className="gap-1.5 text-xs"
                  onPress={loadMoreTurns}
                >
                  <ChevronUp className="size-3.5" />
                  {t("loadEarlier")}
                </Button>
              </div>
            )}

            {/* Completed turns */}
            {conversationTurns.map((turn) => (
              <div key={turn.id} id={turn.id}>
                <ConversationTurnCard
                  turn={turn}
                  onRetry={retryMessage}
                  toolToSkillMap={toolToSkillMap}
                  onSubmitAsk={submitAsk}
                  onDismissAsk={dismissAsk}
                />
              </div>
            ))}

            {/* Streaming turn (current) */}
            {streamingTurn && (
              <ConversationTurnCard
                turn={streamingTurn}
                toolToSkillMap={toolToSkillMap}
                onSubmitAsk={submitAsk}
                onDismissAsk={dismissAsk}
              />
            )}

            <div ref={messagesEndRef} />
          </div>
        )}
      </ScrollShadow>

      {/* Scroll to bottom floating button */}
      {showScrollToBottom && (
        <div className="absolute bottom-2 left-1/2 z-10 -translate-x-1/2">
          <Button
            variant="secondary"
            size="sm"
            isIconOnly
            className="size-8 rounded-full shadow-md"
            aria-label={t("scrollToBottom")}
            onPress={() => forceScrollToBottom("smooth")}
          >
            <ChevronDown className="size-4" />
          </Button>
        </div>
      )}
      </div>

      {/* Mission Progress Panel */}
      {currentSession && (
        <MissionPanel sessionId={currentSession.id} />
      )}

      {/* Input Area */}
      <div
        className={`relative shrink-0 transition-colors ${isDragOver || isAssetDragging ? "rounded-lg ring-2 ring-accent ring-offset-2" : ""}`}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
      >
        {/* Quick Input Bar */}
        <div className="pb-2">
          <ScrollShadow orientation="horizontal" hideScrollBar>
            <div className="flex gap-2">
              {quickInputs.map((preset, idx) => (
                <Button
                  key={idx}
                  variant="ghost"
                  size="sm"
                  className="shrink-0 gap-1.5 text-xs"
                  onPress={() => handleQuickInput(preset.message)}
                >
                  <span className="text-accent">{preset.icon}</span>
                  <span>{preset.label}</span>
                </Button>
              ))}
            </div>
          </ScrollShadow>
        </div>

        {/* Skill Chip Bar (above input box, below quick inputs) */}
        {sessionSkills.length > 0 && (
          <div className="flex flex-wrap gap-1.5 px-3 pb-2">
            {sessionSkills.map((name) => {
              const skill = skills.find((s) => s.name === name);
              return (
                <div
                  key={name}
                  className="inline-flex h-7 items-center gap-1.5 rounded-full border border-accent/20 bg-accent/8 px-2.5 text-[11px] font-medium text-accent"
                >
                  <Zap className="size-3" />
                  <span className="max-w-32 truncate">{skill?.displayName ?? name}</span>
                  <button
                    type="button"
                    aria-label={t("skill.clear")}
                    onClick={() => setSessionSkills((prev) => prev.filter((s) => s !== name))}
                    className="rounded-full p-0.5 transition-colors hover:bg-accent/15"
                  >
                    <X className="size-3" />
                  </button>
                </div>
              );
            })}
          </div>
        )}
        {/* Attachment Preview Bar */}
        <AttachmentPreviewBar
          attachments={attachments}
          onRemove={removeAttachment}
          onRetry={retryAttachment}
        />

        {/* Hidden file input */}
        <input
          ref={fileInputRef}
          type="file"
          multiple
          accept={acceptString}
          className="hidden"
          onChange={(e) => {
            if (e.target.files) {
              addFiles(Array.from(e.target.files));
              e.target.value = "";
            }
          }}
        />

        {/* Input Box */}
        <Card ref={editorContainerRef} variant="tertiary" className="relative overflow-hidden p-0 gap-0">
          {/* Drop overlay */}
          {isDragOver && (
            <div className="absolute inset-0 z-10 flex items-center justify-center bg-accent/5">
              <div className="flex items-center gap-2 rounded-lg bg-accent/10 px-4 py-2 text-sm font-medium text-accent">
                <Paperclip className="size-4" />
                {t("dropToAttach")}
              </div>
            </div>
          )}
          <MentionPromptEditor
            value={inputValue}
            onChange={setInputValue}
            onSubmit={handleSendMessage}
            canSubmit={!isGenerating && !hasUploading && !!inputValue.trim()}
            placeholder={t("inputPlaceholder")}
            disabled={isGenerating}
            mentionItems={mentionItems}
            mentionItemMap={mentionItemMap}
            renderDropdown={renderMentionDropdown}
            onMentionTrigger={handleMentionTrigger}
            onPasteFiles={addFiles}
            style={{ minHeight: 140, maxHeight: 320 }}
          />
          <div className="flex flex-wrap items-center justify-between gap-2 bg-muted/5 px-3 py-2">
            <div className="flex min-w-0 flex-wrap items-center gap-2">
              {/* Attachment Upload Button */}
              <Tooltip delay={0}>
                <Button
                  variant="ghost"
                  size="sm"
                  isIconOnly
                  className="size-7"
                  aria-label={t("addAttachment")}
                  onPress={() => fileInputRef.current?.click()}
                >
                  <Paperclip className="size-3.5" />
                </Button>
                <Tooltip.Content>{t("addAttachment")}</Tooltip.Content>
              </Tooltip>

              {/* Skill Picker */}
              <Popover>
                <Tooltip delay={0}>
                  <Popover.Trigger>
                    <Button
                      variant="ghost"
                      size="sm"
                      isIconOnly
                      className={`size-7 ${sessionSkills.length > 0 ? "bg-accent/10 text-accent" : ""}`}
                      aria-label={t("skill.tooltip")}
                    >
                      <Zap className="size-3.5" />
                    </Button>
                  </Popover.Trigger>
                  <Tooltip.Content>{t("skill.tooltip")}</Tooltip.Content>
                </Tooltip>
                <Popover.Content className="w-56 p-0">
                  <div className="border-b border-border/50 px-3 py-2 text-xs font-medium text-muted">
                    {t("skill.label")}
                  </div>
                  <ScrollShadow className="max-h-48" hideScrollBar>
                    {isLoadingSkills ? (
                      <div className="flex items-center justify-center py-6">
                        <Loader2 className="size-4 animate-spin text-muted" />
                      </div>
                    ) : skills.length === 0 ? (
                      <p className="px-3 py-4 text-center text-xs text-muted">{t("skill.noSkills")}</p>
                    ) : (
                      <div className="p-1">
                        {sessionSkills.length > 0 && (
                          <button
                            className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-xs text-muted transition-colors hover:bg-muted/10"
                            onClick={() => setSessionSkills([])}
                          >
                            <X className="size-3.5" />
                            {t("skill.clearAll")}
                          </button>
                        )}
                        {/* WORKSPACE skills */}
                        {workspaceSkills.length > 0 && (
                          <>
                            <div className="px-2.5 pb-1 pt-2 text-[10px] font-semibold uppercase tracking-wider text-accent/70">
                              {t("skill.scopeWorkspace")}
                            </div>
                            {workspaceSkills.map((skill) => {
                              const isSelected = sessionSkills.includes(skill.name);
                              const isCustomized = overriddenSystemNames.has(skill.name);
                              return (
                                <button
                                  key={skill.name}
                                  className={`flex w-full items-start gap-2 rounded-lg px-2.5 py-2 text-left transition-colors hover:bg-muted/10 ${isSelected ? "bg-accent/8" : ""}`}
                                  onClick={() => {
                                    setSessionSkills((prev) =>
                                      isSelected ? prev.filter((s) => s !== skill.name) : [...prev, skill.name],
                                    );
                                  }}
                                >
                                  <Zap className={`mt-0.5 size-3.5 shrink-0 ${isSelected ? "text-accent" : "text-accent/50"}`} />
                                  <div className="min-w-0 flex-1">
                                    <div className="flex items-center gap-1.5">
                                      <p className={`text-xs font-medium ${isSelected ? "text-accent" : "text-foreground"}`}>
                                        {skill.displayName ?? skill.name}
                                      </p>
                                      {isCustomized && (
                                        <span className="rounded bg-accent/10 px-1 py-0.5 text-[9px] font-medium text-accent/70">
                                          {t("skill.customized")}
                                        </span>
                                      )}
                                    </div>
                                    {skill.description && (
                                      <p className="line-clamp-1 text-[10px] text-muted">{skill.description}</p>
                                    )}
                                  </div>
                                  {isSelected && (
                                    <Check className="ml-auto mt-0.5 size-3 shrink-0 text-accent" />
                                  )}
                                </button>
                              );
                            })}
                          </>
                        )}
                        {/* SYSTEM skills */}
                        {systemSkills.length > 0 && (
                          <>
                            <div className="px-2.5 pb-1 pt-2 text-[10px] font-semibold uppercase tracking-wider text-muted">
                              {t("skill.scopeSystem")}
                            </div>
                            {systemSkills.map((skill) => {
                              const isSelected = sessionSkills.includes(skill.name);
                              return (
                                <button
                                  key={skill.name}
                                  className={`flex w-full items-start gap-2 rounded-lg px-2.5 py-2 text-left transition-colors hover:bg-muted/10 ${isSelected ? "bg-accent/8" : ""}`}
                                  onClick={() => {
                                    setSessionSkills((prev) =>
                                      isSelected ? prev.filter((s) => s !== skill.name) : [...prev, skill.name],
                                    );
                                  }}
                                >
                                  <Zap className={`mt-0.5 size-3.5 shrink-0 ${isSelected ? "text-accent" : "text-muted"}`} />
                                  <div className="min-w-0">
                                    <p className={`text-xs font-medium ${isSelected ? "text-accent" : "text-foreground"}`}>
                                      {skill.displayName ?? skill.name}
                                    </p>
                                    {skill.description && (
                                      <p className="line-clamp-1 text-[10px] text-muted">{skill.description}</p>
                                    )}
                                  </div>
                                  {isSelected && (
                                    <Check className="ml-auto mt-0.5 size-3 shrink-0 text-accent" />
                                  )}
                                </button>
                              );
                            })}
                          </>
                        )}
                      </div>
                    )}
                  </ScrollShadow>
                </Popover.Content>
              </Popover>
            </div>
            <div className="flex min-w-0 items-center gap-2 sm:gap-3">
              <span className={`max-w-44 truncate text-[11px] sm:max-w-64 sm:text-xs ${composerStatus.tone}`}>
                {composerStatus.text}
              </span>
              <span className="hidden text-[11px] text-muted lg:inline">
                {t("focusComposerShortcut")}
              </span>
              {isGenerating ? (
                <Tooltip delay={0}>
                  <Button
                    variant="tertiary"
                    size="sm"
                    isIconOnly
                    aria-label={t("cancel")}
                    onPress={cancelGeneration}
                  >
                    <StopCircle className="size-4" />
                  </Button>
                  <Tooltip.Content>{t("cancelGeneration")}</Tooltip.Content>
                </Tooltip>
              ) : (
                <Tooltip delay={0}>
                  <Button
                    size="sm"
                    isIconOnly
                    aria-label={t("sendMessage")}
                    isDisabled={!inputValue.trim() || hasUploading}
                    onPress={handleSendMessage}
                  >
                    <Send className="size-4" />
                  </Button>
                  <Tooltip.Content>{hasUploading ? t("sendBlockedUploading") : t("sendMessage")}</Tooltip.Content>
                </Tooltip>
              )}
            </div>
          </div>
        </Card>
      </div>

      {/* Confirm Modals */}
      <ConfirmModal
        isOpen={!!deleteTarget}
        onOpenChange={(open) => { if (!open) setDeleteTarget(null); }}
        title={t("deleteConfirmTitle")}
        message={t("deleteConfirmMessage")}
        confirmLabel={t("deleteSession")}
        onConfirm={confirmDeleteSession}
        isPending={isActionPending}
        variant="danger"
      />
      <ConfirmModal
        isOpen={!!archiveTarget}
        onOpenChange={(open) => { if (!open) setArchiveTarget(null); }}
        title={t("archiveConfirmTitle")}
        message={t("archiveConfirmMessage")}
        confirmLabel={t("archiveSession")}
        onConfirm={confirmArchiveSession}
        isPending={isActionPending}
      />
    </Card>
  );
}
