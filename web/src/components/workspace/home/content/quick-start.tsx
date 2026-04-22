"use client";

import { Card } from "@heroui/react";
import { FileText, Upload, Bot, Lightbulb } from "lucide-react";

interface QuickStartItem {
  icon: typeof FileText;
  title: string;
  description: string;
  onClick?: () => void;
  color: string;
}

interface QuickStartProps {
  onCreateFromTemplate?: () => void;
  onImport?: () => void;
  onAIGenerate?: () => void;
}

export function QuickStart({ onCreateFromTemplate, onImport, onAIGenerate }: QuickStartProps) {
  const quickStartItems: QuickStartItem[] = [
    {
      icon: FileText,
      title: "从模板创建",
      description: "使用预设模板快速开始",
      onClick: onCreateFromTemplate,
      color: "bg-blue-500/10 text-blue-500",
    },
    {
      icon: Upload,
      title: "导入剧本",
      description: "支持 PDF、Word 等格式",
      onClick: onImport,
      color: "bg-green-500/10 text-green-500",
    },
    {
      icon: Bot,
      title: "AI 生成",
      description: "让 AI 帮你生成创意",
      onClick: onAIGenerate,
      color: "bg-purple-500/10 text-purple-500",
    },
  ];

  return (
    <Card variant="tertiary" className="p-5">
      {/* Header */}
      <div className="mb-4 flex items-center gap-2">
        <Lightbulb className="size-4 text-muted" />
        <h2 className="font-semibold">快速开始</h2>
      </div>

      {/* Quick Start Grid */}
      <div className="grid grid-cols-3 gap-3">
        {quickStartItems.map((item) => {
          const Icon = item.icon;
          return (
            <button
              key={item.title}
              type="button"
              className="group flex flex-col items-center rounded-xl border border-border bg-background p-4 text-center transition-all hover:border-accent/50 hover:shadow-md"
              onClick={item.onClick}
            >
              <div className={`flex size-12 items-center justify-center rounded-xl ${item.color} transition-transform group-hover:scale-110`}>
                <Icon className="size-6" />
              </div>
              <h3 className="mt-3 text-sm font-medium group-hover:text-accent">
                {item.title}
              </h3>
              <p className="mt-1 text-xs text-muted">
                {item.description}
              </p>
            </button>
          );
        })}
      </div>
    </Card>
  );
}
