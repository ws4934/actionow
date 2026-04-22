/**
 * Editing Overlay Component
 * Shows when another user is editing an entity, with fallback mechanisms
 */

"use client";

import { useState, useEffect, useCallback } from "react";
import { Avatar, Button } from "@heroui/react";
import { RefreshCw, Edit3, Eye } from "lucide-react";
import { useTranslations } from "next-intl";
import type { CollabUser } from "@/lib/websocket/types";

interface EditingOverlayProps {
  /** The user currently editing */
  editor: CollabUser;
  /** When the lock started (timestamp) */
  lockedAt?: number | null;
  /** Timeout threshold in minutes before showing force edit option */
  timeoutMinutes?: number;
  /** Callback when user clicks "View Read-only" */
  onViewReadOnly?: () => void;
  /** Callback when user clicks "Refresh Status" */
  onRefresh?: () => void;
  /** Callback when user clicks "Force Edit" */
  onForceEdit?: () => void;
  /** Additional class names */
  className?: string;
}

function formatDuration(ms: number): string {
  const seconds = Math.floor(ms / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);

  if (hours > 0) {
    return `${hours}:${String(minutes % 60).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;
  }
  return `${minutes}:${String(seconds % 60).padStart(2, "0")}`;
}

export function EditingOverlay({
  editor,
  lockedAt,
  timeoutMinutes = 5,
  onViewReadOnly,
  onRefresh,
  onForceEdit,
  className = "",
}: EditingOverlayProps) {
  const [elapsedMs, setElapsedMs] = useState(0);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const t = useTranslations("workspace.studio.editing");

  const timeoutMs = timeoutMinutes * 60 * 1000;
  const isTimedOut = elapsedMs >= timeoutMs;

  // Update elapsed time every second
  useEffect(() => {
    if (!lockedAt) {
      setElapsedMs(0);
      return;
    }

    const updateElapsed = () => {
      setElapsedMs(Date.now() - lockedAt);
    };

    updateElapsed();
    const interval = setInterval(updateElapsed, 1000);

    return () => clearInterval(interval);
  }, [lockedAt]);

  const handleRefresh = useCallback(async () => {
    if (!onRefresh) return;
    setIsRefreshing(true);
    try {
      await onRefresh();
    } finally {
      // Small delay for visual feedback
      setTimeout(() => setIsRefreshing(false), 500);
    }
  }, [onRefresh]);

  return (
    <div
      className={`absolute inset-0 z-30 flex items-center justify-center bg-background/80 backdrop-blur-sm ${className}`}
    >
      <div className="flex flex-col items-center gap-4 rounded-xl bg-muted/10 p-6 shadow-lg">
        {/* Editor Avatar */}
        <div className="relative">
          <Avatar size="lg" className="size-16 border-2 border-accent">
            {editor.avatar ? (
              <Avatar.Image src={editor.avatar} alt={editor.nickname} />
            ) : null}
            <Avatar.Fallback className="text-lg">
              {editor.nickname?.charAt(0) || "?"}
            </Avatar.Fallback>
          </Avatar>
          <div className="absolute -bottom-1 -right-1 rounded-full bg-accent p-1.5">
            <Edit3 className="size-3 text-white" />
          </div>
        </div>

        {/* Editor Info */}
        <div className="text-center">
          <p className="text-sm font-medium">{editor.nickname}</p>
          <p className="mt-1 flex items-center justify-center gap-1.5 text-xs text-muted">
            <span className="inline-block size-1.5 animate-pulse rounded-full bg-accent" />
            {t("isEditing")}
          </p>
        </div>

        {/* Lock Duration */}
        {lockedAt && (
          <div className="text-xs text-muted">
            {t("lockedDuration", { duration: formatDuration(elapsedMs) })}
          </div>
        )}

        {/* Actions */}
        <div className="flex items-center gap-2">
          {onViewReadOnly && (
            <Button
              variant="secondary"
              size="sm"
              className="gap-1.5 text-xs"
              onPress={onViewReadOnly}
            >
              <Eye className="size-3.5" />
              {t("viewReadOnly")}
            </Button>
          )}
          {onRefresh && (
            <Button
              variant="ghost"
              size="sm"
              className="gap-1.5 text-xs"
              onPress={handleRefresh}
              isDisabled={isRefreshing}
            >
              <RefreshCw className={`size-3.5 ${isRefreshing ? "animate-spin" : ""}`} />
              {t("refreshStatus")}
            </Button>
          )}
        </div>

        {/* Force Edit (shown after timeout) */}
        {isTimedOut && onForceEdit && (
          <div className="mt-2 border-t border-muted/20 pt-4 text-center">
            <p className="mb-2 text-xs text-muted">
              {t("lockTimeout", { minutes: timeoutMinutes })}
            </p>
            <Button
              variant="outline"
              size="sm"
              className="gap-1.5 text-xs text-warning hover:bg-warning/10"
              onPress={onForceEdit}
            >
              <Edit3 className="size-3.5" />
              {t("forceEdit")}
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}

export default EditingOverlay;
