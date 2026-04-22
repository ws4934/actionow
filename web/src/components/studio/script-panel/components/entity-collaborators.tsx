/**
 * Entity Collaborators Component
 * Shows viewers and editor overlay on entity cards
 */

"use client";

import { Avatar, Tooltip } from "@heroui/react";
import { Edit3 } from "lucide-react";
import type { CollabUser } from "@/lib/websocket/types";

interface EntityCollaboratorsProps {
  viewers: CollabUser[];
  editor: CollabUser | null;
  position?: "top-right" | "bottom-right";
  maxViewers?: number;
}

export function EntityCollaborators({
  viewers,
  editor,
  position = "top-right",
  maxViewers = 2,
}: EntityCollaboratorsProps) {
  const hasCollaborators = viewers.length > 0 || editor;

  if (!hasCollaborators) return null;

  const displayViewers = viewers.slice(0, maxViewers);
  const extraViewers = Math.max(0, viewers.length - maxViewers);

  const positionClasses =
    position === "top-right" ? "top-1.5 right-1.5" : "bottom-1.5 right-1.5";

  return (
    <div className={`absolute ${positionClasses} z-10 flex items-center gap-1`}>
      {/* Viewers */}
      {displayViewers.length > 0 && (
        <Tooltip delay={0}>
          <Tooltip.Trigger>
            <div className="flex -space-x-1.5">
              {displayViewers.map((viewer) => (
                <Avatar
                  key={viewer.userId}
                  size="sm"
                  className="size-5 border border-background shadow-sm"
                >
                  {viewer.avatar ? (
                    <Avatar.Image src={viewer.avatar} alt={viewer.nickname} />
                  ) : null}
                  <Avatar.Fallback className="text-[8px]">
                    {viewer.nickname?.charAt(0) || "?"}
                  </Avatar.Fallback>
                </Avatar>
              ))}
              {extraViewers > 0 && (
                <div className="flex size-5 items-center justify-center rounded-full border border-background bg-muted/80 text-[8px] font-medium text-foreground shadow-sm">
                  +{extraViewers}
                </div>
              )}
            </div>
          </Tooltip.Trigger>
          <Tooltip.Content>
            <div className="text-xs">
              {viewers.map((v) => v.nickname).join(", ")} 正在查看
            </div>
          </Tooltip.Content>
        </Tooltip>
      )}

      {/* Editor */}
      {editor && (
        <Tooltip delay={0}>
          <Tooltip.Trigger>
            <div className="flex items-center gap-1 rounded-full bg-accent px-1.5 py-0.5 shadow-sm">
              <Edit3 className="size-3 text-white" />
              <Avatar size="sm" className="size-4 border border-white/30">
                {editor.avatar ? (
                  <Avatar.Image src={editor.avatar} alt={editor.nickname} />
                ) : null}
                <Avatar.Fallback className="text-[8px]">
                  {editor.nickname?.charAt(0) || "?"}
                </Avatar.Fallback>
              </Avatar>
            </div>
          </Tooltip.Trigger>
          <Tooltip.Content>
            <div className="text-xs">{editor.nickname} 正在编辑</div>
          </Tooltip.Content>
        </Tooltip>
      )}
    </div>
  );
}

export default EntityCollaborators;
