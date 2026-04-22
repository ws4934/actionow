"use client";

import { useEffect, useState, useCallback, type RefObject } from "react";
import { useOnboardingStore } from "@/lib/stores/onboarding-store";

interface TargetRect {
  top: number;
  left: number;
  width: number;
  height: number;
  bottom: number;
}

interface SpotlightOverlayProps {
  tipId: string;
  targetRef: RefObject<HTMLElement | null>;
  title: string;
  description: string;
  dismissLabel?: string;
}

export function SpotlightOverlay({
  tipId,
  targetRef,
  title,
  description,
  dismissLabel = "Got it",
}: SpotlightOverlayProps) {
  const dismissedTips = useOnboardingStore((s) => s.dismissedTips);
  const dismissTip = useOnboardingStore((s) => s.dismissTip);
  const [rect, setRect] = useState<TargetRect | null>(null);

  const measure = useCallback(() => {
    if (targetRef.current) {
      const r = targetRef.current.getBoundingClientRect();
      setRect({ top: r.top, left: r.left, width: r.width, height: r.height, bottom: r.bottom });
    }
  }, [targetRef]);

  useEffect(() => {
    const timer = setTimeout(measure, 60);
    window.addEventListener("resize", measure);
    return () => {
      clearTimeout(timer);
      window.removeEventListener("resize", measure);
    };
  }, [measure]);

  if (dismissedTips.includes(tipId) || !rect) return null;

  const pad = 8;
  const sTop = rect.top - pad;
  const sLeft = rect.left - pad;
  const sW = rect.width + pad * 2;
  const sH = rect.height + pad * 2;
  const sBottom = sTop + sH;
  const sRight = sLeft + sW;

  const dismiss = () => dismissTip(tipId);
  const mask = "rgba(0,0,0,0.65)";
  const OVERLAY_Z = 40;

  // Tooltip: center on spotlight, clamp within viewport
  const tooltipW = 256;
  const vpW = typeof window !== "undefined" ? window.innerWidth : 1200;
  const tooltipLeft = Math.max(16, Math.min(rect.left + rect.width / 2 - tooltipW / 2, vpW - tooltipW - 16));
  const arrowLeft = Math.max(8, Math.min(rect.left + rect.width / 2 - tooltipLeft - 6, tooltipW - 20));

  return (
    <>
      {/* Four-quadrant dark mask */}
      <div aria-hidden="true" className="fixed inset-x-0 top-0 cursor-default" style={{ height: sTop, background: mask, zIndex: OVERLAY_Z }} onClick={dismiss} />
      <div aria-hidden="true" className="fixed inset-x-0 bottom-0 cursor-default" style={{ top: sBottom, background: mask, zIndex: OVERLAY_Z }} onClick={dismiss} />
      <div aria-hidden="true" className="fixed left-0 cursor-default" style={{ top: sTop, height: sH, width: Math.max(0, sLeft), background: mask, zIndex: OVERLAY_Z }} onClick={dismiss} />
      <div aria-hidden="true" className="fixed right-0 cursor-default" style={{ top: sTop, height: sH, left: sRight, background: mask, zIndex: OVERLAY_Z }} onClick={dismiss} />

      {/* Spotlight accent ring */}
      <div
        aria-hidden="true"
        className="pointer-events-none fixed rounded-lg"
        style={{
          top: sTop,
          left: sLeft,
          width: sW,
          height: sH,
          zIndex: OVERLAY_Z + 1,
          boxShadow: "0 0 0 2px var(--color-accent, oklch(0.65 0.2 260)), 0 0 12px 4px rgba(var(--color-accent-rgb, 99 102 241) / 0.3)",
        }}
      />

      {/* Callout tooltip */}
      <div
        role="tooltip"
        className="fixed w-64 rounded-xl border border-border bg-surface p-4 shadow-2xl"
        style={{ top: sBottom + 12, left: tooltipLeft, zIndex: OVERLAY_Z + 10 }}
      >
        {/* Arrow pointing up toward the button */}
        <div
          aria-hidden="true"
          className="absolute -top-[7px] size-3 rotate-45 border-l border-t border-border bg-surface"
          style={{ left: arrowLeft }}
        />
        <p className="text-sm font-semibold text-foreground">{title}</p>
        <p className="mt-1 text-xs text-muted">{description}</p>
        <button
          type="button"
          onClick={dismiss}
          className="mt-3 text-xs font-medium text-accent transition-colors hover:text-accent/80"
        >
          {dismissLabel}
        </button>
      </div>
    </>
  );
}
