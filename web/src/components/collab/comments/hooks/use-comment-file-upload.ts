"use client";

import { useState, useCallback } from "react";
import { toast } from "@heroui/react";
import { useTranslations, useLocale} from "next-intl";
import { projectService } from "@/lib/api/services/project.service";
import type { ChatAttachment } from "@/components/studio/agent-chat/types";
import type { CommentAttachmentDTO } from "@/lib/api/dto/comment.dto";
import type { AssetType } from "@/lib/api/dto";
import { uuid } from "@/lib/utils/uuid";
import { getErrorFromException } from "@/lib/api";

const MAX_ATTACHMENTS = 10;
const MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB
const MAX_TOTAL_SIZE = 50 * 1024 * 1024; // 50MB

const ACCEPTED_MIME_TYPES = [
  "image/*",
  "video/*",
  "audio/*",
  "application/pdf",
  "application/msword",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "text/plain",
  "text/markdown",
];

const ACCEPT_STRING = "image/*,video/*,audio/*,.pdf,.doc,.docx,.txt,.md";

function getAssetType(mimeType: string): AssetType {
  if (mimeType.startsWith("image/")) return "IMAGE";
  if (mimeType.startsWith("video/")) return "VIDEO";
  if (mimeType.startsWith("audio/")) return "AUDIO";
  return "DOCUMENT";
}

function isAcceptedMimeType(mimeType: string): boolean {
  return ACCEPTED_MIME_TYPES.some((accepted) => {
    if (accepted.endsWith("/*")) {
      return mimeType.startsWith(accepted.replace("/*", "/"));
    }
    return mimeType === accepted;
  });
}

export function useCommentFileUpload(scriptId: string) {
  const locale = useLocale();
  const t = useTranslations("workspace.agent");
  const [attachments, setAttachments] = useState<ChatAttachment[]>([]);

  const addFiles = useCallback(async (files: File[]) => {
    const currentCount = attachments.length;
    if (currentCount + files.length > MAX_ATTACHMENTS) {
      toast.danger(t("tooManyFiles", { max: MAX_ATTACHMENTS }));
      return;
    }

    const validFiles: File[] = [];
    for (const file of files) {
      if (file.size > MAX_FILE_SIZE) {
        toast.danger(t("fileTooLarge", { max: Math.round(MAX_FILE_SIZE / (1024 * 1024)) }));
        continue;
      }
      if (!isAcceptedMimeType(file.type)) {
        toast.danger(t("unsupportedFileType"));
        continue;
      }
      validFiles.push(file);
    }

    if (validFiles.length === 0) return;

    const currentTotalSize = attachments.reduce((sum, a) => sum + a.fileSize, 0);
    const newTotalSize = validFiles.reduce((sum, f) => sum + f.size, 0);
    if (currentTotalSize + newTotalSize > MAX_TOTAL_SIZE) {
      toast.danger(t("totalSizeTooLarge", { max: Math.round(MAX_TOTAL_SIZE / (1024 * 1024)) }));
      return;
    }

    const placeholders: ChatAttachment[] = validFiles.map((file) => ({
      id: uuid(),
      name: file.name,
      mimeType: file.type,
      fileSize: file.size,
      url: "",
      assetType: getAssetType(file.type),
      status: "uploading" as const,
      progress: 0,
    }));

    setAttachments((prev) => [...prev, ...placeholders]);

    for (let i = 0; i < validFiles.length; i++) {
      const file = validFiles[i];
      const placeholderId = placeholders[i].id;

      try {
        const initResponse = await projectService.initAssetUpload({
          name: file.name,
          fileName: file.name,
          mimeType: file.type,
          fileSize: file.size,
          scope: "SCRIPT",
          scriptId,
        });

        setAttachments((prev) =>
          prev.map((a) =>
            a.id === placeholderId ? { ...a, progress: 33 } : a,
          ),
        );

        await fetch(initResponse.uploadUrl, {
          method: initResponse.method,
          headers: initResponse.headers,
          body: file,
        });

        setAttachments((prev) =>
          prev.map((a) =>
            a.id === placeholderId ? { ...a, progress: 66 } : a,
          ),
        );

        await projectService.confirmAssetUpload(initResponse.assetId, {
          fileKey: initResponse.fileKey,
          actualFileSize: file.size,
        });

        setAttachments((prev) =>
          prev.map((a) =>
            a.id === placeholderId
              ? {
                  ...a,
                  id: initResponse.assetId,
                  url: initResponse.uploadUrl.split("?")[0],
                  status: "completed" as const,
                  progress: 100,
                }
              : a,
          ),
        );
      } catch (error) {
        console.error("File upload failed:", error);
        toast.danger(getErrorFromException(error, locale));
        setAttachments((prev) =>
          prev.map((a) =>
            a.id === placeholderId
              ? { ...a, status: "failed" as const, progress: 0 }
              : a,
          ),
        );
        toast.danger(t("uploadFailed"));
      }
    }
  }, [scriptId, attachments, t]);

  const removeAttachment = useCallback((id: string) => {
    setAttachments((prev) => prev.filter((a) => a.id !== id));
  }, []);

  const clearAttachments = useCallback(() => {
    setAttachments([]);
  }, []);

  /** Map completed uploads to CommentAttachmentDTO[] for the API payload */
  const toCommentAttachments = useCallback((): CommentAttachmentDTO[] => {
    return attachments
      .filter((a) => a.status === "completed")
      .map((a) => ({
        assetId: a.id,
        assetType: a.assetType as CommentAttachmentDTO["assetType"],
        fileName: a.name,
        fileUrl: a.url,
        fileSize: a.fileSize,
        mimeType: a.mimeType,
      }));
  }, [attachments]);

  const hasUploading = attachments.some((a) => a.status === "uploading");

  return {
    attachments,
    addFiles,
    removeAttachment,
    clearAttachments,
    toCommentAttachments,
    hasUploading,
    acceptString: ACCEPT_STRING,
  };
}
