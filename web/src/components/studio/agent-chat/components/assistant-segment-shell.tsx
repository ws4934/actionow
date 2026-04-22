"use client";

import type { ReactNode } from "react";
import { Avatar } from "@heroui/react";
import { Bot } from "lucide-react";

/**
 * Shared layout for assistant-side segment cards.  Reserves the avatar slot so
 * segments after the first render with the same left offset even when their
 * avatar is hidden.
 */
export function AssistantSegmentShell({
  showAvatar,
  children,
}: {
  showAvatar?: boolean;
  children: ReactNode;
}) {
  return (
    <div className="group flex gap-3">
      <div className="w-8 shrink-0">
        {showAvatar && (
          <Avatar size="sm" className="shrink-0">
            <Avatar.Fallback>
              <Bot className="size-4" />
            </Avatar.Fallback>
          </Avatar>
        )}
      </div>
      <div className="flex min-w-0 flex-1 flex-col items-stretch">
        {children}
      </div>
    </div>
  );
}
