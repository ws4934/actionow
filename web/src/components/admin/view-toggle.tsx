"use client";

import { Button } from "@heroui/react";
import { LayoutGrid, List } from "lucide-react";

export type ViewMode = "card" | "list";

interface ViewToggleProps {
  value: ViewMode;
  onChange: (mode: ViewMode) => void;
}

export function ViewToggle({ value, onChange }: ViewToggleProps) {
  return (
    <Button
      size="sm"
      variant="ghost"
      isIconOnly
      onPress={() => onChange(value === "card" ? "list" : "card")}
      aria-label={value === "card" ? "切换为列表视图" : "切换为卡片视图"}
    >
      {value === "card" ? <List className="size-4" /> : <LayoutGrid className="size-4" />}
    </Button>
  );
}

export default ViewToggle;
