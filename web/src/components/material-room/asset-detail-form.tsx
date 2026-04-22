"use client";

import { useState, useEffect, forwardRef, useImperativeHandle } from "react";
import {
  Input,
  Label,
  Spinner,
  toast,
} from "@heroui/react";
import {
  ImageIcon,
  FileVideo,
  FileAudio,
  FileText,
  FileBox,
} from "lucide-react";
import { projectService, getErrorFromException} from "@/lib/api";
import type { AssetDetailDTO } from "@/lib/api/dto";
import { useTranslations, useLocale} from "next-intl";
import type { DetailFormHandle } from "./shared";
import Image from "@/components/ui/content-image";

interface AssetDetailFormProps {
  entityId: string;
  workspaceId: string;
  onUpdated?: () => void;
  onFormStateChange?: (canSave: boolean, isSaving: boolean) => void;
}

const ASSET_TYPE_ICONS: Record<string, typeof FileBox> = {
  IMAGE: ImageIcon,
  VIDEO: FileVideo,
  AUDIO: FileAudio,
  DOCUMENT: FileText,
};

function formatFileSize(bytes: number | null | undefined): string {
  if (!bytes) return "--";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export const AssetDetailForm = forwardRef<DetailFormHandle, AssetDetailFormProps>(
  function AssetDetailForm({ entityId, workspaceId, onUpdated, onFormStateChange }, ref) {
    const t = useTranslations("workspace.materialRoom.detail");
    const locale = useLocale();
    const [data, setData] = useState<AssetDetailDTO | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);

    const [form, setForm] = useState({ name: "", description: "" });

    useEffect(() => {
      const load = async () => {
        try {
          setIsLoading(true);
          const detail = await projectService.getAsset(entityId);
          setData(detail);
          setForm({
            name: detail.name || "",
            description: detail.description || "",
          });
        } catch (err) {
          console.error("Failed to load asset:", err);
          toast.danger(getErrorFromException(err, locale));
        } finally {
          setIsLoading(false);
        }
      };
      load();
    }, [entityId, workspaceId, locale]);

    const handleSave = async () => {
      if (!data) return;
      try {
        setIsSaving(true);
        await projectService.updateAsset(entityId, {
          name: form.name.trim(),
          description: form.description.trim() || null,
        });
        toast.success(t("saveSuccess"));
        onUpdated?.();
      } catch (err) {
        console.error("Failed to save asset:", err);
        toast.danger(t("saveFailed"));
      } finally {
        setIsSaving(false);
      }
    };

    useImperativeHandle(ref, () => ({
      save: handleSave,
      canSave: !!form.name.trim(),
      isSaving,
    }), [form.name, isSaving, data, form]);

    useEffect(() => {
      onFormStateChange?.(!!form.name.trim(), isSaving);
    }, [form.name, isSaving, onFormStateChange]);

    if (isLoading) {
      return (
        <div className="flex h-64 items-center justify-center">
          <Spinner size="lg" />
        </div>
      );
    }

    if (!data) return null;

    const TypeIcon = ASSET_TYPE_ICONS[data.assetType] ?? FileBox;
    const isVideo = data.assetType === "VIDEO" || data.mimeType?.startsWith("video/");
    const isAudio = data.assetType === "AUDIO" || data.mimeType?.startsWith("audio/");

    const renderPreview = () => {
      if (isVideo && data.fileUrl) {
        return (
          <video
            src={data.fileUrl}
            className="size-full object-contain"
            controls
            muted
            preload="metadata"
          />
        );
      }
      if (data.thumbnailUrl) {
        return <Image src={data.thumbnailUrl} alt={data.name} fill className="object-contain" sizes="(min-width: 768px) 33vw, 50vw" />;
      }
      if (data.assetType === "IMAGE" && data.fileUrl) {
        return <Image src={data.fileUrl} alt={data.name} fill className="object-contain" sizes="(min-width: 768px) 33vw, 50vw" />;
      }
      return (
        <div className="flex size-full items-center justify-center">
          <TypeIcon className="size-12 text-muted/20" />
        </div>
      );
    };

    return (
      <div className="flex flex-col gap-6 p-1 md:flex-row">
        {/* Left: Preview */}
        <div className="md:sticky md:top-0 md:w-2/5 md:shrink-0 md:self-start">
          <div className="relative aspect-[4/3] max-h-[36vh] overflow-hidden rounded-xl bg-muted/10">
            {renderPreview()}
          </div>
        </div>

        {/* Right: Fields */}
        <div className="min-w-0 flex-1 space-y-3">
          <div className="flex flex-col gap-1">
            <Label className="block text-xs text-muted">{t("name")}</Label>
            <Input
              variant="secondary"
              value={form.name}
              onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))}
              className="w-full"
              placeholder={t("namePlaceholder")}
            />
          </div>
          <div className="flex flex-col gap-1">
            <Label className="block text-xs text-muted">{t("description")}</Label>
            <Input
              variant="secondary"
              value={form.description}
              onChange={(e) => setForm((p) => ({ ...p, description: e.target.value }))}
              className="w-full"
              placeholder={t("descriptionPlaceholder")}
            />
          </div>

          {/* File Info */}
          <div className="rounded-xl bg-muted/5 p-4">
            <h4 className="mb-3 flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-muted">
              <TypeIcon className="size-3.5 text-accent" />
              {t("fileInfo")}
            </h4>
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
              <div className="rounded-lg bg-background/50 p-2.5">
                <div className="text-[10px] font-medium uppercase tracking-wide text-muted/70">{t("assetType")}</div>
                <div className="mt-0.5 text-sm text-foreground">{data.assetType}</div>
              </div>
              <div className="rounded-lg bg-background/50 p-2.5">
                <div className="text-[10px] font-medium uppercase tracking-wide text-muted/70">{t("fileSize")}</div>
                <div className="mt-0.5 text-sm text-foreground">{formatFileSize(data.fileSize)}</div>
              </div>
              {data.mimeType && (
                <div className="rounded-lg bg-background/50 p-2.5">
                  <div className="text-[10px] font-medium uppercase tracking-wide text-muted/70">MIME</div>
                  <div className="mt-0.5 truncate text-sm text-foreground">{data.mimeType}</div>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    );
  }
);
