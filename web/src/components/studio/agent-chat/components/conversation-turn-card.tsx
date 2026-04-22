"use client";

import { useTranslations } from "next-intl";
import { Loader2 } from "lucide-react";
import type { SkillResponseDTO, UserAnswerDTO } from "@/lib/api/dto";
import type { ConversationTurn, TurnSegment } from "../types";
import type { SegmentPosition } from "./segment-card-shell";
import { UserMessageBubble } from "./user-message-bubble";
import { ToolCallsMessageCard } from "./tool-calls-message-card";
import { ThinkingSegmentCard } from "./thinking-segment-card";
import { TextSegmentCard } from "./text-segment-card";
import { AskUserCard } from "./ask-user-card";
import { StructuredDataCard } from "./structured-data-card";
import { StatusIndicator } from "./status-indicator";
import { AssistantSegmentShell } from "./assistant-segment-shell";

/**
 * Render a single conversation turn as a user bubble followed by an ordered
 * list of independent assistant-side segments.  Only the first avatar-carrying
 * segment shows the avatar; subsequent segments align against the same left
 * offset via AssistantSegmentShell's reserved slot.
 */
export function ConversationTurnCard({
  turn,
  onRetry,
  toolToSkillMap,
  onSubmitAsk,
  onDismissAsk,
}: {
  turn: ConversationTurn;
  onRetry?: (userMessageId: string, content: string) => void;
  toolToSkillMap?: Map<string, SkillResponseDTO>;
  onSubmitAsk?: (askId: string, answer: UserAnswerDTO) => void | Promise<void>;
  onDismissAsk?: (askId: string, reason?: string) => void | Promise<void>;
}) {
  const t = useTranslations("workspace.agent");
  const segments = turn.segments ?? [];

  // Every kind of segment now uses AssistantSegmentShell — the first one
  // carries the avatar, the rest align to the same left offset.
  const firstAvatarIdx = segments.length > 0 ? 0 : -1;

  // Last text segment is the only one that renders the action footer.
  let lastTextIdx = -1;
  for (let i = 0; i < segments.length; i++) {
    if (segments[i].kind === "text") lastTextIdx = i;
  }

  const turnCompleted = !turn.isStreaming;

  // Pre-segment placeholder: between user send and the first SSE body event there's a
  // gap where no segment exists yet.  Without this, the assistant side is blank and the
  // live streamingStatus (onStatus-only events) has nowhere to render.
  const showPendingPlaceholder =
    turn.isStreaming &&
    segments.length === 0;

  const segCount = segments.length;
  const getPosition = (idx: number): SegmentPosition => {
    if (segCount === 1) return "only";
    if (idx === 0) return "first";
    if (idx === segCount - 1) return "last";
    return "middle";
  };

  return (
    <div className="space-y-4">
      <UserMessageBubble message={turn.userMessage} />

      <div className="flex flex-col gap-1">
        {segments.map((seg, idx) => renderSegment({
          seg,
          idx,
          showAvatar: idx === firstAvatarIdx,
          isLastText: idx === lastTextIdx,
          turn,
          turnCompleted,
          toolToSkillMap,
          onRetry,
          onSubmitAsk,
          onDismissAsk,
          position: getPosition(idx),
        }))}

        {showPendingPlaceholder && (
          <AssistantSegmentShell showAvatar>
            <div
              className="bg-muted/10 px-4 py-2.5"
              style={{ borderRadius: "0.5rem 1rem 1rem 1rem" }}
            >
              {turn.streamingStatus ? (
                <StatusIndicator status={turn.streamingStatus} />
              ) : (
                <div className="flex items-center gap-2">
                  <Loader2 className="size-4 animate-spin text-muted" />
                  <span className="text-sm text-muted">{t("generatingInProgress")}</span>
                </div>
              )}
            </div>
          </AssistantSegmentShell>
        )}
      </div>
    </div>
  );
}

function renderSegment({
  seg,
  idx,
  showAvatar,
  isLastText,
  turn,
  turnCompleted,
  toolToSkillMap,
  onRetry,
  onSubmitAsk,
  onDismissAsk,
  position,
}: {
  seg: TurnSegment;
  idx: number;
  showAvatar: boolean;
  isLastText: boolean;
  turn: ConversationTurn;
  turnCompleted: boolean;
  toolToSkillMap?: Map<string, SkillResponseDTO>;
  onRetry?: (userMessageId: string, content: string) => void;
  onSubmitAsk?: (askId: string, answer: UserAnswerDTO) => void | Promise<void>;
  onDismissAsk?: (askId: string, reason?: string) => void | Promise<void>;
  position: SegmentPosition;
}) {
  switch (seg.kind) {
    case "thinking":
      return (
        <ThinkingSegmentCard
          key={seg.id}
          content={seg.content}
          isStreaming={!seg.done}
          showAvatar={showAvatar}
          suppressPulse={!!turn.streamingStatus}
          position={position}
        />
      );
    case "text": {
      const msgStatus = seg.message?.status;
      // The last text segment in a completed turn gets the footer.  The
      // streamingStatus indicator attaches to the currently-open text segment only.
      const isOpen = !seg.done;
      const streamingStatus = isOpen ? turn.streamingStatus : undefined;
      const failed = msgStatus === "failed";
      const completed = turnCompleted && msgStatus !== "failed" && msgStatus !== "cancelled";
      return (
        <TextSegmentCard
          key={seg.id}
          message={seg.message}
          streamingContent={seg.streamingContent}
          streamingStatus={streamingStatus}
          isStreaming={isOpen}
          showAvatar={showAvatar}
          isLastInTurn={isLastText && turnCompleted}
          position={position}
          onRetry={
            isLastText && failed && onRetry
              ? () => onRetry(turn.userMessage.id, turn.userMessage.content)
              : undefined
          }
          onRegenerate={
            isLastText && completed && onRetry
              ? () => onRetry(turn.userMessage.id, turn.userMessage.content)
              : undefined
          }
        />
      );
    }
    case "tools":
      return (
        <ToolCallsMessageCard
          key={seg.id}
          toolCalls={seg.messages}
          isStreaming={turn.isStreaming}
          toolToSkillMap={toolToSkillMap}
          showAvatar={showAvatar}
          position={position}
        />
      );
    case "ask": {
      const ask = seg.message.metadata?.askUser;
      if (!ask) return null;
      return (
        <AssistantSegmentShell key={seg.id} showAvatar={showAvatar}>
          <AskUserCard
            ask={ask}
            onSubmit={(answer) => onSubmitAsk?.(ask.askId, answer)}
            onDismiss={(reason) => onDismissAsk?.(ask.askId, reason)}
            position={position}
          />
        </AssistantSegmentShell>
      );
    }
    case "structured": {
      const meta = seg.message.metadata?.structuredData;
      if (!meta) return null;
      return (
        <AssistantSegmentShell key={seg.id} showAvatar={showAvatar}>
          <StructuredDataCard meta={meta} position={position} />
        </AssistantSegmentShell>
      );
    }
    default: {
      const _exhaustive: never = seg;
      void _exhaustive;
      void idx;
      return null;
    }
  }
}
