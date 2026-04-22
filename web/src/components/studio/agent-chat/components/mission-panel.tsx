"use client";

import { useState, useEffect, useRef } from "react";
import { useTranslations } from "next-intl";
import {
  ChevronDown,
  ChevronUp,
  X,
  Loader2,
  CheckCircle2,
  XCircle,
  Clock,
  Circle,
  Zap,
} from "lucide-react";
import { Button, Chip } from "@heroui/react";
import { useMission } from "../hooks/use-mission";
import type { MissionStatus, StepStatus } from "@/lib/api/dto/mission.dto";

// ============================================================================
// Props
// ============================================================================

interface MissionPanelProps {
  sessionId: string;
  onDismiss?: () => void;
}

// ============================================================================
// Helpers
// ============================================================================

const STATUS_COLOR_MAP: Record<MissionStatus, "default" | "accent" | "success" | "danger" | "warning"> = {
  CREATED: "default",
  EXECUTING: "accent",
  WAITING: "warning",
  COMPLETED: "success",
  FAILED: "danger",
  CANCELLED: "warning",
};

const TERMINAL_STATUSES = new Set<MissionStatus>(["COMPLETED", "FAILED", "CANCELLED"]);

function StepStatusIcon({ status }: { status: StepStatus }) {
  switch (status) {
    case "RUNNING":
      return <Loader2 className="size-3.5 animate-spin text-accent" />;
    case "COMPLETED":
      return <CheckCircle2 className="size-3.5 text-success" />;
    case "FAILED":
      return <XCircle className="size-3.5 text-danger" />;
    default:
      return <Circle className="size-3.5 text-foreground-3" />;
  }
}

// ============================================================================
// Component
// ============================================================================

export function MissionPanel({ sessionId, onDismiss }: MissionPanelProps) {
  const t = useTranslations("workspace.missions");
  const { mission, progress, isConnected, cancelMission } = useMission(sessionId);
  const [isExpanded, setIsExpanded] = useState(false);
  const [isDismissed, setIsDismissed] = useState(false);
  const dismissTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Auto-dismiss 5s after reaching terminal state
  useEffect(() => {
    if (!mission) return;
    if (TERMINAL_STATUSES.has(mission.status)) {
      dismissTimerRef.current = setTimeout(() => {
        setIsDismissed(true);
        onDismiss?.();
      }, 5000);
    }
    return () => {
      if (dismissTimerRef.current) clearTimeout(dismissTimerRef.current);
    };
  }, [mission?.status, onDismiss]); // eslint-disable-line react-hooks/exhaustive-deps

  // Don't render if no mission, dismissed, or session changed and mission reset
  if (!mission || isDismissed) return null;

  const statusColor = STATUS_COLOR_MAP[mission.status] || "default";
  const isTerminal = TERMINAL_STATUSES.has(mission.status);
  const progressValue = progress?.progress ?? mission.progress ?? 0;
  const steps = progress?.steps ?? [];

  const handleDismiss = () => {
    setIsDismissed(true);
    onDismiss?.();
  };

  const handleCancel = async () => {
    await cancelMission();
  };

  return (
    <div className="mx-0 mb-2 overflow-hidden rounded-lg border border-border/60 bg-surface shadow-sm">
      {/* Collapsed bar (always visible) */}
      <div className="flex items-center gap-2 px-3 py-2">
        {/* Status indicator */}
        {!isTerminal && isConnected ? (
          <Loader2 className="size-3.5 shrink-0 animate-spin text-accent" />
        ) : (
          <Zap className="size-3.5 shrink-0 text-foreground-2" />
        )}

        {/* Title */}
        <span className="min-w-0 flex-1 truncate text-xs font-medium text-foreground">
          {mission.title || t("panel.title")}
        </span>

        {/* Progress bar (inline, compact) */}
        <div className="hidden w-20 sm:block">
          <div className="h-1 w-full overflow-hidden rounded-full bg-surface-2">
            <div
              className="h-full rounded-full bg-accent transition-all duration-500"
              style={{ width: `${Math.min(progressValue, 100)}%` }}
            />
          </div>
        </div>

        {/* Status chip */}
        <Chip size="sm" variant="soft" color={statusColor} className="shrink-0">
          {t(`status.${mission.status}`)}
        </Chip>

        {/* Expand/collapse */}
        <Button
          variant="ghost"
          size="sm"
          isIconOnly
          className="size-6 shrink-0"
          aria-label={isExpanded ? t("panel.collapse") : t("panel.expand")}
          onPress={() => setIsExpanded((v) => !v)}
        >
          {isExpanded ? <ChevronUp className="size-3.5" /> : <ChevronDown className="size-3.5" />}
        </Button>

        {/* Dismiss */}
        <Button
          variant="ghost"
          size="sm"
          isIconOnly
          className="size-6 shrink-0"
          aria-label={t("panel.dismiss")}
          onPress={handleDismiss}
        >
          <X className="size-3.5" />
        </Button>
      </div>

      {/* Expanded details */}
      {isExpanded && (
        <div className="border-t border-border/40 px-3 py-3 space-y-3">
          {/* Full progress bar */}
          <div>
            <div className="mb-1 flex items-center justify-between text-[11px] text-foreground-2">
              <span>
                {t("detail.progress")}: {mission.currentStep}/{mission.totalSteps}
              </span>
              <span className="tabular-nums">{progressValue}%</span>
            </div>
            <div className="h-1.5 w-full overflow-hidden rounded-full bg-surface-2">
              <div
                className="h-full rounded-full bg-accent transition-all duration-500"
                style={{ width: `${Math.min(progressValue, 100)}%` }}
              />
            </div>
          </div>

          {/* Current activity */}
          {progress?.currentActivity && (
            <p className="text-xs text-foreground-2 leading-relaxed">
              {progress.currentActivity}
            </p>
          )}

          {/* Steps list */}
          {steps.length > 0 && (
            <div className="space-y-1.5">
              {steps.map((step) => (
                <div key={step.number} className="flex items-center gap-2">
                  <StepStatusIcon status={step.status} />
                  <span className="min-w-0 flex-1 truncate text-xs text-foreground-2">
                    <span className="font-medium text-foreground">{t(`step.${step.type}`)}</span>
                    {step.summary && <span className="ml-1 text-foreground-3">— {step.summary}</span>}
                  </span>
                </div>
              ))}
            </div>
          )}

          {/* Credit cost */}
          {(progress?.totalCreditCost ?? mission.totalCreditCost) > 0 && (
            <div className="flex items-center gap-1.5 text-[11px] text-foreground-2">
              <span>{t("detail.creditCost")}:</span>
              <span className="font-medium text-foreground">
                {progress?.totalCreditCost ?? mission.totalCreditCost}
              </span>
            </div>
          )}

          {/* Waiting tasks stats */}
          {mission.status === "WAITING" && progress?.pendingTasks && (
            <div className="flex items-center gap-3 rounded-md bg-warning/5 px-2.5 py-2 text-[11px]">
              <Clock className="size-3.5 text-warning" />
              <span className="text-foreground-2">
                {t("detail.pendingTasks")}:{" "}
                <span className="font-medium text-foreground">
                  {progress.pendingTasks.completed}/{progress.pendingTasks.total}
                </span>
              </span>
            </div>
          )}

          {/* Cancel button */}
          {!isTerminal && (
            <Button
              variant="secondary"
              size="sm"
              className="h-7 gap-1.5 px-2.5 text-xs"
              onPress={handleCancel}
            >
              <X className="size-3" />
              {t("cancel")}
            </Button>
          )}
        </div>
      )}
    </div>
  );
}
