"use client";

/**
 * Asset Detail Content Component
 * Extracted from asset-tab.tsx with i18n, collapsible header, and UX fixes
 */

import { useState, useCallback, useEffect } from "react";
import dynamic from "next/dynamic";
import { Button, ScrollShadow, TextArea, Input, Label, Tooltip, Spinner, toast } from "@heroui/react";
import {
  ChevronDown,
  ChevronUp,
  Edit3,
  Download,
  FileVideo,
  FileAudio,
  File,
  Sparkles,
  RefreshCw,
  Image as ImageIcon,
  Bot,
  Zap,
} from "lucide-react";
import NextImage from "@/components/ui/content-image";
import { useTranslations } from "next-intl";
import { projectService } from "@/lib/api";
import { aiService } from "@/lib/api/services/ai.service";
import type { AssetDetailDTO } from "@/lib/api/dto";
import type { AvailableProviderDTO, ProviderType } from "@/lib/api/dto/ai.dto";
import { useWorkspace } from "@/components/providers/workspace-provider";
import { StatusBadge } from "../../common";
import { formatDate } from "../../utils";
import { downloadFile } from "@/lib/utils/download";

// Dynamic import for ImageEditorModal to avoid SSR issues with canvas
const ImageEditorModal = dynamic(
  () => import("@/components/common/image-editor/image-editor-modal").then(mod => mod.ImageEditorModal),
  { ssr: false }
);

// Asset type options (returns key for i18n lookup)
const assetTypeMap: Record<string, { icon: typeof ImageIcon; labelKey: string }> = {
  IMAGE: { icon: ImageIcon, labelKey: "image" },
  VIDEO: { icon: FileVideo, labelKey: "video" },
  AUDIO: { icon: FileAudio, labelKey: "audio" },
};

function getAssetTypeIcon(assetType: string) {
  const entry = assetTypeMap[assetType];
  const IconComponent = entry?.icon || File;
  return <IconComponent className="size-4" />;
}

function getStatusInfo(status: string): { key: string; color: string } {
  switch (status) {
    case "COMPLETED":
      return { key: "completed", color: "text-success" };
    case "PROCESSING":
      return { key: "inProgress", color: "text-warning" };
    case "PENDING":
      return { key: "draft", color: "text-muted" };
    case "FAILED":
      return { key: "failed", color: "text-danger" };
    default:
      return { key: status.toLowerCase(), color: "text-muted" };
  }
}

function formatFileSize(bytes: number | null): string {
  if (!bytes) return "-";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

interface AssetDetailContentProps {
  data: AssetDetailDTO;
  onUpdate?: () => void;
  onStartEditing?: () => void;
  onStopEditing?: () => void;
  isReadOnly?: boolean;
  scriptId?: string;
}

export function AssetDetailContent({
  data,
  onUpdate,
  onStartEditing,
  onStopEditing,
  isReadOnly = false,
  scriptId,
}: AssetDetailContentProps) {
  const { currentWorkspaceId } = useWorkspace();
  const t = useTranslations("workspace.studio.asset.detail");
  const tAssetType = useTranslations("workspace.studio.common.assetType");
  const tStatus = useTranslations("workspace.studio.status");
  const [isHeaderCollapsed, setIsHeaderCollapsed] = useState(true);
  const [isEditingInfo, setIsEditingInfo] = useState(false);
  const [isExtraInfoCollapsed, setIsExtraInfoCollapsed] = useState(false);
  const [isSaving, setIsSaving] = useState(false);

  // Edit form state
  const [editForm, setEditForm] = useState({
    name: data.name || "",
    description: data.description || "",
  });

  // Sync with data changes
  useEffect(() => {
    setEditForm({
      name: data.name || "",
      description: data.description || "",
    });
  }, [data]);

  // Save info
  const handleSaveInfo = async () => {
    if (!currentWorkspaceId) return;
    try {
      setIsSaving(true);
      await projectService.updateAsset( data.id, {
        name: editForm.name || undefined,
        description: editForm.description || undefined,
      });
      setIsEditingInfo(false);
      onStopEditing?.();
      onUpdate?.();
      toast.success(t("saveSuccess"));
    } catch (err) {
      console.error("Failed to update asset:", err);
      toast.danger(t("saveFailed"));
    } finally {
      setIsSaving(false);
    }
  };

  // Get generation params from extraInfo
  const extraInfo = data.extraInfo || {};
  const generationParams = (extraInfo.generationParams || {}) as Record<string, unknown>;
  const params = (generationParams.params || {}) as Record<string, unknown>;
  const entityType = generationParams.entityType as string | undefined;
  const providerId = generationParams.providerId as string | undefined;
  const generationType = generationParams.generationType as string | undefined;

  // Fetch provider info based on providerId
  const [providerInfo, setProviderInfo] = useState<AvailableProviderDTO | null>(null);
  useEffect(() => {
    if (!providerId || !generationType) {
      setProviderInfo(null);
      return;
    }
    let cancelled = false;
    aiService.getProvidersByType(generationType as ProviderType).then((providers) => {
      if (cancelled) return;
      const found = providers.find((p) => p.id === providerId);
      setProviderInfo(found || null);
    }).catch(() => {
      if (!cancelled) setProviderInfo(null);
    });
    return () => { cancelled = true; };
  }, [providerId, generationType]);

  const statusInfo = getStatusInfo(data.generationStatus);
  const assetTypeEntry = assetTypeMap[data.assetType];
  const assetTypeLabel = assetTypeEntry ? tAssetType(assetTypeEntry.labelKey) : data.assetType;

  // Check if this is a visual asset (image or video)
  const isVisualAsset = data.assetType === "IMAGE" || data.assetType === "VIDEO";

  // Image editor modal state
  const [isImageEditorOpen, setIsImageEditorOpen] = useState(false);

  return (
    <div className="relative flex h-full flex-col">
      {/* Main Content - Scrollable */}
      <ScrollShadow className="h-full" hideScrollBar>
        <div className="space-y-3">
          {/* Large Media Preview - Priority Display */}
          {isVisualAsset && (data.fileUrl || data.thumbnailUrl) && (
            <div className="relative overflow-hidden rounded-2xl bg-muted/5 p-3">
              <div className="relative overflow-hidden rounded-xl">
                {data.assetType === "IMAGE" ? (
                  <img
                    src={data.fileUrl || data.thumbnailUrl || ""}
                    alt={data.name}
                    className="max-h-96 w-full cursor-pointer object-contain transition-opacity hover:opacity-90"
                    onClick={() => setIsImageEditorOpen(true)}
                  />
                ) : (
                  <video
                    src={data.fileUrl || ""}
                    controls
                    className="max-h-96 w-full"
                    preload="metadata"
                  />
                )}
              </div>
              {/* Overlay info on media */}
              <div className="absolute left-5 top-5 flex flex-wrap gap-2">
                <span className="inline-flex items-center gap-1.5 rounded-md bg-black/60 px-2 py-1 text-xs text-white backdrop-blur-sm">
                  {getAssetTypeIcon(data.assetType)}
                  {assetTypeLabel}
                </span>
                {data.source === "AI_GENERATED" && (
                  <span className="inline-flex items-center gap-1.5 rounded-md bg-accent/80 px-2 py-1 text-xs text-white backdrop-blur-sm">
                    <Sparkles className="size-3" />
                    {t("aiGeneratedLabel")}
                  </span>
                )}
              </div>
              {/* Actions overlay */}
              <div className="absolute right-5 top-5 flex gap-1">
                {data.assetType === "IMAGE" && (
                  <Button
                    variant="secondary"
                    size="sm"
                    isIconOnly
                    className="size-8 bg-black/60 text-white backdrop-blur-sm hover:bg-black/80"
                    onPress={() => setIsImageEditorOpen(true)}
                  >
                    <Edit3 className="size-4" />
                  </Button>
                )}
                {data.fileUrl && (
                  <Button
                    variant="secondary"
                    size="sm"
                    isIconOnly
                    className="size-8 bg-black/60 text-white backdrop-blur-sm hover:bg-black/80"
                    onPress={() => downloadFile(data.fileUrl!, data.name || "asset")}
                  >
                    <Download className="size-4" />
                  </Button>
                )}
              </div>
            </div>
          )}

          {/* Audio Player - Full Width */}
          {data.assetType === "AUDIO" && data.fileUrl && (
            <div className="rounded-2xl bg-muted/5 p-4">
              <div className="flex items-center gap-4">
                <div className="flex size-16 shrink-0 items-center justify-center rounded-xl bg-accent/10">
                  <FileAudio className="size-8 text-accent" />
                </div>
                <div className="min-w-0 flex-1">
                  <h3 className="truncate text-sm font-medium">{data.name}</h3>
                  <p className="mt-1 text-xs text-muted">
                    {formatFileSize(data.fileSize)} · {data.mimeType}
                  </p>
                  <audio
                    src={data.fileUrl}
                    controls
                    className="mt-2 w-full"
                    preload="metadata"
                  />
                </div>
              </div>
            </div>
          )}

          {/* Other file types */}
          {!isVisualAsset && data.assetType !== "AUDIO" && (
            <div className="flex h-32 items-center justify-center rounded-2xl bg-muted/5">
              <File className="size-16 text-muted/30" />
            </div>
          )}

          {/* Info Card - Collapsible Header */}
          <div className="rounded-xl bg-muted/5 transition-all duration-300">
            {/* Collapsed Header Bar */}
            <div
              className={`flex items-center justify-between gap-3 px-4 py-3 transition-all ${
                isHeaderCollapsed ? "" : "border-b border-muted/10"
              }`}
            >
              <div
                className="flex min-w-0 flex-1 cursor-pointer items-center gap-3"
                onClick={() => setIsHeaderCollapsed(!isHeaderCollapsed)}
              >
                {isHeaderCollapsed && (data.thumbnailUrl || data.fileUrl) && isVisualAsset && (
                  <div className="relative h-8 w-14 shrink-0 overflow-hidden rounded-lg">
                    <NextImage src={data.thumbnailUrl || data.fileUrl || ""} alt="" fill className="object-cover" sizes="120px" />
                  </div>
                )}
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <h3 className="truncate text-sm font-semibold">{data.name}</h3>
                    <StatusBadge status={data.generationStatus} />
                  </div>
                  {isHeaderCollapsed && data.description && (
                    <p className="mt-0.5 truncate text-xs text-muted">{data.description}</p>
                  )}
                </div>
              </div>
              <div className="flex items-center gap-1">
                <Tooltip delay={0}>
                  <Button
                    variant="ghost"
                    size="sm"
                    isIconOnly
                    className="size-7"
                    onPress={() => onUpdate?.()}
                  >
                    <RefreshCw className="size-3.5" />
                  </Button>
                  <Tooltip.Content>{t("refreshData")}</Tooltip.Content>
                </Tooltip>
                <Button
                  variant="ghost"
                  size="sm"
                  isIconOnly
                  className="size-7"
                  onPress={() => setIsHeaderCollapsed(!isHeaderCollapsed)}
                >
                  {isHeaderCollapsed ? <ChevronDown className="size-4" /> : <ChevronUp className="size-4" />}
                </Button>
              </div>
            </div>

            {/* Expandable Content */}
            {!isHeaderCollapsed && (
              <div className="p-4 pt-3">
                {isEditingInfo ? (
                  <div className="space-y-3">
                    <div className="space-y-1">
                      <Label className="text-xs text-muted">{t("name")}</Label>
                      <Input
                        variant="secondary"
                        value={editForm.name}
                        onChange={(e) => setEditForm((prev) => ({ ...prev, name: e.target.value }))}
                        className="w-full"
                        placeholder={t("namePlaceholder")}
                      />
                    </div>
                    <div className="space-y-1">
                      <Label className="text-xs text-muted">{t("description")}</Label>
                      <TextArea
                        variant="secondary"
                        rows={3}
                        value={editForm.description}
                        onChange={(e) => setEditForm((prev) => ({ ...prev, description: e.target.value }))}
                        className="w-full"
                        placeholder={t("descriptionPlaceholder")}
                      />
                    </div>
                    <div className="flex justify-end gap-2">
                      <Button variant="ghost" size="sm" onPress={() => {
                        setIsEditingInfo(false);
                        onStopEditing?.();
                      }} isDisabled={isSaving}>
                        {t("cancel")}
                      </Button>
                      <Button variant="tertiary" size="sm" className="gap-1.5" onPress={handleSaveInfo} isPending={isSaving}>
                        {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}{t("save")}</>)}
                      </Button>
                    </div>
                  </div>
                ) : (
                  <>
                    <div className="mb-3 flex items-center justify-end">
                      <Button
                        variant="ghost"
                        size="sm"
                        isIconOnly
                        className="size-7"
                        isDisabled={isReadOnly}
                        onPress={() => {
                          setIsEditingInfo(true);
                          onStartEditing?.();
                        }}
                      >
                        <Edit3 className="size-3.5" />
                      </Button>
                    </div>
                    {data.description ? (
                      <p className="text-sm text-muted">{data.description}</p>
                    ) : (
                      <p className="text-sm text-muted/40">{t("noDescription")}</p>
                    )}

                    {/* Meta info - grid layout */}
                    <div className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1 text-[11px] text-muted/70">
                      <span>{formatFileSize(data.fileSize)}</span>
                      <span>{data.mimeType}</span>
                      <span>v{data.versionNumber}</span>
                      <span>{data.createdByNickname || data.createdByUsername || "-"}</span>
                      <span>{formatDate(data.createdAt)}</span>
                    </div>
                  </>
                )}
              </div>
            )}
          </div>

          {/* AI Generation Params - Collapsible */}
          {data.source === "AI_GENERATED" && (entityType || providerId || Object.keys(params).length > 0) && (
            <div className="rounded-2xl bg-muted/5">
              <div
                className="flex cursor-pointer items-center justify-between p-4"
                onClick={() => setIsExtraInfoCollapsed(!isExtraInfoCollapsed)}
              >
                <h4 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-muted">
                  <Sparkles className="size-3.5 text-accent" />
                  {t("aiGenerationParams")}
                </h4>
                <Button variant="ghost" size="sm" isIconOnly className="size-6">
                  {isExtraInfoCollapsed ? <ChevronDown className="size-3.5" /> : <ChevronUp className="size-3.5" />}
                </Button>
              </div>

              {!isExtraInfoCollapsed && (
                <div className="space-y-3 border-t border-muted/10 p-4 pt-3">
                  {/* Provider card */}
                  {(providerInfo || providerId) && (
                    <div className="flex items-center gap-2.5 rounded-xl bg-background/50 px-3 py-2.5">
                      {providerInfo?.iconUrl ? (
                        <img src={providerInfo.iconUrl} alt="" className="size-7 shrink-0 rounded-lg" />
                      ) : (
                        <div className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-muted/10">
                          <Bot className="size-4 text-muted" />
                        </div>
                      )}
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <span className="truncate text-sm font-medium">
                            {providerInfo?.name || t("providerNotFound")}
                          </span>
                          {providerInfo && providerInfo.creditCost > 0 && (
                            <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-warning/10 px-1.5 py-0.5 text-[11px] font-medium text-warning">
                              <Zap className="size-2.5" />
                              {providerInfo.creditCost}
                            </span>
                          )}
                        </div>
                        {providerInfo?.description && (
                          <p className="mt-0.5 truncate text-xs text-muted">{providerInfo.description}</p>
                        )}
                      </div>
                    </div>
                  )}

                  {/* Tags row: entityType + generationType */}
                  {(entityType || generationType) ? (
                    <div className="flex flex-wrap gap-1.5">
                      {entityType ? (
                        <span className="inline-flex items-center gap-1 rounded-md bg-accent/10 px-2 py-1 text-xs text-accent">
                          {t(`entityType.${entityType}` as Parameters<typeof t>[0])}
                        </span>
                      ) : null}
                      {generationType ? (
                        <span className="inline-flex items-center gap-1 rounded-md bg-muted/10 px-2 py-1 text-xs text-muted">
                          {t("paramGenerationType")}: {String(generationType)}
                        </span>
                      ) : null}
                    </div>
                  ) : null}

                  {/* Prompt */}
                  {params.prompt ? (
                    <div>
                      <div className="mb-1 text-xs text-muted/70">{t("paramPrompt")}</div>
                      <p className="whitespace-pre-wrap text-sm leading-relaxed text-foreground">
                        {String(params.prompt)}
                      </p>
                    </div>
                  ) : null}

                  {/* Technical params — compact key-value */}
                  {(params.image_size || params.aspect_ratio) ? (
                    <div className="grid grid-cols-2 gap-x-4 gap-y-1.5 text-xs">
                      {params.image_size ? (
                        <>
                          <span className="text-muted/70">{t("paramImageSize")}</span>
                          <span className="text-foreground">{String(params.image_size)}</span>
                        </>
                      ) : null}
                      {params.aspect_ratio ? (
                        <>
                          <span className="text-muted/70">{t("paramAspectRatio")}</span>
                          <span className="text-foreground">{String(params.aspect_ratio)}</span>
                        </>
                      ) : null}
                    </div>
                  ) : null}
                </div>
              )}
            </div>
          )}
        </div>
      </ScrollShadow>

      {/* Image Editor Modal */}
      {data.assetType === "IMAGE" && data.fileUrl && (
        <ImageEditorModal
          isOpen={isImageEditorOpen}
          onOpenChange={setIsImageEditorOpen}
          src={data.fileUrl}
          workspaceId={currentWorkspaceId || ""}
          scriptId={scriptId || ""}
          onSave={async () => {
            onUpdate?.();
          }}
        />
      )}
    </div>
  );
}

export default AssetDetailContent;
