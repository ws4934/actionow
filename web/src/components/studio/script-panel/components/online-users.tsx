/**
 * Online Users Component
 * Shows avatar group of users currently in the script
 */

"use client";

import { Avatar, Tooltip } from "@heroui/react";
import { Users } from "lucide-react";
import type { CollabUser } from "@/lib/websocket/types";

interface OnlineUsersProps {
  users: CollabUser[];
  maxDisplay?: number;
  size?: "sm" | "md";
}

export function OnlineUsers({ users, maxDisplay = 4, size = "sm" }: OnlineUsersProps) {
  if (users.length === 0) return null;

  const displayUsers = users.slice(0, maxDisplay);
  const remainingCount = Math.max(0, users.length - maxDisplay);

  const avatarSize = size === "sm" ? "size-6" : "size-8";
  const fontSize = size === "sm" ? "text-[10px]" : "text-xs";

  return (
    <Tooltip delay={0}>
      <Tooltip.Trigger>
        <div className="flex items-center gap-1">
          <div className="flex -space-x-2">
            {displayUsers.map((user) => (
              <Avatar
                key={user.userId}
                size="sm"
                className={`${avatarSize} border-2 border-background`}
              >
                {user.avatar ? (
                  <Avatar.Image src={user.avatar} alt={user.nickname} />
                ) : null}
                <Avatar.Fallback className={fontSize}>
                  {user.nickname?.charAt(0) || "?"}
                </Avatar.Fallback>
              </Avatar>
            ))}
            {remainingCount > 0 && (
              <div
                className={`flex ${avatarSize} items-center justify-center rounded-full border-2 border-background bg-muted/20 ${fontSize} font-medium text-muted`}
              >
                +{remainingCount}
              </div>
            )}
          </div>
          <span className="flex items-center gap-0.5 text-xs text-muted">
            <Users className="size-3" />
            {users.length}
          </span>
        </div>
      </Tooltip.Trigger>
      <Tooltip.Content>
        <div className="flex flex-col gap-1 py-1">
          <div className="text-xs font-medium">{users.length} 人在线</div>
          <div className="flex flex-col gap-0.5">
            {users.slice(0, 10).map((user) => (
              <div key={user.userId} className="flex items-center gap-1.5 text-xs">
                <span
                  className={`size-1.5 rounded-full ${
                    user.collabStatus === "EDITING" ? "bg-accent" : "bg-success"
                  }`}
                />
                <span>{user.nickname}</span>
                {user.collabStatus === "EDITING" && (
                  <span className="text-accent">(编辑中)</span>
                )}
              </div>
            ))}
            {users.length > 10 && (
              <div className="text-xs text-muted">还有 {users.length - 10} 人...</div>
            )}
          </div>
        </div>
      </Tooltip.Content>
    </Tooltip>
  );
}

export default OnlineUsers;
