"use client";

import { useState, useCallback, useRef, useEffect } from "react";
import { toast } from "@heroui/react";
import { useTranslations, useLocale} from "next-intl";
import { projectService } from "@/lib/api/services/project.service";
import { useWorkspace } from "@/components/providers/workspace-provider";
import type { ChatAttachment, MessageAttachment } from "../types";
import type { AssetType } from "@/lib/api/dto";
import type { AssetDragData } from "@/lib/stores/drag-drop-store";
import { uuid } from "@/lib/utils/uuid";
import { getErrorFromException } from "@/lib/api";

const MAX_ATTACHMENTS = 10;
const MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB
const MAX_TOTAL_SIZE = 50 * 1024 * 1024; // 50MB
const UPLOAD_CONCURRENCY = 3;

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

interface UploadTask {
  placeholderId: string;
  file: File;
}

export function useFileUpload(scriptId: string) {
  const locale = useLocale();
  const t = useTranslations("workspace.agent");
  const { currentWorkspaceId } = useWorkspace();
  const [attachments, setAttachments] = useState<ChatAttachment[]>([]);
  const attachmentsRef = useRef<ChatAttachment[]>(attachments);
  const uploadControllersRef = useRef(new Map<string, AbortController>());

  attachmentsRef.current = attachments;

  useEffect(() => {
    const controllers = uploadControllersRef.current;
    return () => {
      controllers.forEach((controller) => controller.abort());
      controllers.clear();
    };
  }, []);

  const uploadSingle = useCallback(async ({ placeholderId, file }: UploadTask) => {
    if (!currentWorkspaceId) return;

    const controller = new AbortController();
    uploadControllersRef.current.set(placeholderId, controller);

    try {
      const initResponse = await projectService.initAssetUpload({
        name: file.name,
        fileName: file.name,
        mimeType: file.type,
        fileSize: file.size,
        scope: "SCRIPT",
        scriptId,
      });

      if (controller.signal.aborted) return;

      setAttachments((prev) =>
        prev.map((attachment) =>
          attachment.id === placeholderId
            ? { ...attachment, progress: 33, errorMessage: null }
            : attachment,
        ),
      );

      const uploadResponse = await fetch(initResponse.uploadUrl, {
        method: initResponse.method,
        headers: initResponse.headers,
        body: file,
        signal: controller.signal,
      });

      if (!uploadResponse.ok) {
        throw new Error(`Upload failed with status ${uploadResponse.status}`);
      }

      if (controller.signal.aborted) return;

      setAttachments((prev) =>
        prev.map((attachment) =>
          attachment.id === placeholderId
            ? { ...attachment, progress: 66 }
            : attachment,
        ),
      );

      await projectService.confirmAssetUpload(initResponse.assetId, {
        fileKey: initResponse.fileKey,
        actualFileSize: file.size,
      });

      if (controller.signal.aborted) return;

      setAttachments((prev) =>
        prev.map((attachment) =>
          attachment.id === placeholderId
            ? {
                ...attachment,
                id: initResponse.assetId,
                url: initResponse.uploadUrl.split("?")[0],
                status: "completed" as const,
                progress: 100,
                localFile: undefined,
                errorMessage: null,
              }
            : attachment,
        ),
      );
    } catch (error) {
      if (controller.signal.aborted) return;

      console.error("File upload failed:", error);
      toast.danger(getErrorFromException(error, locale));
      setAttachments((prev) =>
        prev.map((attachment) =>
          attachment.id === placeholderId
            ? {
                ...attachment,
                status: "failed" as const,
                progress: 0,
                errorMessage: error instanceof Error ? error.message : t("uploadFailed"),
              }
            : attachment,
        ),
      );
      toast.danger(t("uploadFailed"));
    } finally {
      uploadControllersRef.current.delete(placeholderId);
    }
  }, [currentWorkspaceId, scriptId, t]);

  const runUploadQueue = useCallback((tasks: UploadTask[]) => {
    if (tasks.length === 0) return;

    let cursor = 0;
    const workerCount = Math.min(UPLOAD_CONCURRENCY, tasks.length);

    const runWorker = async () => {
      while (cursor < tasks.length) {
        const task = tasks[cursor++];
        await uploadSingle(task);
      }
    };

    void Promise.all(Array.from({ length: workerCount }, () => runWorker()));
  }, [uploadSingle]);

  const addFiles = useCallback(async (files: File[]) => {
    if (!currentWorkspaceId) return;

    const currentAttachments = attachmentsRef.current;
    const currentCount = currentAttachments.length;
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

    const currentTotalSize = currentAttachments.reduce((sum, attachment) => sum + attachment.fileSize, 0);
    const newTotalSize = validFiles.reduce((sum, file) => sum + file.size, 0);
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
      localFile: file,
      errorMessage: null,
    }));

    setAttachments((prev) => [...prev, ...placeholders]);
    runUploadQueue(placeholders.map((attachment, index) => ({
      placeholderId: attachment.id,
      file: validFiles[index],
    })));
  }, [currentWorkspaceId, runUploadQueue, t]);

  const removeAttachment = useCallback((id: string) => {
    uploadControllersRef.current.get(id)?.abort();
    uploadControllersRef.current.delete(id);
    setAttachments((prev) => prev.filter((attachment) => attachment.id !== id));
  }, []);

  const clearAttachments = useCallback(() => {
    uploadControllersRef.current.forEach((controller) => controller.abort());
    uploadControllersRef.current.clear();
    setAttachments([]);
  }, []);

  const retryAttachment = useCallback((id: string) => {
    const attachment = attachmentsRef.current.find((item) => item.id === id);
    if (!attachment?.localFile || attachment.status !== "failed") return;

    setAttachments((prev) =>
      prev.map((item) =>
        item.id === id
          ? { ...item, status: "uploading" as const, progress: 0, errorMessage: null }
          : item,
      ),
    );

    runUploadQueue([{ placeholderId: id, file: attachment.localFile }]);
  }, [runUploadQueue]);

  const getAttachmentIds = useCallback(() => {
    return attachments
      .filter((attachment) => attachment.status === "completed")
      .map((attachment) => attachment.id);
  }, [attachments]);

  const getAttachmentMetadata = useCallback((): MessageAttachment[] => {
    return attachments
      .filter((attachment) => attachment.status === "completed")
      .map((attachment) => ({
        url: attachment.url,
        fileName: attachment.name,
        mimeType: attachment.mimeType,
        mediaCategory: attachment.assetType,
      }));
  }, [attachments]);

  const addAsset = useCallback((asset: AssetDragData) => {
    const currentAttachments = attachmentsRef.current;

    if (currentAttachments.length >= MAX_ATTACHMENTS) {
      toast.danger(t("tooManyFiles", { max: MAX_ATTACHMENTS }));
      return;
    }

    if (currentAttachments.some((attachment) => attachment.id === asset.assetId)) return;

    const newAttachment: ChatAttachment = {
      id: asset.assetId,
      name: asset.name,
      mimeType: asset.mimeType,
      fileSize: asset.fileSize ?? 0,
      url: asset.url,
      assetType: asset.assetType,
      status: "completed",
      progress: 100,
      errorMessage: null,
    };
    setAttachments((prev) => [...prev, newAttachment]);
  }, [t]);

  const hasUploading = attachments.some((attachment) => attachment.status === "uploading");

  return {
    attachments,
    addFiles,
    addAsset,
    removeAttachment,
    clearAttachments,
    retryAttachment,
    getAttachmentIds,
    getAttachmentMetadata,
    hasUploading,
    acceptString: ACCEPT_STRING,
  };
}
