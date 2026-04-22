"use client";

import type { ReactNode } from "react";
import type { LucideIcon } from "lucide-react";

type CardVariant = "default" | "danger" | "accent";

interface SectionCardProps {
  children: ReactNode;
  title?: string;
  icon?: LucideIcon;
  variant?: CardVariant;
  className?: string;
  noPadding?: boolean;
}

const variantClasses: Record<CardVariant, string> = {
  default: "border-border bg-surface",
  danger: "border-danger/20 bg-danger/5",
  accent: "border-accent/20 bg-gradient-to-br from-accent/10 to-accent/5",
};

const iconVariantClasses: Record<CardVariant, string> = {
  default: "text-muted",
  danger: "text-danger",
  accent: "text-accent",
};

/**
 * Director 区块卡片组件
 * 支持标题、图标和多种样式变体
 */
export function SectionCard({
  children,
  title,
  icon: Icon,
  variant = "default",
  className = "",
  noPadding = false,
}: SectionCardProps) {
  const hasHeader = title || Icon;

  return (
    <div
      className={`overflow-hidden rounded-xl border ${variantClasses[variant]} ${className}`}
    >
      {hasHeader && (
        <div className="border-b border-border px-6 py-4">
          <div className="flex items-center gap-2">
            {Icon && (
              <Icon className={`size-5 ${iconVariantClasses[variant]}`} />
            )}
            {title && (
              <h3 className="font-semibold text-foreground">{title}</h3>
            )}
          </div>
        </div>
      )}
      <div className={noPadding ? "" : "p-6"}>{children}</div>
    </div>
  );
}

/**
 * 卡片头部组件 (用于自定义头部)
 */
interface SectionCardHeaderProps {
  children: ReactNode;
  className?: string;
}

export function SectionCardHeader({
  children,
  className = "",
}: SectionCardHeaderProps) {
  return (
    <div className={`border-b border-border px-6 py-4 ${className}`}>
      {children}
    </div>
  );
}

/**
 * 卡片内容组件 (用于自定义内容区)
 */
interface SectionCardContentProps {
  children: ReactNode;
  className?: string;
  noPadding?: boolean;
}

export function SectionCardContent({
  children,
  className = "",
  noPadding = false,
}: SectionCardContentProps) {
  return (
    <div className={`${noPadding ? "" : "p-6"} ${className}`}>{children}</div>
  );
}
