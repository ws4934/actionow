"use client";

import NextImage from "next/image";
import { useRef, useEffect } from "react";
import { useTranslations } from "next-intl";
import { ScrollShadow, Spinner, ButtonGroup, Button } from "@heroui/react";
import { Search, Check } from "lucide-react";
import type { EntityCategoryKey, LinkedEntity, EntityCategoryDef } from "../types";

interface EntityItem {
  id: string;
  name: string;
  coverUrl?: string | null;
}

interface MentionPopupProps {
  isOpen: boolean;
  anchorRef: React.RefObject<HTMLTextAreaElement | null>;
  searchQuery: string;
  activeCategory: EntityCategoryKey;
  categories: EntityCategoryDef[];
  filteredEntities: EntityItem[];
  highlightIndex: number;
  linkedEntities: LinkedEntity[];
  isLoading: boolean;
  onCategoryChange: (key: EntityCategoryKey) => void;
  onSelect: (entity: LinkedEntity) => void;
  onClose: () => void;
}

export function MentionPopup({
  isOpen,
  anchorRef,
  searchQuery,
  activeCategory,
  categories,
  filteredEntities,
  highlightIndex,
  linkedEntities,
  isLoading,
  onCategoryChange,
  onSelect,
  onClose,
}: MentionPopupProps) {
  const t = useTranslations("workspace.agent");
  const popupRef = useRef<HTMLDivElement>(null);
  const highlightedItemRef = useRef<HTMLButtonElement>(null);

  // Scroll highlighted item into view
  useEffect(() => {
    highlightedItemRef.current?.scrollIntoView({ block: "nearest" });
  }, [highlightIndex]);

  // Close on click outside (excluding the TextArea anchor)
  useEffect(() => {
    if (!isOpen) return;
    const handleClick = (e: MouseEvent) => {
      const target = e.target as Node;
      if (
        popupRef.current && !popupRef.current.contains(target) &&
        anchorRef.current && !anchorRef.current.contains(target)
      ) {
        onClose();
      }
    };
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, [isOpen, onClose, anchorRef]);

  if (!isOpen) return null;

  const isLinked = (id: string, category: EntityCategoryKey) =>
    linkedEntities.some((e) => e.category === category && e.id === id);

  return (
    <div ref={popupRef} className="absolute bottom-full left-0 z-50 mb-1 w-full">
      {/* Popover-style container: bg-overlay + shadow-overlay + rounded-3xl */}
      <div className="rounded-3xl bg-overlay p-0 text-sm shadow-overlay">
        {/* Search query indicator */}
        {searchQuery && (
          <div className="flex items-center gap-2 border-b border-border/50 px-3 py-1.5">
            <Search className="size-3.5 shrink-0 text-muted" />
            <span className="text-xs text-muted">{searchQuery}</span>
          </div>
        )}

        {/* Category Tabs */}
        <div className="border-b border-border/50 px-2 py-1.5">
          <ButtonGroup size="sm" variant="ghost" className="w-full flex-wrap">
            {categories.map((cat) => {
              const Icon = cat.icon;
              const isActive = cat.key === activeCategory;
              return (
                <Button
                  key={cat.key}
                  variant={isActive ? "primary" : undefined}
                  className="flex-1 gap-1 text-xs"
                  onPress={() => onCategoryChange(cat.key)}
                >
                  <Icon className="size-3.5" />
                  {t(cat.labelKey)}
                </Button>
              );
            })}
          </ButtonGroup>
        </div>

        {/* Entity List */}
        <ScrollShadow className="max-h-64" hideScrollBar>
          {isLoading ? (
            <div className="flex items-center justify-center py-8">
              <Spinner size="sm" />
            </div>
          ) : filteredEntities.length === 0 ? (
            <div className="py-8 text-center text-sm text-muted">
              {t("mention.noResults")}
            </div>
          ) : (
            <div className="p-1">
              {filteredEntities.map((entity, idx) => {
                const linked = isLinked(entity.id, activeCategory);
                const highlighted = idx === highlightIndex;
                return (
                  <button
                    key={entity.id}
                    ref={highlighted ? highlightedItemRef : undefined}
                    onMouseDown={(e) => {
                      e.preventDefault();
                      onSelect({
                        id: entity.id,
                        category: activeCategory,
                        name: entity.name,
                        coverUrl: entity.coverUrl,
                      });
                    }}
                    className={`flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-left transition-colors ${
                      highlighted ? "bg-accent/10" : "hover:bg-muted/10"
                    }`}
                  >
                    {entity.coverUrl ? (
                      <NextImage
                        src={entity.coverUrl}
                        alt={entity.name}
                        width={36}
                        height={36}
                        className="size-9 shrink-0 rounded-lg object-cover"
                        unoptimized
                      />
                    ) : (
                      <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-muted/10 text-sm text-muted">
                        {entity.name[0]}
                      </div>
                    )}
                    <span className="min-w-0 flex-1 truncate">{entity.name}</span>
                    {linked && <Check className="size-4 shrink-0 text-accent" />}
                  </button>
                );
              })}
            </div>
          )}
        </ScrollShadow>

        {/* Footer */}
        <div className="flex items-center justify-between gap-2 border-t border-border/50 px-3 py-2 text-xs text-muted">
          <span>{t("mention.linked")}: {linkedEntities.length}</span>
          <span className="hidden sm:inline">{t("dismissPanelsHint")}</span>
        </div>
      </div>
    </div>
  );
}
