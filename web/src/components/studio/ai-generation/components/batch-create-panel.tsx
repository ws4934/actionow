"use client";

import { useState, useEffect, useCallback, useMemo } from "react";
import { useTranslations } from "next-intl";
import {
  Button,
  Select,
  ListBox,
  Chip,
  Switch,
  NumberField,
  TextField,
  Disclosure,
  toast,
  Label,
  Description,
  CheckboxGroup,
  Checkbox,
  RadioGroup,
  Radio,
  Input,
  Separator,
  ScrollShadow,
  Surface,
  Spinner,
  CloseButton,
  Tooltip,
  Alert,
} from "@heroui/react";
import {
  ChevronRight,
  Zap,
  Settings2,
  Layers,
  Play,
  Target,
  GitBranch,
  FlaskConical,
  LayoutList,
  Bot,
  Image as ImageIcon,
  Video,
  Music,
} from "lucide-react";
import { batchJobService } from "@/lib/api/services/batch-job.service";
import { aiService } from "@/lib/api/services/ai.service";
import { projectService } from "@/lib/api/services/project.service";
import type {
  BatchType,
  ErrorStrategy,
  SkipCondition,
  ScopeEntityType,
  PipelineTemplate,
  PipelineStepType,
  CreateBatchJobRequestDTO,
  BatchJobItemRequestDTO,
} from "@/lib/api/dto/batch-job.dto";
import type { ProviderType, AvailableProviderDTO } from "@/lib/api/dto/ai.dto";
import type { EntityType, EpisodeListDTO } from "@/lib/api/dto/project.dto";
import { type FileValue } from "./form-fields";

// ============================================================================
// Constants
// ============================================================================

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

const STEP_TYPE_TO_PROVIDER_TYPE: Record<PipelineStepType, ProviderType> = {
  GENERATE_TEXT: "TEXT",
  GENERATE_IMAGE: "IMAGE",
  GENERATE_VIDEO: "VIDEO",
  GENERATE_AUDIO: "AUDIO",
  GENERATE_TTS: "TTS" as ProviderType,
};

const BATCH_TYPE_CONFIG: { type: BatchType; icon: typeof LayoutList }[] = [
  { type: "SIMPLE", icon: LayoutList },
  { type: "SCOPE", icon: Target },
  { type: "PIPELINE", icon: GitBranch },
  { type: "AB_TEST", icon: FlaskConical },
];

/** Type options visible in the UI (TEXT excluded — internal only) */
const VISIBLE_TYPE_OPTIONS: { type: ProviderType; icon: typeof ImageIcon }[] = [
  { type: "IMAGE", icon: ImageIcon },
  { type: "VIDEO", icon: Video },
  { type: "AUDIO", icon: Music },
];

const SCOPE_ENTITY_TYPES: ScopeEntityType[] = ["EPISODE", "SCRIPT", "CHARACTER", "SCENE", "PROP"];

const ERROR_STRATEGIES: ErrorStrategy[] = ["CONTINUE", "STOP", "RETRY_THEN_CONTINUE"];

// ============================================================================
// Props
// ============================================================================

interface BatchCreatePanelProps {
  scriptId: string;
  entityType: EntityType | null;
  entityId: string | null;
  entityName: string;
  generationType: ProviderType;
  selectedProviderId: string | null;
  providers: AvailableProviderDTO[];
  isLoadingProviders: boolean;
  prompt: string;
  formValues: Record<string, unknown>;
  onGenerationTypeChange: (type: ProviderType) => void;
  onProviderIdChange: (id: string) => void;
  onBatchCreated: (jobId: string) => void;
  onClose: () => void;
}

// ============================================================================
// Component
// ============================================================================

export function BatchCreatePanel({
  scriptId,
  entityType,
  entityId,
  entityName,
  generationType,
  selectedProviderId,
  providers,
  isLoadingProviders,
  prompt,
  formValues,
  onGenerationTypeChange,
  onProviderIdChange,
  onBatchCreated,
  onClose,
}: BatchCreatePanelProps) {
  const t = useTranslations("workspace.aiGeneration.batch");
  const tGen = useTranslations("workspace.aiGeneration");
  const tBatch = useTranslations("workspace.batchJobs");
  const tPipeline = useTranslations("workspace.pipeline");

  // ── Internal state ──────────────────────────────────────────────────
  const [batchType, setBatchType] = useState<BatchType>("SIMPLE");
  const [scopeEntityType, setScopeEntityType] = useState<ScopeEntityType>("EPISODE");
  const [scopeEntityId, setScopeEntityId] = useState<string>("");
  const [pipelineTemplate, setPipelineTemplate] = useState<PipelineTemplate | null>(null);
  const [stepProviderOverrides, setStepProviderOverrides] = useState<Record<string, string>>({});
  const [abTestProviderIds, setAbTestProviderIds] = useState<string[]>([]);
  const [concurrency, setConcurrency] = useState(5);
  const [errorStrategy, setErrorStrategy] = useState<ErrorStrategy>("CONTINUE");
  const [skipCondition, setSkipCondition] = useState<SkipCondition>("NONE");
  const [title, setTitle] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Lazy-loaded data
  const [episodes, setEpisodes] = useState<EpisodeListDTO[]>([]);
  const [isLoadingEpisodes, setIsLoadingEpisodes] = useState(false);
  const [stepProviders, setStepProviders] = useState<Record<string, AvailableProviderDTO[]>>({});

  // ── Load episodes for SCOPE+EPISODE ─────────────────────────────────
  useEffect(() => {
    if (batchType !== "SCOPE" || scopeEntityType !== "EPISODE") return;
    if (episodes.length > 0) return;

    let cancelled = false;
    setIsLoadingEpisodes(true);
    projectService
      .getEpisodesByScript(scriptId)
      .then((data) => {
        if (!cancelled) setEpisodes(data);
      })
      .catch(() => {})
      .finally(() => {
        if (!cancelled) setIsLoadingEpisodes(false);
      });
    return () => { cancelled = true; };
  }, [batchType, scopeEntityType, scriptId, episodes.length]);

  // ── Load providers for pipeline steps ───────────────────────────────
  useEffect(() => {
    if (batchType !== "PIPELINE" || !pipelineTemplate) return;

    const tpl = PIPELINE_TEMPLATES.find((tmpl) => tmpl.code === pipelineTemplate);
    if (!tpl) return;

    const neededTypes = new Set(tpl.steps.map((s) => STEP_TYPE_TO_PROVIDER_TYPE[s]));
    const alreadyLoaded = new Set(Object.keys(stepProviders));
    const toLoad = [...neededTypes].filter((pt) => !alreadyLoaded.has(pt));

    if (toLoad.length === 0) return;

    toLoad.forEach((providerType) => {
      aiService
        .getProvidersByType(providerType)
        .then((list) => {
          setStepProviders((prev) => ({ ...prev, [providerType]: list }));
        })
        .catch(() => {});
    });
  }, [batchType, pipelineTemplate, stepProviders]);

  // ── Transform form values (FileValue→assetId) ──────────────────────
  const transformFormValues = useCallback((): Record<string, unknown> => {
    const transformed: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(formValues)) {
      if (value && typeof value === "object") {
        if ("assetId" in value && typeof (value as FileValue).assetId === "string") {
          transformed[key] = (value as FileValue).assetId;
        } else if (Array.isArray(value)) {
          if (value.length > 0 && value[0] && typeof value[0] === "object" && "assetId" in value[0]) {
            transformed[key] = value.map((v: FileValue) => v.assetId);
          } else {
            transformed[key] = value;
          }
        } else {
          transformed[key] = value;
        }
      } else {
        transformed[key] = value;
      }
    }
    return transformed;
  }, [formValues]);

  // ── Build single item ───────────────────────────────────────────────
  const buildItem = useCallback((): BatchJobItemRequestDTO => {
    const transformedValues = transformFormValues();
    return {
      entityType: entityType ?? undefined,
      entityId: entityId ?? undefined,
      entityName: entityName || undefined,
      params: { prompt, ...transformedValues },
      providerId: selectedProviderId ?? undefined,
      generationType,
    };
  }, [entityType, entityId, entityName, prompt, transformFormValues, selectedProviderId, generationType]);

  // ── Validation ──────────────────────────────────────────────────────
  const validate = useCallback((): string | null => {
    switch (batchType) {
      case "SIMPLE":
        if (!entityId) return t("noEntityHint");
        if (!selectedProviderId) return tBatch("detail.provider");
        return null;
      case "SCOPE":
        if (scopeEntityType === "EPISODE" && !scopeEntityId) return t("scope.selectEpisode");
        if (!selectedProviderId) return tBatch("detail.provider");
        return null;
      case "PIPELINE":
        if (!pipelineTemplate) return t("pipeline.selectTemplate");
        return null;
      case "AB_TEST":
        if (!entityId) return t("noEntityHint");
        if (abTestProviderIds.length < 2) return t("abTest.minProviders");
        return null;
      default:
        return null;
    }
  }, [batchType, entityId, selectedProviderId, scopeEntityType, scopeEntityId, pipelineTemplate, abTestProviderIds, t, tBatch]);

  // ── Submit ──────────────────────────────────────────────────────────
  const handleSubmit = useCallback(async () => {
    const error = validate();
    if (error) {
      toast.danger(error);
      return;
    }

    setIsSubmitting(true);

    try {
      const base: CreateBatchJobRequestDTO = {
        batchType,
        title: title || undefined,
        scriptId,
        generationType,
        providerId: selectedProviderId ?? undefined,
        concurrency,
        errorStrategy,
        skipCondition,
      };

      let response;

      switch (batchType) {
        case "SCOPE":
          response = await batchJobService.expand({
            ...base,
            scopeEntityType,
            scopeEntityId: scopeEntityType === "SCRIPT" ? scriptId : scopeEntityId,
          });
          break;
        case "PIPELINE": {
          const overrides = Object.keys(stepProviderOverrides).length > 0
            ? stepProviderOverrides
            : undefined;
          response = await batchJobService.create({
            ...base,
            pipelineTemplate: pipelineTemplate!,
            stepProviderOverrides: overrides,
            items: entityId ? [buildItem()] : undefined,
          });
          break;
        }
        case "AB_TEST":
          response = await batchJobService.abTest({
            ...base,
            abTestProviderIds,
            items: entityId ? [buildItem()] : undefined,
          });
          break;
        case "SIMPLE":
        default:
          response = await batchJobService.create({
            ...base,
            items: entityId ? [buildItem()] : undefined,
          });
          break;
      }

      toast.success(t("toast.createSuccess"));
      onBatchCreated(response.id);
    } catch {
      toast.danger(t("toast.createFailed"));
    } finally {
      setIsSubmitting(false);
    }
  }, [
    validate, batchType, title, scriptId, generationType, selectedProviderId,
    concurrency, errorStrategy, skipCondition, scopeEntityType, scopeEntityId,
    pipelineTemplate, stepProviderOverrides, abTestProviderIds, entityId,
    buildItem, onBatchCreated, t,
  ]);

  // ── Derived ─────────────────────────────────────────────────────────
  const selectedProvider = providers.find((p) => p.id === selectedProviderId);
  const estimatedCredits = selectedProvider?.creditCost ?? 0;

  const selectedTemplate = useMemo(
    () => pipelineTemplate
      ? PIPELINE_TEMPLATES.find((tmpl) => tmpl.code === pipelineTemplate) ?? null
      : null,
    [pipelineTemplate],
  );

  // Current type icon for the type selector display
  const currentTypeOption = VISIBLE_TYPE_OPTIONS.find((o) => o.type === generationType);
  const CurrentTypeIcon = currentTypeOption?.icon ?? ImageIcon;

  // Scope info text for non-EPISODE types
  const scopeInfoText = useMemo(() => {
    const map: Record<string, string> = {
      SCRIPT: t("scope.currentScript"),
      CHARACTER: t("scope.allCharacters"),
      SCENE: t("scope.allScenes"),
      PROP: t("scope.allProps"),
    };
    return map[scopeEntityType] ?? "";
  }, [scopeEntityType, t]);

  // ── Render ──────────────────────────────────────────────────────────
  return (
    <div className="flex flex-col border-t border-border/40">
      {/* ── Header ─────────────────────────────────────────────────── */}
      <div className="flex items-center justify-between px-2.5 py-1.5">
        <div className="flex items-center gap-1.5">
          <Layers className="size-3.5 text-accent" />
          <span className="text-xs font-semibold">{t("title")}</span>
        </div>
        <CloseButton aria-label={t("close")} onPress={onClose} />
      </div>

      {/* ── Type & Model Selector (SIMPLE / SCOPE only) ─────────── */}
      {(batchType === "SIMPLE" || batchType === "SCOPE") && (
      <div className="flex items-center gap-1 border-t border-border/30 px-2.5 py-1">
        {/* Type */}
        <Select
          aria-label={tGen("provider.typeLabel")}
          className="w-auto shrink-0"
          variant="secondary"
          value={generationType}
          onChange={(value) => value && onGenerationTypeChange(value as ProviderType)}
        >
          <Select.Trigger className="h-6 gap-1 border-0 bg-transparent px-1.5 hover:bg-muted/20">
            <Select.Value>
              {() => (
                <span className="flex items-center gap-1 text-xs">
                  <CurrentTypeIcon className="size-3 shrink-0 text-accent" />
                  <span>{tGen(`type.${generationType.toLowerCase()}`)}</span>
                </span>
              )}
            </Select.Value>
          </Select.Trigger>
          <Select.Popover>
            <ListBox>
              {VISIBLE_TYPE_OPTIONS.map(({ type: pt, icon: Icon }) => (
                <ListBox.Item key={pt} id={pt} textValue={tGen(`type.${pt.toLowerCase()}`)} className="data-[selected=true]:bg-accent/10">
                  <Icon className="size-3.5 text-muted" />
                  <Label>{tGen(`type.${pt.toLowerCase()}`)}</Label>
                </ListBox.Item>
              ))}
            </ListBox>
          </Select.Popover>
        </Select>

        <div className="h-3.5 w-px bg-border/40" />

        {/* Model */}
        <Select
          aria-label={tGen("provider.modelLabel")}
          className="min-w-0 flex-1"
          variant="secondary"
          value={selectedProviderId}
          onChange={(value) => value && onProviderIdChange(value as string)}
          isDisabled={isLoadingProviders}
        >
          <Select.Trigger className="h-6 gap-1 border-0 bg-transparent px-1.5 hover:bg-muted/20">
            <Select.Value>
              {({ isPlaceholder }) => (
                <span className="flex min-w-0 items-center gap-1 text-xs">
                  {isLoadingProviders ? (
                    <Spinner size="sm" />
                  ) : selectedProvider?.iconUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={selectedProvider.iconUrl} alt="" className="size-3 shrink-0 rounded" />
                  ) : (
                    <Bot className="size-3 shrink-0" />
                  )}
                  <span className="truncate">
                    {isLoadingProviders
                      ? tGen("provider.loading")
                      : isPlaceholder
                        ? tGen("provider.selectModel")
                        : selectedProvider?.name}
                  </span>
                </span>
              )}
            </Select.Value>
          </Select.Trigger>
          <Select.Popover>
            {providers.length === 0 ? (
              <div className="px-3 py-3 text-center text-xs text-muted">{tGen("provider.noModels")}</div>
            ) : (
              <ListBox>
                {providers.map((p) => (
                  <ListBox.Item key={p.id} id={p.id} textValue={p.name} className="data-[selected=true]:bg-accent/10">
                    {p.iconUrl ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={p.iconUrl} alt="" className="size-3.5 rounded" />
                    ) : (
                      <Bot className="size-3.5 text-muted" />
                    )}
                    <div className="flex flex-col">
                      <Label>{p.name}</Label>
                      {p.creditCost > 0 && <Description>{p.creditCost} credits</Description>}
                    </div>
                  </ListBox.Item>
                ))}
              </ListBox>
            )}
          </Select.Popover>
        </Select>

        {/* Credits badge */}
        {estimatedCredits > 0 && (
          <div className="flex shrink-0 items-center gap-0.5 rounded-full bg-warning/10 px-1.5 py-0.5 text-[10px] font-medium text-warning">
            <Zap className="size-2.5" />
            <span>{estimatedCredits}</span>
          </div>
        )}
      </div>
      )}

      <Separator />

      {/* ── Scrollable Body ────────────────────────────────────────── */}
      <ScrollShadow className="max-h-[400px] min-h-0 flex-1" hideScrollBar>
        <div className="flex flex-col gap-2 p-2.5">

          {/* ── Batch Type Selector ──────────────────────────────── */}
          <div className="flex flex-col gap-1">
            <Label className="text-[11px] font-medium text-foreground-2">
              {t("typeSelector.label")}
            </Label>
            <div className="grid grid-cols-4 gap-1">
              {BATCH_TYPE_CONFIG.map(({ type: bt, icon: Icon }) => (
                <Tooltip key={bt} delay={300}>
                  <Button
                    variant={batchType === bt ? "primary" : "secondary"}
                    size="sm"
                    className="h-7 gap-1 text-[11px]"
                    onPress={() => setBatchType(bt)}
                  >
                    <Icon className="size-3" />
                    {tBatch(`batchType.${bt}`)}
                  </Button>
                  <Tooltip.Content className="max-w-48 text-xs">
                    {tBatch(`batchType.${bt}`)}
                  </Tooltip.Content>
                </Tooltip>
              ))}
            </div>
          </div>

          <Separator />

          {/* ── Type Config Area ──────────────────────────────────── */}

          {/* ─── SIMPLE ─────────────────────────────────────────── */}
          {batchType === "SIMPLE" && (
            entityId ? (
              <Alert status="accent">
                <Alert.Indicator />
                <Alert.Content>
                  <Alert.Title className="text-xs">
                    {t("singleEntity", { name: entityName })}
                  </Alert.Title>
                </Alert.Content>
              </Alert>
            ) : (
              <Alert status="warning">
                <Alert.Indicator />
                <Alert.Content>
                  <Alert.Description className="text-[11px]">
                    {t("noEntityHint")}
                  </Alert.Description>
                </Alert.Content>
              </Alert>
            )
          )}

          {/* ─── SCOPE ──────────────────────────────────────────── */}
          {batchType === "SCOPE" && (
            <Surface variant="secondary" className="flex flex-col gap-2 rounded-lg p-2.5">
              {/* Scope Entity Type — RadioGroup */}
              <RadioGroup
                aria-label={t("scope.entityType")}
                value={scopeEntityType}
                onChange={(value) => {
                  setScopeEntityType(value as ScopeEntityType);
                  setScopeEntityId("");
                }}
                orientation="horizontal"
                className="flex-wrap gap-1"
              >
                {SCOPE_ENTITY_TYPES.map((set) => (
                  <Radio
                    key={set}
                    value={set}
                    className="cursor-pointer rounded-md border border-transparent px-2 py-1 text-[11px] transition-colors data-[selected=true]:border-accent data-[selected=true]:bg-accent/10"
                  >
                    <Radio.Control className="sr-only">
                      <Radio.Indicator />
                    </Radio.Control>
                    <Radio.Content>
                      <Label className="cursor-pointer text-[11px]">{t(`scope.entityTypes.${set}`)}</Label>
                    </Radio.Content>
                  </Radio>
                ))}
              </RadioGroup>

              <Separator variant="tertiary" />

              {/* Episode selector */}
              {scopeEntityType === "EPISODE" && (
                <Select
                  aria-label={t("scope.selectEpisode")}
                  value={scopeEntityId}
                  onChange={(value) => value && setScopeEntityId(value as string)}
                  isDisabled={isLoadingEpisodes}
                  placeholder={t("scope.selectEpisode")}
                >
                  <Label className="text-[11px]">{t("scope.selectEpisode")}</Label>
                  <Select.Trigger className="h-7">
                    <Select.Value>
                      {({ isPlaceholder }) => (
                        <span className="text-xs">
                          {isLoadingEpisodes
                            ? <Spinner size="sm" />
                            : isPlaceholder
                              ? t("scope.selectEpisode")
                              : episodes.find((e) => e.id === scopeEntityId)?.title ?? scopeEntityId
                          }
                        </span>
                      )}
                    </Select.Value>
                  </Select.Trigger>
                  <Select.Popover>
                    <ListBox>
                      {episodes.map((ep) => (
                        <ListBox.Item key={ep.id} id={ep.id} textValue={ep.title}>
                          <Label>{ep.title}</Label>
                          <Description>#{ep.sequence}</Description>
                        </ListBox.Item>
                      ))}
                    </ListBox>
                  </Select.Popover>
                </Select>
              )}

              {/* Non-EPISODE scope: read-only info */}
              {scopeEntityType !== "EPISODE" && (
                <Chip size="sm" variant="soft" className="self-start text-[11px]">
                  {scopeInfoText}
                </Chip>
              )}

              <Separator variant="tertiary" />

              {/* Skip existing toggle */}
              <Switch
                isSelected={skipCondition === "ASSET_EXISTS"}
                onChange={(selected) =>
                  setSkipCondition(selected ? "ASSET_EXISTS" : "NONE")
                }
                size="sm"
              >
                <Switch.Control>
                  <Switch.Thumb />
                </Switch.Control>
                <Switch.Content>
                  <Label className="text-[11px]">{t("scope.skipExisting")}</Label>
                </Switch.Content>
              </Switch>
            </Surface>
          )}

          {/* ─── PIPELINE ───────────────────────────────────────── */}
          {batchType === "PIPELINE" && (
            <Surface variant="secondary" className="flex flex-col gap-2 rounded-lg p-2.5">
              {/* Template selector */}
              <Select
                aria-label={t("pipeline.label")}
                value={pipelineTemplate ?? undefined}
                onChange={(value) => {
                  if (value) {
                    setPipelineTemplate(value as PipelineTemplate);
                    setStepProviderOverrides({});
                  }
                }}
                placeholder={t("pipeline.selectTemplate")}
              >
                <Label className="text-[11px]">{t("pipeline.label")}</Label>
                <Select.Trigger className="h-7">
                  <Select.Value>
                    {({ isPlaceholder }) => (
                      <span className="text-xs">
                        {isPlaceholder || !pipelineTemplate
                          ? t("pipeline.selectTemplate")
                          : tPipeline(`template.${pipelineTemplate}.name`)
                        }
                      </span>
                    )}
                  </Select.Value>
                </Select.Trigger>
                <Select.Popover>
                  <ListBox>
                    {PIPELINE_TEMPLATES.map((tpl) => (
                      <ListBox.Item key={tpl.code} id={tpl.code} textValue={tPipeline(`template.${tpl.code}.name`)}>
                        <div className="flex flex-col">
                          <Label>{tPipeline(`template.${tpl.code}.name`)}</Label>
                          <Description className="text-[11px]">
                            {tPipeline(`template.${tpl.code}.description`)}
                          </Description>
                        </div>
                      </ListBox.Item>
                    ))}
                  </ListBox>
                </Select.Popover>
              </Select>

              {/* Step visualization + provider overrides */}
              {selectedTemplate && (
                <>
                  <Separator variant="tertiary" />

                  {/* Steps flow — arrow-connected chips */}
                  <div className="flex flex-wrap items-center gap-1">
                    {selectedTemplate.steps.map((step, idx) => (
                      <div key={idx} className="flex items-center gap-0.5">
                        {idx > 0 && <ChevronRight className="size-2.5 shrink-0 text-foreground-3" />}
                        <Chip size="sm" variant="soft" color="accent" className="text-[10px]">
                          {tPipeline(`step.type.${step}`)}
                        </Chip>
                      </div>
                    ))}
                  </div>

                  <Separator variant="tertiary" />

                  {/* Provider overrides per step */}
                  <div className="flex flex-col gap-1.5">
                    <Label className="text-[11px] font-medium text-foreground-2">
                      {t("pipeline.stepOverrides")}
                    </Label>
                    {selectedTemplate.steps.map((step, idx) => {
                      const providerType = STEP_TYPE_TO_PROVIDER_TYPE[step];
                      const available = stepProviders[providerType] ?? [];
                      const stepKey = String(idx);

                      return (
                        <div key={idx} className="flex items-center gap-1.5">
                          <Chip size="sm" variant="soft" className="shrink-0 text-[10px]">
                            {t("pipeline.step", { number: idx + 1 })}
                          </Chip>
                          <Select
                            aria-label={t("pipeline.step", { number: idx + 1 })}
                            value={stepProviderOverrides[stepKey] ?? ""}
                            onChange={(value) => {
                              setStepProviderOverrides((prev) => {
                                const next = { ...prev };
                                if (value) {
                                  next[stepKey] = value as string;
                                } else {
                                  delete next[stepKey];
                                }
                                return next;
                              });
                            }}
                            className="flex-1"
                          >
                            <Select.Trigger className="h-6 text-[11px]">
                              <Select.Value>
                                {({ isPlaceholder }) => (
                                  <span className="text-[11px]">
                                    {isPlaceholder || !stepProviderOverrides[stepKey]
                                      ? t("pipeline.useDefault")
                                      : available.find((p) => p.id === stepProviderOverrides[stepKey])?.name ?? t("pipeline.useDefault")
                                    }
                                  </span>
                                )}
                              </Select.Value>
                            </Select.Trigger>
                            <Select.Popover>
                              <ListBox>
                                <ListBox.Item key="default" id="" textValue={t("pipeline.useDefault")}>
                                  <Label className="text-muted">{t("pipeline.useDefault")}</Label>
                                </ListBox.Item>
                                {available.map((p) => (
                                  <ListBox.Item key={p.id} id={p.id} textValue={p.name}>
                                    <Label>{p.name}</Label>
                                    {p.creditCost > 0 && (
                                      <Description>{p.creditCost} credits</Description>
                                    )}
                                  </ListBox.Item>
                                ))}
                              </ListBox>
                            </Select.Popover>
                          </Select>
                        </div>
                      );
                    })}
                  </div>
                </>
              )}
            </Surface>
          )}

          {/* ─── AB_TEST ────────────────────────────────────────── */}
          {batchType === "AB_TEST" && (
            <Surface variant="secondary" className="flex flex-col gap-2 rounded-lg p-2.5">
              {!entityId && (
                <Alert status="warning">
                  <Alert.Indicator />
                  <Alert.Content>
                    <Alert.Description className="text-[11px]">
                      {t("noEntityHint")}
                    </Alert.Description>
                  </Alert.Content>
                </Alert>
              )}

              <CheckboxGroup
                aria-label={t("abTest.label")}
                value={abTestProviderIds}
                onChange={(values) => setAbTestProviderIds(values as string[])}
              >
                <Label className="text-[11px] font-medium">{t("abTest.label")}</Label>
                <Description className="text-[11px] text-foreground-3">
                  {t("abTest.selectProviders")}
                </Description>
                {providers.map((p) => (
                  <Checkbox
                    key={p.id}
                    value={p.id}
                    className="rounded-md px-1.5 py-1 transition-colors data-[selected=true]:bg-accent/5"
                  >
                    <Checkbox.Control>
                      <Checkbox.Indicator />
                    </Checkbox.Control>
                    <Checkbox.Content>
                      <Label className="cursor-pointer text-xs">{p.name}</Label>
                      {p.creditCost > 0 && (
                        <Description className="text-[11px]">
                          {p.creditCost} credits
                        </Description>
                      )}
                    </Checkbox.Content>
                  </Checkbox>
                ))}
              </CheckboxGroup>
            </Surface>
          )}

          <Separator />

          {/* ── Advanced Options ──────────────────────────────────── */}
          <Disclosure>
            <Disclosure.Trigger className="flex w-full items-center gap-1 rounded-md px-1 py-0.5 text-[11px] text-foreground-2 transition-colors hover:bg-muted/10 hover:text-foreground">
              <Settings2 className="size-3" />
              {t("advanced.label")}
            </Disclosure.Trigger>
            <Disclosure.Content>
              <div className="mt-1.5 flex flex-col gap-2">
                {/* Job Title — full width */}
                <TextField
                  aria-label={t("batchTitle")}
                  value={title}
                  onChange={setTitle}
                >
                  <Label className="text-[11px]">{t("batchTitle")}</Label>
                  <Input placeholder={t("batchTitlePlaceholder")} className="h-7 text-xs" />
                </TextField>

                {/* Concurrency + Error Strategy — 2-column grid */}
                <div className="grid grid-cols-2 gap-2">
                  <NumberField
                    aria-label={t("advanced.concurrency")}
                    value={concurrency}
                    onChange={(v) => setConcurrency(v)}
                    minValue={1}
                    maxValue={20}
                  >
                    <Label className="text-[11px]">{t("advanced.concurrency")}</Label>
                    <NumberField.Input className="h-7 text-xs" />
                  </NumberField>

                  <Select
                    aria-label={t("advanced.errorStrategy")}
                    value={errorStrategy}
                    onChange={(value) => value && setErrorStrategy(value as ErrorStrategy)}
                  >
                    <Label className="text-[11px]">{t("advanced.errorStrategy")}</Label>
                    <Select.Trigger className="h-7">
                      <Select.Value>
                        {() => (
                          <span className="text-xs">{tBatch(`errorStrategy.${errorStrategy}`)}</span>
                        )}
                      </Select.Value>
                    </Select.Trigger>
                    <Select.Popover>
                      <ListBox>
                        {ERROR_STRATEGIES.map((es) => (
                          <ListBox.Item key={es} id={es} textValue={tBatch(`errorStrategy.${es}`)}>
                            <Label>{tBatch(`errorStrategy.${es}`)}</Label>
                          </ListBox.Item>
                        ))}
                      </ListBox>
                    </Select.Popover>
                  </Select>
                </div>
              </div>
            </Disclosure.Content>
          </Disclosure>
        </div>
      </ScrollShadow>

      <Separator />

      {/* ── Submit Bar ─────────────────────────────────────────────── */}
      <div className="flex items-center justify-between px-2.5 py-2">
        {/* Left: cancel */}
        <Button variant="ghost" size="sm" className="h-7 text-xs" onPress={onClose} isDisabled={isSubmitting}>
          {t("close")}
        </Button>

        {/* Right: submit */}
        <Button
          variant="primary"
          size="sm"
          className="h-7 gap-1 text-xs"
          onPress={handleSubmit}
          isDisabled={isSubmitting}
        >
          {isSubmitting ? (
            <>
              <Spinner size="sm" />
              {t("submit.submitting")}
            </>
          ) : (
            <>
              <Play className="size-3" />
              {t("submit.button")}
            </>
          )}
        </Button>
      </div>
    </div>
  );
}
