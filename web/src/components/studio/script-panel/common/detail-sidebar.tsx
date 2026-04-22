"use client";

/**
 * Detail Sidebar Component
 * Sidebar for detail view with collapsible state
 * - Expanded: 16:9 card with cover image, title and status
 * - Collapsed: 1:1 square card with cover image, hover shows title
 */

import { ReactNode } from "react";
import { Button, ScrollShadow, Tooltip, Spinner } from "@heroui/react";
import { PanelLeft, PanelLeftClose, Image as ImageIcon, X, Trash2, FileVideo, FileAudio, File, User, MessageSquare } from "lucide-react";
import NextImage from "@/components/ui/content-image";
import { useTranslations } from "next-intl";
import type { EntityItem } from "../types";

interface DetailSidebarItemProps {
  item: EntityItem;
  isSelected: boolean;
  isCollapsed: boolean;
  onSelect: (id: string) => void;
  /** Callback when delete button is clicked */
  onDelete?: (id: string) => void;
  /** Callback when the comment icon is clicked — opens comment panel for this entity */
  onComment?: (id: string) => void;
  /** Custom icon when no cover image */
  placeholderIcon?: ReactNode;
  /** Entity type label for tooltip (e.g., "角色", "场景") */
  entityTypeLabel?: string;
  /** Show generating status */
  isGenerating?: boolean;
  /** Show failed status */
  isFailed?: boolean;
}

export function DetailSidebarItem({
  item,
  isSelected,
  isCollapsed,
  onSelect,
  onDelete,
  onComment,
  placeholderIcon,
  entityTypeLabel,
  isGenerating = false,
  isFailed = false,
}: DetailSidebarItemProps) {
  const PlaceholderIcon = placeholderIcon || <ImageIcon className="size-5 text-muted/30" />;
  const isDisabled = isGenerating;
  const t = useTranslations("workspace.studio.common");

  const handleDeleteClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    onDelete?.(item.id);
  };

  const handleCommentClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    onComment?.(item.id);
  };

  // Render media content based on asset type
  const renderMediaContent = (className: string) => {
    // VIDEO: use video tag to show first frame (no controls)
    if (item.assetType === "VIDEO" && (item.fileUrl || item.coverUrl)) {
      return (
        <video
          src={item.fileUrl || item.coverUrl || ""}
          className={className}
          muted
          playsInline
          preload="metadata"
        />
      );
    }
    // AUDIO: show audio icon
    if (item.assetType === "AUDIO") {
      return (
        <div className="flex size-full items-center justify-center bg-muted/20">
          <FileAudio className="size-8 text-muted/50" />
        </div>
      );
    }
    // IMAGE or other: use img tag
    if (item.coverUrl) {
      return <NextImage src={item.coverUrl} alt="" fill className={className} />;
    }
    // No cover
    return (
      <div className="flex size-full items-center justify-center">
        {PlaceholderIcon}
      </div>
    );
  };

  // Card content for collapsed state (1:1 square)
  const collapsedCard = (
    <div className="relative aspect-square w-full overflow-hidden rounded-xl bg-muted/10">
      {renderMediaContent("object-cover")}
      {/* Generating overlay */}
      {isGenerating && (
        <div className="absolute inset-0 flex items-center justify-center bg-black/60">
          <Spinner size="sm" className="text-white" />
        </div>
      )}
      {/* Failed overlay */}
      {isFailed && (
        <div className="absolute inset-0 flex items-center justify-center bg-danger/60">
          <X className="size-4 text-white" />
        </div>
      )}
      {/* No delete button in collapsed mode - easy to misclick */}
    </div>
  );

  // Card content for expanded state (16:9)
  const expandedCard = (
    <div className="relative aspect-video w-full overflow-hidden rounded-xl bg-muted/10">
      {renderMediaContent("object-cover")}
      {/* Generating overlay */}
      {isGenerating && (
        <div className="absolute inset-0 flex items-center justify-center bg-black/60">
          <div className="text-center">
            <Spinner size="sm" className="text-white" />
            <p className="mt-1 text-[10px] text-white/80">{t("generating")}</p>
          </div>
        </div>
      )}
      {/* Failed overlay */}
      {isFailed && (
        <div className="absolute inset-0 flex items-center justify-center bg-danger/60">
          <div className="text-center">
            <X className="mx-auto size-5 text-white" />
            <p className="mt-1 text-[10px] text-white/80">{t("generateFailed")}</p>
          </div>
        </div>
      )}
      {/* Delete button - hover (top right) */}
      {onDelete && !isFailed && (
        <Tooltip delay={0}>
          <Tooltip.Trigger>
            <div
              className={`absolute right-1.5 top-1.5 z-10 flex size-6 cursor-pointer items-center justify-center rounded-md bg-black/40 text-white backdrop-blur-sm transition-all hover:bg-danger/80 ${isGenerating ? "opacity-100" : "opacity-0 group-hover:opacity-100"}`}
              onClick={handleDeleteClick}
            >
              <Trash2 className="size-3" />
            </div>
          </Tooltip.Trigger>
          <Tooltip.Content>{t("delete")}</Tooltip.Content>
        </Tooltip>
      )}
      {/* Bottom info overlay */}
      {!isGenerating && !isFailed && (
        <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/80 via-black/40 to-transparent px-2.5 pb-2 pt-6">
          <div className="flex items-end gap-1.5">
            {/* Text info */}
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-1.5">
                <p className="truncate text-xs font-medium text-white">{item.title}</p>
                {/* Asset type icon */}
                {item.assetType && (
                  <span className="shrink-0 text-white/70">
                    {item.assetType === "IMAGE" && <ImageIcon className="size-3" />}
                    {item.assetType === "VIDEO" && <FileVideo className="size-3" />}
                    {item.assetType === "AUDIO" && <FileAudio className="size-3" />}
                    {!["IMAGE", "VIDEO", "AUDIO"].includes(item.assetType) && <File className="size-3" />}
                  </span>
                )}
              </div>
              {/* Creator name */}
              {(item.createdByNickname || item.createdByUsername) && (
                <p className="mt-0.5 flex items-center gap-1 truncate text-[10px] text-white/60">
                  <User className="size-2.5" />
                  {item.createdByNickname || item.createdByUsername}
                </p>
              )}
              {item.status && !item.assetType && (
                <p className="mt-0.5 truncate text-[10px] text-white/70">{item.status}</p>
              )}
            </div>
            {/* Comment icon — only rendered when onComment is provided */}
            {onComment && (
              <Tooltip delay={0}>
                <Tooltip.Trigger>
                  <div
                    className="shrink-0 flex size-6 cursor-pointer items-center justify-center rounded-md bg-black/40 text-white/70 backdrop-blur-sm transition-all hover:bg-accent/80 hover:text-white opacity-0 group-hover:opacity-100"
                    onClick={handleCommentClick}
                  >
                    <MessageSquare className="size-3" />
                  </div>
                </Tooltip.Trigger>
                <Tooltip.Content>{t("viewComments")}</Tooltip.Content>
              </Tooltip>
            )}
          </div>
        </div>
      )}
      {/* Selected indicator */}
      {isSelected && (
        <div className="absolute inset-0 rounded-xl ring-2 ring-inset ring-accent" />
      )}
    </div>
  );

  // Collapsed state: wrap in Tooltip for hover info
  if (isCollapsed) {
    return (
      <Tooltip delay={0}>
        <Tooltip.Trigger>
          <div
            className={`group relative overflow-hidden rounded-xl transition-all ${
              isDisabled ? "cursor-default opacity-70" : "cursor-pointer"
            } ${
              isSelected
                ? "ring-2 ring-accent/50"
                : "hover:ring-1 hover:ring-muted/30"
            }`}
            onClick={() => !isDisabled && onSelect(item.id)}
          >
            {collapsedCard}
          </div>
        </Tooltip.Trigger>
        <Tooltip.Content placement="right" className="max-w-52">
          <div>
            <p className="font-medium">
              {entityTypeLabel ? `${entityTypeLabel}: ${item.title}` : item.title}
            </p>
            {item.description && (
              <p className="mt-1 text-xs text-muted">{item.description}</p>
            )}
            {isGenerating && <p className="mt-1 text-xs text-accent">{t("generating")}</p>}
            {isFailed && <p className="mt-1 text-xs text-danger">{t("generateFailed")}</p>}
          </div>
        </Tooltip.Content>
      </Tooltip>
    );
  }

  // Expanded state: no tooltip wrapper needed
  return (
    <div
      className={`group relative overflow-hidden rounded-xl transition-all ${
        isDisabled ? "cursor-default opacity-70" : "cursor-pointer"
      } ${
        isSelected
          ? "ring-2 ring-accent/50"
          : "hover:ring-1 hover:ring-muted/30"
      }`}
      onClick={() => !isDisabled && onSelect(item.id)}
    >
      {expandedCard}
    </div>
  );
}

interface DetailSidebarProps {
  items: EntityItem[];
  selectedId: string | null;
  onSelect: (id: string) => void;
  /** Callback when delete button is clicked */
  onDelete?: (id: string) => void;
  /** Callback when comment icon is clicked on a sidebar item */
  onComment?: (id: string) => void;
  isCollapsed: boolean;
  onToggle: () => void;
  /** Custom icon when no cover image */
  placeholderIcon?: ReactNode;
  /** Entity type label for tooltip (e.g., "角色", "场景") */
  entityTypeLabel?: string;
  /** Width when expanded */
  expandedWidth?: string;
  /** Width when collapsed */
  collapsedWidth?: string;
  /** Check if item is generating */
  isItemGenerating?: (item: EntityItem) => boolean;
  /** Check if item is failed */
  isItemFailed?: (item: EntityItem) => boolean;
}

export function DetailSidebar({
  items,
  selectedId,
  onSelect,
  onDelete,
  onComment,
  isCollapsed,
  onToggle,
  placeholderIcon,
  entityTypeLabel,
  expandedWidth = "w-52",
  collapsedWidth = "w-16",
  isItemGenerating,
  isItemFailed,
}: DetailSidebarProps) {
  const t = useTranslations("workspace.studio.common");
  return (
    <div
      className={`shrink-0 overflow-hidden rounded-xl bg-muted/5 transition-all duration-300 ${
        isCollapsed ? collapsedWidth : expandedWidth
      }`}
    >
      <div className="flex h-full flex-col">
        {/* Toggle Header */}
        <div className="flex shrink-0 items-center justify-center border-b border-muted/10 p-2">
          <Button
            variant="ghost"
            size="sm"
            className={`gap-1.5 text-xs ${isCollapsed ? "px-2" : "w-full justify-start px-2"}`}
            onPress={onToggle}
          >
            {isCollapsed ? (
              <PanelLeft className="size-4" />
            ) : (
              <>
                <PanelLeftClose className="size-4" />
                <span>{t("collapseList")}</span>
              </>
            )}
          </Button>
        </div>

        {/* Items List */}
        <ScrollShadow className="min-h-0 flex-1 p-2" hideScrollBar>
          <div className="space-y-2">
            {items.map((item) => (
              <DetailSidebarItem
                key={item.id}
                item={item}
                isSelected={selectedId === item.id || (!selectedId && items[0]?.id === item.id)}
                isCollapsed={isCollapsed}
                onSelect={onSelect}
                onDelete={onDelete}
                onComment={onComment}
                placeholderIcon={placeholderIcon}
                entityTypeLabel={entityTypeLabel}
                isGenerating={isItemGenerating?.(item)}
                isFailed={isItemFailed?.(item)}
              />
            ))}
          </div>
        </ScrollShadow>
      </div>
    </div>
  );
}

export default DetailSidebar;
