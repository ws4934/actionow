"use client";

import type { ReactNode } from "react";
import type { LucideIcon } from "lucide-react";
import { Button } from "@heroui/react";

interface EmptyStateProps {
  icon: LucideIcon;
  title: string;
  description?: string;
  action?: {
    label: string;
    onClick: () => void;
  };
  compact?: boolean;
  className?: string;
}

/**
 * 空状态组件
 * 用于展示无数据时的占位内容
 */
export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
  compact = false,
  className = "",
}: EmptyStateProps) {
  const padding = compact ? "py-8" : "py-12";

  return (
    <div
      className={`rounded-xl border border-border bg-surface ${padding} text-center ${className}`}
    >
      <Icon className="mx-auto size-12 text-muted/40" />
      <p className="mt-4 font-medium text-foreground">{title}</p>
      {description && (
        <p className="mt-1.5 text-sm text-muted">{description}</p>
      )}
      {action && (
        <Button className="mt-4" onPress={action.onClick}>
          {action.label}
        </Button>
      )}
    </div>
  );
}

/**
 * 内联空状态 (无边框，用于卡片内部)
 */
interface InlineEmptyStateProps {
  icon: LucideIcon;
  message: string;
  className?: string;
}

export function InlineEmptyState({
  icon: Icon,
  message,
  className = "",
}: InlineEmptyStateProps) {
  return (
    <div className={`py-8 text-center ${className}`}>
      <Icon className="mx-auto size-10 text-muted/30" />
      <p className="mt-3 text-sm text-muted">{message}</p>
    </div>
  );
}
