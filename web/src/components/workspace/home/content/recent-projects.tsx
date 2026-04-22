"use client";

import Link from "next/link";
import { Card, Chip, ScrollShadow } from "@heroui/react";
import { Film, ChevronRight, Clock } from "lucide-react";
import type { ScriptListDTO } from "@/lib/api/dto";

interface RecentProjectsProps {
  projects: ScriptListDTO[];
  onViewAll?: () => void;
}

// Format relative time
function formatRelativeTime(dateStr: string): string {
  const date = new Date(dateStr);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMinutes = Math.floor(diffMs / (1000 * 60));
  const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

  if (diffMinutes < 1) return "刚刚";
  if (diffMinutes < 60) return `${diffMinutes}分钟前`;
  if (diffHours < 24) return `${diffHours}小时前`;
  if (diffDays === 1) return "昨天";
  if (diffDays < 7) return `${diffDays}天前`;
  return date.toLocaleDateString("zh-CN", { month: "short", day: "numeric" });
}

// Get status label and color
function getStatusInfo(status: string): { label: string; color: "default" | "accent" | "success" | "warning" } {
  switch (status) {
    case "DRAFT":
      return { label: "草稿", color: "default" };
    case "IN_PROGRESS":
      return { label: "进行中", color: "accent" };
    case "COMPLETED":
      return { label: "已完成", color: "success" };
    case "ARCHIVED":
      return { label: "已归档", color: "warning" };
    default:
      return { label: status, color: "default" };
  }
}

export function RecentProjects({ projects, onViewAll }: RecentProjectsProps) {
  return (
    <Card variant="tertiary" className="p-5">
      {/* Header */}
      <div className="mb-4 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Clock className="size-4 text-muted" />
          <h2 className="font-semibold">继续编辑</h2>
        </div>
        <Link
          href="/workspace/projects"
          className="flex items-center gap-1 text-xs text-muted transition-colors hover:text-accent"
        >
          全部
          <ChevronRight className="size-3.5" />
        </Link>
      </div>

      {/* Projects Grid */}
      {projects.length > 0 ? (
        <ScrollShadow orientation="horizontal" className="-mx-1 px-1" hideScrollBar>
          <div className="flex gap-3">
            {projects.map((project) => {
              const statusInfo = getStatusInfo(project.status);
              return (
                <Link
                  key={project.id}
                  href={`/workspace/projects/${project.id}`}
                  className="group w-44 shrink-0"
                >
                  <div className="overflow-hidden rounded-xl border border-border bg-background transition-all group-hover:border-accent/50 group-hover:shadow-md">
                    {/* Cover */}
                    <div className="relative aspect-video overflow-hidden bg-muted/10">
                      {project.coverUrl ? (
                        <img
                          src={project.coverUrl}
                          alt={project.title}
                          className="size-full object-cover transition-transform group-hover:scale-105"
                        />
                      ) : (
                        <div className="flex size-full items-center justify-center">
                          <Film className="size-8 text-muted/30" />
                        </div>
                      )}
                      {/* Hover overlay */}
                      <div className="absolute inset-0 flex items-center justify-center bg-black/40 opacity-0 transition-opacity group-hover:opacity-100">
                        <span className="rounded-lg bg-white/90 px-3 py-1.5 text-xs font-medium text-foreground">
                          打开
                        </span>
                      </div>
                    </div>

                    {/* Info */}
                    <div className="p-3">
                      <h3 className="truncate text-sm font-medium group-hover:text-accent">
                        {project.title}
                      </h3>
                      <div className="mt-1.5 flex items-center justify-between">
                        <span className="text-xs text-muted">
                          {formatRelativeTime(project.updatedAt)}
                        </span>
                        <Chip size="sm" variant="soft" color={statusInfo.color}>
                          {statusInfo.label}
                        </Chip>
                      </div>
                    </div>
                  </div>
                </Link>
              );
            })}
          </div>
        </ScrollShadow>
      ) : (
        <div className="flex flex-col items-center justify-center py-8 text-center">
          <Film className="size-10 text-muted/30" />
          <p className="mt-3 text-sm text-muted">暂无项目</p>
          <p className="mt-1 text-xs text-muted">创建你的第一个剧本项目吧</p>
        </div>
      )}
    </Card>
  );
}
