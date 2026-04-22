"use client";

/**
 * Assets Section Component
 * Displays categorized assets (images, videos, audio)
 * Supports dragging assets to AI generation panel
 */

import { Button } from "@heroui/react";
import { Image as ImageIcon, FileImage, Video, Music, Play, GripVertical } from "lucide-react";
import { useTranslations } from "next-intl";
import type { RelationType } from "@/lib/api/dto";
import { AssetCardGrid, type AssetWithRelation } from "../../asset-card";
import { useDragDropActions, createAssetDragData, ASSET_DRAG_TYPE } from "@/lib/stores/drag-drop-store";

interface AssetsSectionProps {
  title: string;
  imageAssets: AssetWithRelation[];
  videoAssets: AssetWithRelation[];
  audioAssets: AssetWithRelation[];
  onStatusChange?: (asset: AssetWithRelation, relationType: RelationType) => void;
  columns?: 2 | 3 | 4;
  draggable?: boolean;
}

export function AssetsSection({
  title,
  imageAssets,
  videoAssets,
  audioAssets,
  onStatusChange,
  columns = 3,
  draggable = true,
}: AssetsSectionProps) {
  const { startDrag, endDrag } = useDragDropActions();
  const t = useTranslations("workspace.studio.asset.section");
  const totalCount = imageAssets.length + videoAssets.length + audioAssets.length;

  // Handle audio drag
  const handleAudioDragStart = (e: React.DragEvent, asset: AssetWithRelation) => {
    const dragData = createAssetDragData({
      assetId: asset.id,
      url: asset.fileUrl,
      name: asset.name,
      mimeType: asset.mimeType,
      fileSize: asset.fileSize,
      assetType: asset.assetType,
    });
    e.dataTransfer.setData(ASSET_DRAG_TYPE, JSON.stringify(dragData));
    e.dataTransfer.effectAllowed = "copy";
    startDrag(dragData);
  };

  const handleDragEnd = () => {
    endDrag();
  };

  if (totalCount === 0) {
    return null;
  }

  return (
    <div className="rounded-xl bg-muted/5 p-4">
      <div className="mb-3 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <ImageIcon className="size-4 text-accent" />
          <span className="text-sm font-medium">{title}</span>
        </div>
        <span className="text-xs text-muted">{t("totalCount", { count: totalCount })}</span>
      </div>

      <div className="space-y-4">
        {/* Images */}
        {imageAssets.length > 0 && (
          <div>
            <div className="mb-2 flex items-center gap-1.5 text-xs text-muted">
              <FileImage className="size-3" />
              {t("imageLabel")} ({imageAssets.length})
              {draggable && <span className="text-muted/60">· {t("dragHint")}</span>}
            </div>
            <AssetCardGrid
              assets={imageAssets.slice(0, 8)}
              columns={columns}
              size="md"
              onStatusChange={onStatusChange}
              draggable={draggable}
            />
            {imageAssets.length > 8 && (
              <p className="mt-2 text-center text-xs text-muted">{t("moreImages", { count: imageAssets.length - 8 })}</p>
            )}
          </div>
        )}

        {/* Videos */}
        {videoAssets.length > 0 && (
          <div>
            <div className="mb-2 flex items-center gap-1.5 text-xs text-muted">
              <Video className="size-3" />
              {t("videoLabel")} ({videoAssets.length})
            </div>
            <AssetCardGrid
              assets={videoAssets.slice(0, 6)}
              columns={Math.max(2, columns - 1) as 2 | 3 | 4 | 5}
              size="md"
              onStatusChange={onStatusChange}
              draggable={draggable}
            />
            {videoAssets.length > 6 && (
              <p className="mt-2 text-center text-xs text-muted">{t("moreVideos", { count: videoAssets.length - 6 })}</p>
            )}
          </div>
        )}

        {/* Audio */}
        {audioAssets.length > 0 && (
          <div>
            <div className="mb-2 flex items-center gap-1.5 text-xs text-muted">
              <Music className="size-3" />
              {t("audioLabel")} ({audioAssets.length})
            </div>
            <div className="space-y-1.5">
              {audioAssets.slice(0, 4).map((asset) => (
                <div
                  key={asset.id}
                  draggable={draggable}
                  onDragStart={(e) => handleAudioDragStart(e, asset)}
                  onDragEnd={handleDragEnd}
                  className={`group flex items-center gap-2 rounded-lg bg-muted/5 p-2 transition-colors hover:bg-muted/10 ${
                    draggable ? "cursor-grab active:cursor-grabbing" : ""
                  }`}
                >
                  {draggable && (
                    <GripVertical className="size-3 text-muted/40 opacity-0 transition-opacity group-hover:opacity-100" />
                  )}
                  <div className="flex size-8 items-center justify-center rounded-lg bg-accent/10">
                    <Music className="size-4 text-accent" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-xs font-medium">{asset.name}</p>
                  </div>
                  <Button variant="ghost" size="sm" isIconOnly className="size-6">
                    <Play className="size-3" />
                  </Button>
                </div>
              ))}
              {audioAssets.length > 4 && (
                <p className="text-center text-xs text-muted">{t("moreAudios", { count: audioAssets.length - 4 })}</p>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default AssetsSection;
