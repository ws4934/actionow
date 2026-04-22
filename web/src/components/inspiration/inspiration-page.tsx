"use client";

import { useEffect, useCallback, useState, useRef } from "react";
import { useTranslations, useLocale } from "next-intl";
import { toast } from "@heroui/react";
import { getErrorFromException } from "@/lib/api";
import { ConfirmModal } from "@/components/studio/agent-chat/components/confirm-modal";
import { BackgroundRippleEffect } from "@/components/ui/background-ripple-effect";
import { useInspirationSessions } from "./hooks/use-inspiration-sessions";
import { useInspirationRecords } from "./hooks/use-inspiration-records";
import { useInspirationGenerate } from "./hooks/use-inspiration-generate";
import { InspirationSessionSelector } from "./inspiration-session-selector";
import { InspirationFeed } from "./inspiration-feed";
import { InspirationInputPanel } from "./inspiration-input-panel";
import { inspirationService } from "@/lib/api/services/inspiration.service";
import { useInspirationStore } from "@/lib/stores/inspiration-store";

export function InspirationPage() {
  const t = useTranslations("workspace.inspiration");
  const locale = useLocale();
  const [showScrollBtn, setShowScrollBtn] = useState(false);

  const {
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
    handleSessionListScroll,
    createNewSession,
    deleteTarget,
    setDeleteTarget,
    isActionPending,
    confirmDeleteSession,
  } = useInspirationSessions();

  const {
    records,
    isLoadingRecords,
    scrollContainerRef,
    bottomRef,
    handleScroll: handleFeedScroll,
    scrollToBottom,
  } = useInspirationRecords(currentSessionId);

  const {
    generationType,
    setGenerationType,
    providers,
    selectedProviderId,
    setSelectedProviderId,
    isLoadingProviders,
    prompt,
    setPrompt,
    formValues,
    setFormValues,
    inputSchema,
    basicParams,
    basicNonEnumParams,
    advancedParams,
    styles,
    isLoadingStyles,
    isOptimizingPrompt,
    hasGeneratedPrompt,
    optimizePrompt,
    promptProvider,
    estimatedCost,
    isGenerating,
    submitGeneration,
    canGenerate,
  } = useInspirationGenerate(currentSessionId);

  // Load sessions on mount
  useEffect(() => {
    loadSessions(true);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Handle scroll-to-bottom button visibility
  const handleFeedScrollWrapper = useCallback(() => {
    handleFeedScroll();
    const container = scrollContainerRef.current;
    if (container) {
      const isAtBottom =
        container.scrollHeight - container.scrollTop - container.clientHeight < 80;
      setShowScrollBtn(!isAtBottom && records.length > 3);
    }
  }, [handleFeedScroll, scrollContainerRef, records.length]);

  const handleTemplateClick = useCallback(
    (templatePrompt: string) => {
      setPrompt(templatePrompt);
    },
    [setPrompt]
  );

  const handleRetry = useCallback(
    async (recordId: string) => {
      // For retry, we'd re-submit the same prompt. For now, just find the record.
      const record = records.find((r) => r.id === recordId);
      if (record) {
        setPrompt(record.prompt);
      }
    },
    [records, setPrompt]
  );

  const removeRecord = useInspirationStore((s) => s.removeRecord);

  const handleDeleteRecord = useCallback(
    async (recordId: string) => {
      if (!currentSessionId) return;
      try {
        await inspirationService.deleteRecord(currentSessionId, recordId);
        removeRecord(recordId);
      } catch (error) {
        console.error("Failed to delete record:", error);
        toast.danger(getErrorFromException(error, locale));
      }
    },
    [currentSessionId, removeRecord]
  );

  return (
    <div className="relative flex h-full flex-col overflow-hidden">
      {/* Interactive grid background */}
      <BackgroundRippleEffect />

      {/* Content layers — pointer-events-none lets clicks pass through to the ripple grid;
           inner interactive elements re-enable pointer-events */}
      <div className="pointer-events-none relative z-10 flex h-full flex-col">

      {/* Top bar: Session selector */}
      <div className="pointer-events-auto flex shrink-0 items-center px-4 py-2">
        <InspirationSessionSelector
          sessions={sessions}
          currentSession={currentSession}
          isOpen={isSessionSelectorOpen}
          onOpenChange={setIsSessionSelectorOpen}
          onSelect={selectSession}
          onNew={createNewSession}
          onDelete={setDeleteTarget}
          isLoading={isLoadingSessions}
          sessionListRef={sessionListRef}
          onScroll={handleSessionListScroll}
        />
      </div>

      {/* Feed area */}
      <div className="min-h-0 flex-1 overflow-hidden">
        <InspirationFeed
          records={records}
          isLoading={isLoadingRecords}
          scrollContainerRef={scrollContainerRef}
          bottomRef={bottomRef}
          onScroll={handleFeedScrollWrapper}
          onScrollToBottom={scrollToBottom}
          showScrollToBottom={showScrollBtn}
          onRetry={handleRetry}
          onDelete={handleDeleteRecord}
          onTemplateClick={handleTemplateClick}
        />
      </div>

      {/* Input panel */}
      <div className="shrink-0 p-3">
        <InspirationInputPanel
          generationType={generationType}
          onGenerationTypeChange={setGenerationType}
          providers={providers}
          selectedProviderId={selectedProviderId}
          selectedProvider={providers.find((p) => p.id === selectedProviderId) ?? null}
          onProviderChange={setSelectedProviderId}
          isLoadingProviders={isLoadingProviders}
          prompt={prompt}
          onPromptChange={setPrompt}
          formValues={formValues}
          onFormValuesChange={setFormValues}
          inputSchema={inputSchema}
          basicParams={basicParams}
          basicNonEnumParams={basicNonEnumParams}
          advancedParams={advancedParams}
          styles={styles}
          isLoadingStyles={isLoadingStyles}
          onGenerate={submitGeneration}
          onOptimize={optimizePrompt}
          canGenerate={canGenerate}
          isGenerating={isGenerating}
          isOptimizing={isOptimizingPrompt}
          hasGeneratedPrompt={hasGeneratedPrompt}
          canOptimize={!!promptProvider}
          estimatedCost={estimatedCost}
        />
      </div>
      </div>

      {/* Delete session confirmation */}
      <ConfirmModal
        isOpen={!!deleteTarget}
        onOpenChange={(open) => { if (!open) setDeleteTarget(null); }}
        title={t("session.delete")}
        message={t("session.confirmDelete")}
        confirmLabel={t("session.delete")}
        onConfirm={confirmDeleteSession}
        isPending={isActionPending}
        variant="danger"
      />
    </div>
  );
}
