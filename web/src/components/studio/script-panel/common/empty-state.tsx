"use client";

/**
 * Empty State
 * Centered placeholder shown when a list/grid has no items.
 * Used across all script-panel tabs for visual consistency.
 */

import { Inbox } from "lucide-react";
import type { ReactNode } from "react";

interface EmptyStateProps {
  icon?: ReactNode;
  title: string;
  description?: string;
  className?: string;
}

export function EmptyState({ icon, title, description, className }: EmptyStateProps) {
  return (
    <div className={`flex h-full flex-col items-center justify-center gap-3 text-muted ${className ?? ""}`}>
      <div className="opacity-20">
        {icon ?? <Inbox className="size-16" />}
      </div>
      <p className="text-lg font-medium">{title}</p>
      {description && <p className="text-sm">{description}</p>}
    </div>
  );
}
