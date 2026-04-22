"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import { useTranslations } from "next-intl";
import {
  Card,
  Chip,
  Select,
  ListBox,
  Spinner,
  Label,
  Button,
  Modal,
  Input,
  TextField,
  TextArea,
  RadioGroup,
  Radio,
  NumberField,
  toast,
} from "@heroui/react";
import {
  GitBranch,
  ArrowRight,
  Type,
  Image,
  Video,
  AudioLines,
  MessageSquare,
  Play,
  Pause,
  Square,
  RotateCcw,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Eye,
  Save,
  type LucideIcon,
} from "lucide-react";
import { aiService } from "@/lib/api/services/ai.service";
import { batchJobService } from "@/lib/api/services/batch-job.service";
import { adminService } from "@/lib/api/services/admin.service";
import type {
  PipelineTemplate,
  PipelineStepType,
  BatchJobResponseDTO,
  BatchJobItemDTO,
  BatchJobStatus,
  ErrorStrategy,
  ScopeEntityType,
  CreateBatchJobRequestDTO,
  BatchJobSseEvent,
} from "@/lib/api/dto/batch-job.dto";
import type { AvailableProviderDTO } from "@/lib/api/dto/ai.dto";
import type { SystemConfigDTO } from "@/lib/api/dto/admin.dto";

// ============================================================================
// Constants
// ============================================================================

const STEP_TYPE_ICON: Record<PipelineStepType, LucideIcon> = {
  GENERATE_TEXT: Type,
  GENERATE_IMAGE: Image,
  GENERATE_VIDEO: Video,
  GENERATE_AUDIO: AudioLines,
  GENERATE_TTS: MessageSquare,
};

interface TemplateDefinition {
  code: PipelineTemplate;
  steps: PipelineStepType[];
}

const PIPELINE_TEMPLATES: TemplateDefinition[] = [
  { code: "TEXT_TO_PROMPT_TO_IMAGE", steps: ["GENERATE_TEXT", "GENERATE_IMAGE"] },
  { code: "TEXT_TO_PROMPT_TO_VIDEO", steps: ["GENERATE_TEXT", "GENERATE_VIDEO"] },
  { code: "TEXT_TO_PROMPT_TO_AUDIO", steps: ["GENERATE_TEXT", "GENERATE_AUDIO"] },
  { code: "TEXT_TO_IMAGE_TO_VIDEO", steps: ["GENERATE_IMAGE", "GENERATE_VIDEO"] },
  { code: "TEXT_TO_KEYFRAMES_TO_VIDEO", steps: ["GENERATE_IMAGE", "GENERATE_VIDEO"] },
  { code: "FULL_STORYBOARD", steps: ["GENERATE_TEXT", "GENERATE_IMAGE", "GENERATE_VIDEO"] },
];

const STEP_TO_PROVIDER_TYPE: Record<PipelineStepType, string> = {
  GENERATE_TEXT: "TEXT",
  GENERATE_IMAGE: "IMAGE",
  GENERATE_VIDEO: "VIDEO",
  GENERATE_AUDIO: "AUDIO",
  GENERATE_TTS: "TTS",
};

const JOB_STATUS_COLOR: Record<string, "default" | "accent" | "success" | "danger" | "warning"> = {
  CREATED: "default",
  RUNNING: "accent",
  PAUSED: "warning",
  COMPLETED: "success",
  FAILED: "danger",
  CANCELLED: "warning",
};

const HISTORY_PAGE_SIZE = 10;
const ITEMS_PAGE_SIZE = 10;
const CONFIG_KEY_PREFIX = "pipeline.template.";

// ============================================================================
// Helpers
// ============================================================================

function getConfigKey(templateCode: string, stepNumber: number): string {
  return `${CONFIG_KEY_PREFIX}${templateCode}.step.${stepNumber}.providerId`;
}

function getStepProviderFromConfigs(
  configs: SystemConfigDTO[],
  templateCode: string,
  stepNumber: number
): string | undefined {
  const key = getConfigKey(templateCode, stepNumber);
  const cfg = configs.find((c) => c.configKey === key);
  return cfg?.configValue || undefined;
}

async function saveStepProvider(
  configs: SystemConfigDTO[],
  templateCode: string,
  stepNumber: number,
  providerId: string
): Promise<SystemConfigDTO> {
  const key = getConfigKey(templateCode, stepNumber);
  const existing = configs.find((c) => c.configKey === key);

  if (existing) {
    return adminService.updateSystemConfig(existing.id, {
      configValue: providerId,
    });
  }

  return adminService.createSystemConfig({
    configKey: key,
    configValue: providerId,
    configType: "global",
    description: `Default provider for ${templateCode} step ${stepNumber}`,
    valueType: "STRING",
    enabled: true,
  });
}

// ============================================================================
// Page
// ============================================================================

export default function PipelineManagementPage() {
  const t = useTranslations("workspace.pipeline");

  const [providers, setProviders] = useState<Record<string, AvailableProviderDTO[]>>({});
  const [isLoadingProviders, setIsLoadingProviders] = useState(true);
  const [systemConfigs, setSystemConfigs] = useState<SystemConfigDTO[]>([]);
  const [isLoadingConfigs, setIsLoadingConfigs] = useState(true);

  // Launch modal
  const [launchTemplate, setLaunchTemplate] = useState<TemplateDefinition | null>(null);

  // Load providers
  useEffect(() => {
    const types = ["TEXT", "IMAGE", "VIDEO", "AUDIO", "TTS"] as const;
    setIsLoadingProviders(true);
    Promise.all(
      types.map(async (type) => {
        try {
          const data = await aiService.getProvidersByType(type as never);
          return [type, data] as const;
        } catch {
          return [type, []] as const;
        }
      })
    )
      .then((results) => {
        const map: Record<string, AvailableProviderDTO[]> = {};
        for (const [type, data] of results) map[type] = [...data];
        setProviders(map);
      })
      .finally(() => setIsLoadingProviders(false));
  }, []);

  // Load system configs for pipeline templates
  const loadConfigs = useCallback(async () => {
    setIsLoadingConfigs(true);
    try {
      const page = await adminService.getSystemConfigPage({
        configKey: CONFIG_KEY_PREFIX,
        size: 100,
      });
      setSystemConfigs(page.records);
    } catch {
      setSystemConfigs([]);
    } finally {
      setIsLoadingConfigs(false);
    }
  }, []);

  useEffect(() => {
    loadConfigs();
  }, [loadConfigs]);

  const handleProviderSaved = useCallback(
    (updated: SystemConfigDTO) => {
      setSystemConfigs((prev) => {
        const idx = prev.findIndex((c) => c.configKey === updated.configKey);
        if (idx >= 0) {
          const next = [...prev];
          next[idx] = updated;
          return next;
        }
        return [...prev, updated];
      });
    },
    []
  );

  return (
    <div className="flex h-full flex-col bg-background">
      {/* Header */}
      <div className="shrink-0 border-b border-border bg-surface px-6 py-4">
        <div className="flex items-center gap-3">
          <GitBranch className="size-5 text-accent" />
          <div>
            <h1 className="text-lg font-semibold text-foreground">{t("title")}</h1>
            <p className="text-sm text-foreground-2">{t("description")}</p>
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="min-h-0 flex-1 overflow-auto p-6">
        {/* Template cards grid */}
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2 xl:grid-cols-3">
          {PIPELINE_TEMPLATES.map((template) => (
            <TemplateCard
              key={template.code}
              template={template}
              providers={providers}
              isLoadingProviders={isLoadingProviders}
              systemConfigs={systemConfigs}
              isLoadingConfigs={isLoadingConfigs}
              onProviderSaved={handleProviderSaved}
              onLaunch={() => setLaunchTemplate(template)}
              t={t}
            />
          ))}
        </div>

        {/* Pipeline History */}
        <PipelineHistorySection providers={providers} />
      </div>

      {/* Launch Pipeline Modal */}
      {launchTemplate && (
        <LaunchPipelineModal
          template={launchTemplate}
          providers={providers}
          systemConfigs={systemConfigs}
          isOpen={!!launchTemplate}
          onClose={() => setLaunchTemplate(null)}
        />
      )}
    </div>
  );
}

// ============================================================================
// Template Card
// ============================================================================

function TemplateCard({
  template,
  providers,
  isLoadingProviders,
  systemConfigs,
  isLoadingConfigs,
  onProviderSaved,
  onLaunch,
  t,
}: {
  template: TemplateDefinition;
  providers: Record<string, AvailableProviderDTO[]>;
  isLoadingProviders: boolean;
  systemConfigs: SystemConfigDTO[];
  isLoadingConfigs: boolean;
  onProviderSaved: (updated: SystemConfigDTO) => void;
  onLaunch: () => void;
  t: ReturnType<typeof useTranslations>;
}) {
  const tStep = useTranslations("workspace.pipeline.step.type");

  return (
    <Card className="flex flex-col p-4">
      <div className="mb-3">
        <h3 className="text-sm font-semibold text-foreground">
          {t(`template.${template.code}.name` as never)}
        </h3>
        <p className="mt-0.5 text-xs text-foreground-2">
          {t(`template.${template.code}.description` as never)}
        </p>
        <code className="mt-1 block text-[10px] text-foreground-3">{template.code}</code>
      </div>

      {/* Step flow visualization */}
      <div className="mb-3 flex flex-wrap items-center gap-1">
        {template.steps.map((step, idx) => {
          const Icon = STEP_TYPE_ICON[step];
          return (
            <div key={idx} className="flex items-center gap-1">
              {idx > 0 && <ArrowRight className="size-3 text-foreground-3" />}
              <Chip size="sm" variant="soft" className="gap-1">
                <Icon className="size-3" />
                {tStep(step)}
              </Chip>
            </div>
          );
        })}
      </div>

      {/* Provider selectors per step */}
      <div className="mb-3 space-y-2">
        {template.steps.map((step, idx) => {
          const providerType = STEP_TO_PROVIDER_TYPE[step];
          const availableProviders = providers[providerType] ?? [];
          return (
            <StepProviderSelector
              key={idx}
              templateCode={template.code}
              stepNumber={idx + 1}
              stepType={step}
              providers={availableProviders}
              isLoading={isLoadingProviders || isLoadingConfigs}
              systemConfigs={systemConfigs}
              onProviderSaved={onProviderSaved}
              t={t}
            />
          );
        })}
      </div>

      {/* Launch button */}
      <div className="mt-auto pt-2">
        <Button
          variant="primary"
          size="sm"
          className="w-full"
          onPress={onLaunch}
        >
          <Play className="size-3.5" />
          {t("launchJob")}
        </Button>
      </div>
    </Card>
  );
}

// ============================================================================
// Step Provider Selector (with persistence)
// ============================================================================

function StepProviderSelector({
  templateCode,
  stepNumber,
  stepType,
  providers,
  isLoading,
  systemConfigs,
  onProviderSaved,
  t,
}: {
  templateCode: string;
  stepNumber: number;
  stepType: PipelineStepType;
  providers: AvailableProviderDTO[];
  isLoading: boolean;
  systemConfigs: SystemConfigDTO[];
  onProviderSaved: (updated: SystemConfigDTO) => void;
  t: ReturnType<typeof useTranslations>;
}) {
  const tStep = useTranslations("workspace.pipeline.step.type");
  const savedId = getStepProviderFromConfigs(systemConfigs, templateCode, stepNumber);
  const [selectedId, setSelectedId] = useState<string>("");
  const [isSaving, setIsSaving] = useState(false);

  // Sync from system config when loaded
  useEffect(() => {
    if (savedId) setSelectedId(savedId);
  }, [savedId]);

  const handleChange = useCallback(
    async (key: React.Key | null) => {
      const newId = key ? String(key) : "";
      setSelectedId(newId);
      if (!newId) return;

      setIsSaving(true);
      try {
        const updated = await saveStepProvider(systemConfigs, templateCode, stepNumber, newId);
        onProviderSaved(updated);
        toast.success(t("providerSaved"));
      } catch {
        toast.danger(t("providerSaveFailed"));
      } finally {
        setIsSaving(false);
      }
    },
    [systemConfigs, templateCode, stepNumber, onProviderSaved, t]
  );

  return (
    <div className="flex items-center gap-2">
      <span className="w-16 shrink-0 text-xs text-foreground-2">
        Step {stepNumber}: {tStep(stepType)}
      </span>
      <Select
        aria-label={`${t("step.provider")} - Step ${stepNumber}`}
        selectedKey={selectedId || null}
        onSelectionChange={handleChange}
        isDisabled={isLoading || isSaving}
        className="flex-1"
        placeholder={isLoading ? "..." : t("selectProvider")}
      >
        <Select.Trigger />
        <Select.Popover>
          <ListBox>
            {providers.map((p) => (
              <ListBox.Item key={p.id} id={p.id} textValue={p.name}>
                <Label>{p.name}</Label>
              </ListBox.Item>
            ))}
          </ListBox>
        </Select.Popover>
      </Select>
      {isSaving && <Spinner size="sm" />}
    </div>
  );
}

// ============================================================================
// Pipeline History Section
// ============================================================================

function PipelineHistorySection({
  providers,
}: {
  providers: Record<string, AvailableProviderDTO[]>;
}) {
  const t = useTranslations("workspace.pipeline");
  const tStatus = useTranslations("workspace.batchJobs.status");

  const [jobs, setJobs] = useState<BatchJobResponseDTO[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState<BatchJobStatus | "ALL">("ALL");
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [detailJob, setDetailJob] = useState<BatchJobResponseDTO | null>(null);

  // SSE refs for running jobs
  const sseAbortRefs = useRef<Map<string, { abort: () => void }>>(new Map());

  const loadJobs = useCallback(
    async (page: number, status: BatchJobStatus | "ALL") => {
      setIsLoading(true);
      try {
        const params: Record<string, unknown> = {
          batchType: "PIPELINE" as const,
          pageNum: page,
          pageSize: HISTORY_PAGE_SIZE,
        };
        if (status !== "ALL") params.status = status;

        const data = await batchJobService.list(params as never);
        setJobs(data.records ?? []);
        setTotalPages(data.pages || 1);
      } catch {
        setJobs([]);
      } finally {
        setIsLoading(false);
      }
    },
    []
  );

  useEffect(() => {
    loadJobs(currentPage, statusFilter);
  }, [loadJobs, currentPage, statusFilter]);

  // Subscribe to SSE for running jobs
  useEffect(() => {
    const runningJobs = jobs.filter((j) => j.status === "RUNNING");
    const currentIds = new Set(runningJobs.map((j) => j.id));

    // Cleanup SSE for jobs no longer running
    for (const [id, handle] of sseAbortRefs.current) {
      if (!currentIds.has(id)) {
        handle.abort();
        sseAbortRefs.current.delete(id);
      }
    }

    // Subscribe to new running jobs
    for (const job of runningJobs) {
      if (sseAbortRefs.current.has(job.id)) continue;

      const handle = batchJobService.streamProgress(job.id, {
        onEvent: (event: BatchJobSseEvent) => {
          setJobs((prev) =>
            prev.map((j) => {
              if (j.id !== event.batchJobId) return j;
              return {
                ...j,
                status: event.status ?? j.status,
                progress: event.progress ?? j.progress,
                completedItems: event.completedItems ?? j.completedItems,
                failedItems: event.failedItems ?? j.failedItems,
                actualCredits: event.actualCredits ?? j.actualCredits,
              };
            })
          );
        },
        onClose: () => {
          sseAbortRefs.current.delete(job.id);
        },
      });
      sseAbortRefs.current.set(job.id, handle);
    }

    return () => {
      for (const handle of sseAbortRefs.current.values()) handle.abort();
      sseAbortRefs.current.clear();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [jobs.map((j) => `${j.id}:${j.status}`).join(",")]);

  const handleAction = useCallback(
    async (jobId: string, action: "pause" | "resume" | "cancel" | "retryFailed") => {
      const tToast = {
        pause: { ok: "workspace.batchJobs.toast.pauseSuccess", fail: "workspace.batchJobs.toast.pauseFailed" },
        resume: { ok: "workspace.batchJobs.toast.resumeSuccess", fail: "workspace.batchJobs.toast.resumeFailed" },
        cancel: { ok: "workspace.batchJobs.toast.cancelSuccess", fail: "workspace.batchJobs.toast.cancelFailed" },
        retryFailed: { ok: "workspace.batchJobs.toast.retrySuccess", fail: "workspace.batchJobs.toast.retryFailed" },
      };
      try {
        await batchJobService[action](jobId);
        toast.success(tToast[action].ok);
        loadJobs(currentPage, statusFilter);
      } catch {
        toast.danger(tToast[action].fail);
      }
    },
    [loadJobs, currentPage, statusFilter]
  );

  const filterStatuses: (BatchJobStatus | "ALL")[] = ["ALL", "RUNNING", "COMPLETED", "FAILED", "PAUSED", "CANCELLED"];

  return (
    <div className="mt-8">
      <h2 className="mb-4 text-sm font-semibold text-foreground">{t("history.title")}</h2>

      {/* Status filter chips */}
      <div className="mb-4 flex flex-wrap gap-2">
        {filterStatuses.map((s) => (
          <Chip
            key={s}
            size="sm"
            variant={statusFilter === s ? "primary" : "soft"}
            color={s === "ALL" ? "default" : JOB_STATUS_COLOR[s] ?? "default"}
            className="cursor-pointer"
            onClick={() => {
              setStatusFilter(s);
              setCurrentPage(1);
            }}
          >
            {s === "ALL" ? t("history.filterAll") : tStatus(s as never)}
          </Chip>
        ))}
      </div>

      {/* Job list */}
      {isLoading ? (
        <div className="flex items-center justify-center py-8">
          <Spinner size="sm" />
        </div>
      ) : jobs.length === 0 ? (
        <p className="py-8 text-center text-sm text-foreground-3">{t("history.empty")}</p>
      ) : (
        <div className="space-y-2">
          {jobs.map((job) => (
            <PipelineHistoryRow
              key={job.id}
              job={job}
              onAction={handleAction}
              onViewDetail={() => setDetailJob(job)}
            />
          ))}
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="mt-4 flex items-center justify-center gap-2">
          <Button
            size="sm"
            variant="ghost"
            isDisabled={currentPage <= 1}
            onPress={() => setCurrentPage((p) => Math.max(1, p - 1))}
          >
            <ChevronLeft className="size-4" />
          </Button>
          <span className="text-xs text-foreground-2">
            {currentPage} / {totalPages}
          </span>
          <Button
            size="sm"
            variant="ghost"
            isDisabled={currentPage >= totalPages}
            onPress={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
          >
            <ChevronRight className="size-4" />
          </Button>
        </div>
      )}

      {/* Detail modal */}
      {detailJob && (
        <PipelineJobDetailModal
          job={detailJob}
          isOpen={!!detailJob}
          onClose={() => setDetailJob(null)}
          onAction={handleAction}
          providers={providers}
        />
      )}
    </div>
  );
}

// ============================================================================
// Pipeline History Row
// ============================================================================

function PipelineHistoryRow({
  job,
  onAction,
  onViewDetail,
}: {
  job: BatchJobResponseDTO;
  onAction: (id: string, action: "pause" | "resume" | "cancel" | "retryFailed") => void;
  onViewDetail: () => void;
}) {
  const tStatus = useTranslations("workspace.batchJobs.status");
  const tAction = useTranslations("workspace.pipeline.action");

  const canPause = job.status === "RUNNING";
  const canResume = job.status === "PAUSED";
  const canCancel = job.status === "RUNNING" || job.status === "PAUSED";
  const canRetry = job.status === "FAILED" || (job.status === "COMPLETED" && job.failedItems > 0);

  return (
    <div className="flex items-center gap-3 rounded-lg border border-border bg-surface px-4 py-2.5">
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="truncate text-sm font-medium text-foreground">
            {job.title || job.pipelineTemplate || job.id.slice(0, 8)}
          </span>
          <Chip size="sm" variant="soft" color={JOB_STATUS_COLOR[job.status] ?? "default"}>
            {tStatus(job.status as never)}
          </Chip>
          {job.pipelineTemplate && (
            <Chip size="sm" variant="secondary">
              {job.pipelineTemplate}
            </Chip>
          )}
        </div>
        <div className="mt-1 flex items-center gap-3 text-xs text-foreground-3">
          <span>
            {job.completedItems}/{job.totalItems} items
            {job.failedItems > 0 && (
              <span className="ml-1 text-danger">({job.failedItems} failed)</span>
            )}
          </span>
          <span>{job.actualCredits > 0 ? `${job.actualCredits} credits` : ""}</span>
          <span>{new Date(job.createdAt).toLocaleString()}</span>
        </div>
      </div>

      {/* Progress bar */}
      <div className="w-20">
        <div className="h-1.5 w-full overflow-hidden rounded-full bg-surface-2">
          <div
            className="h-full rounded-full bg-accent transition-all"
            style={{ width: `${Math.min(job.progress, 100)}%` }}
          />
        </div>
        <span className="text-[10px] text-foreground-3">{job.progress}%</span>
      </div>

      {/* Actions */}
      <div className="flex items-center gap-1">
        {canPause && (
          <Button
            size="sm"
            variant="ghost"
            aria-label={tAction("pause")}
            onPress={() => onAction(job.id, "pause")}
          >
            <Pause className="size-3.5" />
          </Button>
        )}
        {canResume && (
          <Button
            size="sm"
            variant="ghost"
            aria-label={tAction("resume")}
            onPress={() => onAction(job.id, "resume")}
          >
            <Play className="size-3.5" />
          </Button>
        )}
        {canCancel && (
          <Button
            size="sm"
            variant="ghost"
            aria-label={tAction("cancel")}
            onPress={() => onAction(job.id, "cancel")}
          >
            <Square className="size-3.5" />
          </Button>
        )}
        {canRetry && (
          <Button
            size="sm"
            variant="ghost"
            aria-label={tAction("retryFailed")}
            onPress={() => onAction(job.id, "retryFailed")}
          >
            <RotateCcw className="size-3.5" />
          </Button>
        )}
        <Button
          size="sm"
          variant="ghost"
          aria-label="View detail"
          onPress={onViewDetail}
        >
          <Eye className="size-3.5" />
        </Button>
      </div>
    </div>
  );
}

// ============================================================================
// Launch Pipeline Modal
// ============================================================================

function LaunchPipelineModal({
  template,
  providers,
  systemConfigs,
  isOpen,
  onClose,
}: {
  template: TemplateDefinition;
  providers: Record<string, AvailableProviderDTO[]>;
  systemConfigs: SystemConfigDTO[];
  isOpen: boolean;
  onClose: () => void;
}) {
  const t = useTranslations("workspace.pipeline");
  const tStep = useTranslations("workspace.pipeline.step.type");

  const [jobName, setJobName] = useState("");
  const [scriptId, setScriptId] = useState("");
  const [inputMode, setInputMode] = useState<"scope" | "manual">("scope");
  const [scopeEntityType, setScopeEntityType] = useState<ScopeEntityType>("EPISODE");
  const [scopeEntityId, setScopeEntityId] = useState("");
  const [manualItems, setManualItems] = useState("");
  const [concurrency, setConcurrency] = useState(5);
  const [errorStrategy, setErrorStrategy] = useState<ErrorStrategy>("CONTINUE");
  const [stepProviderOverrides, setStepProviderOverrides] = useState<Record<string, string>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Pre-fill step providers from system config
  useEffect(() => {
    const overrides: Record<string, string> = {};
    template.steps.forEach((_, idx) => {
      const saved = getStepProviderFromConfigs(systemConfigs, template.code, idx + 1);
      if (saved) overrides[String(idx + 1)] = saved;
    });
    setStepProviderOverrides(overrides);
  }, [template, systemConfigs]);

  const handleSubmit = async () => {
    if (!scriptId.trim()) {
      toast.danger("Script ID is required");
      return;
    }

    setIsSubmitting(true);
    try {
      const baseRequest: CreateBatchJobRequestDTO = {
        batchType: "PIPELINE",
        pipelineTemplate: template.code,
        title: jobName.trim() || t(`template.${template.code}.name` as never),
        scriptId: scriptId.trim(),
        concurrency,
        errorStrategy,
        stepProviderOverrides:
          Object.keys(stepProviderOverrides).length > 0 ? stepProviderOverrides : undefined,
      };

      if (inputMode === "scope") {
        if (!scopeEntityId.trim()) {
          toast.danger("Scope Entity ID is required");
          setIsSubmitting(false);
          return;
        }
        await batchJobService.expand({
          ...baseRequest,
          scopeEntityType,
          scopeEntityId: scopeEntityId.trim(),
        });
      } else {
        let items;
        try {
          items = JSON.parse(manualItems);
          if (!Array.isArray(items)) throw new Error("Not an array");
        } catch {
          toast.danger("Invalid JSON array for entity list");
          setIsSubmitting(false);
          return;
        }
        await batchJobService.create({
          ...baseRequest,
          items,
        });
      }

      toast.success(t("launchModal.submitSuccess"));
      onClose();
    } catch (err) {
      toast.danger(
        t("launchModal.submitFailed") +
          (err instanceof Error ? `: ${err.message}` : "")
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  const scopeEntityTypes: ScopeEntityType[] = ["EPISODE", "SCRIPT", "CHARACTER", "SCENE", "PROP"];

  return (
    <Modal.Backdrop isOpen={isOpen} onOpenChange={(open) => !open && onClose()}>
      <Modal.Container size="lg">
        <Modal.Dialog>
          <Modal.CloseTrigger />
          <Modal.Header>
            <Modal.Heading>{t("launchModal.title")}</Modal.Heading>
          </Modal.Header>
          <Modal.Body className="space-y-4">
            {/* Template info */}
            <div className="rounded-lg bg-surface-2 p-3">
              <span className="text-sm font-medium">{t(`template.${template.code}.name` as never)}</span>
              <div className="mt-1 flex flex-wrap items-center gap-1">
                {template.steps.map((step, idx) => {
                  const Icon = STEP_TYPE_ICON[step];
                  return (
                    <div key={idx} className="flex items-center gap-1">
                      {idx > 0 && <ArrowRight className="size-3 text-foreground-3" />}
                      <Chip size="sm" variant="soft" className="gap-1">
                        <Icon className="size-3" />
                        {tStep(step)}
                      </Chip>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Job name */}
            <TextField
              value={jobName}
              onChange={setJobName}
            >
              <Label>{t("launchModal.jobName")}</Label>
              <Input placeholder={t("launchModal.jobNamePlaceholder")} />
            </TextField>

            {/* Script ID */}
            <TextField
              value={scriptId}
              onChange={setScriptId}
              isRequired
            >
              <Label>{t("launchModal.scriptId")}</Label>
              <Input placeholder={t("launchModal.scriptIdPlaceholder")} />
            </TextField>

            {/* Input mode */}
            <div>
              <Label className="mb-1 block text-sm">{t("launchModal.inputMode")}</Label>
              <RadioGroup
                aria-label={t("launchModal.inputMode")}
                value={inputMode}
                onChange={(v) => setInputMode(v as "scope" | "manual")}
                orientation="horizontal"
                className="flex gap-4"
              >
                <Radio value="scope" className="flex items-center gap-2">
                  <Radio.Control><Radio.Indicator /></Radio.Control>
                  <Radio.Content><Label className="cursor-pointer">{t("launchModal.inputModeScope")}</Label></Radio.Content>
                </Radio>
                <Radio value="manual" className="flex items-center gap-2">
                  <Radio.Control><Radio.Indicator /></Radio.Control>
                  <Radio.Content><Label className="cursor-pointer">{t("launchModal.inputModeManual")}</Label></Radio.Content>
                </Radio>
              </RadioGroup>
            </div>

            {inputMode === "scope" ? (
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <Select
                    aria-label={t("launchModal.scopeEntityType")}
                    selectedKey={scopeEntityType}
                    onSelectionChange={(key) => key && setScopeEntityType(String(key) as ScopeEntityType)}
                  >
                    <Label>{t("launchModal.scopeEntityType")}</Label>
                    <Select.Trigger />
                    <Select.Popover>
                      <ListBox>
                        {scopeEntityTypes.map((et) => (
                          <ListBox.Item key={et} id={et} textValue={et}>
                            <Label>{et}</Label>
                          </ListBox.Item>
                        ))}
                      </ListBox>
                    </Select.Popover>
                  </Select>
                </div>
                <TextField
                  value={scopeEntityId}
                  onChange={setScopeEntityId}
                  isRequired
                >
                  <Label>{t("launchModal.scopeEntityId")}</Label>
                  <Input placeholder={t("launchModal.scopeEntityIdPlaceholder")} />
                </TextField>
              </div>
            ) : (
              <div>
                <Label className="mb-1 block text-sm">{t("launchModal.manualItems")}</Label>
                <TextArea
                  value={manualItems}
                  onChange={(e) => setManualItems(e.target.value)}
                  placeholder={t("launchModal.manualItemsPlaceholder")}
                  rows={4}
                  className="w-full font-mono text-xs"
                />
              </div>
            )}

            {/* Concurrency & Error Strategy */}
            <div className="grid grid-cols-2 gap-3">
              <NumberField
                value={concurrency}
                onChange={(v) => setConcurrency(v ?? 5)}
                minValue={1}
                maxValue={50}
              >
                <Label>{t("launchModal.concurrency")}</Label>
                <Input />
              </NumberField>
              <Select
                aria-label={t("launchModal.errorStrategy")}
                selectedKey={errorStrategy}
                onSelectionChange={(key) => key && setErrorStrategy(String(key) as ErrorStrategy)}
              >
                <Label>{t("launchModal.errorStrategy")}</Label>
                <Select.Trigger />
                <Select.Popover>
                  <ListBox>
                    <ListBox.Item id="CONTINUE" textValue="CONTINUE">
                      <Label>{t("launchModal.errorStrategyContinue")}</Label>
                    </ListBox.Item>
                    <ListBox.Item id="STOP" textValue="STOP">
                      <Label>{t("launchModal.errorStrategyStop")}</Label>
                    </ListBox.Item>
                    <ListBox.Item id="RETRY_THEN_CONTINUE" textValue="RETRY_THEN_CONTINUE">
                      <Label>{t("launchModal.errorStrategyRetry")}</Label>
                    </ListBox.Item>
                  </ListBox>
                </Select.Popover>
              </Select>
            </div>

            {/* Step provider overrides */}
            <div>
              <Label className="mb-2 block text-sm font-medium">
                {t("step.provider")}
              </Label>
              <div className="space-y-2">
                {template.steps.map((step, idx) => {
                  const providerType = STEP_TO_PROVIDER_TYPE[step];
                  const available = providers[providerType] ?? [];
                  const stepKey = String(idx + 1);
                  return (
                    <div key={idx} className="flex items-center gap-2">
                      <span className="w-32 shrink-0 text-xs text-foreground-2">
                        Step {idx + 1}: {tStep(step)}
                      </span>
                      <Select
                        aria-label={`Step ${idx + 1} provider`}
                        selectedKey={stepProviderOverrides[stepKey] || null}
                        onSelectionChange={(key) => {
                          setStepProviderOverrides((prev) => ({
                            ...prev,
                            [stepKey]: key ? String(key) : "",
                          }));
                        }}
                        className="flex-1"
                        placeholder={t("selectProvider")}
                      >
                        <Select.Trigger />
                        <Select.Popover>
                          <ListBox>
                            {available.map((p) => (
                              <ListBox.Item key={p.id} id={p.id} textValue={p.name}>
                                <Label>{p.name}</Label>
                              </ListBox.Item>
                            ))}
                          </ListBox>
                        </Select.Popover>
                      </Select>
                    </div>
                  );
                })}
              </div>
            </div>
          </Modal.Body>
          <Modal.Footer>
            <Button variant="ghost" onPress={onClose} isDisabled={isSubmitting}>
              {t("action.cancel")}
            </Button>
            <Button
              variant="primary"
              onPress={handleSubmit}
              isDisabled={isSubmitting}
            >
              {isSubmitting ? (
                <>
                  <Spinner size="sm" />
                  {t("launchModal.submitting")}
                </>
              ) : (
                t("launchModal.submit")
              )}
            </Button>
          </Modal.Footer>
        </Modal.Dialog>
      </Modal.Container>
    </Modal.Backdrop>
  );
}

// ============================================================================
// Pipeline Job Detail Modal
// ============================================================================

function PipelineJobDetailModal({
  job,
  isOpen,
  onClose,
  onAction,
  providers,
}: {
  job: BatchJobResponseDTO;
  isOpen: boolean;
  onClose: () => void;
  onAction: (id: string, action: "pause" | "resume" | "cancel" | "retryFailed") => void;
  providers: Record<string, AvailableProviderDTO[]>;
}) {
  const t = useTranslations("workspace.pipeline");
  const tStatus = useTranslations("workspace.batchJobs.status");
  const tItemStatus = useTranslations("workspace.batchJobs.itemStatus");
  const tDetail = useTranslations("workspace.batchJobs.detail");

  const [detail, setDetail] = useState<BatchJobResponseDTO | null>(null);
  const [items, setItems] = useState<BatchJobItemDTO[]>([]);
  const [isLoadingDetail, setIsLoadingDetail] = useState(true);
  const [isLoadingItems, setIsLoadingItems] = useState(true);
  const [itemsPage, setItemsPage] = useState(1);
  const [itemsTotalPages, setItemsTotalPages] = useState(1);
  const [isRetryingStep, setIsRetryingStep] = useState<number | null>(null);

  // Load detail
  useEffect(() => {
    if (!isOpen) return;
    setIsLoadingDetail(true);
    batchJobService
      .getDetail(job.id)
      .then(setDetail)
      .catch(() => setDetail(job))
      .finally(() => setIsLoadingDetail(false));
  }, [job, isOpen]);

  // Load items
  const loadItems = useCallback(
    async (page: number) => {
      setIsLoadingItems(true);
      try {
        const data = await batchJobService.getItems(job.id, {
          pageNum: page,
          pageSize: ITEMS_PAGE_SIZE,
        });
        setItems(data.records ?? []);
        setItemsTotalPages(data.pages || 1);
      } catch {
        setItems([]);
      } finally {
        setIsLoadingItems(false);
      }
    },
    [job.id]
  );

  useEffect(() => {
    if (isOpen) loadItems(itemsPage);
  }, [isOpen, loadItems, itemsPage]);

  const handleRetryStep = async (stepNumber: number) => {
    setIsRetryingStep(stepNumber);
    try {
      await batchJobService.retryStep(job.id, stepNumber);
      toast.success(t("history.retryStepSuccess", { step: stepNumber }));
      loadItems(itemsPage);
    } catch {
      toast.danger("Retry step failed");
    } finally {
      setIsRetryingStep(null);
    }
  };

  const d = detail || job;

  // Compute step progress from items
  const stepNumbers = [...new Set(items.filter((i) => i.pipelineStepNumber != null).map((i) => i.pipelineStepNumber!))].sort();

  return (
    <Modal.Backdrop isOpen={isOpen} onOpenChange={(open) => !open && onClose()}>
      <Modal.Container size="lg">
        <Modal.Dialog>
          <Modal.CloseTrigger />
          <Modal.Header>
            <Modal.Heading>
              {t("history.detail")}: {d.title || d.pipelineTemplate || d.id.slice(0, 8)}
            </Modal.Heading>
          </Modal.Header>
          <Modal.Body className="max-h-[70vh] space-y-4 overflow-auto">
            {isLoadingDetail ? (
              <div className="flex justify-center py-4">
                <Spinner size="sm" />
              </div>
            ) : (
              <>
                {/* Job info */}
                <div className="grid grid-cols-2 gap-3 rounded-lg bg-surface-2 p-3 text-sm md:grid-cols-4">
                  <div>
                    <span className="text-xs text-foreground-3">{tDetail("progress")}</span>
                    <div className="flex items-center gap-2">
                      <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-surface">
                        <div
                          className="h-full rounded-full bg-accent transition-all"
                          style={{ width: `${Math.min(d.progress, 100)}%` }}
                        />
                      </div>
                      <span className="text-xs font-medium">{d.progress}%</span>
                    </div>
                  </div>
                  <div>
                    <span className="text-xs text-foreground-3">Status</span>
                    <div>
                      <Chip size="sm" variant="soft" color={JOB_STATUS_COLOR[d.status] ?? "default"}>
                        {tStatus(d.status as never)}
                      </Chip>
                    </div>
                  </div>
                  <div>
                    <span className="text-xs text-foreground-3">{tDetail("totalItems")}</span>
                    <div className="font-medium">
                      {d.completedItems}/{d.totalItems}
                      {d.failedItems > 0 && (
                        <span className="ml-1 text-danger">({d.failedItems} failed)</span>
                      )}
                    </div>
                  </div>
                  <div>
                    <span className="text-xs text-foreground-3">{tDetail("actualCredits")}</span>
                    <div className="font-medium">{d.actualCredits}</div>
                  </div>
                </div>

                {/* Timeline */}
                <div className="grid grid-cols-3 gap-3 text-xs">
                  <div>
                    <span className="text-foreground-3">{t("history.createdAt")}</span>
                    <div>{new Date(d.createdAt).toLocaleString()}</div>
                  </div>
                  <div>
                    <span className="text-foreground-3">{t("history.startedAt")}</span>
                    <div>{d.startedAt ? new Date(d.startedAt).toLocaleString() : "-"}</div>
                  </div>
                  <div>
                    <span className="text-foreground-3">{t("history.completedAt")}</span>
                    <div>{d.completedAt ? new Date(d.completedAt).toLocaleString() : "-"}</div>
                  </div>
                </div>

                {/* Job actions */}
                <div className="flex gap-2">
                  {d.status === "RUNNING" && (
                    <Button size="sm" variant="secondary" onPress={() => onAction(d.id, "pause")}>
                      <Pause className="size-3.5" />
                      {t("action.pause")}
                    </Button>
                  )}
                  {d.status === "PAUSED" && (
                    <Button size="sm" variant="secondary" onPress={() => onAction(d.id, "resume")}>
                      <Play className="size-3.5" />
                      {t("action.resume")}
                    </Button>
                  )}
                  {(d.status === "RUNNING" || d.status === "PAUSED") && (
                    <Button size="sm" variant="danger-soft" onPress={() => onAction(d.id, "cancel")}>
                      <Square className="size-3.5" />
                      {t("action.cancel")}
                    </Button>
                  )}
                  {(d.status === "FAILED" || (d.status === "COMPLETED" && d.failedItems > 0)) && (
                    <Button size="sm" variant="secondary" onPress={() => onAction(d.id, "retryFailed")}>
                      <RotateCcw className="size-3.5" />
                      {t("action.retryFailed")}
                    </Button>
                  )}
                </div>

                {/* Step progress + retry */}
                {stepNumbers.length > 0 && (
                  <div>
                    <h4 className="mb-2 text-sm font-semibold">{t("history.steps")}</h4>
                    <div className="space-y-1">
                      {stepNumbers.map((stepNum) => {
                        const stepItems = items.filter((i) => i.pipelineStepNumber === stepNum);
                        const completed = stepItems.filter((i) => i.status === "COMPLETED").length;
                        const failed = stepItems.filter((i) => i.status === "FAILED").length;
                        const total = stepItems.length;
                        const pct = total > 0 ? Math.round((completed / total) * 100) : 0;
                        return (
                          <div key={stepNum} className="flex items-center gap-3 rounded bg-surface-2 px-3 py-2 text-xs">
                            <span className="w-16 font-medium">Step {stepNum}</span>
                            <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-surface">
                              <div
                                className="h-full rounded-full bg-accent transition-all"
                                style={{ width: `${pct}%` }}
                              />
                            </div>
                            <span className="w-24 text-right">
                              {completed}/{total}
                              {failed > 0 && <span className="ml-1 text-danger">({failed} fail)</span>}
                            </span>
                            {failed > 0 && (
                              <Button
                                size="sm"
                                variant="ghost"
                                isDisabled={isRetryingStep === stepNum}
                                onPress={() => handleRetryStep(stepNum)}
                              >
                                {isRetryingStep === stepNum ? (
                                  <Spinner size="sm" />
                                ) : (
                                  <RotateCcw className="size-3" />
                                )}
                              </Button>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}

                {/* Items list */}
                <div>
                  <h4 className="mb-2 text-sm font-semibold">{t("history.items")}</h4>
                  {isLoadingItems ? (
                    <div className="flex justify-center py-4">
                      <Spinner size="sm" />
                    </div>
                  ) : items.length === 0 ? (
                    <p className="py-4 text-center text-xs text-foreground-3">{t("history.noItems")}</p>
                  ) : (
                    <>
                      <div className="space-y-1">
                        {items.map((item) => (
                          <div
                            key={item.id}
                            className="flex items-center gap-2 rounded border border-border px-3 py-1.5 text-xs"
                          >
                            <span className="w-8 shrink-0 text-foreground-3">#{item.sequenceNumber}</span>
                            <span className="min-w-0 flex-1 truncate">
                              {item.entityName || item.entityId || "-"}
                            </span>
                            {item.pipelineStepNumber != null && (
                              <span className="shrink-0 text-foreground-3">
                                S{item.pipelineStepNumber}
                              </span>
                            )}
                            <Chip
                              size="sm"
                              variant="soft"
                              color={
                                item.status === "COMPLETED"
                                  ? "success"
                                  : item.status === "FAILED"
                                    ? "danger"
                                    : item.status === "RUNNING"
                                      ? "accent"
                                      : "default"
                              }
                            >
                              {tItemStatus(item.status as never)}
                            </Chip>
                            {item.providerName && (
                              <span className="shrink-0 text-foreground-3">{item.providerName}</span>
                            )}
                            {item.creditCost > 0 && (
                              <span className="shrink-0 text-foreground-3">{item.creditCost}cr</span>
                            )}
                          </div>
                        ))}
                      </div>
                      {itemsTotalPages > 1 && (
                        <div className="mt-3 flex items-center justify-center gap-2">
                          <Button
                            size="sm"
                            variant="ghost"
                            isDisabled={itemsPage <= 1}
                            onPress={() => setItemsPage((p) => Math.max(1, p - 1))}
                          >
                            <ChevronLeft className="size-4" />
                          </Button>
                          <span className="text-xs text-foreground-2">
                            {itemsPage} / {itemsTotalPages}
                          </span>
                          <Button
                            size="sm"
                            variant="ghost"
                            isDisabled={itemsPage >= itemsTotalPages}
                            onPress={() => setItemsPage((p) => Math.min(itemsTotalPages, p + 1))}
                          >
                            <ChevronRight className="size-4" />
                          </Button>
                        </div>
                      )}
                    </>
                  )}
                </div>
              </>
            )}
          </Modal.Body>
          <Modal.Footer>
            <Button variant="ghost" onPress={onClose}>
              {t("action.close")}
            </Button>
          </Modal.Footer>
        </Modal.Dialog>
      </Modal.Container>
    </Modal.Backdrop>
  );
}
