"use client";

/**
 * Asset Card Component
 * Reusable card for displaying assets in reference/related assets sections
 * Shows drag handle, delete, favorite/unfavorite, download buttons on hover
 * Clicking opens asset detail modal (Gallery style), edit button opens image editor
 */

import { useState, useCallback, useMemo } from "react";
import dynamic from "next/dynamic";
import { Spinner, Tooltip, Button, toast} from "@heroui/react";
import {
  GripVertical,
  Trash2,
  Download,
  X,
  Image as ImageIcon,
  Play,
  Music,
  RefreshCw,
  Edit3,
  BadgeCheck,
  CircleDashed,
  Loader2,
} from "lucide-react";
import { AssetPreviewModal } from "@/components/common/asset-preview-modal";
import type { AssetPreviewInfo, AssetPreviewRelation } from "@/components/common/asset-preview-modal";
import type { EntityAssetRelationDTO } from "@/lib/api/dto";
import { projectService } from "@/lib/api/services";
import { useLocale } from "next-intl";
import { getErrorFromException } from "@/lib/api";

// Dynamic import to avoid SSR issues with canvas dependency
const ImageEditorModal = dynamic(
  () => import("@/components/common/image-editor/image-editor-modal").then(mod => mod.ImageEditorModal),
  { ssr: false }
);

interface AssetCardProps {
  relation: EntityAssetRelationDTO;
  /** Script ID for loading assets in the editor */
  scriptId?: string;
  /** Workspace ID for API calls */
  workspaceId?: string;
  /** Entity type for context */
  entityType?: "CHARACTER" | "SCENE" | "PROP" | "STORYBOARD";
  /** Entity ID for context */
  entityId?: string;
  /** Reference images to show in the asset browser */
  refImages?: string[];
  /** All assets for finding next official asset when unfavoriting */
  allAssets?: EntityAssetRelationDTO[];
  /** Current entity cover asset ID */
  currentCoverAssetId?: string | null;
  /** Called when drag starts */
  onDragStart?: (e: React.DragEvent, relation: EntityAssetRelationDTO) => void;
  /** Called when drag ends */
  onDragEnd?: (e: React.DragEvent) => void;
  /** Called when delete button is clicked */
  onDelete?: (relationId: string) => void;
  /** Called when download button is clicked */
  onDownload?: (relation: EntityAssetRelationDTO) => void;
  /** Called when image is saved from editor */
  onSave?: (relation: EntityAssetRelationDTO, dataUrl: string) => Promise<void>;
  /** Called after publish status is toggled (to refresh data) */
  onPublishToggled?: () => void;
}

export function AssetCard({
  relation,
  scriptId,
  workspaceId,
  entityType,
  entityId,
  refImages = [],
  allAssets = [],
  currentCoverAssetId,
  onDragStart,
  onDragEnd,
  onDelete,
  onDownload,
  onSave,
  onPublishToggled,
}: AssetCardProps) {
  const locale = useLocale();
  const [isEditorOpen, setIsEditorOpen] = useState(false);
  const [isDetailOpen, setIsDetailOpen] = useState(false);
  const [isUpdating, setIsUpdating] = useState(false);

  // Early return if asset is null
  if (!relation.asset) {
    return null;
  }

  const isGenerating = relation.asset.generationStatus === "GENERATING";
  const isFailed = relation.asset.generationStatus === "FAILED";
  const isOfficial = relation.relationType === "OFFICIAL";
  const isCover = currentCoverAssetId === relation.asset.id;
  const assetType = relation.asset.assetType;

  /**
   * Set entity cover via API
   */
  const setCover = async (assetId: string) => {
    if (!workspaceId || !entityType || !entityId) return;

    try {
      switch (entityType) {
        case "CHARACTER":
          await projectService.setCharacterCover( entityId, assetId);
          break;
        case "SCENE":
          await projectService.setSceneCover( entityId, assetId);
          break;
        case "PROP":
          await projectService.setPropCover( entityId, assetId);
          break;
        case "STORYBOARD":
          await projectService.setStoryboardCover( entityId, assetId);
          break;
      }
    } catch (error) {
      console.error("Failed to set cover:", error);
      toast.danger(getErrorFromException(error, locale));
    }
  };

  /**
   * Handle toggling publish status with auto-cover logic:
   * - When setting to OFFICIAL: auto-set as cover (only for IMAGE type)
   * - When setting to DRAFT: find next official IMAGE asset as cover
   */
  const handleTogglePublish = async () => {
    if (!workspaceId || isUpdating) return;

    const newRelationType = isOfficial ? "DRAFT" : "OFFICIAL";
    const isImageAsset = assetType === "IMAGE";

    setIsUpdating(true);
    try {
      // 1. Update relation type via API
      await projectService.updateEntityAssetRelation( relation.id, {
        relationType: newRelationType,
      });

      // 2. Handle auto-cover logic (only for IMAGE assets)
      if (entityType && entityId && isImageAsset) {
        if (newRelationType === "OFFICIAL") {
          // Setting to OFFICIAL: auto-set as cover
          if (currentCoverAssetId !== relation.asset.id) {
            await setCover(relation.asset.id);
          }
        } else {
          // Setting to DRAFT: find another OFFICIAL IMAGE asset to be cover
          const nextOfficialImageAsset = allAssets.find(
            a => a.asset?.id !== relation.asset.id &&
                 a.relationType === "OFFICIAL" &&
                 a.asset?.assetType === "IMAGE"
          );
          if (nextOfficialImageAsset?.asset) {
            if (currentCoverAssetId !== nextOfficialImageAsset.asset.id) {
              await setCover(nextOfficialImageAsset.asset.id);
            }
          } else {
            // No official IMAGE asset found, use first available IMAGE asset
            const firstImageAsset = allAssets.find(
              a => a.asset?.id !== relation.asset.id && a.asset?.assetType === "IMAGE"
            );
            if (firstImageAsset?.asset && currentCoverAssetId !== firstImageAsset.asset.id) {
              await setCover(firstImageAsset.asset.id);
            }
          }
        }
      }

      // 3. Notify parent to refresh data
      onPublishToggled?.();
    } catch (error) {
      console.error("Failed to toggle publish status:", error);
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setIsUpdating(false);
    }
  };

  // Click card to show detail modal
  const handleCardClick = (e: React.MouseEvent) => {
    if (!isGenerating && !isFailed) {
      setIsDetailOpen(true);
    }
  };

  // Click edit button to open image editor
  const handleEditClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (assetType === "IMAGE" && relation.asset.fileUrl && !isGenerating && !isFailed) {
      setIsEditorOpen(true);
    }
  };

  const handleDownload = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (relation.asset.fileUrl) {
      if (onDownload) {
        onDownload(relation);
      } else {
        // Default download behavior - download file directly
        const link = document.createElement("a");
        link.href = relation.asset.fileUrl;
        link.download = relation.asset.name || "download";
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
      }
    }
  };

  const handleDelete = (e: React.MouseEvent) => {
    e.stopPropagation();
    onDelete?.(relation.id);
  };

  const handleSaveFromEditor = async (dataUrl: string) => {
    await onSave?.(relation, dataUrl);
  };

  // Build data for the shared AssetPreviewModal
  const assetPreviewInfo = useMemo((): AssetPreviewInfo => ({
    id: relation.asset.id,
    name: relation.asset.name,
    description: relation.asset.description,
    assetType: relation.asset.assetType,
    fileUrl: relation.asset.fileUrl,
    thumbnailUrl: relation.asset.thumbnailUrl,
    mimeType: relation.asset.mimeType,
    fileSize: relation.asset.fileSize,
    source: relation.asset.source,
    generationStatus: relation.asset.generationStatus,
    versionNumber: relation.asset.versionNumber,
    scope: relation.asset.scope,
    createdAt: relation.asset.createdAt,
    createdByUsername: relation.asset.createdByUsername,
    createdByNickname: relation.asset.createdByNickname,
    extraInfo: (relation.asset as { extraInfo?: Record<string, unknown> }).extraInfo ?? null,
  }), [relation.asset]);

  const assetRelationInfo = useMemo((): AssetPreviewRelation => ({
    relationType: relation.relationType,
    sequence: relation.sequence,
    extraInfo: relation.extraInfo,
  }), [relation.relationType, relation.sequence, relation.extraInfo]);

  // Render asset content based on type
  const renderAssetContent = () => {
    if (assetType === "IMAGE") {
      return relation.asset.fileUrl ? (
        <img
          src={relation.asset.fileUrl}
          alt={relation.asset.name}
          className="size-full object-cover"
        />
      ) : (
        <div className="flex size-full items-center justify-center bg-muted/20">
          <ImageIcon className="size-6 text-muted/30" />
        </div>
      );
    }

    if (assetType === "VIDEO") {
      return relation.asset.fileUrl ? (
        <>
          <video
            src={relation.asset.fileUrl}
            className="size-full object-cover"
            muted
            preload="metadata"
          />
          <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
            <div className="flex size-8 items-center justify-center rounded-full bg-black/40 backdrop-blur-sm">
              <Play className="size-4 text-white" />
            </div>
          </div>
        </>
      ) : (
        <div className="flex size-full items-center justify-center bg-muted/20">
          <Play className="size-6 text-muted/30" />
        </div>
      );
    }

    if (assetType === "AUDIO") {
      return (
        <div className="flex size-full flex-col items-center justify-center bg-accent/5">
          <Music className="size-6 text-accent" />
        </div>
      );
    }

    // Default fallback
    return (
      <div className="flex size-full items-center justify-center bg-muted/20">
        <ImageIcon className="size-6 text-muted/30" />
      </div>
    );
  };

  return (
    <>
      <div
        className={`group relative aspect-video overflow-hidden rounded-lg bg-muted/10 ${
          isGenerating ? "cursor-default" : "cursor-pointer"
        } ${isOfficial ? "ring-2 ring-accent" : ""}`}
        draggable={!isGenerating && !!onDragStart}
        onClick={handleCardClick}
        onDragStart={(e) => !isGenerating && onDragStart?.(e, relation)}
        onDragEnd={onDragEnd}
      >
        {/* Asset content */}
        {renderAssetContent()}

        {/* Top-left badge: Official */}
        {isOfficial && !isGenerating && !isFailed && (
          <div className="absolute left-1.5 top-1.5 z-10 flex w-fit items-center gap-1 rounded-md bg-accent px-1.5 py-0.5 text-[10px] font-medium text-accent-foreground">
            <BadgeCheck className="size-3" />
            正式
          </div>
        )}

        {/* Bottom-left: Creator name */}
        {!isGenerating && !isFailed && (relation.asset.createdByNickname || relation.asset.createdByUsername) && (
          <div className="absolute bottom-1.5 left-1.5 z-10 flex w-fit items-center gap-1 rounded-md bg-black/60 px-1.5 py-0.5 text-[10px] text-white backdrop-blur-sm">
            {relation.asset.createdByNickname || relation.asset.createdByUsername}
          </div>
        )}

        {/* Generating overlay */}
        {isGenerating && (
          <div className="absolute inset-0 z-20 flex flex-col items-center justify-center gap-2 bg-black/60">
            <Spinner size="sm" className="text-white" />
            <span className="text-[10px] text-white">生成中...</span>
            <Tooltip delay={0}>
              <Tooltip.Trigger>
                <div
                  className="flex size-6 cursor-pointer items-center justify-center rounded-md bg-black/40 text-white backdrop-blur-sm transition-colors hover:bg-danger/80"
                  onClick={handleDelete}
                >
                  <Trash2 className="size-3.5" />
                </div>
              </Tooltip.Trigger>
              <Tooltip.Content placement="top">删除</Tooltip.Content>
            </Tooltip>
          </div>
        )}

        {/* Failed overlay with action buttons */}
        {isFailed && (
          <div className="absolute inset-0 z-20 flex flex-col items-center justify-center gap-2 bg-danger/60">
            <X className="size-4 text-white" />
            <span className="text-[10px] text-white">生成失败</span>
            <div className="flex items-center gap-1.5">
              {/* Regenerate button (disabled) */}
              <Tooltip delay={0}>
                <Tooltip.Trigger>
                  <div className="flex h-6 cursor-not-allowed items-center gap-1 rounded-md bg-black/40 px-2 text-white/50 backdrop-blur-sm">
                    <RefreshCw className="size-3" />
                    <span className="text-[10px]">重新生成</span>
                  </div>
                </Tooltip.Trigger>
                <Tooltip.Content placement="top">暂时不可用</Tooltip.Content>
              </Tooltip>

              {/* Delete button */}
              <Tooltip delay={0}>
                <Tooltip.Trigger>
                  <div
                    className="flex size-6 cursor-pointer items-center justify-center rounded-md bg-black/40 text-white backdrop-blur-sm transition-colors hover:bg-danger/80"
                    onClick={handleDelete}
                  >
                    <Trash2 className="size-3.5" />
                  </div>
                </Tooltip.Trigger>
                <Tooltip.Content placement="top">删除</Tooltip.Content>
              </Tooltip>
            </div>
          </div>
        )}

        {/* Hover overlay with action buttons */}
        {!isGenerating && !isFailed && (
          <div className="absolute inset-0 flex items-center justify-center bg-black/40 opacity-0 transition-opacity group-hover:opacity-100">
            {/* Center - Action buttons row */}
            <div className="flex items-center gap-1.5">
              {/* Drag handle - for all draggable assets */}
              {onDragStart && (
                <Tooltip delay={0}>
                  <Tooltip.Trigger>
                    <div
                      className="flex size-7 cursor-grab items-center justify-center rounded-md bg-black/60 text-white backdrop-blur-sm transition-colors hover:bg-accent/80 active:cursor-grabbing"
                      onMouseDown={(e) => e.stopPropagation()}
                    >
                      <GripVertical className="size-4" />
                    </div>
                  </Tooltip.Trigger>
                  <Tooltip.Content placement="top">拖拽到AI生成</Tooltip.Content>
                </Tooltip>
              )}

              {/* Edit button for images */}
              {assetType === "IMAGE" && relation.asset.fileUrl && (
                <Tooltip delay={0}>
                  <Tooltip.Trigger>
                    <div
                      className="flex size-7 cursor-pointer items-center justify-center rounded-md bg-black/60 text-white backdrop-blur-sm transition-colors hover:bg-accent/80"
                      onClick={handleEditClick}
                    >
                      <Edit3 className="size-4" />
                    </div>
                  </Tooltip.Trigger>
                  <Tooltip.Content placement="top">编辑</Tooltip.Content>
                </Tooltip>
              )}

              {/* Publish/Unpublish button (OFFICIAL/DRAFT toggle) */}
              <Tooltip delay={0}>
                <Tooltip.Trigger>
                  <div
                    className={`flex size-7 items-center justify-center rounded-md backdrop-blur-sm transition-colors ${
                      isUpdating
                        ? "cursor-wait bg-black/60 text-white"
                        : isOfficial
                          ? "cursor-pointer bg-accent text-accent-foreground hover:bg-accent/80"
                          : "cursor-pointer bg-black/60 text-white hover:bg-white/20"
                    }`}
                    onClick={(e) => {
                      e.stopPropagation();
                      if (!isUpdating) {
                        handleTogglePublish();
                      }
                    }}
                  >
                    {isUpdating ? (
                      <Loader2 className="size-4 animate-spin" />
                    ) : isOfficial ? (
                      <BadgeCheck className="size-4" />
                    ) : (
                      <CircleDashed className="size-4" />
                    )}
                  </div>
                </Tooltip.Trigger>
                <Tooltip.Content placement="top">
                  {isUpdating ? "更新中..." : isOfficial ? "设为草稿" : "设为正式"}
                  {!isUpdating && isCover && isOfficial && " (当前封面)"}
                </Tooltip.Content>
              </Tooltip>

              {/* Download button */}
              {relation.asset.fileUrl && (
                <Tooltip delay={0}>
                  <Tooltip.Trigger>
                    <div
                      className="flex size-7 cursor-pointer items-center justify-center rounded-md bg-black/60 text-white backdrop-blur-sm transition-colors hover:bg-white/20"
                      onClick={handleDownload}
                    >
                      <Download className="size-4" />
                    </div>
                  </Tooltip.Trigger>
                  <Tooltip.Content placement="top">下载</Tooltip.Content>
                </Tooltip>
              )}

              {/* Delete button */}
              <Tooltip delay={0}>
                <Tooltip.Trigger>
                  <div
                    className="flex size-7 cursor-pointer items-center justify-center rounded-md bg-black/60 text-white backdrop-blur-sm transition-colors hover:bg-danger/80"
                    onClick={handleDelete}
                  >
                    <Trash2 className="size-4" />
                  </div>
                </Tooltip.Trigger>
                <Tooltip.Content placement="top">删除</Tooltip.Content>
              </Tooltip>
            </div>
          </div>
        )}
      </div>

      {/* Full-screen Image Editor Modal */}
      <ImageEditorModal
        isOpen={isEditorOpen}
        onOpenChange={setIsEditorOpen}
        src={relation.asset.fileUrl || ""}
        refImages={refImages}
        scriptId={scriptId}
        workspaceId={workspaceId}
        entityType={entityType}
        entityId={entityId}
        title={`编辑图片 - ${relation.asset.name}`}
        onSave={onSave ? handleSaveFromEditor : undefined}
        onCancel={() => setIsEditorOpen(false)}
      />

      {/* Asset Detail Modal - Shared Component */}
      <AssetPreviewModal
        isOpen={isDetailOpen}
        onOpenChange={setIsDetailOpen}
        asset={assetPreviewInfo}
        relation={assetRelationInfo}
        actions={
          <>
            {assetType === "IMAGE" && relation.asset.fileUrl && (
              <Button
                size="sm"
                variant="secondary"
                onPress={() => {
                  setIsDetailOpen(false);
                  setIsEditorOpen(true);
                }}
              >
                <Edit3 className="size-4" />
                编辑图片
              </Button>
            )}
            <Button
              size="sm"
              variant={isOfficial ? "primary" : "secondary"}
              onPress={handleTogglePublish}
              isDisabled={isUpdating}
            >
              {isUpdating ? (
                <>
                  <Loader2 className="size-4 animate-spin" />
                  更新中...
                </>
              ) : isOfficial ? (
                <>
                  <CircleDashed className="size-4" />
                  设为草稿
                </>
              ) : (
                <>
                  <BadgeCheck className="size-4" />
                  设为正式
                </>
              )}
            </Button>
          </>
        }
      />
    </>
  );
}

export default AssetCard;
