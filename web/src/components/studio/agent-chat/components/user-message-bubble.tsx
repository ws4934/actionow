"use client";

import NextImage from "next/image";
import { useState } from "react";
import { useTranslations, useLocale } from "next-intl";
import { Button, Avatar, Tooltip } from "@heroui/react";
import { User, Copy, Check, Music, FileText, Download } from "lucide-react";
import type { RawMessage, MessageAttachment } from "../types";
import { formatTime } from "../utils";
import { MarkdownRenderer } from "./markdown-renderer";

export function AttachmentGrid({ attachments }: { attachments: MessageAttachment[] }) {
  const images = attachments.filter((a) => a.mediaCategory === "IMAGE" || a.mimeType.startsWith("image/"));
  const videos = attachments.filter((a) => a.mediaCategory === "VIDEO" || a.mimeType.startsWith("video/"));
  const audios = attachments.filter((a) => a.mediaCategory === "AUDIO" || a.mimeType.startsWith("audio/"));
  const docs = attachments.filter(
    (a) =>
      a.mediaCategory === "DOCUMENT" ||
      (!a.mimeType.startsWith("image/") && !a.mimeType.startsWith("video/") && !a.mimeType.startsWith("audio/")),
  );

  return (
    <div className="mt-2 space-y-2">
      {/* Image grid */}
      {images.length > 0 && (
        <div className="grid grid-cols-2 gap-1.5">
          {images.map((img, idx) => (
            <a key={idx} href={img.url} target="_blank" rel="noopener noreferrer">
              <NextImage
                src={img.url}
                alt={img.fileName}
                width={320}
                height={240}
                className="max-h-40 w-full rounded-lg object-cover transition-opacity hover:opacity-80"
                unoptimized
              />
            </a>
          ))}
        </div>
      )}

      {/* Videos */}
      {videos.map((vid, idx) => (
        <video key={idx} src={vid.url} controls className="max-h-48 w-full rounded-lg" />
      ))}

      {/* Audios */}
      {audios.map((aud, idx) => (
        <div key={idx} className="flex items-center gap-2 rounded-lg bg-muted/10 p-2">
          <Music className="size-4 shrink-0 text-green-500" />
          <audio src={aud.url} controls className="h-8 min-w-0 flex-1" />
        </div>
      ))}

      {/* Documents */}
      {docs.map((doc, idx) => (
        <a
          key={idx}
          href={doc.url}
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center gap-2 rounded-lg bg-muted/10 px-3 py-2 transition-colors hover:bg-muted/20"
        >
          <FileText className="size-4 shrink-0 text-orange-500" />
          <span className="min-w-0 flex-1 truncate text-xs">{doc.fileName}</span>
          <Download className="size-3.5 shrink-0 text-muted" />
        </a>
      ))}
    </div>
  );
}

export function UserMessageBubble({ message }: { message: RawMessage }) {
  const t = useTranslations("workspace.agent");
  const locale = useLocale();
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    await navigator.clipboard.writeText(message.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const attachments = (message.metadata as { attachments?: MessageAttachment[] } | undefined)?.attachments;

  return (
    <div className="group flex flex-row-reverse gap-3">
      <Avatar size="sm" color="accent" className="shrink-0">
        <Avatar.Fallback>
          <User className="size-4" />
        </Avatar.Fallback>
      </Avatar>

      <div className="flex max-w-[92%] flex-col items-end sm:max-w-[85%]">
        <div className="rounded-2xl rounded-tr-sm bg-accent/20 px-4 py-2.5 text-foreground">
          <MarkdownRenderer content={message.content} />
          {attachments && attachments.length > 0 && (
            <AttachmentGrid attachments={attachments} />
          )}
        </div>

        <div className="mt-1.5 flex flex-row-reverse items-center gap-2">
          <span className="text-xs text-muted">
            {formatTime(new Date(message.createdAt), locale)}
          </span>
          <div className="opacity-0 transition-opacity group-hover:opacity-100 group-focus-within:opacity-100 has-[:focus]:opacity-100 [@media(hover:none)]:opacity-100">
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
          </div>
        </div>
      </div>
    </div>
  );
}
