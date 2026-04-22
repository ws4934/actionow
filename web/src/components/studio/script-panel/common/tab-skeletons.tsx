"use client";

/**
 * Skeleton loading states for script panel tabs.
 * Each skeleton matches the actual content layout of its corresponding tab.
 */

import { Skeleton } from "@heroui/react";

// ============ Shared Building Blocks ============

/** Toolbar skeleton - matches the Surface toolbar bar */
function ToolbarSkeleton({ hasEpisodeSelector = false }: { hasEpisodeSelector?: boolean }) {
  return (
    <div className="mb-3 flex shrink-0 items-center justify-between gap-4 rounded-xl bg-surface-secondary px-3 py-2">
      <div className="flex items-center gap-3">
        {hasEpisodeSelector && (
          <>
            <Skeleton className="h-8 w-36 rounded-lg" />
            <div className="h-5 w-px bg-border/50" />
          </>
        )}
        {/* View mode buttons */}
        <div className="flex gap-0.5">
          <Skeleton className="size-8 rounded-lg" />
          <Skeleton className="size-8 rounded-lg" />
          <Skeleton className="size-8 rounded-lg" />
        </div>
        <div className="h-5 w-px bg-border/50" />
        {/* Search */}
        <Skeleton className="h-8 w-44 rounded-lg" />
      </div>
      <div className="flex items-center gap-2">
        <Skeleton className="size-8 rounded-lg" />
        <Skeleton className="h-8 w-20 rounded-lg" />
      </div>
    </div>
  );
}

/** List item skeleton - matches EntityListView rows */
function ListItemSkeleton() {
  return (
    <div className="flex items-center gap-3 border-b border-border/30 px-3 py-2.5">
      <Skeleton className="size-10 shrink-0 rounded-lg" />
      <div className="min-w-0 flex-1 space-y-1.5">
        <Skeleton className="h-3.5 w-2/5 rounded" />
        <Skeleton className="h-3 w-3/5 rounded" />
      </div>
      <Skeleton className="h-5 w-14 rounded-full" />
    </div>
  );
}

/** Grid card skeleton - matches EntityGridView cards */
function GridCardSkeleton() {
  return (
    <div className="overflow-hidden rounded-xl border border-border/30">
      <Skeleton className="aspect-video w-full" />
      <div className="space-y-1.5 p-2.5">
        <Skeleton className="h-3.5 w-3/5 rounded" />
        <Skeleton className="h-3 w-2/5 rounded" />
      </div>
    </div>
  );
}

// ============ Tab-Level Skeletons ============

/**
 * Skeleton for multi-view entity tabs (character, episode, scene, prop, entity-tab).
 * Shows toolbar + list items.
 */
export function EntityTabSkeleton({ hasEpisodeSelector = false }: { hasEpisodeSelector?: boolean }) {
  return (
    <div className="flex h-full flex-col p-0">
      <ToolbarSkeleton hasEpisodeSelector={hasEpisodeSelector} />
      <div className="min-h-0 flex-1 overflow-hidden rounded-xl border border-border/30">
        {Array.from({ length: 6 }).map((_, i) => (
          <ListItemSkeleton key={i} />
        ))}
      </div>
    </div>
  );
}

/**
 * Skeleton for the asset tab.
 * Shows toolbar with type filter + grid of asset cards.
 */
export function AssetTabSkeleton() {
  return (
    <div className="flex h-full flex-col p-0">
      <div className="mb-3 flex shrink-0 items-center justify-between gap-4 rounded-xl bg-surface-secondary px-3 py-2">
        <div className="flex items-center gap-3">
          {/* View mode buttons */}
          <div className="flex gap-0.5">
            <Skeleton className="size-8 rounded-lg" />
            <Skeleton className="size-8 rounded-lg" />
            <Skeleton className="size-8 rounded-lg" />
          </div>
          <div className="h-5 w-px bg-border/50" />
          {/* Type filter chips */}
          <div className="flex gap-1.5">
            <Skeleton className="h-7 w-12 rounded-full" />
            <Skeleton className="h-7 w-12 rounded-full" />
            <Skeleton className="h-7 w-12 rounded-full" />
            <Skeleton className="h-7 w-12 rounded-full" />
          </div>
          <div className="h-5 w-px bg-border/50" />
          {/* Search */}
          <Skeleton className="h-8 w-44 rounded-lg" />
        </div>
        <div className="flex items-center gap-2">
          <Skeleton className="size-8 rounded-lg" />
          <Skeleton className="size-8 rounded-lg" />
        </div>
      </div>
      <div className="grid min-h-0 flex-1 grid-cols-[repeat(auto-fill,minmax(160px,1fr))] gap-3 overflow-hidden">
        {Array.from({ length: 8 }).map((_, i) => (
          <GridCardSkeleton key={i} />
        ))}
      </div>
    </div>
  );
}

/**
 * Skeleton for the script details tab.
 * Shows collapsible header card + content editor area.
 */
export function ScriptDetailSkeleton() {
  return (
    <div className="flex h-full flex-col gap-3">
      {/* Header card */}
      <div className="shrink-0 rounded-2xl bg-muted/5 p-4">
        <div className="flex gap-4">
          {/* Cover image */}
          <Skeleton className="h-28 w-44 shrink-0 rounded-xl" />
          {/* Info */}
          <div className="min-w-0 flex-1 space-y-3">
            <div className="flex items-center gap-2">
              <Skeleton className="h-5 w-48 rounded" />
              <Skeleton className="h-5 w-14 rounded-full" />
            </div>
            <Skeleton className="h-3.5 w-full rounded" />
            <Skeleton className="h-3.5 w-4/5 rounded" />
            <div className="flex items-center gap-4 pt-1">
              <Skeleton className="h-3 w-24 rounded" />
              <Skeleton className="h-3 w-24 rounded" />
            </div>
          </div>
        </div>
      </div>
      {/* Content editor area */}
      <div className="min-h-0 flex-1 rounded-xl bg-muted/5 p-4">
        <div className="space-y-3">
          <Skeleton className="h-4 w-full rounded" />
          <Skeleton className="h-4 w-5/6 rounded" />
          <Skeleton className="h-4 w-4/6 rounded" />
          <Skeleton className="h-4 w-full rounded" />
          <Skeleton className="h-4 w-3/6 rounded" />
          <Skeleton className="h-4 w-5/6 rounded" />
          <Skeleton className="h-4 w-2/6 rounded" />
        </div>
      </div>
    </div>
  );
}

/**
 * Skeleton for detail pane content (used when selecting an item in detail view mode).
 * Matches the form-based detail layout.
 */
export function DetailPaneSkeleton() {
  return (
    <div className="flex h-full flex-col gap-4 p-4">
      {/* Title area */}
      <div className="flex items-center gap-3">
        <Skeleton className="size-12 shrink-0 rounded-xl" />
        <div className="min-w-0 flex-1 space-y-2">
          <Skeleton className="h-5 w-48 rounded" />
          <Skeleton className="h-3.5 w-32 rounded" />
        </div>
      </div>
      {/* Form fields */}
      <div className="space-y-4 pt-2">
        <div className="space-y-1.5">
          <Skeleton className="h-3 w-16 rounded" />
          <Skeleton className="h-9 w-full rounded-lg" />
        </div>
        <div className="space-y-1.5">
          <Skeleton className="h-3 w-20 rounded" />
          <Skeleton className="h-20 w-full rounded-lg" />
        </div>
        <div className="space-y-1.5">
          <Skeleton className="h-3 w-24 rounded" />
          <Skeleton className="h-9 w-full rounded-lg" />
        </div>
      </div>
      {/* Assets area */}
      <div className="space-y-2 pt-2">
        <div className="flex items-center justify-between">
          <Skeleton className="h-4 w-20 rounded" />
          <Skeleton className="h-7 w-20 rounded-lg" />
        </div>
        <div className="grid grid-cols-[repeat(auto-fill,minmax(120px,1fr))] gap-2">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="aspect-square rounded-lg" />
          ))}
        </div>
      </div>
    </div>
  );
}

/**
 * Skeleton for the assets section within detail views.
 * Shows a grid of asset card placeholders.
 */
export function AssetsSectionSkeleton() {
  return (
    <div className="grid auto-rows-auto grid-cols-[repeat(auto-fill,minmax(300px,1fr))] gap-3">
      {Array.from({ length: 3 }).map((_, i) => (
        <div key={i} className="flex gap-3 rounded-xl border border-border/30 p-3">
          <Skeleton className="size-20 shrink-0 rounded-lg" />
          <div className="min-w-0 flex-1 space-y-2">
            <Skeleton className="h-3.5 w-3/5 rounded" />
            <Skeleton className="h-3 w-2/5 rounded" />
            <Skeleton className="h-3 w-1/3 rounded" />
          </div>
        </div>
      ))}
    </div>
  );
}

/**
 * Skeleton for version history panel.
 * Shows a list of version items.
 */
export function VersionListSkeleton() {
  return (
    <div className="flex h-full flex-col">
      <div className="flex shrink-0 items-center justify-between border-b border-muted/10 px-4 py-3">
        <Skeleton className="h-5 w-24 rounded" />
        <Skeleton className="size-7 rounded-lg" />
      </div>
      <div className="flex-1 space-y-0">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="border-b border-muted/10 px-4 py-3">
            <div className="flex items-center justify-between">
              <Skeleton className="h-3.5 w-24 rounded" />
              <Skeleton className="h-3 w-16 rounded" />
            </div>
            <Skeleton className="mt-1.5 h-3 w-40 rounded" />
          </div>
        ))}
      </div>
    </div>
  );
}
