"use client";

import { useTranslations } from "next-intl";
import { Chip, ProgressBar, Spinner } from "@heroui/react";
import { Sparkles, Search, Cpu, ListChecks, Target, ShieldCheck, Loader2 } from "lucide-react";
import type { ComponentType } from "react";
import type { StatusEventMetadata } from "@/lib/api/dto";

const PHASE_MAP: Record<string, { icon: ComponentType<{ className?: string }>; i18nKey: string }> = {
  skill_loading: { icon: Sparkles, i18nKey: "skillLoading" },
  rag_retrieval: { icon: Search, i18nKey: "ragRetrieval" },
  llm_invoking: { icon: Cpu, i18nKey: "llmInvoking" },
  tool_batch_progress: { icon: ListChecks, i18nKey: "toolBatchProgress" },
  mission_step: { icon: Target, i18nKey: "missionStep" },
  preflight: { icon: ShieldCheck, i18nKey: "preflight" },
  context_preparing: { icon: Loader2, i18nKey: "contextPreparing" },
};

export function StatusIndicator({ status }: { status: StatusEventMetadata }) {
  const t = useTranslations("workspace.agent.status");
  const meta = PHASE_MAP[status.phase] ?? { icon: Loader2, i18nKey: "generic" };
  const Icon = meta.icon;
  const label = status.label || t(meta.i18nKey);
  const pct = status.progress != null ? Math.round(status.progress * 100) : null;

  return (
    <div className="mb-2 flex flex-col gap-1 rounded-md bg-accent/5 px-2 py-1.5">
      <div className="flex items-center gap-2 text-[11px] text-accent/90">
        <Chip size="sm" variant="soft" color="accent" className="shrink-0 gap-1">
          <Icon className="size-3" />
          <span>{label}</span>
        </Chip>
        {pct == null && <Spinner size="sm" color="current" className="ml-auto" />}
      </div>
      {pct != null && (
        <ProgressBar
          aria-label={label}
          value={pct}
          size="sm"
          color="accent"
        >
          <ProgressBar.Track>
            <ProgressBar.Fill />
          </ProgressBar.Track>
          <ProgressBar.Output />
        </ProgressBar>
      )}
    </div>
  );
}
