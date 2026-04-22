"use client";

import type { ReactNode } from "react";

interface ListRowProps {
  left?: ReactNode;
  children: ReactNode;
  right?: ReactNode;
  onClick?: () => void;
  compact?: boolean;
  className?: string;
}

/**
 * 标准列表行组件
 * 三栏布局: 左侧(图标/头像) + 中间(内容) + 右侧(操作)
 */
export function ListRow({
  left,
  children,
  right,
  onClick,
  compact = false,
  className = "",
}: ListRowProps) {
  const padding = compact ? "px-4 py-3" : "px-6 py-4";
  const isClickable = !!onClick;

  return (
    <div
      className={`flex items-center gap-4 ${padding} transition-colors ${
        isClickable ? "cursor-pointer hover:bg-surface/50" : ""
      } ${className}`}
      onClick={onClick}
      role={isClickable ? "button" : undefined}
      tabIndex={isClickable ? 0 : undefined}
      onKeyDown={
        isClickable
          ? (e) => {
              if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                onClick();
              }
            }
          : undefined
      }
    >
      {left && <div className="shrink-0">{left}</div>}
      <div className="min-w-0 flex-1">{children}</div>
      {right && <div className="shrink-0">{right}</div>}
    </div>
  );
}

/**
 * 列表行主要文本
 */
interface ListRowTitleProps {
  children: ReactNode;
  className?: string;
}

export function ListRowTitle({ children, className = "" }: ListRowTitleProps) {
  return (
    <p className={`truncate font-medium text-foreground ${className}`}>
      {children}
    </p>
  );
}

/**
 * 列表行次要文本
 */
interface ListRowDescriptionProps {
  children: ReactNode;
  className?: string;
}

export function ListRowDescription({
  children,
  className = "",
}: ListRowDescriptionProps) {
  return (
    <p className={`truncate text-sm text-muted ${className}`}>{children}</p>
  );
}
