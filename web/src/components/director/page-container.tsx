"use client";

import type { ReactNode } from "react";

interface PageContainerProps {
  children: ReactNode;
  className?: string;
}

/**
 * Director 页面容器组件
 * 提供宽屏响应式布局
 */
export function PageContainer({
  children,
  className = "",
}: PageContainerProps) {
  return (
    <div className={`mx-auto max-w-6xl space-y-6 ${className}`}>
      {children}
    </div>
  );
}

interface TwoColumnLayoutProps {
  children: ReactNode;
  className?: string;
}

/**
 * 双栏布局容器
 * 在中等屏幕上显示为左右两栏，小屏幕上堆叠
 */
export function TwoColumnLayout({
  children,
  className = "",
}: TwoColumnLayoutProps) {
  return (
    <div
      className={`grid gap-6 ${className}`}
      style={{
        gridTemplateColumns: "1fr 320px",
      }}
    >
      {children}
    </div>
  );
}

interface ColumnProps {
  children: ReactNode;
  className?: string;
}

/**
 * 主栏（左侧）
 */
export function MainColumn({ children, className = "" }: ColumnProps) {
  return <div className={`space-y-4 ${className}`}>{children}</div>;
}

/**
 * 侧栏（右侧）
 */
export function SideColumn({ children, className = "" }: ColumnProps) {
  return <div className={`space-y-4 ${className}`}>{children}</div>;
}
