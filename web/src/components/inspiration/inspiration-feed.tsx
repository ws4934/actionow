"use client";

import { useMemo, useState, useEffect, useCallback } from "react";
import { ScrollShadow, Button, Spinner } from "@heroui/react";
import { ArrowDown } from "lucide-react";
import type { InspirationRecordDTO, InspirationAssetDTO } from "@/lib/api/dto/inspiration.dto";
import {
  MasonryAssetCard,
  MasonryStatusCard,
  MasonrySkeletonCard,
  type GalleryItem,
} from "./inspiration-record-card";
import { InspirationEmptyState } from "./inspiration-empty-state";

// ── Responsive column count based on container width ──

const COL_BREAKPOINTS = { sm: 640, md: 900, lg: 1200, xl: 1600 };

function widthToColumns(width: number) {
  if (width >= COL_BREAKPOINTS.xl) return 6;
  if (width >= COL_BREAKPOINTS.lg) return 5;
  if (width >= COL_BREAKPOINTS.md) return 4;
  if (width >= COL_BREAKPOINTS.sm) return 3;
  return 2;
}

function useColumnCount() {
  const [columns, setColumns] = useState(4);
  const [el, setEl] = useState<HTMLElement | null>(null);

  // callback ref — fires whenever the DOM element mounts/unmounts
  const masonryRef = useCallback((node: HTMLDivElement | null) => {
    setEl(node);
  }, []);

  useEffect(() => {
    if (!el) return;

    // set immediately from current size
    const width = el.getBoundingClientRect().width;
    if (width > 0) setColumns(widthToColumns(width));

    const observer = new ResizeObserver((entries) => {
      const w = entries[0]?.contentRect.width ?? 0;
      if (w <= 0) return;
      setColumns(widthToColumns(w));
    });

    observer.observe(el);
    return () => observer.disconnect();
  }, [el]);

  return { columns, masonryRef };
}

// ── Masonry item union type ──

type MasonryItem =
  | { type: "asset"; key: string; asset: InspirationAssetDTO; record: InspirationRecordDTO }
  | { type: "status"; key: string; record: InspirationRecordDTO }
  | { type: "skeleton"; key: string; record: InspirationRecordDTO };

// ── Component ──

interface InspirationFeedProps {
  records: InspirationRecordDTO[];
  isLoading: boolean;
  scrollContainerRef: React.RefObject<HTMLDivElement | null>;
  bottomRef: React.RefObject<HTMLDivElement | null>;
  onScroll: () => void;
  onScrollToBottom: () => void;
  showScrollToBottom: boolean;
  onRetry?: (recordId: string) => void;
  onDelete?: (recordId: string) => void;
  onTemplateClick: (prompt: string) => void;
}

export function InspirationFeed({
  records,
  isLoading,
  scrollContainerRef,
  bottomRef,
  onScroll,
  onScrollToBottom,
  showScrollToBottom,
  onRetry,
  onDelete,
  onTemplateClick,
}: InspirationFeedProps) {
  const { columns: columnCount, masonryRef } = useColumnCount();

  // Flatten records into a single ordered stream of masonry items
  // PENDING/RUNNING records without assets → skeleton placeholder
  // FAILED records without assets → status card (shows error)
  const masonryItems = useMemo(() => {
    const items: MasonryItem[] = [];
    for (const record of records) {
      if (record.assets.length > 0) {
        for (const asset of record.assets) {
          items.push({ type: "asset", key: asset.id, asset, record });
        }
      } else if (record.status === "PENDING" || record.status === "RUNNING") {
        items.push({ type: "skeleton", key: record.id, record });
      } else {
        items.push({ type: "status", key: record.id, record });
      }
    }
    return items;
  }, [records]);

  // Round-robin distribute items into columns (row-first ordering)
  const columns = useMemo(() => {
    const cols: MasonryItem[][] = Array.from({ length: columnCount }, () => []);
    masonryItems.forEach((item, i) => {
      cols[i % columnCount].push(item);
    });
    return cols;
  }, [masonryItems, columnCount]);

  if (isLoading && records.length === 0) {
    return (
      <div className="flex h-full items-center justify-center">
        <Spinner size="lg" />
      </div>
    );
  }

  if (records.length === 0) {
    return <InspirationEmptyState onTemplateClick={onTemplateClick} />;
  }

  return (
    <div className="relative flex h-full justify-center px-4">
      <ScrollShadow
        ref={scrollContainerRef}
        className="h-full w-full overflow-y-auto py-4"
        onScroll={onScroll}
      >
        {/* Masonry grid — row-first via round-robin column distribution */}
        <div ref={masonryRef} className="flex gap-3">
          {columns.map((col, colIdx) => (
            <div key={colIdx} className="flex flex-1 flex-col gap-3">
              {col.map((item) => {
                if (item.type === "asset") {
                  return (
                    <MasonryAssetCard
                      key={item.key}
                      item={item}
                      onRetry={onRetry}
                      onDelete={onDelete}
                    />
                  );
                }
                if (item.type === "skeleton") {
                  return (
                    <MasonrySkeletonCard
                      key={item.key}
                      record={item.record}
                    />
                  );
                }
                return (
                  <MasonryStatusCard
                    key={item.key}
                    record={item.record}
                    onRetry={onRetry}
                    onDelete={onDelete}
                  />
                );
              })}
            </div>
          ))}
        </div>

        <div ref={bottomRef} />
      </ScrollShadow>

      {/* Scroll to bottom FAB */}
      {showScrollToBottom && (
        <div className="pointer-events-auto absolute inset-x-0 bottom-2 flex justify-center">
          <Button
            isIconOnly
            variant="secondary"
            size="sm"
            onPress={onScrollToBottom}
            className="rounded-full shadow-md"
          >
            <ArrowDown className="size-4" />
          </Button>
        </div>
      )}
    </div>
  );
}
