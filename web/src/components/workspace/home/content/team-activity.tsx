"use client";

import Link from "next/link";
import { Card, Avatar } from "@heroui/react";
import { Activity, ChevronRight, Edit3, Upload, Plus, Trash2 } from "lucide-react";

export interface ActivityItem {
  id: string;
  userId: string;
  userName: string;
  userAvatar?: string;
  action: "edit" | "create" | "upload" | "delete";
  targetType: string;
  targetName: string;
  timestamp: string;
}

interface TeamActivityProps {
  activities: ActivityItem[];
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

// Get action icon and text
function getActionInfo(action: ActivityItem["action"]): { icon: typeof Edit3; text: string; color: string } {
  switch (action) {
    case "edit":
      return { icon: Edit3, text: "编辑了", color: "text-blue-500" };
    case "create":
      return { icon: Plus, text: "创建了", color: "text-green-500" };
    case "upload":
      return { icon: Upload, text: "上传了", color: "text-purple-500" };
    case "delete":
      return { icon: Trash2, text: "删除了", color: "text-red-500" };
    default:
      return { icon: Edit3, text: "操作了", color: "text-muted" };
  }
}

export function TeamActivity({ activities, onViewAll }: TeamActivityProps) {
  return (
    <Card variant="tertiary" className="flex flex-col p-5">
      {/* Header */}
      <div className="mb-4 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Activity className="size-4 text-muted" />
          <h2 className="font-semibold">团队动态</h2>
        </div>
        <Link
          href="/workspace/activity"
          className="flex items-center gap-1 text-xs text-muted transition-colors hover:text-accent"
        >
          查看全部
          <ChevronRight className="size-3.5" />
        </Link>
      </div>

      {/* Activity List */}
      {activities.length > 0 ? (
        <div className="flex flex-1 flex-col gap-3">
          {activities.map((activity) => {
            const actionInfo = getActionInfo(activity.action);
            const ActionIcon = actionInfo.icon;
            return (
              <div
                key={activity.id}
                className="flex items-start gap-3 rounded-lg p-2 transition-colors hover:bg-muted/5"
              >
                <Avatar size="sm">
                  {activity.userAvatar ? (
                    <Avatar.Image src={activity.userAvatar} alt={activity.userName} />
                  ) : null}
                  <Avatar.Fallback className="text-xs">
                    {activity.userName.charAt(0).toUpperCase()}
                  </Avatar.Fallback>
                </Avatar>
                <div className="min-w-0 flex-1">
                  <p className="text-sm">
                    <span className="font-medium">{activity.userName}</span>
                    <span className="mx-1 text-muted">{actionInfo.text}</span>
                    <span className="font-medium">{activity.targetName}</span>
                  </p>
                  <p className="mt-0.5 text-xs text-muted">
                    {formatRelativeTime(activity.timestamp)}
                  </p>
                </div>
                <ActionIcon className={`size-4 shrink-0 ${actionInfo.color}`} />
              </div>
            );
          })}
        </div>
      ) : (
        <div className="flex flex-1 flex-col items-center justify-center py-8 text-center">
          <Activity className="size-10 text-muted/30" />
          <p className="mt-3 text-sm text-muted">暂无动态</p>
        </div>
      )}
    </Card>
  );
}
