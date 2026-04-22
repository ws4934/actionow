"use client";

/**
 * Entity List View Component
 * Displays entities in a list format with cover images and hover actions
 */

import { useState } from "react";
import { Button, Dropdown, Spinner } from "@heroui/react";
import { Film, MoreVertical, Edit3, Copy, Trash2, X, Image, FileVideo, FileAudio, File, User, FileText, MessageSquare } from "lucide-react";
import NextImage from "@/components/ui/content-image";
import { useTranslations } from "next-intl";
import type { EntityItem } from "../types";
import { StatusBadge } from "./status-badge";
import { DeleteConfirmModal } from "./delete-confirm-modal";
import { EmptyState } from "./empty-state";

// Get asset type icon
function getAssetTypeIcon(assetType?: string) {
  switch (assetType) {
    case "IMAGE":
      return <Image className="size-3.5" />;
    case "VIDEO":
      return <FileVideo className="size-3.5" />;
    case "AUDIO":
      return <FileAudio className="size-3.5" />;
    default:
      return <File className="size-3.5" />;
  }
}

// Get asset type label key
function getAssetTypeLabelKey(assetType?: string): string {
  switch (assetType) {
    case "IMAGE":
      return "assetType.image";
    case "VIDEO":
      return "assetType.video";
    case "AUDIO":
      return "assetType.audio";
    case "DOCUMENT":
      return "assetType.document";
    default:
      return "assetType.other";
  }
}

// Render media thumbnail based on asset type
function renderMediaThumbnail(item: EntityItem) {
  const { assetType, coverUrl, fileUrl } = item;

  // VIDEO: use video tag to show first frame
  if (assetType === "VIDEO") {
    const videoSrc = fileUrl || coverUrl;
    if (videoSrc) {
      return (
        <video
          src={videoSrc}
          className="size-full object-cover"
          muted
          playsInline
          preload="metadata"
        />
      );
    }
    return (
      <div className="flex size-full items-center justify-center bg-muted/20">
        <FileVideo className="size-6 text-muted/50" />
      </div>
    );
  }

  // AUDIO: show audio icon
  if (assetType === "AUDIO") {
    return (
      <div className="flex size-full items-center justify-center bg-gradient-to-br from-accent/10 to-accent/5">
        <FileAudio className="size-6 text-accent/50" />
      </div>
    );
  }

  // DOCUMENT: show document icon
  if (assetType === "DOCUMENT") {
    return (
      <div className="flex size-full items-center justify-center bg-gradient-to-br from-muted/20 to-muted/10">
        <FileText className="size-6 text-muted/50" />
      </div>
    );
  }

  // IMAGE or default: show cover image
  if (coverUrl) {
    return <NextImage src={coverUrl} alt="" fill className="object-cover" sizes="120px" />;
  }

  // Fallback placeholder
  return (
    <div className="flex size-full items-center justify-center">
      <Film className="size-6 text-muted/30" />
    </div>
  );
}

interface EntityListViewProps {
  items: EntityItem[];
  onSelect: (id: string) => void;
  onEdit?: (id: string) => void;
  onCopy?: (id: string) => void;
  onDelete?: (id: string) => void;
}

export function EntityListView({ items, onSelect, onEdit, onCopy, onDelete }: EntityListViewProps) {
  const [deleteConfirm, setDeleteConfirm] = useState<EntityItem | null>(null);
  const t = useTranslations("workspace.studio.common");
  const hasActions = onEdit || onCopy || onDelete;

  const handleAction = (key: string, item: EntityItem) => {
    switch (key) {
      case "edit":
        onEdit?.(item.id);
        break;
      case "copy":
        onCopy?.(item.id);
        break;
      case "delete":
        setDeleteConfirm(item);
        break;
    }
  };

  const handleConfirmDelete = () => {
    if (deleteConfirm) {
      onDelete?.(deleteConfirm.id);
      setDeleteConfirm(null);
    }
  };

  if (items.length === 0) {
    return <EmptyState title={t("noData")} />;
  }

  return (
    <>
      <div className="space-y-2">
        {items.map((item) => {
          const isGenerating = item.generationStatus === "GENERATING";
          const isFailed = item.generationStatus === "FAILED";
          return (
          <div
            key={item.id}
            className={`group flex h-20 items-center gap-3 rounded-xl bg-muted/5 p-3 transition-all hover:bg-muted/10 ${
              isGenerating ? "cursor-default" : "cursor-pointer"
            }`}
            onClick={() => !isGenerating && onSelect(item.id)}
          >
            {/* Cover Image - Always shown */}
            <div className="relative h-14 w-20 shrink-0 overflow-hidden rounded-lg bg-muted/10">
              {renderMediaThumbnail(item)}

              {/* Generating Overlay */}
              {isGenerating && (
                <div className="absolute inset-0 z-10 flex items-center justify-center bg-black/60">
                  <Spinner size="sm" className="text-white" />
                </div>
              )}

              {/* Failed Overlay */}
              {isFailed && (
                <div className="absolute inset-0 z-10 flex items-center justify-center bg-danger/60">
                  <X className="size-4 text-white" />
                </div>
              )}
            </div>

            {/* Content */}
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <p className="truncate text-sm font-medium transition-colors group-hover:text-accent">
                  {item.title}
                </p>
                {/* Asset type badge */}
                {item.assetType && (
                  <span className="inline-flex shrink-0 items-center gap-1 rounded bg-muted/20 px-1.5 py-0.5 text-[10px] text-muted">
                    {getAssetTypeIcon(item.assetType)}
                    {t(getAssetTypeLabelKey(item.assetType))}
                  </span>
                )}
              </div>
              {item.description && (
                <p className="mt-0.5 line-clamp-1 text-xs text-muted">{item.description}</p>
              )}
              {/* Creator name */}
              {(item.createdByNickname || item.createdByUsername) && (
                <p className="mt-0.5 flex items-center gap-1 text-[10px] text-muted/70">
                  <User className="size-3" />
                  {item.createdByNickname || item.createdByUsername}
                </p>
              )}
              {isGenerating && (
                <p className="mt-0.5 text-xs text-accent">{t("generating")}</p>
              )}
              {isFailed && (
                <p className="mt-0.5 text-xs text-danger">{t("generateFailed")}</p>
              )}
            </div>

            {/* Status */}
            <div className="flex shrink-0 items-center gap-2">
              {item.commentCount != null && item.commentCount > 0 && (
                <span className="flex items-center gap-0.5 text-[10px] text-muted">
                  <MessageSquare className="size-3" />
                  {item.commentCount}
                </span>
              )}
              <StatusBadge status={item.status} />
            </div>

            {/* Hover Actions - More Button */}
            {hasActions && (!isGenerating || !!onDelete) && (
              <div
                className="shrink-0 opacity-0 transition-opacity group-hover:opacity-100"
                onClick={(e) => e.stopPropagation()}
                onPointerDown={(e) => e.stopPropagation()}
              >
                <Dropdown>
                  <Button
                    variant="secondary"
                    size="sm"
                    isIconOnly
                    className="size-7 min-w-0 border border-border/50 bg-background/90 shadow-sm"
                  >
                    <MoreVertical className="size-3.5" />
                  </Button>
                  <Dropdown.Popover>
                    <Dropdown.Menu
                      onAction={(actionId) => {
                        handleAction(String(actionId), item);
                      }}
                    >
                      {!isGenerating && onEdit && (
                        <Dropdown.Item id="edit" textValue={t("edit")}>
                          <Edit3 className="size-4" />
                          <span>{t("edit")}</span>
                        </Dropdown.Item>
                      )}
                      {!isGenerating && onCopy && (
                        <Dropdown.Item id="copy" textValue={t("copy")}>
                          <Copy className="size-4" />
                          <span>{t("copy")}</span>
                        </Dropdown.Item>
                      )}
                      {onDelete && (
                        <Dropdown.Item id="delete" textValue={t("delete")} className="text-danger">
                          <Trash2 className="size-4" />
                          <span>{t("delete")}</span>
                        </Dropdown.Item>
                      )}
                    </Dropdown.Menu>
                  </Dropdown.Popover>
                </Dropdown>
              </div>
            )}
          </div>
          );
        })}
      </div>

      {/* Delete confirmation modal */}
      <DeleteConfirmModal
        title={deleteConfirm?.title ?? null}
        isOpen={!!deleteConfirm}
        onClose={() => setDeleteConfirm(null)}
        onConfirm={handleConfirmDelete}
      />
    </>
  );
}

export default EntityListView;
