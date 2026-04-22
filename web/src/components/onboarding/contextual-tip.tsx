"use client";

import type { LucideIcon } from "lucide-react";
import { X } from "lucide-react";
import { useOnboardingStore } from "@/lib/stores/onboarding-store";

interface ContextualTipProps {
  tipId: string;
  icon: LucideIcon;
  title: string;
  description: string;
  actionLabel?: string;
  onAction?: () => void;
}

export function ContextualTip({
  tipId,
  icon: Icon,
  title,
  description,
  actionLabel,
  onAction,
}: ContextualTipProps) {
  const dismissedTips = useOnboardingStore((s) => s.dismissedTips);
  const dismissTip = useOnboardingStore((s) => s.dismissTip);

  if (dismissedTips.includes(tipId)) return null;

  return (
    <div className="relative mx-auto mt-6 max-w-md rounded-xl border border-accent/20 bg-accent/5 p-6">
      <button
        type="button"
        onClick={() => dismissTip(tipId)}
        className="absolute top-3 right-3 rounded-md p-1 text-foreground/40 transition-colors hover:bg-foreground/10 hover:text-foreground/70"
      >
        <X className="size-4" />
      </button>

      <div className="flex items-start gap-4">
        <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-accent/10">
          <Icon className="size-5 text-accent" />
        </div>
        <div className="min-w-0">
          <h4 className="font-semibold text-foreground">{title}</h4>
          <p className="mt-1 text-sm text-foreground/60">{description}</p>
          {actionLabel && onAction && (
            <button
              type="button"
              onClick={onAction}
              className="mt-3 text-sm font-medium text-accent transition-colors hover:text-accent/80"
            >
              {actionLabel} &rarr;
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
