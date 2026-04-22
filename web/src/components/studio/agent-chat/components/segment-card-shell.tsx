"use client";

import { useState, useEffect, useRef, type ReactNode, type MouseEvent } from "react";
import { ChevronDown, ChevronRight } from "lucide-react";

/**
 * Shared bubble shell for every assistant-side segment card (thinking / text /
 * tools / ask / structured).  The only visual difference between kinds is the
 * icon + title slot and the optional accent left border.
 */
export type SegmentPosition = "first" | "middle" | "last" | "only";

export interface SegmentCardShellProps {
  icon?: ReactNode;
  title?: ReactNode;
  subtitle?: ReactNode;
  meta?: ReactNode;
  accentBorder?: boolean;
  collapsible?: boolean;
  defaultCollapsed?: boolean;
  /**
   * External signal to collapse (e.g. tool calls auto-collapse after completion).
   * User clicks take precedence — once the header is clicked the user's intent
   * is preserved and forceCollapsed no longer overrides.
   */
  forceCollapsed?: boolean;
  pulse?: boolean;
  /**
   * Override the default bubble classes (used by text segment for failed/cancelled states).
   * When provided, accentBorder and bgClass are ignored.  Should NOT include
   * border-radius classes — radius is computed from `position`.
   */
  bubbleClassName?: string;
  /** Override the default `bg-muted/10` background class. */
  bgClass?: string;
  /** Position within a consecutive segment group for border-radius computation. */
  position?: SegmentPosition;
  children: ReactNode;
}

/** Compute border-radius style from segment position within a group. */
function positionRadius(pos: SegmentPosition | undefined): React.CSSProperties {
  const x = "1rem";
  const h = "0.5rem"; // x / 2
  switch (pos) {
    case "first":  return { borderRadius: `${h} ${x} ${h} ${h}` };
    case "middle": return { borderRadius: h };
    case "last":   return { borderRadius: `${h} ${h} ${x} ${x}` };
    case "only":
    default:       return { borderRadius: `${h} ${x} ${x} ${x}` };
  }
}

export function SegmentCardShell({
  icon,
  title,
  subtitle,
  meta,
  accentBorder,
  collapsible,
  defaultCollapsed,
  forceCollapsed,
  pulse,
  bubbleClassName,
  bgClass,
  position,
  children,
}: SegmentCardShellProps) {
  const [collapsed, setCollapsed] = useState<boolean>(!!defaultCollapsed);
  const userTouched = useRef(false);

  useEffect(() => {
    if (userTouched.current) return;
    if (typeof forceCollapsed === "boolean") {
      setCollapsed(forceCollapsed);
    }
  }, [forceCollapsed]);

  const handleToggle = () => {
    if (!collapsible) return;
    userTouched.current = true;
    setCollapsed((c) => !c);
  };

  const stopPropagation = (e: MouseEvent) => e.stopPropagation();

  const hasHeader = !!(icon || title || subtitle || meta || collapsible);

  const bg = bgClass ?? "bg-muted/10";
  const bubbleClass = bubbleClassName
    ? bubbleClassName
    : accentBorder
      ? `w-full border-l-2 border-accent/60 ${bg} py-2.5 pl-3.5 pr-4`
      : `w-full ${bg} px-4 py-2.5`;

  const radiusStyle = positionRadius(position);

  return (
    <div className={bubbleClass} style={radiusStyle}>
      {hasHeader && (
        <div
          role={collapsible ? "button" : undefined}
          tabIndex={collapsible ? 0 : undefined}
          onClick={handleToggle}
          onKeyDown={(e) => {
            if (!collapsible) return;
            if (e.key === "Enter" || e.key === " ") {
              e.preventDefault();
              handleToggle();
            }
          }}
          className={`flex w-full items-center gap-2 ${collapsible ? "cursor-pointer select-none" : ""}`}
        >
          {icon && <span className="flex size-4 shrink-0 items-center justify-center">{icon}</span>}
          <div className="min-w-0 flex-1">
            {title && (
              <div className="truncate text-sm text-foreground/90">{title}</div>
            )}
            {subtitle && (
              <div className="mt-0.5 truncate text-[11px] text-muted">{subtitle}</div>
            )}
          </div>
          {pulse && (
            <span className="mx-1 size-1.5 shrink-0 animate-pulse rounded-full bg-accent" />
          )}
          {meta && (
            <div onClick={stopPropagation} className="shrink-0">
              {meta}
            </div>
          )}
          {collapsible && (
            collapsed ? (
              <ChevronRight className="size-4 shrink-0 text-muted" />
            ) : (
              <ChevronDown className="size-4 shrink-0 text-muted" />
            )
          )}
        </div>
      )}

      {(!collapsible || !collapsed) && (
        <div className={hasHeader ? "mt-2" : undefined}>{children}</div>
      )}
    </div>
  );
}
