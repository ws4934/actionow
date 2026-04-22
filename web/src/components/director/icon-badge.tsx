"use client";

import type { ReactNode } from "react";
import type { LucideIcon } from "lucide-react";

type BadgeSize = "sm" | "md" | "lg";
type BadgeVariant = "default" | "accent" | "success" | "danger" | "warning";
type BadgeShape = "circle" | "rounded";

interface IconBadgeProps {
  icon?: LucideIcon;
  children?: ReactNode;
  size?: BadgeSize;
  variant?: BadgeVariant;
  shape?: BadgeShape;
  className?: string;
}

const sizeClasses: Record<BadgeSize, { container: string; icon: string }> = {
  sm: { container: "size-8", icon: "size-4" },
  md: { container: "size-10", icon: "size-5" },
  lg: { container: "size-12", icon: "size-6" },
};

const variantClasses: Record<BadgeVariant, { bg: string; text: string }> = {
  default: { bg: "bg-surface", text: "text-muted" },
  accent: { bg: "bg-accent/10", text: "text-accent" },
  success: { bg: "bg-success/10", text: "text-success" },
  danger: { bg: "bg-danger/10", text: "text-danger" },
  warning: { bg: "bg-warning/10", text: "text-warning" },
};

const shapeClasses: Record<BadgeShape, string> = {
  circle: "rounded-full",
  rounded: "rounded-lg",
};

/**
 * 图标徽章组件
 * 用于列表项、状态指示等场景
 */
export function IconBadge({
  icon: Icon,
  children,
  size = "md",
  variant = "default",
  shape = "circle",
  className = "",
}: IconBadgeProps) {
  const { container, icon } = sizeClasses[size];
  const { bg, text } = variantClasses[variant];
  const shapeClass = shapeClasses[shape];

  return (
    <div
      className={`flex items-center justify-center ${container} ${shapeClass} ${bg} ${className}`}
    >
      {Icon ? (
        <Icon className={`${icon} ${text}`} />
      ) : (
        <span className={text}>{children}</span>
      )}
    </div>
  );
}
