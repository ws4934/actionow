"use client";

import { useRef, useEffect } from "react";
import { useTranslations } from "next-intl";
import { Brain } from "lucide-react";
import { AssistantSegmentShell } from "./assistant-segment-shell";
import { SegmentCardShell, type SegmentPosition } from "./segment-card-shell";

/**
 * One thinking segment = one LLM iteration's reasoning block.
 * Default-collapsed for history, auto-expanded while streaming via defaultCollapsed=!isStreaming.
 */
export function ThinkingSegmentCard({
  content,
  isStreaming,
  showAvatar,
  suppressPulse,
  position,
}: {
  content: string;
  isStreaming?: boolean;
  showAvatar?: boolean;
  suppressPulse?: boolean;
  position?: SegmentPosition;
}) {
  const t = useTranslations("workspace.agent");
  const contentRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (isStreaming && contentRef.current) {
      contentRef.current.scrollTop = contentRef.current.scrollHeight;
    }
  }, [content, isStreaming]);

  if (!content) return null;

  return (
    <AssistantSegmentShell showAvatar={showAvatar}>
      <SegmentCardShell
        icon={<Brain className="size-3.5 text-violet-400" />}
        title={<span className="text-xs text-muted">{t("thinkingProcess")}</span>}
        bgClass="bg-violet-400/10"
        collapsible
        defaultCollapsed={!isStreaming}
        pulse={isStreaming && !suppressPulse}
        position={position}
      >
        <div
          ref={contentRef}
          className="max-h-60 overflow-y-auto rounded-md border border-border/20 bg-muted/5 p-2.5"
        >
          <pre className="whitespace-pre-wrap font-sans text-xs leading-relaxed text-muted">
            {content}
          </pre>
        </div>
      </SegmentCardShell>
    </AssistantSegmentShell>
  );
}
