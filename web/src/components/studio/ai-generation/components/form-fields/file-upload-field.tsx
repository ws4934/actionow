"use client";

import { useEffect, useRef, useState } from "react";
import { useTranslations, useLocale} from "next-intl";
import { Button, toast} from "@heroui/react";
import { Upload, X, Loader2, Image, Video, Music, FileText } from "lucide-react";
import type { InputParamDefinition, InputParamType } from "@/lib/api/dto/ai.dto";
import { useDragDropActions, useDragDropState, ASSET_DRAG_TYPE } from "@/lib/stores/drag-drop-store";
import { FieldLabel } from "./field-label";
import { getErrorFromException } from "@/lib/api";

interface FileUploadFieldProps {
  param: InputParamDefinition;
  value: FileValue | null;
  onChange: (value: FileValue | null) => void;
  onUpload?: (file: File) => Promise<FileValue>;
  disabled?: boolean;
  error?: string;
  compact?: boolean;
}

export interface FileValue {
  assetId: string;
  url: string;
  name: string;
  mimeType: string;
  fileSize: number;
}

// Get accepted file types based on param type
function getAcceptedTypes(type: InputParamType): string {
  switch (type) {
    case "IMAGE":
      return "image/*";
    case "VIDEO":
      return "video/*";
    case "AUDIO":
      return "audio/*";
    case "DOCUMENT":
      return ".pdf,.doc,.docx,.txt,.md";
    default:
      return "*/*";
  }
}

// Get icon based on param type
function getIcon(type: InputParamType) {
  switch (type) {
    case "IMAGE":
      return Image;
    case "VIDEO":
      return Video;
    case "AUDIO":
      return Music;
    case "DOCUMENT":
      return FileText;
    default:
      return Upload;
  }
}

// Get type label key for i18n
function getTypeLabelKey(type: InputParamType): string {
  switch (type) {
    case "IMAGE":
      return "image";
    case "VIDEO":
      return "video";
    case "AUDIO":
      return "audio";
    case "DOCUMENT":
      return "document";
    default:
      return "file";
  }
}

// Check if asset type is compatible with param type
function isAssetTypeCompatible(paramType: InputParamType, assetType: string): boolean {
  const normalizedAssetType = assetType.toUpperCase();

  switch (paramType) {
    case "IMAGE":
      return normalizedAssetType === "IMAGE";
    case "VIDEO":
      return normalizedAssetType === "VIDEO";
    case "AUDIO":
      return normalizedAssetType === "AUDIO";
    case "DOCUMENT":
      return normalizedAssetType === "DOCUMENT";
    default:
      return true; // Allow any for generic types
  }
}

export function FileUploadField({
  param,
  value,
  onChange,
  onUpload,
  disabled,
  error,
}: FileUploadFieldProps) {
  const locale = useLocale();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [isDragging, setIsDragging] = useState(false);
  const [imageError, setImageError] = useState(false);
  const { isDragging: isAssetDragging, draggedAsset } = useDragDropState();
  const { endDrag } = useDragDropActions();
  const t = useTranslations("workspace.aiGeneration.fileUpload");

  const Icon = getIcon(param.type);
  const typeLabel = t(getTypeLabelKey(param.type));
  const acceptTypes = getAcceptedTypes(param.type);

  useEffect(() => {
    setImageError(false);
  }, [value?.url]);

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !onUpload) return;

    try {
      setIsUploading(true);
      const result = await onUpload(file);
      onChange(result);
    } catch (err) {
      console.error("Failed to upload file:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsUploading(false);
      // Reset input
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    }
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (!disabled) setIsDragging(true);
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
  };

  const handleDrop = async (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);

    if (disabled) return;

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
          onChange({
            assetId: assetData.assetId || "",
            url: assetData.url,
            name: assetData.name || t("asset"),
            mimeType: assetData.mimeType || "image/*",
            fileSize: assetData.fileSize || 0,
          });
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
      onChange({
        assetId: draggedAsset.assetId,
        url: draggedAsset.url,
        name: draggedAsset.name,
        mimeType: draggedAsset.mimeType,
        fileSize: draggedAsset.fileSize || 0,
      });
      endDrag();
      return;
    }

    // File drop
    if (!onUpload) return;
    const file = e.dataTransfer.files[0];
    if (!file) return;

    try {
      setIsUploading(true);
      const result = await onUpload(file);
      onChange(result);
    } catch (err) {
      console.error("Failed to upload file:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsUploading(false);
    }
  };

  const handleRemove = () => {
    onChange(null);
  };

  // Highlight when asset is being dragged over (only for compatible types)
  const isCompatibleDrag = isAssetDragging &&
    !value &&
    draggedAsset?.assetType &&
    isAssetTypeCompatible(param.type, draggedAsset.assetType);
  const showDropHighlight = isDragging || isCompatibleDrag;

  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <FieldLabel
        label={param.label}
        required={param.required}
        description={param.description}
        containerClassName="min-w-0"
        labelClassName="truncate"
      />

      <input
        ref={fileInputRef}
        type="file"
        accept={acceptTypes}
        className="hidden"
        onChange={handleFileChange}
        disabled={disabled || isUploading}
      />

      {value ? (
        // Show uploaded file - 16:9 aspect ratio for images/videos
        <div className="group relative">
          {param.type === "IMAGE" && value.url && !imageError ? (
            <div className="relative aspect-video w-full overflow-hidden rounded-lg border border-border">
              <img
                src={value.url}
                alt={value.name}
                className="size-full object-cover"
                onError={() => setImageError(true)}
              />
              <div className="absolute inset-0 flex items-center justify-center bg-black/50 opacity-0 transition-opacity group-hover:opacity-100">
                <Button
                  variant="secondary"
                  size="sm"
                  isIconOnly
                  aria-label={t("removeFile")}
                  onPress={handleRemove}
                  isDisabled={disabled}
                >
                  <X className="size-4" />
                </Button>
              </div>
            </div>
          ) : param.type === "VIDEO" && value.url ? (
            <div className="relative aspect-video w-full overflow-hidden rounded-lg border border-border bg-black">
              <video
                src={value.url}
                className="size-full object-contain"
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
                  onPress={handleRemove}
                  isDisabled={disabled}
                >
                  <X className="size-4" />
                </Button>
              </div>
              {/* Video icon overlay */}
              <div className="absolute bottom-2 left-2 flex items-center gap-1 rounded bg-black/60 px-1.5 py-0.5 text-xs text-white">
                <Video className="size-3" />
              </div>
            </div>
          ) : (
            <div className="flex items-center gap-3 rounded-lg border border-border bg-muted/5 p-3">
              <div className="flex size-10 items-center justify-center rounded-md bg-muted/10">
                <Icon className="size-5 text-muted" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm">{value.name}</p>
                <p className="text-xs text-muted">
                  {(value.fileSize / 1024).toFixed(1)} KB
                </p>
              </div>
              <Button
                variant="ghost"
                size="sm"
                isIconOnly
                aria-label={t("removeFile")}
                onPress={handleRemove}
                isDisabled={disabled}
                className="opacity-0 transition-opacity group-hover:opacity-100"
              >
                <X className="size-4" />
              </Button>
            </div>
          )}
        </div>
      ) : (
        // Show upload area - 16:9 aspect ratio
        <div
          className={`relative aspect-video w-full cursor-pointer overflow-hidden rounded-lg border-2 border-dashed transition-colors ${
            showDropHighlight
              ? "border-accent bg-accent/5"
              : error
                ? "border-danger"
                : "border-muted/40 hover:border-accent/50 hover:bg-muted/5"
          }`}
          onClick={() => !disabled && !isUploading && fileInputRef.current?.click()}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
        >
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-1.5 px-2">
            {isUploading ? (
              <Loader2 className="size-6 animate-spin text-muted" />
            ) : showDropHighlight ? (
              <>
                <Icon className="size-6 text-accent" />
                <p className="text-center text-xs text-accent">{t("dropToAdd", { type: typeLabel })}</p>
              </>
            ) : (
              <>
                <Icon className="size-6 text-muted/60" />
                <p className="text-center text-xs text-muted">
                  {t("clickOrDrag", { type: typeLabel })}
                </p>
              </>
            )}
          </div>
        </div>
      )}

      {error && <span className="text-xs text-danger">{error}</span>}
    </div>
  );
}
