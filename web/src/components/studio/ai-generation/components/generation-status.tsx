"use client";

import { Button, Spinner } from "@heroui/react";
import { X } from "lucide-react";
import { useTranslations } from "next-intl";
import type { ExecutionStatus } from "@/lib/api/dto/ai.dto";

interface GenerationStatusProps {
  status: ExecutionStatus;
  progress?: number;
  currentStep?: string;
  onCancel?: () => void;
}

export function GenerationStatus({
  status,
  progress,
  currentStep,
  onCancel,
}: GenerationStatusProps) {
  const t = useTranslations("workspace.aiGeneration.generationStatus");
  const tAction = useTranslations("workspace.aiGeneration.action");
  const isActive = status === "PENDING" || status === "RUNNING";

  if (!isActive) return null;

  return (
    <div className="absolute inset-0 z-10 flex flex-col items-center justify-center gap-4 bg-background/80 backdrop-blur-sm">
      <div className="flex flex-col items-center gap-3">
        <Spinner size="lg" color="accent" />

        <div className="flex flex-col items-center gap-1 text-center">
          <span className="text-sm font-medium">
            {status === "PENDING" ? t("pending") : t("running")}
          </span>
          {currentStep && (
            <span className="text-xs text-muted">{currentStep}</span>
          )}
        </div>

        {progress !== undefined && progress > 0 && (
          <div className="w-48">
            <div className="h-1.5 overflow-hidden rounded-full bg-muted/20">
              <div
                className="h-full rounded-full bg-accent transition-all duration-300"
                style={{ width: `${Math.min(100, progress)}%` }}
              />
            </div>
            <span className="mt-1 block text-center text-xs text-muted">
              {Math.round(progress)}%
            </span>
          </div>
        )}
      </div>

      {onCancel && (
        <Button
          variant="ghost"
          size="sm"
          onPress={onCancel}
        >
          <X className="size-4" />
          <span>{tAction("cancel")}</span>
        </Button>
      )}
    </div>
  );
}

// Inline status indicator for the header
export function GenerationStatusBadge({
  status,
}: {
  status: ExecutionStatus | null;
}) {
  const t = useTranslations("workspace.aiGeneration.generationStatus");
  if (!status) return null;

  const getStatusConfig = () => {
    switch (status) {
      case "PENDING":
        return { label: t("pending"), color: "text-warning" };
      case "RUNNING":
        return { label: t("running"), color: "text-accent" };
      case "SUCCEEDED":
        return { label: t("succeeded"), color: "text-success" };
      case "FAILED":
        return { label: t("failed"), color: "text-danger" };
      case "CANCELLED":
        return { label: t("cancelled"), color: "text-muted" };
      case "TIMEOUT":
        return { label: t("timeout"), color: "text-danger" };
      default:
        return null;
    }
  };

  const config = getStatusConfig();
  if (!config) return null;

  return (
    <span className={`text-xs ${config.color}`}>
      {(status === "PENDING" || status === "RUNNING") && (
        <Spinner size="sm" className="mr-1 inline-block size-3" />
      )}
      {config.label}
    </span>
  );
}
