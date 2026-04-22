"use client";

import { useState, useEffect } from "react";
import { useTranslations } from "next-intl";
import {
  Loader2,
  X as XIcon,
  Layers,
  CheckCircle2,
  XCircle,
  Circle,
  ChevronDown,
  ChevronRight,
  Clock,
  AlertTriangle,
} from "lucide-react";
import { Button, Card, Chip, Skeleton, ScrollShadow, ProgressBar } from "@heroui/react";
import { missionService } from "@/lib/api/services/mission.service";
import type {
  MissionResponseDTO,
  MissionStepDTO,
  MissionProgressDTO,
  MissionStatus,
  StepStatus,
} from "@/lib/api/dto/mission.dto";

// ============================================================================
// Props
// ============================================================================

interface MissionDetailPanelProps {
  missionId: string | null;
  onClose: () => void;
  onCancel?: (missionId: string) => void;
}

// ============================================================================
// Constants
// ============================================================================

const STATUS_COLOR_MAP: Record<MissionStatus, "default" | "accent" | "success" | "danger" | "warning"> = {
  CREATED: "default",
  EXECUTING: "accent",
  WAITING: "warning",
  COMPLETED: "success",
  FAILED: "danger",
  CANCELLED: "warning",
};

// ============================================================================
// Sub-components
// ============================================================================

function DetailRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-4 py-1.5">
      <span className="shrink-0 text-xs text-foreground-2">{label}</span>
      <span className="text-right text-xs font-medium text-foreground">{children}</span>
    </div>
  );
}

function StepStatusIcon({ status }: { status: StepStatus }) {
  switch (status) {
    case "RUNNING":
      return <Loader2 className="size-4 animate-spin text-accent" />;
    case "COMPLETED":
      return <CheckCircle2 className="size-4 text-success" />;
    case "FAILED":
      return <XCircle className="size-4 text-danger" />;
    default:
      return <Circle className="size-4 text-foreground-3" />;
  }
}

function StepAccordionItem({
  step,
  t,
}: {
  step: MissionStepDTO;
  t: ReturnType<typeof useTranslations>;
}) {
  const [open, setOpen] = useState(false);
  const hasToolCalls = step.toolCalls && step.toolCalls.length > 0;

  return (
    <div className="rounded-lg border border-border/50 bg-surface">
      <button
        className="flex w-full items-center gap-3 px-3 py-2.5 text-left"
        onClick={() => setOpen((v) => !v)}
      >
        <StepStatusIcon status={step.status} />
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <span className="text-xs font-medium text-foreground">
              {t(`step.${step.stepType}`)}
            </span>
            <span className="rounded bg-surface-2 px-1.5 py-0.5 text-[10px] text-foreground-2">
              #{step.stepNumber}
            </span>
            <Chip size="sm" variant="soft" color={
              step.status === "COMPLETED" ? "success" :
              step.status === "FAILED" ? "danger" :
              step.status === "RUNNING" ? "accent" : "default"
            }>
              {t(`stepStatus.${step.status}`)}
            </Chip>
          </div>
          {step.outputSummary && (
            <p className="mt-0.5 truncate text-xs text-foreground-2">{step.outputSummary}</p>
          )}
        </div>
        <div className="flex shrink-0 items-center gap-2 text-[11px] text-foreground-3">
          {step.durationMs != null && (
            <span className="flex items-center gap-0.5">
              <Clock className="size-3" />
              {Math.round(step.durationMs / 1000)}s
            </span>
          )}
          {step.creditCost > 0 && <span>{step.creditCost} cr</span>}
          {hasToolCalls && (
            open ? <ChevronDown className="size-3.5" /> : <ChevronRight className="size-3.5" />
          )}
        </div>
      </button>

      {open && (
        <div className="space-y-2 border-t border-border/40 px-3 py-2.5">
          {step.inputSummary && (
            <div>
              <p className="mb-1 text-[11px] font-medium text-foreground-2">Input</p>
              <p className="text-xs text-foreground">{step.inputSummary}</p>
            </div>
          )}
          {hasToolCalls && (
            <div>
              <p className="mb-1 text-[11px] font-medium text-foreground-2">Tool Calls</p>
              <pre className="max-h-32 overflow-auto rounded bg-surface-2 p-2 text-[11px] text-foreground-2">
                {JSON.stringify(step.toolCalls, null, 2)}
              </pre>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function DetailSkeleton() {
  return (
    <div className="space-y-4 p-4">
      <Skeleton className="h-8 w-full rounded-lg" />
      <Skeleton className="h-20 w-full rounded-lg" />
      <div className="space-y-3">
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className="flex justify-between gap-4">
            <Skeleton className="h-3 w-24 rounded" />
            <Skeleton className="h-3 w-20 rounded" />
          </div>
        ))}
      </div>
      {Array.from({ length: 3 }).map((_, i) => (
        <Skeleton key={i} className="h-14 w-full rounded-lg" />
      ))}
    </div>
  );
}

// ============================================================================
// Component
// ============================================================================

export function MissionDetailPanel({
  missionId,
  onClose,
  onCancel,
}: MissionDetailPanelProps) {
  const t = useTranslations("workspace.missions");
  const [mission, setMission] = useState<MissionResponseDTO | null>(null);
  const [steps, setSteps] = useState<MissionStepDTO[]>([]);
  const [progress, setProgress] = useState<MissionProgressDTO | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (!missionId) {
      setMission(null);
      setSteps([]);
      setProgress(null);
      return;
    }

    const load = async () => {
      setIsLoading(true);
      try {
        const [m, s, p] = await Promise.allSettled([
          missionService.getMission(missionId),
          missionService.getSteps(missionId),
          missionService.getProgress(missionId),
        ]);
        if (m.status === "fulfilled") setMission(m.value);
        if (s.status === "fulfilled") setSteps(s.value);
        if (p.status === "fulfilled") setProgress(p.value);
      } finally {
        setIsLoading(false);
      }
    };
    load();
  }, [missionId]);

  const isActive = mission?.status === "EXECUTING" || mission?.status === "WAITING" || mission?.status === "CREATED";
  const statusColor = mission ? STATUS_COLOR_MAP[mission.status] || "default" : "default";
  const progressValue = progress?.progress ?? mission?.progress ?? 0;

  return (
    <Card className="flex h-full flex-col overflow-hidden">
      {/* Header */}
      <div className="flex shrink-0 items-center gap-3 border-b border-border px-4 py-3">
        <div className="flex size-7 shrink-0 items-center justify-center rounded-md bg-surface">
          <Layers className="size-3.5 text-foreground-2" />
        </div>
        <span className="min-w-0 flex-1 truncate text-sm font-medium text-foreground">
          {mission?.title || t("detail.title")}
        </span>
        {mission && (
          <Chip size="sm" variant="soft" color={statusColor} className="shrink-0">
            {t(`status.${mission.status}`)}
          </Chip>
        )}
        <Button isIconOnly variant="ghost" size="sm" onPress={onClose} aria-label="Close" className="ml-1 shrink-0">
          <XIcon className="size-4" />
        </Button>
      </div>

      {/* Body */}
      <ScrollShadow className="min-h-0 flex-1 overflow-y-auto">
        {isLoading ? (
          <DetailSkeleton />
        ) : mission ? (
          <div className="space-y-4 p-4">
            {/* Progress bar */}
            {progressValue > 0 && (
              <ProgressBar aria-label="Mission progress" value={Math.min(progressValue, 100)} size="md" color="accent">
                <div className="flex items-center justify-between text-xs text-foreground-2">
                  <span>{mission.currentStep}/{mission.totalSteps} {t("column.steps").toLowerCase()}</span>
                </div>
                <ProgressBar.Output />
                <ProgressBar.Track>
                  <ProgressBar.Fill />
                </ProgressBar.Track>
              </ProgressBar>
            )}

            {/* Goal */}
            {mission.goal && (
              <div className="rounded-lg bg-surface px-3 py-2.5">
                <p className="mb-1 text-xs font-medium text-foreground-2">{t("detail.goal")}</p>
                <p className="text-xs leading-relaxed text-foreground">{mission.goal}</p>
              </div>
            )}

            {/* Key info */}
            <div className="rounded-lg bg-surface px-3">
              {mission.totalCreditCost > 0 && (
                <DetailRow label={t("detail.creditCost")}>{mission.totalCreditCost}</DetailRow>
              )}
              {progress?.currentActivity && (
                <DetailRow label={t("detail.currentActivity")}>{progress.currentActivity}</DetailRow>
              )}
              {mission.startedAt && (
                <DetailRow label={t("column.startedAt")}>{new Date(mission.startedAt).toLocaleString()}</DetailRow>
              )}
              {mission.completedAt && (
                <DetailRow label="Completed">{new Date(mission.completedAt).toLocaleString()}</DetailRow>
              )}
            </div>

            {/* Pending Tasks stats */}
            {mission.status === "WAITING" && progress?.pendingTasks && (
              <div className="rounded-lg border border-warning/20 bg-warning/5 px-3 py-2.5">
                <p className="mb-1.5 text-xs font-medium text-warning">{t("detail.pendingTasks")}</p>
                <div className="flex gap-4 text-xs text-foreground-2">
                  <span>Total: <span className="font-medium text-foreground">{progress.pendingTasks.total}</span></span>
                  <span>Done: <span className="font-medium text-success">{progress.pendingTasks.completed}</span></span>
                  <span>Failed: <span className="font-medium text-danger">{progress.pendingTasks.failed}</span></span>
                  <span>Running: <span className="font-medium text-accent">{progress.pendingTasks.running}</span></span>
                </div>
              </div>
            )}

            {/* Error */}
            {mission.status === "FAILED" && mission.errorMessage && (
              <div className="rounded-lg border border-danger/20 bg-danger/5 p-3">
                <div className="mb-1 flex items-center gap-1.5 text-xs font-medium text-danger">
                  <AlertTriangle className="size-3.5" />
                  Error
                </div>
                <pre className="max-h-24 overflow-auto whitespace-pre-wrap break-all text-xs text-danger/80">
                  {mission.errorMessage}
                </pre>
              </div>
            )}

            {/* Steps */}
            {steps.length > 0 && (
              <div>
                <p className="mb-2 text-xs font-medium text-foreground">{t("detail.steps")}</p>
                <div className="space-y-2">
                  {steps.map((step) => (
                    <StepAccordionItem key={step.id} step={step} t={t} />
                  ))}
                </div>
              </div>
            )}
          </div>
        ) : (
          <p className="py-10 text-center text-foreground-2">Mission not found</p>
        )}
      </ScrollShadow>

      {/* Footer */}
      {isActive && onCancel && missionId && (
        <div className="flex shrink-0 items-center gap-2 border-t border-border px-4 py-2.5">
          <Button variant="secondary" size="sm" onPress={() => onCancel(missionId)}>
            <XIcon className="size-4" />
            {t("cancel")}
          </Button>
        </div>
      )}
    </Card>
  );
}
