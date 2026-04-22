/**
 * Tab User Badge Component
 * Shows small user count indicator on tabs
 */

"use client";

interface TabUserBadgeProps {
  count: number;
}

export function TabUserBadge({ count }: TabUserBadgeProps) {
  if (count <= 0) return null;

  return (
    <span className="flex size-4 items-center justify-center rounded-full bg-accent/20 text-[10px] font-medium text-accent">
      {count > 9 ? "9+" : count}
    </span>
  );
}

export default TabUserBadge;
