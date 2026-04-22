"use client";

/**
 * Status Badge Component
 * Displays status with colored badge
 */

import { useTranslations } from "next-intl";

interface StatusBadgeProps {
  status: string;
}

const STATUS_CONFIG: Record<string, { key: string; bg: string; text: string; dot: string }> = {
  DRAFT: { key: "draft", bg: "bg-warning/15", text: "text-warning", dot: "bg-warning" },
  IN_PROGRESS: { key: "inProgress", bg: "bg-accent/15", text: "text-accent", dot: "bg-accent" },
  COMPLETED: { key: "completed", bg: "bg-success/15", text: "text-success", dot: "bg-success" },
  ARCHIVED: { key: "archived", bg: "bg-muted/15", text: "text-muted", dot: "bg-muted" },
  GENERATING: { key: "generating", bg: "bg-accent/15", text: "text-accent", dot: "bg-accent" },
  FAILED: { key: "failed", bg: "bg-danger/15", text: "text-danger", dot: "bg-danger" },
};

const DEFAULT_CONFIG = { key: "", bg: "bg-muted/15", text: "text-muted", dot: "bg-muted" };

export function StatusBadge({ status }: StatusBadgeProps) {
  const t = useTranslations("workspace.studio.status");
  const config = STATUS_CONFIG[status] || DEFAULT_CONFIG;
  const displayLabel = config.key ? t(config.key) : status;

  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-[11px] font-medium ${config.bg} ${config.text}`}>
      <span className={`size-1.5 rounded-full ${config.dot}`} />
      {displayLabel}
    </span>
  );
}

export default StatusBadge;
