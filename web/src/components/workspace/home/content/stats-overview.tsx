"use client";

import { Card } from "@heroui/react";
import { FileText, Image, Users, Film, HardDrive } from "lucide-react";

interface StatsOverviewProps {
  projectCount: number;
  assetCount: number;
  characterCount: number;
  storyboardCount: number;
  storageUsed: number; // in bytes
  storageLimit: number; // in bytes
}

// Format storage size
function formatStorage(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} GB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} TB`;
}

export function StatsOverview({
  projectCount,
  assetCount,
  characterCount,
  storyboardCount,
  storageUsed,
  storageLimit,
}: StatsOverviewProps) {
  const storagePercent = Math.min(100, Math.round((storageUsed / storageLimit) * 100));

  const stats = [
    { icon: FileText, label: "项目", value: projectCount, color: "text-blue-500" },
    { icon: Image, label: "素材", value: assetCount, color: "text-green-500" },
    { icon: Users, label: "角色", value: characterCount, color: "text-purple-500" },
    { icon: Film, label: "分镜", value: storyboardCount, color: "text-orange-500" },
  ];

  return (
    <Card variant="tertiary" className="p-5">
      <div className="mb-4 flex items-center gap-2">
        <HardDrive className="size-4 text-muted" />
        <h2 className="font-semibold">数据概览</h2>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-2 gap-3">
        {stats.map((stat) => {
          const Icon = stat.icon;
          return (
            <div
              key={stat.label}
              className="flex flex-col items-center rounded-lg bg-muted/5 p-3"
            >
              <Icon className={`size-5 ${stat.color}`} />
              <span className="mt-2 text-2xl font-bold">{stat.value}</span>
              <span className="text-xs text-muted">{stat.label}</span>
            </div>
          );
        })}
      </div>

      {/* Storage */}
      <div className="mt-4 border-t border-border/50 pt-4">
        <div className="mb-2 flex items-center justify-between text-xs">
          <span className="text-muted">存储空间</span>
          <span className="font-medium">
            {formatStorage(storageUsed)} / {formatStorage(storageLimit)}
          </span>
        </div>
        <div className="h-2 overflow-hidden rounded-full bg-muted/20">
          <div
            className={`h-full rounded-full transition-all ${
              storagePercent > 90
                ? "bg-danger"
                : storagePercent > 70
                  ? "bg-warning"
                  : "bg-accent"
            }`}
            style={{ width: `${storagePercent}%` }}
          />
        </div>
        <p className="mt-1 text-xs text-muted">{storagePercent}% 已使用</p>
      </div>
    </Card>
  );
}
