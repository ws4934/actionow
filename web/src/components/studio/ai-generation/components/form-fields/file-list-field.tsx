"use client";

import { useRef, useState, useMemo } from "react";
import { useTranslations, useLocale} from "next-intl";
import { Button, toast} from "@heroui/react";
import { Plus, X, Loader2, Image, Video, Music, FileText } from "lucide-react";
import type { InputParamDefinition, InputParamType } from "@/lib/api/dto/ai.dto";
import type { FileValue } from "./file-upload-field";
import { useDragDropActions, useDragDropState, ASSET_DRAG_TYPE } from "@/lib/stores/drag-drop-store";
import { FieldLabel } from "./field-label";
import { getErrorFromException } from "@/lib/api";

interface FileListFieldProps {
  param: InputParamDefinition;
  value: FileValue[];
  onChange: (value: FileValue[]) => void;
  onUpload?: (file: File) => Promise<FileValue>;
  disabled?: boolean;
  error?: string;
}

// Get default accepted file types based on param type
function getDefaultAcceptedTypes(type: InputParamType): string {
  switch (type) {
    case "IMAGE_LIST":
      return "image/*";
    case "VIDEO_LIST":
      return "video/*";
    case "AUDIO_LIST":
      return "audio/*";
    case "DOCUMENT_LIST":
      return ".pdf,.doc,.docx,.txt,.md";
    default:
      return "*/*";
  }
}

// Get icon based on param type
function getIcon(type: InputParamType) {
  switch (type) {
    case "IMAGE_LIST":
      return Image;
    case "VIDEO_LIST":
      return Video;
    case "AUDIO_LIST":
      return Music;
    case "DOCUMENT_LIST":
      return FileText;
    default:
      return Plus;
  }
}

// Get type label key for i18n
function getTypeLabelKey(type: InputParamType): string {
  switch (type) {
    case "IMAGE_LIST":
      return "image";
    case "VIDEO_LIST":
      return "video";
    case "AUDIO_LIST":
      return "audio";
    case "DOCUMENT_LIST":
      return "document";
    default:
      return "file";
  }
}

// Check if asset type is compatible with param type
function isAssetTypeCompatible(paramType: InputParamType, assetType: string): boolean {
  const normalizedAssetType = assetType.toUpperCase();

  switch (paramType) {
    case "IMAGE_LIST":
      return normalizedAssetType === "IMAGE";
    case "VIDEO_LIST":
      return normalizedAssetType === "VIDEO";
    case "AUDIO_LIST":
      return normalizedAssetType === "AUDIO";
    case "DOCUMENT_LIST":
      return normalizedAssetType === "DOCUMENT";
    default:
      return true; // Allow any for generic types
  }
}

// Individual file item component with local error state
function FileListItem({
  file,
  index,
  paramType,
  Icon,
  onRemove,
  disabled,
}: {
  file: FileValue;
  index: number;
  paramType: InputParamType;
  Icon: typeof Image;
  onRemove: (index: number) => void;
  disabled?: boolean;
}) {
  const [imageError, setImageError] = useState(false);
  const t = useTranslations("workspace.aiGeneration.fileUpload");

  const isImageType = paramType === "IMAGE_LIST";
  const isVideoType = paramType === "VIDEO_LIST";
  const showImage = isImageType && file.url && !imageError;
  const showVideo = isVideoType && file.url;

  return (
    <div className="group relative aspect-video overflow-hidden rounded-lg border border-border">
      {showImage ? (
        <>
          <img
            src={file.url}
            alt={file.name}
            className="size-full object-cover"
            onError={() => setImageError(true)}
          />
          <div className="absolute inset-0 flex items-center justify-center bg-black/50 opacity-0 transition-opacity group-hover:opacity-100">
            <Button
              variant="secondary"
              size="sm"
              isIconOnly
              aria-label={t("removeFile")}
              onPress={() => onRemove(index)}
              isDisabled={disabled}
            >
              <X className="size-4" />
            </Button>
          </div>
        </>
      ) : showVideo ? (
        <>
          <video
            src={file.url}
            className="size-full bg-black object-contain"
            controls={false}
            muted
            loop
            playsInline
            onMouseEnter={(e) => e.currentTarget.play()}
            onMouseLeave={(e) => {
              e.currentTarget.pause();
              e.currentTarget.currentTime = 0;
            }}
          />
          <div className="absolute inset-0 flex items-center justify-center bg-black/50 opacity-0 transition-opacity group-hover:opacity-100">
            <Button
              variant="secondary"
              size="sm"
              isIconOnly
              aria-label={t("removeFile")}
              onPress={() => onRemove(index)}
              isDisabled={disabled}
            >
              <X className="size-4" />
            </Button>
          </div>
          {/* Video icon overlay */}
          <div className="absolute bottom-1 left-1 flex items-center gap-1 rounded bg-black/60 px-1 py-0.5 text-xs text-white">
            <Video className="size-3" />
          </div>
        </>
      ) : (
        <div className="flex size-full flex-col items-center justify-center gap-1 bg-muted/5">
          <Icon className="size-6 text-muted" />
          <span className="max-w-full truncate px-2 text-xs text-muted">
            {file.name}
          </span>
          <Button
            variant="ghost"
            size="sm"
            isIconOnly
            aria-label={t("removeFile")}
            onPress={() => onRemove(index)}
            isDisabled={disabled}
            className="absolute right-1 top-1 size-6 min-w-0 opacity-0 transition-opacity group-hover:opacity-100"
          >
            <X className="size-4" />
          </Button>
        </div>
      )}
    </div>
  );
}

export function FileListField({
  param,
  value,
  onChange,
  onUpload,
  disabled,
  error,
}: FileListFieldProps) {
  const locale = useLocale();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [isDraggingOver, setIsDraggingOver] = useState(false);
  const { isDragging: isAssetDragging, draggedAsset } = useDragDropState();
  const { endDrag } = useDragDropActions();
  const t = useTranslations("workspace.aiGeneration.fileUpload");

  const Icon = getIcon(param.type);
  const typeLabel = t(getTypeLabelKey(param.type));

  // Use fileConfig if provided, otherwise use defaults
  const fileConfig = param.fileConfig;
  const acceptTypes = fileConfig?.accept || getDefaultAcceptedTypes(param.type);
  const maxFiles = fileConfig?.maxCount || 10;
  const maxSize = fileConfig?.maxSize;
  const maxSizeLabel = fileConfig?.maxSizeLabel;
  const uploadTip = fileConfig?.uploadTip;

  const canAddMore = value.length < maxFiles;

  // Build hint text
  const hintText = useMemo(() => {
    const hints: string[] = [];
    if (uploadTip) hints.push(uploadTip);
    if (maxSizeLabel) hints.push(t("maxSize", { size: maxSizeLabel }));
    return hints.join("，");
  }, [maxSizeLabel, t, uploadTip]);

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files || []);
    if (files.length === 0 || !onUpload) return;

    try {
      setIsUploading(true);
      const results: FileValue[] = [];

      for (const file of files) {
        if (value.length + results.length >= maxFiles) break;

        // Check file size if maxSize is set
        if (maxSize && file.size > maxSize) {
          console.warn(`File ${file.name} exceeds max size of ${maxSizeLabel || maxSize}`);
          continue;
        }

        const result = await onUpload(file);
        results.push(result);
      }

      onChange([...value, ...results]);
    } catch (err) {
      console.error("Failed to upload files:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsUploading(false);
      // Reset input
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    }
  };

  const handleRemove = (index: number) => {
    const newValue = value.filter((_, i) => i !== index);
    onChange(newValue);
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (!disabled && canAddMore) setIsDraggingOver(true);
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDraggingOver(false);
  };

  const handleDrop = async (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDraggingOver(false);

    if (disabled || !canAddMore) return;

    // Check for asset drag data first (from script panel)
    const assetDataStr = e.dataTransfer.getData(ASSET_DRAG_TYPE);
    if (assetDataStr) {
      try {
        const assetData = JSON.parse(assetDataStr);
        if (assetData?.url) {
          // Validate asset type compatibility
          if (assetData.assetType && !isAssetTypeCompatible(param.type, assetData.assetType)) {
            console.warn(`Asset type ${assetData.assetType} is not compatible with ${param.type}`);
            endDrag();
            return;
          }
          const newFile: FileValue = {
            assetId: assetData.assetId || "",
            url: assetData.url,
            name: assetData.name || t("asset"),
            mimeType: assetData.mimeType || "image/*",
            fileSize: assetData.fileSize || 0,
          };
          onChange([...value, newFile]);
          endDrag();
          return;
        }
      } catch {
        // Invalid JSON, continue to file drop
      }
    }

    // Check store for dragged asset (fallback)
    if (draggedAsset?.url) {
      // Validate asset type compatibility
      if (draggedAsset.assetType && !isAssetTypeCompatible(param.type, draggedAsset.assetType)) {
        console.warn(`Asset type ${draggedAsset.assetType} is not compatible with ${param.type}`);
        endDrag();
        return;
      }
      const newFile: FileValue = {
        assetId: draggedAsset.assetId,
        url: draggedAsset.url,
        name: draggedAsset.name,
        mimeType: draggedAsset.mimeType,
        fileSize: draggedAsset.fileSize || 0,
      };
      onChange([...value, newFile]);
      endDrag();
      return;
    }

    // File drop
    if (!onUpload) return;
    const files = Array.from(e.dataTransfer.files);
    if (files.length === 0) return;

    try {
      setIsUploading(true);
      const results: FileValue[] = [];

      for (const file of files) {
        if (value.length + results.length >= maxFiles) break;
        if (maxSize && file.size > maxSize) continue;

        const result = await onUpload(file);
        results.push(result);
      }

      onChange([...value, ...results]);
    } catch (err) {
      console.error("Failed to upload files:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsUploading(false);
    }
  };

  // Show drop highlight when asset is being dragged (only for compatible types)
  const isCompatibleDrag = isAssetDragging &&
    canAddMore &&
    draggedAsset?.assetType &&
    isAssetTypeCompatible(param.type, draggedAsset.assetType);
  const showDropHighlight = isDraggingOver || isCompatibleDrag;

  return (
    <div className="flex flex-col gap-1.5">
      <FieldLabel
        label={param.label}
        required={param.required}
        description={param.description}
      />

      <input
        ref={fileInputRef}
        type="file"
        accept={acceptTypes}
        multiple
        className="hidden"
        onChange={handleFileChange}
        disabled={disabled || isUploading || !canAddMore}
      />

      <div
        className="grid grid-cols-2 gap-2"
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
      >
        {/* Uploaded files */}
        {value.map((file, index) => (
          <FileListItem
            key={`${file.assetId || 'file'}-${index}`}
            file={file}
            index={index}
            paramType={param.type}
            Icon={Icon}
            onRemove={handleRemove}
            disabled={disabled}
          />
        ))}

        {/* Add button - 16:9 aspect ratio */}
        {canAddMore && (
          <button
            type="button"
            className={`flex aspect-video w-full flex-col items-center justify-center gap-1 rounded-lg border-2 border-dashed transition-colors ${
              showDropHighlight
                ? "border-accent bg-accent/5"
                : disabled || isUploading
                  ? "cursor-not-allowed border-muted/20"
                  : "cursor-pointer border-muted/40 hover:border-accent/50 hover:bg-muted/5"
            }`}
            onClick={() => !disabled && !isUploading && fileInputRef.current?.click()}
            disabled={disabled || isUploading}
          >
            {isUploading ? (
              <Loader2 className="size-6 animate-spin text-muted" />
            ) : showDropHighlight ? (
              <>
                <Plus className="size-6 text-accent" />
                <span className="text-xs text-accent">{t("dropToAddShort")}</span>
              </>
            ) : (
              <>
                <Plus className="size-6 text-muted/60" />
                <span className="text-xs text-muted">{t("addType", { type: typeLabel })}</span>
              </>
            )}
          </button>
        )}
      </div>

      <div className="flex items-center gap-2 text-xs text-muted">
        <span>{value.length}/{maxFiles}</span>
        {hintText && <span>· {hintText}</span>}
      </div>

      {error && <span className="text-xs text-danger">{error}</span>}
    </div>
  );
}
