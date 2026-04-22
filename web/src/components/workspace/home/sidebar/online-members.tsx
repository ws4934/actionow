"use client";

import { Avatar, Button } from "@heroui/react";
import { Plus } from "lucide-react";
import type { WorkspaceMemberDTO } from "@/lib/api/dto";

interface OnlineMembersProps {
  members: WorkspaceMemberDTO[];
  currentUserId?: string;
  onInvite?: () => void;
}

export function OnlineMembers({ members, currentUserId, onInvite }: OnlineMembersProps) {
  // Filter to show only active/online members (for now, show all active)
  const activeMembers = members.filter((m) => m.status === "Active").slice(0, 5);

  return (
    <div className="px-3">
      <h3 className="mb-2 px-2 text-xs font-medium uppercase tracking-wide text-muted">
        在线成员 ({activeMembers.length})
      </h3>
      <div className="flex flex-col gap-1">
        {activeMembers.map((member) => {
          const isCurrentUser = member.userId === currentUserId;
          return (
            <div
              key={member.id}
              className="flex items-center gap-2.5 rounded-lg px-2 py-1.5"
            >
              <div className="relative">
                <Avatar size="sm">
                  {member.avatar ? (
                    <Avatar.Image src={member.avatar} alt={member.nickname || member.username} />
                  ) : null}
                  <Avatar.Fallback className="text-xs">
                    {(member.nickname || member.username).charAt(0).toUpperCase()}
                  </Avatar.Fallback>
                </Avatar>
                {/* Online indicator */}
                <span className="absolute bottom-0 right-0 size-2.5 rounded-full border-2 border-background bg-success" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm">
                  {member.nickname || member.username}
                  {isCurrentUser && (
                    <span className="ml-1 text-xs text-muted">(你)</span>
                  )}
                </p>
                <p className="truncate text-xs text-muted">
                  {isCurrentUser ? "在线" : "在线"}
                </p>
              </div>
            </div>
          );
        })}

        {activeMembers.length === 0 && (
          <p className="px-2 py-2 text-xs text-muted">暂无在线成员</p>
        )}

        {onInvite && (
          <Button
            variant="ghost"
            size="sm"
            className="mt-1 w-full justify-start gap-2 px-2 text-xs"
            onPress={onInvite}
          >
            <Plus className="size-4" />
            邀请成员
          </Button>
        )}
      </div>
    </div>
  );
}
