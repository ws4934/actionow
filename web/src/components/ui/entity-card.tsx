"use client";

import type { ComponentType, ReactNode } from "react";
import Image from "next/image";
import { Skeleton } from "@heroui/react";

/**
 * An action item shown inside the card's hover Dropdown menu.
 *
 * Set `separatorBefore` to render a visual separator before the item
 * (useful for destructive actions like delete).
 */
export interface EntityCardAction {
  id: string;
  label: string;
  icon?: ComponentType<{ className?: string }>;
  variant?: "default" | "danger";
  separatorBefore?: boolean;
  onAction: () => void;
}

export interface EntityCardProps {
  /** Title shown under the cover. Single-line, truncated. */
  title: string;
  /** Optional body text. Two-line clamp. */
  description?: string | null;
  /** Fallback rendered inline when description is empty. */
  descriptionFallback?: string;
  /** Cover image URL. Ignored when `coverSlot` is provided. */
  coverUrl?: string | null;
  /** Custom cover node (e.g. a <video>). Overrides the default <img>. */
  coverSlot?: ReactNode;
  /** Node rendered inside the cover when neither coverUrl nor coverSlot is present. */
  fallbackIcon?: ReactNode;
  /** Badge rendered at the top-left corner of the cover. */
  topLeftBadge?: ReactNode;
  /** Badge rendered at the top-right corner. Fades out on hover when actions are present. */
  topRightBadge?: ReactNode;
  /** Actions shown in the hover Dropdown (top-right corner). */
  actions?: EntityCardAction[];
  /** Accessible label for the actions trigger button. */
  actionsLabel?: string;
  /** Marks the actions button as pending (shows spinner). */
  isActionPending?: boolean;
  /** Footer left slot (e.g., author). */
  footerLeft?: ReactNode;
  /** Footer right slot (e.g., date, file size). */
  footerRight?: ReactNode;
  /** Click handler for the whole card. */
  onClick?: () => void;
  /** Additional classes merged into the card root. */
  className?: string;
}

/**
 * Generic entity / media card used across the workspace to render
 * scripts, characters, scenes, props, assets and styles with a
 * consistent look.
 *
 * Visuals:
 *   - aspect-video rounded cover with hover scale
 *   - optional top-left / top-right badges
 *   - hover Dropdown menu for actions
 *   - title + 2-line description
 *   - optional two-slot footer (left/right)
 */
export function EntityCard({
  title,
  description,
  descriptionFallback = "",
  coverUrl,
  coverSlot,
  fallbackIcon,
  topLeftBadge,
  topRightBadge,
  actions,
  actionsLabel = "More",
  isActionPending,
  footerLeft,
  footerRight,
  onClick,
  className,
}: EntityCardProps) {
  const hasActions = !!actions && actions.length > 0;

  return (
    <div
      className={`pointer-events-auto group relative isolate flex transform-gpu cursor-pointer flex-col overflow-visible rounded-2xl bg-white/60 shadow-sm backdrop-blur-xl transition-shadow duration-200 hover:shadow-lg dark:bg-white/5${className ? ` ${className}` : ""}`}
      onClick={onClick}
    >
      {/* Cover */}
      <div className="relative aspect-video w-full overflow-hidden rounded-t-2xl bg-black/5 dark:bg-white/5">
        {coverSlot ?? (
          coverUrl ? (
            <Image
              src={coverUrl}
              alt={title}
              fill
              sizes="(min-width: 1280px) 20vw, (min-width: 1024px) 25vw, (min-width: 768px) 33vw, 50vw"
              className="object-cover transform-gpu transition-transform duration-300 will-change-transform group-hover:scale-105"
            />
          ) : (
            <div className="flex size-full items-center justify-center bg-white/10 dark:bg-white/5">
              {fallbackIcon}
            </div>
          )
        )}

        {/* Top-left badge */}
        {topLeftBadge && (
          <div className="absolute left-2.5 top-2.5">{topLeftBadge}</div>
        )}

        {/* Top-right badge — fades away on hover if a dropdown is present */}
        {topRightBadge && (
          <div
            className={`absolute right-2.5 top-2.5${hasActions ? " transition-opacity group-hover:opacity-0" : ""}`}
          >
            {topRightBadge}
          </div>
        )}

        {/* Hover actions overlay — adaptive grid layout */}
        {hasActions && (
          <div
            className={`absolute inset-0 grid rounded-t-2xl opacity-0 transition-opacity duration-200 ease-out group-hover:opacity-100 ${
              actions!.length === 1
                ? "grid-cols-1 grid-rows-1"
                : actions!.length === 2
                ? "grid-cols-2 grid-rows-1"
                : actions!.length === 3
                ? "grid-cols-2 grid-rows-2"
                : "grid-cols-2 grid-rows-2"
            }`}
            onClick={(e) => e.stopPropagation()}
          >
            {actions!.map((action, idx) => {
              const Icon = action.icon;
              const isDanger = action.variant === "danger";
              // 3 buttons: last one spans full width on second row
              const spanFull = actions!.length === 3 && idx === 2;
              return (
                <button
                  key={action.id}
                  type="button"
                  onClick={() => action.onAction()}
                  className={`group/action flex cursor-pointer flex-col items-center justify-center gap-1 bg-black/40 text-white/70 transition-colors ${
                    isDanger
                      ? "hover:text-danger"
                      : "hover:text-accent"
                  }${spanFull ? " col-span-2" : ""}`}
                >
                  {Icon && <Icon className="size-5 transition-transform group-hover/action:scale-125" />}
                  <span className="text-[11px] font-medium transition-transform group-hover/action:scale-110">{action.label}</span>
                </button>
              );
            })}
          </div>
        )}
      </div>

      {/* Content */}
      <div className="flex flex-1 flex-col p-3.5">
        <h3 className="line-clamp-1 font-semibold text-foreground">{title}</h3>
        <p className="mt-1.5 line-clamp-2 min-h-[2lh] flex-1 text-sm text-muted">
          {description || descriptionFallback}
        </p>
        {(footerLeft || footerRight) && (
          <div className="mt-3 flex items-center justify-between gap-2 text-xs text-muted/70">
            <div className="min-w-0 flex-1 truncate">{footerLeft}</div>
            {footerRight && <div className="flex shrink-0 items-center gap-2">{footerRight}</div>}
          </div>
        )}
      </div>
    </div>
  );
}

/**
 * Skeleton placeholder matching the EntityCard layout — used while
 * lists are loading.
 */
export function EntityCardSkeleton() {
  return (
    <div className="pointer-events-auto flex flex-col overflow-hidden rounded-2xl bg-white/60 backdrop-blur-xl dark:bg-white/5">
      <Skeleton className="aspect-video w-full rounded-none" />
      <div className="flex flex-col gap-2 p-3.5">
        <Skeleton className="h-5 w-3/4 rounded" />
        <Skeleton className="h-4 w-full rounded" />
        <Skeleton className="h-4 w-2/3 rounded" />
        <div className="mt-2 flex items-center justify-between">
          <Skeleton className="h-3 w-20 rounded" />
          <Skeleton className="h-3 w-16 rounded" />
        </div>
      </div>
    </div>
  );
}
