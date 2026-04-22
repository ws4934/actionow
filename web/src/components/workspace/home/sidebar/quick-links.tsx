"use client";

import Link from "next/link";
import { Bot, Package, Users, Settings } from "lucide-react";

interface QuickLink {
  icon: typeof Bot;
  label: string;
  href: string;
}

const quickLinks: QuickLink[] = [
  { icon: Bot, label: "AI 助手", href: "/workspace/ai" },
  { icon: Package, label: "素材库", href: "/workspace/assets" },
  { icon: Users, label: "团队管理", href: "/workspace/team" },
  { icon: Settings, label: "工作空间设置", href: "/workspace/settings" },
];

export function QuickLinks() {
  return (
    <div className="px-3">
      <h3 className="mb-2 px-2 text-xs font-medium uppercase tracking-wide text-muted">
        快捷入口
      </h3>
      <nav className="flex flex-col gap-0.5">
        {quickLinks.map((item) => {
          const Icon = item.icon;
          return (
            <Link
              key={item.href}
              href={item.href}
              className="flex items-center gap-3 rounded-lg px-3 py-2 text-sm text-foreground/80 transition-colors hover:bg-muted/10 hover:text-foreground"
            >
              <Icon className="size-4" />
              <span>{item.label}</span>
            </Link>
          );
        })}
      </nav>
    </div>
  );
}
