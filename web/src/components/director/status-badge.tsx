"use client";

type BadgeVariant = "default" | "accent" | "success" | "danger" | "warning" | "info";
type BadgeSize = "sm" | "md";

interface StatusBadgeProps {
  children: string;
  variant?: BadgeVariant;
  size?: BadgeSize;
  dot?: boolean;
  className?: string;
}

const variantClasses: Record<BadgeVariant, string> = {
  default: "bg-foreground/10 text-foreground/70",
  accent: "bg-accent/20 text-accent",
  success: "bg-success/20 text-success",
  danger: "bg-danger/20 text-danger",
  warning: "bg-warning/20 text-warning",
  info: "bg-blue-500/20 text-blue-600 dark:text-blue-400",
};

const dotColors: Record<BadgeVariant, string> = {
  default: "bg-foreground/50",
  accent: "bg-accent",
  success: "bg-success",
  danger: "bg-danger",
  warning: "bg-warning",
  info: "bg-blue-500",
};

const sizeClasses: Record<BadgeSize, string> = {
  sm: "px-2 py-0.5 text-xs",
  md: "px-2.5 py-1 text-xs",
};

/**
 * 状态标签组件
 */
export function StatusBadge({
  children,
  variant = "default",
  size = "md",
  dot = false,
  className = "",
}: StatusBadgeProps) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full font-medium ${variantClasses[variant]} ${sizeClasses[size]} ${className}`}
    >
      {dot && <span className={`size-1.5 rounded-full ${dotColors[variant]}`} />}
      {children}
    </span>
  );
}

/**
 * 预设状态配置
 */
export const STATUS_CONFIGS = {
  // 通用状态
  active: { variant: "success" as const, label: "活跃" },
  inactive: { variant: "default" as const, label: "未激活" },
  pending: { variant: "warning" as const, label: "待处理" },
  expired: { variant: "danger" as const, label: "已过期" },

  // 邀请状态
  invitation_pending: { variant: "warning" as const, label: "待使用" },
  invitation_used: { variant: "success" as const, label: "已使用" },
  invitation_expired: { variant: "danger" as const, label: "已过期" },

  // 脚本状态
  draft: { variant: "default" as const, label: "草稿" },
  in_progress: { variant: "accent" as const, label: "进行中" },
  completed: { variant: "success" as const, label: "已完成" },
  archived: { variant: "default" as const, label: "已归档" },

  // 交易类型
  income: { variant: "success" as const, label: "收入" },
  expense: { variant: "danger" as const, label: "支出" },
} as const;
