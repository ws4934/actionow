"use client";

import { useState, useRef, useCallback, useEffect } from "react";

interface ResizablePanelsProps {
  leftPanel: React.ReactNode;
  rightPanel: React.ReactNode;
  defaultLeftWidth?: number; // percentage 0-100
  minLeftWidth?: number; // percentage
  maxLeftWidth?: number; // percentage
  onLeftWidthChange?: (width: number) => void;
  className?: string;
}

export function ResizablePanels({
  leftPanel,
  rightPanel,
  defaultLeftWidth = 33,
  minLeftWidth = 20,
  maxLeftWidth = 60,
  onLeftWidthChange,
  className = "",
}: ResizablePanelsProps) {
  const [leftWidth, setLeftWidth] = useState(defaultLeftWidth);
  const [isDragging, setIsDragging] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    setIsDragging(true);
  }, []);

  const handleMouseMove = useCallback(
    (e: MouseEvent) => {
      if (!isDragging || !containerRef.current) return;

      const container = containerRef.current;
      const containerRect = container.getBoundingClientRect();
      const newLeftWidth =
        ((e.clientX - containerRect.left) / containerRect.width) * 100;

      // Clamp the value
      const clampedWidth = Math.min(
        Math.max(newLeftWidth, minLeftWidth),
        maxLeftWidth
      );
      setLeftWidth(clampedWidth);
      onLeftWidthChange?.(clampedWidth);
    },
    [isDragging, minLeftWidth, maxLeftWidth]
  );

  const handleMouseUp = useCallback(() => {
    setIsDragging(false);
  }, []);

  useEffect(() => {
    if (isDragging) {
      document.addEventListener("mousemove", handleMouseMove);
      document.addEventListener("mouseup", handleMouseUp);
      // Prevent text selection while dragging
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

  return (
    <div
      ref={containerRef}
      className={`flex h-full w-full gap-0 pt-3 ${className}`}
    >
      {/* Left Panel */}
      <div
        className="h-full shrink-0 overflow-hidden pr-1.5"
        style={{ width: `${leftWidth}%` }}
      >
        {leftPanel}
      </div>

      {/* Resizer */}
      <div
        className={`group relative z-10 h-full w-1 shrink-0 cursor-col-resize rounded-full ${
          isDragging ? "bg-accent" : "bg-transparent hover:bg-accent/30"
        }`}
        onMouseDown={handleMouseDown}
      >
        {/* Visual indicator */}
        <div
          className={`absolute inset-y-0 -left-1.5 -right-1.5 ${
            isDragging ? "bg-accent/10" : "group-hover:bg-accent/5"
          }`}
        />
        {/* Handle dots */}
        <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2">
          <div className="flex flex-col gap-1">
            <div
              className={`size-1 rounded-full ${
                isDragging ? "bg-accent" : "bg-muted/50 group-hover:bg-accent"
              }`}
            />
            <div
              className={`size-1 rounded-full ${
                isDragging ? "bg-accent" : "bg-muted/50 group-hover:bg-accent"
              }`}
            />
            <div
              className={`size-1 rounded-full ${
                isDragging ? "bg-accent" : "bg-muted/50 group-hover:bg-accent"
              }`}
            />
          </div>
        </div>
      </div>

      {/* Right Panel */}
      <div className="h-full min-w-0 flex-1 overflow-hidden pl-1.5">
        {rightPanel}
      </div>
    </div>
  );
}
