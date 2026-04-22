"use client";

import { useEffect, useState, useCallback, useRef } from "react";
import { useParams } from "next/navigation";
import { useWebSocket } from "@/lib/websocket";
import { ResizablePanels } from "@/components/ui/resizable-panels";
import { AgentChat } from "@/components/studio/agent-chat";
import { ScriptPanel } from "@/components/studio/script-panel";
import { AIGenerationPanel } from "@/components/studio/ai-generation";
import { useAIGeneration } from "@/components/providers/ai-generation-provider";
import { CommentPanel } from "@/components/collab/comment-panel";
import { useCommentPanelStore } from "@/lib/stores/comment-panel-store";
import { usePreferencesStore } from "@/lib/stores/preferences-store";

const COMMENT_PANEL_MIN_WIDTH = 280;
const COMMENT_PANEL_MAX_WIDTH = 560;
const COLLAPSED_WIDTH = 40; // w-10 = 2.5rem = 40px

// Left panel that switches between Agent and AI Generation
function LeftPanel({ scriptId }: { scriptId: string }) {
  const { activePanel } = useAIGeneration();

  return activePanel === "agent" ? (
    <AgentChat scriptId={scriptId} />
  ) : (
    <AIGenerationPanel scriptId={scriptId} />
  );
}

export default function ScriptStudioPage() {
  const params = useParams();
  const scriptId = params.scriptId as string;
  const { enterScript, leaveScript } = useWebSocket();
  const isCommentOpen = useCommentPanelStore((s) => s.isOpen);
  const clearCommentTarget = useCommentPanelStore((s) => s.clearTarget);
  const commentPanelWidth = usePreferencesStore((s) => s.commentPanelWidth);
  const setCommentPanelWidth = usePreferencesStore((s) => s.setCommentPanelWidth);
  const leftPanelWidth = usePreferencesStore((s) => s.leftPanelWidth);
  const setLeftPanelWidth = usePreferencesStore((s) => s.setLeftPanelWidth);

  const [isDragging, setIsDragging] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    setIsDragging(true);
  }, []);

  const handleMouseMove = useCallback(
    (e: MouseEvent) => {
      if (!isDragging || !containerRef.current) return;
      const containerRect = containerRef.current.getBoundingClientRect();
      // Panel is on the right, so width = container right edge - mouse X
      const newWidth = containerRect.right - e.clientX;
      const clamped = Math.min(
        Math.max(newWidth, COMMENT_PANEL_MIN_WIDTH),
        COMMENT_PANEL_MAX_WIDTH
      );
      setCommentPanelWidth(clamped);
    },
    [isDragging, setCommentPanelWidth]
  );

  const handleMouseUp = useCallback(() => {
    setIsDragging(false);
  }, []);

  useEffect(() => {
    if (isDragging) {
      document.addEventListener("mousemove", handleMouseMove);
      document.addEventListener("mouseup", handleMouseUp);
      document.body.style.userSelect = "none";
      document.body.style.cursor = "col-resize";
    }
    return () => {
      document.removeEventListener("mousemove", handleMouseMove);
      document.removeEventListener("mouseup", handleMouseUp);
      document.body.style.userSelect = "";
      document.body.style.cursor = "";
    };
  }, [isDragging, handleMouseMove, handleMouseUp]);

  // Notify backend via WebSocket when entering/leaving script
  useEffect(() => {
    if (scriptId) {
      enterScript(scriptId, "DETAIL");
    }

    return () => {
      leaveScript();
    };
  }, [scriptId, enterScript, leaveScript]);

  // Clear comment panel target when navigating to a different script
  useEffect(() => {
    return () => {
      clearCommentTarget();
    };
  }, [scriptId, clearCommentTarget]);

  return (
    <div ref={containerRef} className="flex h-full flex-col bg-background">
      <div className="flex min-h-0 flex-1">
        {/* Left + Script panels — shrink naturally when comment panel opens */}
        <div className="min-w-0 flex-1">
          <ResizablePanels
            leftPanel={<LeftPanel scriptId={scriptId} />}
            rightPanel={<ScriptPanel scriptId={scriptId} />}
            defaultLeftWidth={leftPanelWidth}
            minLeftWidth={30}
            maxLeftWidth={70}
            onLeftWidthChange={setLeftPanelWidth}
          />
        </div>

        {/* Comment panel — resizable width, slides in/out */}
        <div
          className={`relative flex shrink-0 overflow-hidden ${
            isDragging ? "" : "transition-[width] duration-300"
          }`}
          style={{ width: isCommentOpen ? commentPanelWidth : COLLAPSED_WIDTH }}
        >
          {/* Resize handle — only when panel is open */}
          {isCommentOpen && (
            <div
              className={`group absolute inset-y-0 left-0 z-10 w-1 cursor-col-resize ${
                isDragging ? "bg-accent" : "bg-transparent hover:bg-accent/30"
              }`}
              onMouseDown={handleMouseDown}
            >
              <div
                className={`absolute inset-y-0 -left-1.5 -right-1.5 ${
                  isDragging ? "bg-accent/10" : "group-hover:bg-accent/5"
                }`}
              />
              <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2">
                <div className="flex flex-col gap-1">
                  <div className={`size-1 rounded-full ${isDragging ? "bg-accent" : "bg-muted/50 group-hover:bg-accent"}`} />
                  <div className={`size-1 rounded-full ${isDragging ? "bg-accent" : "bg-muted/50 group-hover:bg-accent"}`} />
                  <div className={`size-1 rounded-full ${isDragging ? "bg-accent" : "bg-muted/50 group-hover:bg-accent"}`} />
                </div>
              </div>
            </div>
          )}
          <CommentPanel />
        </div>
      </div>
    </div>
  );
}
