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
import { Modal, Button, Chip } from "@heroui/react";
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

interface MissionDetailModalProps {
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
  missionId: string | null;
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
        <div className="border-t border-border/40 px-3 py-2.5 space-y-2">
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

// ============================================================================
// Component
// ============================================================================

export function MissionDetailModal({
  isOpen,
  onOpenChange,
  missionId,
  onCancel,
}: MissionDetailModalProps) {
  const t = useTranslations("workspace.missions");
  const [mission, setMission] = useState<MissionResponseDTO | null>(null);
  const [steps, setSteps] = useState<MissionStepDTO[]>([]);
  const [progress, setProgress] = useState<MissionProgressDTO | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (!isOpen || !missionId) {
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
  }, [isOpen, missionId]);

  const isActive = mission?.status === "EXECUTING" || mission?.status === "WAITING" || mission?.status === "CREATED";
  const statusColor = mission ? STATUS_COLOR_MAP[mission.status] || "default" : "default";
  const progressValue = progress?.progress ?? mission?.progress ?? 0;

  return (
    <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange} isDismissable>
      <Modal.Container placement="center">
        <Modal.Dialog className="sm:max-w-lg">
          <Modal.CloseTrigger />
          <Modal.Header>
            <Modal.Heading>
              <div className="flex items-center gap-3">
                <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-surface">
                  <Layers className="size-4 text-foreground-2" />
                </div>
                <div className="min-w-0 flex-1">
                  <span className="block truncate">{mission?.title || t("detail.title")}</span>
                </div>
                {mission && (
                  <Chip size="sm" variant="soft" color={statusColor} className="shrink-0">
                    {t(`status.${mission.status}`)}
                  </Chip>
                )}
              </div>
            </Modal.Heading>
          </Modal.Header>
          <Modal.Body>
            {isLoading ? (
              <div className="flex items-center justify-center py-10">
                <Loader2 className="size-6 animate-spin text-accent" />
              </div>
            ) : mission ? (
              <div className="space-y-4">
                {/* Progress bar */}
                {progressValue > 0 && (
                  <div>
                    <div className="mb-1.5 flex items-center justify-between text-xs text-foreground-2">
                      <span>
                        {mission.currentStep}/{mission.totalSteps} {t("column.steps").toLowerCase()}
                      </span>
                      <span className="tabular-nums">{progressValue}%</span>
                    </div>
                    <div className="h-2 w-full overflow-hidden rounded-full bg-surface-2">
                      <div
                        className="h-full rounded-full bg-accent transition-all duration-500"
                        style={{ width: `${Math.min(progressValue, 100)}%` }}
                      />
                    </div>
                  </div>
                )}

                {/* Goal */}
                {mission.goal && (
                  <div className="rounded-lg bg-surface px-3 py-2.5">
                    <p className="mb-1 text-xs font-medium text-foreground-2">{t("detail.goal")}</p>
                    <p className="text-xs text-foreground leading-relaxed">{mission.goal}</p>
                  </div>
                )}

                {/* Key info */}
                <div className="rounded-lg bg-surface px-3">
                  {mission.totalCreditCost > 0 && (
                    <DetailRow label={t("detail.creditCost")}>
                      {mission.totalCreditCost}
                    </DetailRow>
                  )}
                  {progress?.currentActivity && (
                    <DetailRow label={t("detail.currentActivity")}>
                      {progress.currentActivity}
                    </DetailRow>
                  )}
                  {mission.startedAt && (
                    <DetailRow label={t("column.startedAt")}>
                      {new Date(mission.startedAt).toLocaleString()}
                    </DetailRow>
                  )}
                  {mission.completedAt && (
                    <DetailRow label="Completed">
                      {new Date(mission.completedAt).toLocaleString()}
                    </DetailRow>
                  )}
                </div>

                {/* Pending Tasks stats (WAITING) */}
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

                {/* Steps accordion */}
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
          </Modal.Body>
          <Modal.Footer>
            {isActive && onCancel && missionId && (
              <Button
                variant="secondary"
                size="sm"
                onPress={() => {
                  onCancel(missionId);
                  onOpenChange(false);
                }}
              >
                <XIcon className="size-4" />
                {t("cancel")}
              </Button>
            )}
            <Button variant="secondary" slot="close" size="sm">
              Close
            </Button>
          </Modal.Footer>
        </Modal.Dialog>
      </Modal.Container>
    </Modal.Backdrop>
  );
}
