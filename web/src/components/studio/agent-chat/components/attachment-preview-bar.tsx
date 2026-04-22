"use client";

import NextImage from "next/image";
import { useTranslations } from "next-intl";
import { Button, Spinner, ScrollShadow, Tooltip } from "@heroui/react";
import { X, Image as ImageIcon, Video, Music, FileText, Check, AlertCircle, RotateCcw } from "lucide-react";
import type { ChatAttachment } from "../types";

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes}B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)}MB`;
}

function AttachmentIcon({ mimeType }: { mimeType: string }) {
  if (mimeType.startsWith("image/")) return <ImageIcon className="size-4 text-accent" />;
  if (mimeType.startsWith("video/")) return <Video className="size-4 text-purple-500" />;
  if (mimeType.startsWith("audio/")) return <Music className="size-4 text-green-500" />;
  return <FileText className="size-4 text-orange-500" />;
}

interface AttachmentPreviewBarProps {
  attachments: ChatAttachment[];
  onRemove: (id: string) => void;
  onRetry?: (id: string) => void;
}

export function AttachmentPreviewBar({ attachments, onRemove, onRetry }: AttachmentPreviewBarProps) {
  const t = useTranslations("workspace.agent");

  if (attachments.length === 0) return null;

  return (
    <div className="pb-2">
      <ScrollShadow orientation="horizontal" hideScrollBar>
        <div className="flex gap-2">
          {attachments.map((attachment) => (
            <div
              key={attachment.id}
              className="group/attach relative flex shrink-0 items-center gap-2 rounded-lg border border-border/50 bg-muted/5 px-2.5 py-1.5"
            >
              {/* Thumbnail or icon */}
              {attachment.mimeType.startsWith("image/") && attachment.url ? (
                <NextImage
                  src={attachment.url}
                  alt={attachment.name}
                  width={32}
                  height={32}
                  className="size-8 rounded object-cover"
                  unoptimized
                />
              ) : (
                <AttachmentIcon mimeType={attachment.mimeType} />
              )}

              {/* File info */}
              <Tooltip delay={0}>
                <Tooltip.Trigger>
                  <div className="flex flex-col">
                    <span className="max-w-24 truncate text-xs font-medium">
                      {attachment.name}
                    </span>
                    <span className="text-[10px] text-muted">
                      {formatFileSize(attachment.fileSize)}
                    </span>
                  </div>
                </Tooltip.Trigger>
                <Tooltip.Content>{attachment.name}</Tooltip.Content>
              </Tooltip>

              {/* Status indicator */}
              {attachment.status === "uploading" && (
                <Spinner size="sm" className="size-4" />
              )}
              {attachment.status === "completed" && (
                <Check className="size-3.5 text-success" />
              )}
              {attachment.status === "failed" && (
                <Tooltip delay={0}>
                  <AlertCircle className="size-3.5 text-danger" />
                  <Tooltip.Content>{attachment.errorMessage || t("uploadFailed")}</Tooltip.Content>
                </Tooltip>
              )}

              <div className="flex items-center gap-1 opacity-0 transition-opacity group-hover/attach:opacity-100">
                {attachment.status === "failed" && onRetry && (
                  <Tooltip delay={0}>
                    <Button
                      variant="ghost"
                      size="sm"
                      isIconOnly
                      className="size-5 min-w-0"
                      aria-label={t("retryUpload")}
                      onPress={() => onRetry(attachment.id)}
                    >
                      <RotateCcw className="size-3" />
                    </Button>
                    <Tooltip.Content>{t("retryUpload")}</Tooltip.Content>
                  </Tooltip>
                )}

                {/* Remove button */}
                <Button
                  variant="ghost"
                  size="sm"
                  isIconOnly
                  className="size-5 min-w-0"
                  aria-label={t("removeAttachment")}
                  onPress={() => onRemove(attachment.id)}
                >
                  <X className="size-3" />
                </Button>
              </div>
            </div>
          ))}
        </div>
      </ScrollShadow>
    </div>
  );
}
