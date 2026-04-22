"use client";

import { useState, useCallback, useRef } from "react";
import { Modal, Button, Tabs, toast} from "@heroui/react";
import { Save, Download, X, Image as ImageIcon } from "lucide-react";
import { ImageEditor } from "./image-editor";
import { AssetBrowser } from "./asset-browser";
import type { ImageEditorAPI, ImageEditorModalProps } from "./types";
import { useLocale } from "next-intl";
import { getErrorFromException } from "@/lib/api";

export function ImageEditorModal({
  isOpen,
  onOpenChange,
  src,
  refImages = [],
  entityType,
  entityId,
  scriptId,
  workspaceId,
  onSave,
  onCancel,
  title = "编辑图片",
}: ImageEditorModalProps) {
  const locale = useLocale();
  const [editorApi, setEditorApi] = useState<ImageEditorAPI | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [canvasImages, setCanvasImages] = useState<string[]>([]);

  // Handle image drop from asset browser
  const handleImageDrop = useCallback((imageUrl: string) => {
    setCanvasImages((prev) => [...prev, imageUrl]);
    // The editor will handle adding this to the canvas
  }, []);

  // Handle save - exports only content bounds
  const handleSave = useCallback(async () => {
    if (!editorApi) return;

    setIsSaving(true);
    try {
      // Use exportContentBounds to save only the visible content area
      const dataUrl = await editorApi.exportContentBounds();
      await onSave?.(dataUrl);
      onOpenChange?.(false);
    } catch (error) {
      console.error("Failed to save image:", error);
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setIsSaving(false);
    }
  }, [editorApi, onSave, onOpenChange]);

  // Handle download
  const handleDownload = useCallback(() => {
    if (!editorApi) return;
    editorApi.download("edited-image", "png");
  }, [editorApi]);

  // Handle cancel
  const handleCancel = useCallback(() => {
    onCancel?.();
    onOpenChange?.(false);
  }, [onCancel, onOpenChange]);

  return (
    <Modal.Backdrop
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      variant="opaque"
      isDismissable={false}
    >
      <Modal.Container size="full" className="h-screen w-screen">
        <Modal.Dialog className="relative flex h-full max-h-full w-full max-w-full flex-col rounded-none">
          {/* Header */}
          <div className="flex h-14 shrink-0 items-center justify-between border-b border-border bg-surface px-4">
            <div className="flex items-center gap-3">
              <div className="flex size-8 items-center justify-center rounded-lg bg-accent/10 text-accent">
                <ImageIcon className="size-4" />
              </div>
              <h2 className="text-lg font-semibold">{title}</h2>
            </div>

            <div className="flex items-center gap-2">
              <Button
                variant="ghost"
                size="sm"
                onPress={handleDownload}
                isDisabled={!editorApi}
                className="gap-1.5"
              >
                <Download className="size-4" />
                下载
              </Button>
              <Button
                variant="primary"
                size="sm"
                onPress={handleSave}
                isDisabled={!editorApi || isSaving}
                className="gap-1.5"
              >
                <Save className="size-4" />
                {isSaving ? "保存中..." : "保存"}
              </Button>
              <div className="mx-1 h-6 w-px bg-border/50" />
              <Button
                variant="ghost"
                size="sm"
                isIconOnly
                onPress={handleCancel}
                className="size-9 rounded-full"
              >
                <X className="size-5" />
              </Button>
            </div>
          </div>

          {/* Body - Two Column Layout */}
          <div className="flex min-h-0 flex-1">
            {/* Left Panel - Asset Browser */}
            <div className="w-72 shrink-0 border-r border-border bg-surface xl:w-80">
              <AssetBrowser
                scriptId={scriptId}
                workspaceId={workspaceId}
                refImages={refImages}
                onImageSelect={handleImageDrop}
              />
            </div>

            {/* Right Panel - Image Editor */}
            <div className="min-w-[480px] flex-1 bg-muted/5">
              <ImageEditor
                src={src}
                width="100%"
                height="100%"
                initialTool="select"
                toolbar={{ position: "top" }}
                onReady={setEditorApi}
                className="size-full rounded-none border-0"
                droppedImages={canvasImages}
                onDroppedImagesChange={setCanvasImages}
              />
            </div>
          </div>
        </Modal.Dialog>
      </Modal.Container>
    </Modal.Backdrop>
  );
}

export default ImageEditorModal;
