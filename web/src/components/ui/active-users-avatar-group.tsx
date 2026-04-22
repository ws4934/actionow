"use client";

import { Avatar, Tooltip } from "@heroui/react";
import type { CollabUser } from "@/lib/websocket";

interface ActiveUsersAvatarGroupProps {
  users: CollabUser[];
  maxVisible?: number;
  size?: "sm" | "md";
}

/**
 * Avatar group component showing active users on a project/script
 */
export function ActiveUsersAvatarGroup({
  users,
  maxVisible = 3,
  size = "sm",
}: ActiveUsersAvatarGroupProps) {
  if (users.length === 0) return null;

  const visibleUsers = users.slice(0, maxVisible);
  const remainingCount = users.length - maxVisible;

  const sizeClasses = size === "sm" ? "size-6" : "size-8";
  const fontSize = size === "sm" ? "text-[10px]" : "text-xs";

  return (
    <div className="flex -space-x-1.5">
      {visibleUsers.map((user) => (
        <Tooltip key={user.userId} delay={0}>
          <Tooltip.Trigger>
            <Avatar className={`${sizeClasses} ring-2 ring-background`}>
              {user.avatar ? (
                <Avatar.Image alt={user.nickname} src={user.avatar} />
              ) : null}
              <Avatar.Fallback className={`${fontSize} bg-accent/20 text-accent`}>
                {user.nickname?.charAt(0).toUpperCase() || "U"}
              </Avatar.Fallback>
            </Avatar>
          </Tooltip.Trigger>
          <Tooltip.Content>
            <div className="flex items-center gap-2">
              <span>{user.nickname}</span>
              {user.collabStatus === "EDITING" && (
                <span className="rounded bg-warning/20 px-1.5 py-0.5 text-xs text-warning">
                  编辑中
                </span>
              )}
            </div>
          </Tooltip.Content>
        </Tooltip>
      ))}
      {remainingCount > 0 && (
        <Tooltip delay={0}>
          <Tooltip.Trigger>
            <Avatar className={`${sizeClasses} ring-2 ring-background`}>
              <Avatar.Fallback className={`${fontSize} bg-muted/20 text-muted`}>
                +{remainingCount}
              </Avatar.Fallback>
            </Avatar>
          </Tooltip.Trigger>
          <Tooltip.Content>
            <div className="flex flex-col gap-1">
              {users.slice(maxVisible).map((user) => (
                <span key={user.userId}>{user.nickname}</span>
              ))}
            </div>
          </Tooltip.Content>
        </Tooltip>
      )}
    </div>
  );
}
