"use client";

/**
 * Cover Image Card Component
 * Image card with upload and delete functionality
 */

import { useState, useRef } from "react";
import { Button } from "@heroui/react";
import { ImagePlus, Upload, X, Loader2 } from "lucide-react";
import { useTranslations } from "next-intl";

interface CoverImageCardProps {
  coverUrl: string | null;
  onUpload: (file: File) => void;
  onDelete: () => void;
  isEditing: boolean;
  isUploading: boolean;
}

export function CoverImageCard({
  coverUrl,
  onUpload,
  onDelete,
  isEditing,
  isUploading,
}: CoverImageCardProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isDragging, setIsDragging] = useState(false);
  const t = useTranslations("workspace.studio.common");

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    if (isEditing) setIsDragging(true);
  };

  const handleDragLeave = () => {
    setIsDragging(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    if (isEditing && e.dataTransfer.files.length > 0) {
      onUpload(e.dataTransfer.files[0]);
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      onUpload(e.target.files[0]);
    }
    e.target.value = "";
  };

  return (
    <div
      className={`relative aspect-video w-full max-w-40 shrink-0 overflow-hidden rounded-xl bg-muted/10 transition-all ${
        isDragging ? "ring-2 ring-accent" : ""
      }`}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
    >
      {/* Hidden file input */}
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={handleFileChange}
      />

      {coverUrl ? (
        // Has cover image
        <div className="group relative size-full">
          <img
            src={coverUrl}
            alt="封面图"
            className="size-full object-cover"
          />
          {isEditing && (
            <div className="absolute inset-0 flex items-center justify-center gap-1.5 bg-black/60 opacity-0 transition-opacity group-hover:opacity-100">
              <Button
                variant="ghost"
                size="sm"
                isIconOnly
                className="size-8 bg-white/20 text-white hover:bg-white/30"
                onPress={() => fileInputRef.current?.click()}
                isDisabled={isUploading}
              >
                {isUploading ? <Loader2 className="size-4 animate-spin" /> : <Upload className="size-4" />}
              </Button>
              <Button
                variant="ghost"
                size="sm"
                isIconOnly
                className="size-8 bg-white/20 text-white hover:bg-white/30"
                onPress={onDelete}
                isDisabled={isUploading}
              >
                <X className="size-4" />
              </Button>
            </div>
          )}
        </div>
      ) : (
        // No cover image - empty state
        <div
          className={`flex size-full cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed transition-colors ${
            isEditing ? "border-muted/40 hover:border-accent/50 hover:bg-muted/5" : "border-transparent"
          }`}
          onClick={() => isEditing && fileInputRef.current?.click()}
        >
          {isUploading ? (
            <Loader2 className="size-6 animate-spin text-muted/50" />
          ) : (
            <ImagePlus className="size-6 text-muted/40" />
          )}
          <span className="px-2 text-center text-[10px] text-muted">
            {isUploading ? t("uploading") : isEditing ? t("uploadCover") : t("noCover")}
          </span>
        </div>
      )}
    </div>
  );
}

export default CoverImageCard;
