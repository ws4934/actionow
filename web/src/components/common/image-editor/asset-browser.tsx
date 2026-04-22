"use client";

import { useState, useEffect, useCallback, useMemo, useRef } from "react";
import { Button, ScrollShadow, toast} from "@heroui/react";
import {
  Image as ImageIcon,
  Sparkles,
  Upload,
  RefreshCw,
  GripVertical,
} from "lucide-react";
import { projectService } from "@/lib/api/services/project.service";
import type { AssetListDTO } from "@/lib/api/dto/project.dto";
import { getProxiedImageUrl } from "@/lib/utils/image-proxy";
import { useLocale } from "next-intl";
import { getErrorFromException } from "@/lib/api";

type SourceFilter = "all" | "ai_generated" | "upload";

interface AssetBrowserProps {
  scriptId?: string;
  workspaceId?: string;
  refImages?: string[];
  onImageSelect?: (imageUrl: string) => void;
  onImageDragStart?: (imageUrl: string) => void;
}

interface AssetItem {
  id: string;
  name: string;
  url: string;
  thumbnailUrl?: string;
  source: "AI_GENERATED" | "UPLOAD" | "EXTERNAL" | "REFERENCE";
}

const FILTER_TABS: { id: SourceFilter; label: string; icon: React.ReactNode }[] = [
  { id: "all", label: "全部", icon: <ImageIcon className="size-4" /> },
  { id: "ai_generated", label: "AI生成", icon: <Sparkles className="size-4" /> },
  { id: "upload", label: "用户上传", icon: <Upload className="size-4" /> },
];

export function AssetBrowser({
  scriptId,
  workspaceId,
  refImages = [],
  onImageSelect,
  onImageDragStart,
}: AssetBrowserProps) {
  const locale = useLocale();
  const [activeTab, setActiveTab] = useState<SourceFilter>("all");
  const [assets, setAssets] = useState<AssetItem[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [draggedImage, setDraggedImage] = useState<string | null>(null);

  // Track if we've already loaded assets for this scriptId/workspaceId
  const loadedRef = useRef<string | null>(null);
  const isLoadingRef = useRef(false);

  // Stable reference for refImages - use JSON string comparison
  const refImagesKey = JSON.stringify(refImages);

  // Convert refImages to AssetItems
  const refImageAssets = useMemo<AssetItem[]>(() => {
    return refImages.map((url, index) => ({
      id: `ref-${index}`,
      name: `参考图 ${index + 1}`,
      url,
      thumbnailUrl: url,
      source: "REFERENCE" as const,
    }));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [refImagesKey]);

  // Load assets from API (similar to asset-tab)
  const loadAssets = useCallback(async (force = false) => {
    if (!workspaceId || !scriptId) return;

    const loadKey = `${workspaceId}-${scriptId}`;

    // Prevent duplicate loads unless forced
    if (!force && loadedRef.current === loadKey) return;
    if (isLoadingRef.current) return;

    isLoadingRef.current = true;
    setIsLoading(true);

    try {
      // Load script assets (same approach as asset-tab)
      const scriptAssets = await projectService.getAssetsByScript( scriptId);

      // Filter to IMAGE assets with fileUrl
      const imageAssets = scriptAssets.filter((a) => a.assetType === "IMAGE" && a.fileUrl);

      const allAssets: AssetItem[] = imageAssets.map((asset) => ({
        id: asset.id,
        name: asset.name,
        url: asset.fileUrl!,
        thumbnailUrl: asset.thumbnailUrl || asset.fileUrl!,
        source: (asset.source || "UPLOAD") as "AI_GENERATED" | "UPLOAD" | "EXTERNAL",
      }));

      setAssets(allAssets);
      loadedRef.current = loadKey;
    } catch (error) {
      console.error("Failed to load assets:", error);
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setIsLoading(false);
      isLoadingRef.current = false;
    }
  }, [workspaceId, scriptId]);

  // Load assets on mount and when scriptId/workspaceId change
  useEffect(() => {
    loadAssets();
  }, [loadAssets]);

  // Filter assets based on active tab
  const filteredAssets = useMemo(() => {
    // Always include reference images at the start
    const allItems = [...refImageAssets, ...assets];

    if (activeTab === "all") {
      return allItems;
    }
    if (activeTab === "ai_generated") {
      return allItems.filter((a) => a.source === "AI_GENERATED");
    }
    if (activeTab === "upload") {
      // Include both UPLOAD and REFERENCE (user-provided ref images)
      return allItems.filter((a) => a.source === "UPLOAD" || a.source === "REFERENCE");
    }
    return allItems;
  }, [assets, refImageAssets, activeTab]);

  // Handle drag start
  const handleDragStart = useCallback(
    (e: React.DragEvent, asset: AssetItem) => {
      e.dataTransfer.setData("text/plain", asset.url);
      e.dataTransfer.setData("application/json", JSON.stringify(asset));
      e.dataTransfer.effectAllowed = "copy";
      setDraggedImage(asset.url);
      onImageDragStart?.(asset.url);
    },
    [onImageDragStart]
  );

  // Handle drag end
  const handleDragEnd = useCallback(() => {
    setDraggedImage(null);
  }, []);

  // Handle click to select
  const handleImageClick = useCallback(
    (asset: AssetItem) => {
      onImageSelect?.(asset.url);
    },
    [onImageSelect]
  );

  // Handle manual refresh
  const handleRefresh = useCallback(() => {
    loadAssets(true);
  }, [loadAssets]);

  return (
    <div className="flex h-full flex-col">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-border px-4 py-3">
        <h3 className="text-sm font-medium">素材库</h3>
        <Button
          variant="ghost"
          size="sm"
          isIconOnly
          onPress={handleRefresh}
          isDisabled={isLoading}
        >
          <RefreshCw className={`size-4 ${isLoading ? "animate-spin" : ""}`} />
        </Button>
      </div>

      {/* Filter Tabs */}
      <div className="border-b border-border px-2 py-2">
        <div className="flex flex-wrap gap-1">
          {FILTER_TABS.map((tab) => (
            <Button
              key={tab.id}
              variant={activeTab === tab.id ? "secondary" : "ghost"}
              size="sm"
              className="h-7 gap-1.5 px-2 text-xs"
              onPress={() => setActiveTab(tab.id)}
            >
              {tab.icon}
              {tab.label}
            </Button>
          ))}
        </div>
      </div>

      {/* Asset Grid */}
      <ScrollShadow className="flex-1 overflow-y-auto p-2">
        {isLoading ? (
          <div className="flex h-32 items-center justify-center text-sm text-muted">
            加载中...
          </div>
        ) : filteredAssets.length === 0 ? (
          <div className="flex h-32 flex-col items-center justify-center gap-2 text-sm text-muted">
            <ImageIcon className="size-8 opacity-50" />
            <span>暂无图片</span>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-2 xl:grid-cols-3">
            {filteredAssets.map((asset) => (
              <div
                key={asset.id}
                className={`group relative aspect-square cursor-grab overflow-hidden rounded-lg border border-border bg-muted/20 transition-all hover:border-accent hover:shadow-md active:cursor-grabbing ${
                  draggedImage === asset.url ? "opacity-50 ring-2 ring-accent" : ""
                }`}
                draggable
                onDragStart={(e) => handleDragStart(e, asset)}
                onDragEnd={handleDragEnd}
                onClick={() => handleImageClick(asset)}
              >
                {/* Drag Handle Indicator */}
                <div className="absolute left-1 top-1 z-10 rounded bg-black/50 p-0.5 opacity-0 transition-opacity group-hover:opacity-100">
                  <GripVertical className="size-3 text-white" />
                </div>

                {/* Image */}
                <img
                  src={getProxiedImageUrl(asset.thumbnailUrl || asset.url)}
                  alt={asset.name}
                  className="size-full object-cover"
                  loading="lazy"
                  onError={(e) => {
                    (e.target as HTMLImageElement).src =
                      "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='100' height='100'%3E%3Crect fill='%23f0f0f0' width='100' height='100'/%3E%3Ctext x='50' y='50' text-anchor='middle' dy='.3em' fill='%23999' font-size='12'%3ENo Image%3C/text%3E%3C/svg%3E";
                  }}
                />

                {/* Hover Overlay */}
                <div className="absolute inset-0 flex items-end bg-gradient-to-t from-black/60 via-transparent to-transparent p-2 opacity-0 transition-opacity group-hover:opacity-100">
                  <span className="truncate text-xs text-white">{asset.name}</span>
                </div>

                {/* Source Badge */}
                {asset.source === "AI_GENERATED" && (
                  <div className="absolute right-1 top-1 flex items-center gap-0.5 rounded bg-accent/80 px-1.5 py-0.5 text-[10px] text-white">
                    <Sparkles className="size-3" />
                    AI
                  </div>
                )}
                {asset.source === "REFERENCE" && (
                  <div className="absolute right-1 top-1 rounded bg-black/60 px-1.5 py-0.5 text-[10px] text-white">
                    参考
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </ScrollShadow>

      {/* Footer - Usage hint */}
      <div className="border-t border-border px-3 py-2">
        <p className="text-xs text-muted">
          点击或拖拽图片到画布中
        </p>
      </div>
    </div>
  );
}

export default AssetBrowser;
