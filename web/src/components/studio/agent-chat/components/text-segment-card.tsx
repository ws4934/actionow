"use client";

import { useMemo, useState } from "react";
import { useTranslations, useLocale } from "next-intl";
import { Button, Tooltip } from "@heroui/react";
import {
  StopCircle,
  Copy,
  Check,
  Clock,
  Zap,
  AlignLeft,
  FileCode,
  Loader2,
  Wrench,
  RotateCcw,
} from "lucide-react";
import { MarkdownRenderer } from "./markdown-renderer";
import { StatusIndicator } from "./status-indicator";
import { AttachmentGrid } from "./user-message-bubble";
import { AssistantSegmentShell } from "./assistant-segment-shell";
import { SegmentCardShell, type SegmentPosition } from "./segment-card-shell";
import type { RawMessage, MessageMetadata } from "../types";
import type { StatusEventMetadata } from "@/lib/api/dto";
import { formatTime, formatTokens } from "../utils";

/**
 * One assistant text segment.  Per turn there may be multiple (ReAct emits a
 * text block per iteration); only the last one in a completed turn shows the
 * footer with copy / regenerate / retry actions.
 */
export function TextSegmentCard({
  message,
  streamingContent,
  streamingStatus,
  isStreaming,
  showAvatar,
  isLastInTurn,
  onRetry,
  onRegenerate,
  position,
}: {
  /** Finalized assistant message (null while still streaming the first chunk) */
  message: RawMessage | null;
  /** Live content accumulator; preferred over message.content during streaming */
  streamingContent?: string;
  streamingStatus?: StatusEventMetadata;
  isStreaming?: boolean;
  showAvatar?: boolean;
  /** Only the last text segment of a completed turn renders the footer */
  isLastInTurn?: boolean;
  onRetry?: () => void;
  onRegenerate?: () => void;
  position?: SegmentPosition;
}) {
  const t = useTranslations("workspace.agent");
  const locale = useLocale();
  const [copied, setCopied] = useState(false);
  const [showMarkdown, setShowMarkdown] = useState(true);

  const content = streamingContent ?? message?.content ?? "";
  const status = message?.status;
  const metadata = message?.metadata as MessageMetadata | undefined;

  const handleCopy = async () => {
    await navigator.clipboard.writeText(content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const bubbleClass = useMemo(() => {
    if (status === "failed") return "w-full border border-danger/30 bg-danger/10 px-4 py-2.5";
    if (status === "cancelled") return "w-full border border-warning/30 bg-warning/10 px-4 py-2.5";
    return "w-full bg-muted/10 px-4 py-2.5";
  }, [status]);

  const isCompleted =
    !isStreaming && status !== "generating" && status !== "failed" && status !== "cancelled";
  const showFooter = isLastInTurn;

  return (
    <AssistantSegmentShell showAvatar={showAvatar}>
      <SegmentCardShell bubbleClassName={bubbleClass} position={position}>
        {status === "failed" ? (
          <div className="flex items-start gap-2">
            <span className="mt-0.5 text-danger">⚠</span>
            <div>
              <p className="text-sm font-medium text-danger">{t("generationFailed")}</p>
              {content && (
                <p className="mt-1 text-sm text-danger/80">{content}</p>
              )}
            </div>
          </div>
        ) : status === "cancelled" ? (
          <div className="flex items-start gap-2">
            <StopCircle className="mt-0.5 size-4 shrink-0 text-warning" />
            <div>
              <p className="text-sm font-medium text-warning">{t("cancelled")}</p>
              {content && (
                <p className="mt-1 text-sm text-warning/80">{content}</p>
              )}
            </div>
          </div>
        ) : (isStreaming || status === "generating") ? (
          <div>
            {isStreaming && streamingStatus && (
              <StatusIndicator status={streamingStatus} />
            )}
            {content ? (
              <div>
                <MarkdownRenderer content={content} isStreaming />
                <span className="ml-1 inline-block animate-pulse">▊</span>
              </div>
            ) : streamingStatus ? null : (
              <div className="flex items-center gap-2">
                <Loader2 className="size-4 animate-spin text-muted" />
                <span className="text-sm text-muted">{t("generatingInProgress")}</span>
              </div>
            )}
          </div>
        ) : showMarkdown ? (
          <MarkdownRenderer content={content} />
        ) : (
          <p className="whitespace-pre-wrap text-sm leading-relaxed">{content}</p>
        )}

        {metadata?.attachments && metadata.attachments.length > 0 && (
          <AttachmentGrid attachments={metadata.attachments} />
        )}
      </SegmentCardShell>

      {showFooter && (
        <div className="mt-1.5 flex flex-wrap items-center gap-2">
          {message?.createdAt && !isStreaming && status !== "generating" && (
            <span className="text-xs text-muted">
              {formatTime(new Date(message.createdAt), locale)}
            </span>
          )}

          {status === "failed" && onRetry && (
            <Tooltip delay={0}>
              <Button
                variant="ghost"
                size="sm"
                className="h-6 gap-1 px-2 text-xs text-danger"
                onPress={onRetry}
              >
                <RotateCcw className="size-3" />
                {t("retryMessage")}
              </Button>
              <Tooltip.Content>{t("retryMessage")}</Tooltip.Content>
            </Tooltip>
          )}

          {isCompleted && (
            <>
              {metadata?.elapsedMs && (
                <Tooltip delay={0}>
                  <Tooltip.Trigger>
                    <span className="flex cursor-default items-center gap-1 text-xs text-muted">
                      <Clock className="size-3" />
                      {(metadata.elapsedMs / 1000).toFixed(1)}s
                    </span>
                  </Tooltip.Trigger>
                  <Tooltip.Content>{t("responseTime")}</Tooltip.Content>
                </Tooltip>
              )}

              {metadata?.totalToolCalls && metadata.totalToolCalls > 0 && (
                <Tooltip delay={0}>
                  <Tooltip.Trigger>
                    <span className="flex cursor-default items-center gap-1 text-xs text-muted">
                      <Wrench className="size-3" />
                      {metadata.totalToolCalls}
                    </span>
                  </Tooltip.Trigger>
                  <Tooltip.Content>{t("toolCallCount")}</Tooltip.Content>
                </Tooltip>
              )}

              {metadata?.tokenUsage && (
                <Tooltip delay={0}>
                  <Tooltip.Trigger>
                    <span className="flex cursor-default items-center gap-1 text-xs text-muted">
                      <Zap className="size-3" />
                      {formatTokens(metadata.tokenUsage.totalTokens)}
                    </span>
                  </Tooltip.Trigger>
                  <Tooltip.Content>
                    {t("totalTokens", { count: metadata.tokenUsage.totalTokens })}
                  </Tooltip.Content>
                </Tooltip>
              )}
            </>
          )}

          {isCompleted && content && (
            <div className="flex items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100 group-focus-within:opacity-100 has-[:focus]:opacity-100 [@media(hover:none)]:opacity-100">
              <Tooltip delay={0}>
                <Button
                  variant="ghost"
                  size="sm"
                  isIconOnly
                  className="size-6"
                  onPress={() => setShowMarkdown(!showMarkdown)}
                >
                  {showMarkdown ? <AlignLeft className="size-3" /> : <FileCode className="size-3" />}
                </Button>
                <Tooltip.Content>
                  {showMarkdown ? t("showPlainText") : t("showMarkdown")}
                </Tooltip.Content>
              </Tooltip>
              <Tooltip delay={0}>
                <Button
                  variant="ghost"
                  size="sm"
                  isIconOnly
                  className="size-6"
                  onPress={handleCopy}
                >
                  {copied ? <Check className="size-3 text-success" /> : <Copy className="size-3" />}
                </Button>
                <Tooltip.Content>{copied ? t("copied") : t("copy")}</Tooltip.Content>
              </Tooltip>
              {onRegenerate && (
                <Tooltip delay={0}>
                  <Button
                    variant="ghost"
                    size="sm"
                    isIconOnly
                    className="size-6"
                    onPress={onRegenerate}
                  >
                    <RotateCcw className="size-3" />
                  </Button>
                  <Tooltip.Content>{t("regenerate")}</Tooltip.Content>
                </Tooltip>
              )}
            </div>
          )}
        </div>
      )}
    </AssistantSegmentShell>
  );
}
