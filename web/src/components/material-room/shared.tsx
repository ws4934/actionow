"use client";

import { useState, useRef, type ReactNode } from "react";
import {
  Button,
  Input,
  Label,
  toast,
} from "@heroui/react";
import {
  Loader2,
  ChevronDown,
  ChevronUp,
  Upload,
  ImageIcon,
} from "lucide-react";
import { projectService } from "@/lib/api";
import { useWorkspace } from "@/components/providers/workspace-provider";
import { useTranslations } from "next-intl";
import type { LucideIcon } from "lucide-react";
import Image from "@/components/ui/content-image";

export interface DetailFormHandle {
  save: () => Promise<void>;
  canSave: boolean;
  isSaving: boolean;
}

const MAX_COVER_SIZE = 10 * 1024 * 1024; // 10MB

/**
 * Hook for cover image upload logic shared across character/prop/scene detail forms.
 */
export function useCoverUpload(
  entityId: string,
  setCoverFn: (entityId: string, assetId: string) => Promise<unknown>,
  reloadFn: () => Promise<void>,
) {
  const t = useTranslations("workspace.materialRoom.detail");
  const { currentWorkspace } = useWorkspace();
  const coverInputRef = useRef<HTMLInputElement>(null);
  const [isUploading, setIsUploading] = useState(false);

  const handleCoverUpload = async (file: File) => {
    if (file.size > MAX_COVER_SIZE) {
      toast.danger(t("coverTooLarge"));
      return;
    }
    try {
      setIsUploading(true);
      const initResponse = await projectService.initAssetUpload({
        name: file.name,
        fileName: file.name,
        mimeType: file.type,
        fileSize: file.size,
        scope: currentWorkspace?.isSystem ? "SYSTEM" : "WORKSPACE",
      });
      await fetch(initResponse.uploadUrl, {
        method: initResponse.method,
        headers: initResponse.headers,
        body: file,
      });
      await projectService.confirmAssetUpload(initResponse.assetId, {
        fileKey: initResponse.fileKey,
        actualFileSize: file.size,
      });
      await setCoverFn(entityId, initResponse.assetId);
      await reloadFn();
    } catch (err) {
      console.error("Failed to upload cover:", err);
      toast.danger(t("uploadFailed"));
    } finally {
      setIsUploading(false);
      if (coverInputRef.current) coverInputRef.current.value = "";
    }
  };

  return { coverInputRef, isUploading, handleCoverUpload };
}

/**
 * Cover image section with upload overlay, shared across character/prop/scene forms.
 */
export function CoverImageSection({
  coverUrl,
  name,
  coverInputRef,
  isUploading,
  onUpload,
}: {
  coverUrl?: string | null;
  name: string;
  coverInputRef: React.RefObject<HTMLInputElement | null>;
  isUploading: boolean;
  onUpload: (file: File) => void;
}) {
  return (
    <div className="group relative md:sticky md:top-0 md:w-2/5 md:shrink-0 md:self-start">
      <input
        ref={coverInputRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) onUpload(file);
        }}
      />
      <div className="relative aspect-[4/3] max-h-[36vh] overflow-hidden rounded-xl bg-muted/10">
        {coverUrl ? (
          <Image src={coverUrl} alt={name} fill className="object-contain" sizes="(min-width: 768px) 33vw, 50vw" />
        ) : (
          <div className="flex size-full items-center justify-center">
            <ImageIcon className="size-12 text-muted/20" />
          </div>
        )}
      </div>
      <div className="absolute inset-0 flex items-center justify-center rounded-xl bg-black/40 opacity-0 transition-opacity group-hover:opacity-100">
        <Button
          size="sm"
          variant="secondary"
          isIconOnly
          className="size-9"
          onPress={() => coverInputRef.current?.click()}
          isDisabled={isUploading}
        >
          {isUploading ? <Loader2 className="size-4 animate-spin" /> : <Upload className="size-4" />}
        </Button>
      </div>
    </div>
  );
}

/**
 * Collapsible section for appearance/environment/atmosphere fields.
 */
export function CollapsibleFieldSection({
  icon: Icon,
  title,
  hint,
  isOpen,
  onToggle,
  children,
}: {
  icon: LucideIcon;
  title: string;
  hint: string;
  isOpen: boolean;
  onToggle: () => void;
  children: ReactNode;
}) {
  return (
    <div className="rounded-xl bg-muted/5">
      <div
        className="flex cursor-pointer items-center justify-between p-4"
        onClick={onToggle}
      >
        <h4 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-muted">
          <Icon className="size-3.5 text-accent" />
          {title}
          {!isOpen && (
            <span className="ml-1 text-[10px] font-normal normal-case text-muted/50">
              ({hint})
            </span>
          )}
        </h4>
        <Button variant="ghost" size="sm" isIconOnly className="size-6">
          {isOpen ? <ChevronUp className="size-3.5" /> : <ChevronDown className="size-3.5" />}
        </Button>
      </div>
      {isOpen && (
        <div className="border-t border-muted/10 p-4 pt-3">
          {children}
        </div>
      )}
    </div>
  );
}

/**
 * Grid of fields inside a CollapsibleFieldSection.
 */
export function FieldGrid({
  fields,
  values,
  onChange,
  t,
}: {
  fields: { key: string; labelKey: string; placeholderKey: string; icon?: LucideIcon }[];
  values: Record<string, string>;
  onChange: (key: string, value: string) => void;
  t: (key: string) => string;
}) {
  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
      {fields.map(({ key, labelKey, placeholderKey, icon: FieldIcon }) => (
        <div key={key} className="flex flex-col gap-1 rounded-lg bg-background/50 p-2.5">
          <Label className="flex items-center gap-1.5 text-[10px] font-medium uppercase tracking-wide text-muted/70">
            {FieldIcon && <FieldIcon className="size-3" />}
            {t(labelKey)}
          </Label>
          <Input
            variant="secondary"
            className="h-7 text-xs"
            placeholder={t(placeholderKey)}
            value={values[key] || ""}
            onChange={(e) => onChange(key, e.target.value)}
          />
        </div>
      ))}
    </div>
  );
}
