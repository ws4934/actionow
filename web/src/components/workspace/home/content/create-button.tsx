"use client";

import { Button, Card } from "@heroui/react";
import { Plus, FileText } from "lucide-react";

interface CreateButtonProps {
  onCreateProject?: () => void;
}

export function CreateButton({ onCreateProject }: CreateButtonProps) {
  return (
    <Card
      variant="tertiary"
      className="group relative flex cursor-pointer flex-col items-center justify-center overflow-hidden p-6 transition-all hover:border-accent/50 hover:bg-accent/5"
      onClick={onCreateProject}
    >
      {/* Background decoration */}
      <div className="absolute inset-0 bg-gradient-to-br from-accent/5 to-transparent opacity-0 transition-opacity group-hover:opacity-100" />

      <div className="relative flex flex-col items-center text-center">
        <div className="flex size-12 items-center justify-center rounded-xl bg-accent/10 text-accent transition-transform group-hover:scale-110">
          <Plus className="size-6" />
        </div>
        <h3 className="mt-3 font-semibold">新建剧本</h3>
        <p className="mt-1 text-xs text-muted">
          创建新的剧本项目
        </p>
      </div>
    </Card>
  );
}
