"use client";

import { useState, useEffect, useRef, useCallback, useMemo } from "react";
import { useLocale, useTranslations } from "next-intl";
import { MentionPromptEditor, type FileMentionItem } from "@/components/ui/mention-prompt-editor";
import {
  Button,
  Card,
  ScrollShadow,
  Tooltip,
  Select,
  ListBox,
  Chip,
  Alert,
  Spinner,
  Label,
  Description,
  toast,
} from "@heroui/react";
import {
  Sparkles,
  Zap,
  Bot,
  Image,
  Video,
  Music,
  FileText,
  Lock,
  Unlock,
  Wand2,
  Palette,
  Grid3X3,
  Send,
  X,
  FolderOpen,
  Layers,
} from "lucide-react";
import { useAIGeneration as useAIGenerationContext, getEntityTypeLabel } from "@/components/providers/ai-generation-provider";
import { aiService } from "@/lib/api/services/ai.service";
import { projectService } from "@/lib/api/services/project.service";
import { useWorkspace } from "@/components/providers/workspace-provider";
import { aiGenerationCache } from "@/lib/stores/ai-generation-cache";
import type {
  ProviderType,
  AvailableProviderDTO,
  InputSchemaDTO,
  EntityGenerationRequestDTO,
} from "@/lib/api/dto/ai.dto";
import type { EntityType, StyleListDTO } from "@/lib/api/dto/project.dto";
import { DynamicForm } from "./components/dynamic-form";
import { BatchCreatePanel } from "./components/batch-create-panel";
import { type FileValue } from "./components/form-fields";
import { getErrorFromException } from "@/lib/api";
import NextImage from "@/components/ui/content-image";

type PromptMediaType = Exclude<ProviderType, "TEXT">;
type PromptEntityType = Extract<EntityType, "CHARACTER" | "SCENE" | "PROP" | "STORYBOARD">;

const FILE_PARAM_TYPES: Record<string, "IMAGE" | "VIDEO" | "AUDIO"> = {
  IMAGE: "IMAGE",
  IMAGE_LIST: "IMAGE",
  VIDEO: "VIDEO",
  VIDEO_LIST: "VIDEO",
  AUDIO: "AUDIO",
  AUDIO_LIST: "AUDIO",
};

const MEDIA_KIND_CN: Record<string, string> = {
  IMAGE: "图片",
  VIDEO: "视频",
  AUDIO: "音频",
};

// Type options with icons (labels resolved via i18n)
const TYPE_OPTIONS: { type: ProviderType; labelKey: string; icon: typeof Image }[] = [
  { type: "IMAGE", labelKey: "type.image", icon: Image },
  { type: "VIDEO", labelKey: "type.video", icon: Video },
  { type: "AUDIO", labelKey: "type.audio", icon: Music },
  { type: "TEXT", labelKey: "type.text", icon: FileText },
];

// Grid size options for storyboard (n×m)
const GRID_SIZE_OPTIONS: { value: string; labelKey: string }[] = [
  { value: "1x1", labelKey: "grid.1x1" },
  { value: "1x2", labelKey: "grid.1x2" },
  { value: "2x1", labelKey: "grid.2x1" },
  { value: "2x2", labelKey: "grid.2x2" },
  { value: "2x3", labelKey: "grid.2x3" },
  { value: "3x2", labelKey: "grid.3x2" },
  { value: "3x3", labelKey: "grid.3x3" },
  { value: "3x4", labelKey: "grid.3x4" },
  { value: "4x3", labelKey: "grid.4x3" },
  { value: "4x4", labelKey: "grid.4x4" },
];

const TEXT_PROMPT_PROVIDER_IDS: Record<PromptMediaType, string> = {
  IMAGE: "00000000-0000-0000-0004-000000000001",
  VIDEO: "00000000-0000-0000-0004-000000000003",
  AUDIO: "00000000-0000-0000-0004-000000000005",
};

function isPromptMediaType(type: ProviderType): type is PromptMediaType {
  return type === "IMAGE" || type === "VIDEO" || type === "AUDIO";
}

function isPromptEntityType(type: string | null | undefined): type is PromptEntityType {
  return type === "CHARACTER" || type === "SCENE" || type === "PROP" || type === "STORYBOARD";
}

function getSchemaDefaults(schema: InputSchemaDTO | null): Record<string, unknown> {
  if (!schema) return {};

  return schema.params.reduce<Record<string, unknown>>((defaults, param) => {
    const defaultValue = param.defaultValue ?? param.default;
    if (defaultValue !== undefined) {
      defaults[param.name] = defaultValue;
    }
    return defaults;
  }, {});
}

function pickSchemaValues(
  values: Record<string, unknown>,
  schema: InputSchemaDTO | null
): Record<string, unknown> {
  if (!schema) return {};

  const validNames = new Set(schema.params.map((param) => param.name));
  return Object.fromEntries(
    Object.entries(values).filter(([key]) => validNames.has(key))
  );
}

function gridSizeToCount(value: string): number {
  const [rows, cols] = value.split("x").map(Number);
  if (!Number.isFinite(rows) || !Number.isFinite(cols) || rows <= 0 || cols <= 0) {
    return 1;
  }
  return rows * cols;
}

function normalizeGridCount(value: unknown): number | undefined {
  if (value === null || value === undefined || value === "") {
    return undefined;
  }

  const numericValue =
    typeof value === "number"
      ? value
      : typeof value === "string" && value.trim()
        ? Number(value)
        : NaN;

  if (!Number.isFinite(numericValue)) {
    return undefined;
  }

  const normalized = Math.trunc(numericValue);
  if (normalized < 1 || normalized > 16) {
    return undefined;
  }

  return normalized;
}

function getPromptOutputText(outputs: Record<string, unknown>): string {
  return (
    (typeof outputs.textAccumulated === "string" && outputs.textAccumulated) ||
    (typeof outputs.textDelta === "string" && outputs.textDelta) ||
    (typeof outputs.prompt === "string" && outputs.prompt) ||
    ""
  ).trim();
}

function isBlankValue(value: unknown): boolean {
  if (typeof value === "string") {
    return value.trim().length === 0;
  }
  return value === undefined || value === null;
}

export function AIGenerationPanel({ scriptId: propScriptId }: { scriptId?: string } = {}) {
  const t = useTranslations("workspace.aiGeneration");
  const locale = useLocale();
  const { currentWorkspaceId } = useWorkspace();
  const {
    entityType,
    entityId,
    entityName,
    entityDescription,
    entityFixedDesc,
    entityCoverUrl,
    scriptId: contextScriptId,
    episodeId,
    generationType,
    setGenerationType,
    activePanel,
    switchToAgentPanel,
    isLocked,
    setIsLocked,
    associatedCovers,
    clearEntity,
  } = useAIGenerationContext();

  // Prop takes precedence when context hasn't been seeded by entity selection yet
  const scriptId = propScriptId ?? contextScriptId;

  // Provider state
  const [providers, setProviders] = useState<AvailableProviderDTO[]>([]);
  const [selectedProviderId, setSelectedProviderId] = useState<string | null>(null);
  const [isLoadingProviders, setIsLoadingProviders] = useState(false);

  // Form state
  const [prompt, setPrompt] = useState("");
  const [formValues, setFormValues] = useState<Record<string, unknown>>({});
  const [formErrors, setFormErrors] = useState<Record<string, string>>({});

  // Style and grid state for image generation
  const [styles, setStyles] = useState<StyleListDTO[]>([]);
  const [isLoadingStyles, setIsLoadingStyles] = useState(false);
  const [selectedStyle, setSelectedStyle] = useState("");
  const [gridSize, setGridSize] = useState(() => aiGenerationCache.getGridSize());

  // Prompt optimization state
  const [isOptimizingPrompt, setIsOptimizingPrompt] = useState(false);
  const [textProviders, setTextProviders] = useState<AvailableProviderDTO[]>([]);
  const [isLoadingTextProviders, setIsLoadingTextProviders] = useState(false);
  const [textFormValues, setTextFormValues] = useState<Record<string, unknown>>({});
  const [textFormErrors, setTextFormErrors] = useState<Record<string, string>>({});
  const [hasGeneratedPrompt, setHasGeneratedPrompt] = useState(false);

  // Estimated credit cost (dynamic)
  const [estimatedCost, setEstimatedCost] = useState<number | null>(null);
  const [textEstimatedCost, setTextEstimatedCost] = useState<number | null>(null);

  // Generation state
  const [isGenerating, setIsGenerating] = useState(false);
  const [executionError, setExecutionError] = useState<string | null>(null);

  // Refs
  const promptStreamRef = useRef<{ abort: () => void } | null>(null);
  const cacheInitializedRef = useRef(false);

  const [isBatchMode, setIsBatchMode] = useState(false);

  // Get selected provider
  const selectedProvider = providers.find((p) => p.id === selectedProviderId);

  // Get input schema from selected provider
  const inputSchema: InputSchemaDTO | null = useMemo(() => {
    if (!selectedProvider) return null;
    return aiService.getInputSchema(selectedProvider);
  }, [selectedProvider]);

  // Check if schema has prompt parameter
  const hasPromptParam = useMemo(() => {
    if (!inputSchema) return false;
    return inputSchema.params.some((p) => p.name === "prompt");
  }, [inputSchema]);

  const promptProviderId = useMemo(
    () => (isPromptMediaType(generationType) ? TEXT_PROMPT_PROVIDER_IDS[generationType] : null),
    [generationType]
  );

  const promptProvider = useMemo(() => {
    if (!promptProviderId) return null;
    return textProviders.find((provider) => provider.id === promptProviderId) || null;
  }, [promptProviderId, textProviders]);

  const promptInputSchema: InputSchemaDTO | null = useMemo(() => {
    if (!promptProvider) return null;
    return aiService.getInputSchema(promptProvider);
  }, [promptProvider]);

  const promptContextEntityType = isPromptEntityType(entityType) ? entityType : null;
  const shouldHidePromptEntityFields = Boolean(promptContextEntityType && entityId);
  const shouldReusePromptStyleSelector = generationType === "IMAGE" || generationType === "VIDEO";
  const shouldReusePromptGridSelector = generationType === "IMAGE" && entityType === "STORYBOARD";
  const promptI18n = locale.startsWith("en") ? "en" : "zh";

  const promptHiddenFields = useMemo(() => {
    const hiddenFields = ["customInput", "gridCount", "i18n"];

    if (shouldHidePromptEntityFields) {
      hiddenFields.push("entityType", "entityId");
    }

    if (shouldReusePromptStyleSelector) {
      hiddenFields.push("styleId");
    }

    if (shouldReusePromptGridSelector) {
      hiddenFields.push("gridCount");
    }

    return hiddenFields;
  }, [
    shouldHidePromptEntityFields,
    shouldReusePromptGridSelector,
    shouldReusePromptStyleSelector,
  ]);

  const promptGridCountParam = useMemo(
    () => promptInputSchema?.params.find((param) => param.name === "gridCount") ?? null,
    [promptInputSchema]
  );
  const shouldShowPromptGridCount = generationType === "IMAGE" && Boolean(promptGridCountParam);

  const hasPromptConfigFields = useMemo(
    () => Boolean(promptInputSchema?.params.some((param) => !promptHiddenFields.includes(param.name))),
    [promptHiddenFields, promptInputSchema]
  );

  // Separate params into basic (inline selects in options bar) and everything else
  const basicParams = useMemo(() => {
    if (!inputSchema) return [];
    return inputSchema.params.filter(
      (p) => p.name !== "prompt" && p.group === "basic" && p.enum && p.enum.length > 0
    );
  }, [inputSchema]);

  // Schema for everything except prompt and the entire basic group → passed to DynamicForm
  const remainingSchema: InputSchemaDTO | null = useMemo(() => {
    if (!inputSchema) return null;
    const params = inputSchema.params.filter(
      (p) => p.name !== "prompt" && p.group !== "basic"
    );
    if (params.length === 0) return null;
    return {
      params,
      groups: (inputSchema.groups ?? []).filter((g) => g.name !== "basic"),
      exclusiveGroups: inputSchema.exclusiveGroups ?? [],
    };
  }, [inputSchema]);

  // Guard: TEXT type is internal-only, redirect to IMAGE if cached/set
  useEffect(() => {
    if (generationType === "TEXT") {
      setGenerationType("IMAGE");
    }
  }, [generationType, setGenerationType]);

  // Cache: save generation type when it changes
  useEffect(() => {
    aiGenerationCache.setGenerationType(generationType);
  }, [generationType]);

  useEffect(() => {
    setHasGeneratedPrompt(false);
  }, [generationType]);

  // Cache: save provider selection when it changes
  useEffect(() => {
    if (selectedProviderId) {
      aiGenerationCache.setLastProvider(generationType, selectedProviderId);
    }
  }, [selectedProviderId, generationType]);

  // Cache: save style when it changes (skip initial render to avoid overwriting cached value)
  useEffect(() => {
    if (!cacheInitializedRef.current) return;
    if (scriptId) {
      aiGenerationCache.setStyleForScript(scriptId, selectedStyle);
    }
  }, [scriptId, selectedStyle]);

  // Cache: save form values when they change (skip initial render)
  useEffect(() => {
    if (!cacheInitializedRef.current) return;
    if (selectedProviderId && Object.keys(formValues).length > 0) {
      aiGenerationCache.setProviderParams(selectedProviderId, formValues);
    }
  }, [selectedProviderId, formValues, selectedProvider?.creditCost]);

  // Cache: save grid size when it changes
  useEffect(() => {
    aiGenerationCache.setGridSize(gridSize);
  }, [gridSize]);

  // Load TEXT providers for prompt generation/optimization
  useEffect(() => {
    if (activePanel !== "ai-generation") return;
    let ignore = false;
    setIsLoadingTextProviders(true);
    aiService.getProvidersByType("TEXT").then((data) => {
      if (!ignore) setTextProviders(data);
    }).catch((err) => {
      console.error("Failed to load TEXT providers:", err);
      toast.danger(getErrorFromException(err, locale));
      if (!ignore) setTextProviders([]);
    }).finally(() => {
      if (!ignore) setIsLoadingTextProviders(false);
    });
    return () => { ignore = true; };
  }, [activePanel]);

  // Estimate credit cost when provider or form values change
  useEffect(() => {
    if (!selectedProviderId) {
      setEstimatedCost(null);
      return;
    }

    const timer = setTimeout(async () => {
      try {
        const result = await aiService.estimateCost(selectedProviderId, formValues);
        setEstimatedCost(result.finalCost);
      } catch {
        // Fallback to static cost
        setEstimatedCost(selectedProvider?.creditCost ?? null);
      }
    }, 300);

    return () => clearTimeout(timer);
  }, [selectedProviderId, formValues, selectedProvider]);

  // Estimate TEXT provider cost for prompt generation button
  useEffect(() => {
    if (!promptProvider) {
      setTextEstimatedCost(null);
      return;
    }

    const timer = setTimeout(() => {
      aiService.estimateCost(promptProvider.id, pickSchemaValues(textFormValues, promptInputSchema)).then((result) => {
        setTextEstimatedCost(result.finalCost);
      }).catch(() => {
        setTextEstimatedCost(promptProvider.creditCost);
      });
    }, 300);

    return () => clearTimeout(timer);
  }, [promptProvider, promptInputSchema, textFormValues]);

  // Load providers when type changes or panel becomes active
  useEffect(() => {
    if (activePanel !== "ai-generation") return;

    let ignore = false;
    const loadProviders = async () => {
      try {
        setIsLoadingProviders(true);
        const data = await aiService.getProvidersByType(generationType);
        if (ignore) return;
        setProviders(data);

        // Try to restore last selected provider from cache
        const cachedProviderId = aiGenerationCache.getLastProvider(generationType);
        const cachedProvider = data.find((p) => p.id === cachedProviderId);

        if (cachedProvider) {
          setSelectedProviderId(cachedProviderId);
        } else if (data.length > 0) {
          // Fallback to first provider
          setSelectedProviderId(data[0].id);
        } else {
          setSelectedProviderId(null);
        }
      } catch (error) {
        if (ignore) return;
        console.error("Failed to load providers:", error);
        toast.danger(getErrorFromException(error, locale));
      } finally {
        if (!ignore) setIsLoadingProviders(false);
      }
    };

    loadProviders();
    return () => { ignore = true; };
  }, [generationType, activePanel]);

  // Load styles when scriptId changes or panel becomes active
  useEffect(() => {
    if (activePanel !== "ai-generation" || !currentWorkspaceId || !scriptId) return;

    let ignore = false;
    const loadStyles = async () => {
      try {
        setIsLoadingStyles(true);
        const data = await projectService.getAvailableStyles( scriptId);
        if (ignore) return;
        setStyles(data);

        // Restore cached style for this script
        const cachedStyle = aiGenerationCache.getStyleForScript(scriptId);
        if (cachedStyle && data.some((s) => s.id === cachedStyle)) {
          setSelectedStyle(cachedStyle);
        }
      } catch (error) {
        if (ignore) return;
        console.error("Failed to load styles:", error);
        toast.danger(getErrorFromException(error, locale));
      } finally {
        if (!ignore) setIsLoadingStyles(false);
      }
    };

    loadStyles();
    return () => { ignore = true; };
  }, [currentWorkspaceId, scriptId, activePanel]);

  // Initialize form values when provider/schema changes - merge with cached values
  useEffect(() => {
    if (!inputSchema || !selectedProviderId) {
      setFormValues({});
      return;
    }

    // Collect valid param names from current schema
    const validParamNames = new Set(inputSchema.params.map(p => p.name));

    // Extract default values from schema
    const defaults: Record<string, unknown> = {};
    for (const param of inputSchema.params) {
      const defaultVal = param.defaultValue ?? param.default;
      if (defaultVal !== undefined) {
        defaults[param.name] = defaultVal;
      }
    }

    // Merge with cached values, but only keep params that exist in current schema
    const cached = aiGenerationCache.getProviderParams(selectedProviderId);
    const filteredCached: Record<string, unknown> = {};
    if (cached) {
      for (const [key, value] of Object.entries(cached)) {
        if (validParamNames.has(key)) {
          filteredCached[key] = value;
        }
      }
    }

    const mergedValues = { ...defaults, ...filteredCached };

    // Auto-fill image params from associated covers (only for storyboard entities)
    if (entityType === "STORYBOARD" && associatedCovers && associatedCovers.length > 0) {
      const coverFileValues: FileValue[] = associatedCovers.map(cover => ({
        assetId: cover.coverAssetId,
        url: cover.coverUrl,
        name: cover.entityName,
        mimeType: "image/*",
        fileSize: 0,
      }));

      let singleImageFilled = false;

      for (const param of inputSchema.params) {
        // Skip if already has a cached value
        if (filteredCached[param.name]) continue;

        if (param.type === "IMAGE" && !singleImageFilled && coverFileValues.length > 0) {
          mergedValues[param.name] = coverFileValues[0];
          singleImageFilled = true;
        } else if (param.type === "IMAGE_LIST" && coverFileValues.length > 0) {
          const maxCount = param.fileConfig?.maxCount || 10;
          mergedValues[param.name] = coverFileValues.slice(0, maxCount);
        }
      }
    }

    setFormValues(mergedValues);
    cacheInitializedRef.current = true;
  }, [inputSchema, selectedProviderId, entityType, associatedCovers]);

  useEffect(() => {
    if (!promptProvider || !promptInputSchema) {
      setTextFormValues({});
      setTextFormErrors({});
      return;
    }

    const defaults = getSchemaDefaults(promptInputSchema);
    const cachedValues = pickSchemaValues(
      aiGenerationCache.getProviderParams(promptProvider.id),
      promptInputSchema
    );
    setTextFormValues((currentValues) => {
      const nextValues: Record<string, unknown> = {
        ...defaults,
        ...cachedValues,
        ...pickSchemaValues(currentValues, promptInputSchema),
      };

      if (!nextValues.styleId && selectedStyle) {
        nextValues.styleId = selectedStyle;
      }

      if (generationType === "IMAGE" && nextValues.gridCount === undefined) {
        nextValues.gridCount = gridSizeToCount(gridSize);
      }

      nextValues.i18n = promptI18n;

      return nextValues;
    });
    setTextFormErrors({});
  }, [
    gridSize,
    generationType,
    promptInputSchema,
    promptI18n,
    promptProvider,
    selectedStyle,
  ]);

  useEffect(() => {
    if (!promptProvider || !promptContextEntityType || !entityId) return;

    setTextFormValues((currentValues) => {
      if (
        currentValues.entityType === promptContextEntityType &&
        currentValues.entityId === entityId
      ) {
        return currentValues;
      }

      return {
        ...currentValues,
        entityType: promptContextEntityType,
        entityId,
      };
    });
  }, [entityId, promptContextEntityType, promptProvider]);

  useEffect(() => {
    if (!promptProvider || !shouldReusePromptStyleSelector) return;

    setTextFormValues((currentValues) => {
      if ((currentValues.styleId ?? "") === selectedStyle) {
        return currentValues;
      }

      return {
        ...currentValues,
        styleId: selectedStyle,
      };
    });
  }, [promptProvider, selectedStyle, shouldReusePromptStyleSelector]);

  useEffect(() => {
    if (!promptProvider || !shouldReusePromptGridSelector) return;

    const nextGridCount = gridSizeToCount(gridSize);
    setTextFormValues((currentValues) => {
      if (currentValues.gridCount === nextGridCount) {
        return currentValues;
      }

      return {
        ...currentValues,
        gridCount: nextGridCount,
      };
    });
  }, [gridSize, promptProvider, shouldReusePromptGridSelector]);

  useEffect(() => {
    if (!promptProvider) return;

    setTextFormValues((currentValues) => {
      if (currentValues.i18n === promptI18n) {
        return currentValues;
      }

      return {
        ...currentValues,
        i18n: promptI18n,
      };
    });
  }, [promptI18n, promptProvider]);

  useEffect(() => {
    if (!promptProvider || Object.keys(textFormValues).length === 0) return;
    aiGenerationCache.setProviderParams(promptProvider.id, textFormValues);
  }, [promptProvider, textFormValues]);

  useEffect(() => {
    return () => {
      promptStreamRef.current?.abort();
    };
  }, []);

  const handleTextFormChange = useCallback((values: Record<string, unknown>) => {
    setTextFormValues(values);
    if (Object.keys(textFormErrors).length > 0) {
      setTextFormErrors({});
    }
  }, [textFormErrors]);

  const buildPromptParams = useCallback(() => {
    if (!promptInputSchema) {
      return {} as Record<string, unknown>;
    }

    const nextParams = pickSchemaValues(textFormValues, promptInputSchema);

    if (promptContextEntityType && entityId) {
      nextParams.entityType = promptContextEntityType;
      nextParams.entityId = entityId;
    }

    if (shouldReusePromptStyleSelector) {
      nextParams.styleId = selectedStyle;
    }

    if (generationType !== "IMAGE") {
      delete nextParams.gridCount;
    } else if (shouldReusePromptGridSelector) {
      nextParams.gridCount = gridSizeToCount(gridSize);
    }

    const normalizedGridCount = normalizeGridCount(nextParams.gridCount);
    if (normalizedGridCount === undefined) {
      delete nextParams.gridCount;
    } else {
      nextParams.gridCount = normalizedGridCount;
    }

    const mergedPromptInput = prompt.trim();
    if (mergedPromptInput) {
      nextParams.customInput = mergedPromptInput;
    } else {
      delete nextParams.customInput;
    }

    if (typeof nextParams.entityId === "string") {
      nextParams.entityId = nextParams.entityId.trim();
    }

    if (typeof nextParams.styleId === "string" && !nextParams.styleId.trim()) {
      delete nextParams.styleId;
    }

    nextParams.i18n = promptI18n;

    return nextParams;
  }, [
    entityId,
    generationType,
    gridSize,
    prompt,
    promptI18n,
    promptContextEntityType,
    promptInputSchema,
    selectedStyle,
    shouldReusePromptGridSelector,
    shouldReusePromptStyleSelector,
    textFormValues,
  ]);

  const validatePromptParams = useCallback((params: Record<string, unknown>) => {
    if (!promptInputSchema) {
      return false;
    }

    const nextErrors: Record<string, string> = {};

    for (const param of promptInputSchema.params) {
      const isRequired = param.required || param.validation?.required;
      if (!isRequired) continue;

      if (isBlankValue(params[param.name])) {
        nextErrors[param.name] = t("prompt.requiredField", { field: param.label });
      }
    }

    if (
      generationType === "IMAGE" &&
      !isBlankValue(textFormValues.gridCount) &&
      normalizeGridCount(textFormValues.gridCount) === undefined
    ) {
      nextErrors.gridCount = t("prompt.gridCountRange");
    }

    setTextFormErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  }, [generationType, promptInputSchema, t, textFormValues.gridCount]);

  // Handle file upload
  const handleFileUpload = useCallback(
    async (file: File): Promise<FileValue> => {
      if (!currentWorkspaceId) {
        throw new Error("No workspace selected");
      }

      // Initialize upload
      const initResponse = await projectService.initAssetUpload( {
        name: file.name,
        fileName: file.name,
        mimeType: file.type,
        fileSize: file.size,
        scope: "SCRIPT",
        scriptId: scriptId,
        description: t("error.uploadDescription"),
      });

      // Upload to presigned URL
      await fetch(initResponse.uploadUrl, {
        method: initResponse.method,
        headers: initResponse.headers,
        body: file,
      });

      // Confirm upload
      await projectService.confirmAssetUpload( initResponse.assetId, {
        fileKey: initResponse.fileKey,
        actualFileSize: file.size,
      });

      return {
        assetId: initResponse.assetId,
        url: initResponse.uploadUrl.split("?")[0],
        name: file.name,
        mimeType: file.type,
        fileSize: file.size,
      };
    },
    [currentWorkspaceId, scriptId, t]
  );

  // Handle prompt generation / optimization via the fixed TEXT provider for the current media type
  const handleOptimizePrompt = useCallback(() => {
    if (!promptProvider || isOptimizingPrompt) {
      if (!promptProvider) {
        toast.warning(t("prompt.providerUnavailable"));
      }
      return;
    }

    const isOptimizeAction = hasGeneratedPrompt && prompt.trim().length > 0;
    const params = buildPromptParams();

    if (!validatePromptParams(params)) {
      return;
    }

    setIsOptimizingPrompt(true);

    // Use streaming SSE for TEXT generation
    const stream = aiService.executeStream(
      {
        providerId: promptProvider.id,
        generationType: "TEXT",
        params,
      },
      {
        onFinished: (outputs) => {
          const finalText = getPromptOutputText(outputs);

          if (finalText) {
            setPrompt(finalText);
            setIsLocked(true);
            setHasGeneratedPrompt(true);
            toast.success(isOptimizeAction ? t("toast.promptOptimized") : t("toast.promptGenerated"));
          } else {
            toast.warning(isOptimizeAction ? t("toast.promptOptimizeFailed") : t("toast.promptGenerateFailed"));
          }

          setIsOptimizingPrompt(false);
          promptStreamRef.current = null;
        },
        onError: (errorCode, errorMessage) => {
          console.error("Prompt stream error:", errorCode, errorMessage);
          toast.danger(isOptimizeAction ? t("toast.promptOptimizeError") : t("toast.promptGenerateError"));
          setIsOptimizingPrompt(false);
          promptStreamRef.current = null;
        },
      }
    );

    promptStreamRef.current = stream;
  }, [
    buildPromptParams,
    hasGeneratedPrompt,
    isOptimizingPrompt,
    prompt,
    promptProvider,
    setIsLocked,
    t,
    validatePromptParams,
  ]);

  // Handle generation
  const handleGenerate = useCallback(async () => {
    if (!selectedProviderId || isGenerating || !selectedProvider) return;

    // If has prompt param but no prompt entered, don't proceed
    if (hasPromptParam && !prompt.trim()) return;

    // Transform file values to asset IDs for the backend
    // Only include params defined in the current provider's schema
    const validParamNames = new Set(
      (selectedProvider.inputSchema || []).map(p => p.name)
    );

    const transformedFormValues: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(formValues)) {
      // Skip params not in current provider's schema
      if (!validParamNames.has(key)) continue;

      if (value && typeof value === "object") {
        // Check if it's a FileValue (has assetId property)
        if ("assetId" in value && typeof (value as FileValue).assetId === "string") {
          // Single file - extract assetId
          transformedFormValues[key] = (value as FileValue).assetId;
        } else if (Array.isArray(value)) {
          // Check if it's an array of FileValue
          if (value.length > 0 && value[0] && typeof value[0] === "object" && "assetId" in value[0]) {
            // File list - extract assetIds
            transformedFormValues[key] = value.map((v: FileValue) => v.assetId);
          } else {
            transformedFormValues[key] = value;
          }
        } else {
          transformedFormValues[key] = value;
        }
      } else {
        transformedFormValues[key] = value;
      }
    }

    // Build params - include prompt if supported
    const params = hasPromptParam ? { prompt, ...transformedFormValues } : transformedFormValues;

    // Validate required fields
    const validation = await aiService.validateInput(selectedProviderId, params);

    if (!validation.valid) {
      const errors: Record<string, string> = {};
      for (const err of validation.errors) {
        errors[err.field] = err.message;
      }
      setFormErrors(errors);
      return;
    }

    setFormErrors({});
    setExecutionError(null);
    setIsGenerating(true);

    try {
      const responseMode = aiService.getPreferredSubmissionMode(selectedProvider);

      // Use entity generation API when entity context is available
      if (entityType && entityId) {
        // Build params with prompt included (backend requires prompt in params)
        const generationParams: Record<string, unknown> = {
          ...transformedFormValues,
          prompt: hasPromptParam ? prompt : entityName || "",
        };

        // Add style parameter for IMAGE generation
        if (generationType === "IMAGE" && selectedStyle) {
          generationParams.style = selectedStyle;
        }

        // Add grid size for STORYBOARD
        if (entityType === "STORYBOARD" && gridSize !== "1x1") {
          generationParams.gridSize = gridSize;
          // Parse grid size (e.g., "2x3" -> {rows: 2, cols: 3})
          const [rows, cols] = gridSize.split("x").map(Number);
          generationParams.gridRows = rows;
          generationParams.gridCols = cols;
        }

        const entityRequest: EntityGenerationRequestDTO = {
          entityType: entityType as EntityType,
          entityId: entityId,
          generationType: generationType,
          prompt: hasPromptParam ? prompt : entityName || "",
          providerId: selectedProviderId,
          params: generationParams,
          scriptId: scriptId,
          relationType: "DRAFT",
          responseMode,
        };

        const response = await aiService.submitEntityGeneration(entityRequest);

        if (response.success) {
          toast.success(t("toast.taskSubmitted"));
          setPrompt("");
          if (hasGeneratedPrompt) {
            setIsLocked(false);
            setHasGeneratedPrompt(false);
          }
        } else {
          toast.danger(response.errorMessage || t("toast.taskFailed"));
        }
      } else if (scriptId) {
        const result = await aiService.submitGenerate({
          providerId: selectedProviderId,
          generationType: generationType,
          params: { prompt: hasPromptParam ? prompt : "", ...transformedFormValues },
          scriptId: scriptId,
          responseMode,
        });

        if (result.taskId) {
          toast.success(t("toast.taskSubmitted"));
          setPrompt("");
          if (hasGeneratedPrompt) {
            setIsLocked(false);
            setHasGeneratedPrompt(false);
          }
        } else {
          toast.danger(t("toast.taskFailed"));
        }
      } else {
        // No entity and no script context
        toast.warning(t("toast.noScriptContext"));
      }
    } catch (error) {
      console.error("Generation failed:", error);
      const errorMessage = error instanceof Error ? error.message : t("toast.generationFailed");
      toast.danger(errorMessage);
      setExecutionError(errorMessage);
    } finally {
      setIsGenerating(false);
    }
  }, [selectedProviderId, isGenerating, selectedProvider, hasPromptParam, prompt, formValues,
      entityType, entityId, entityName, generationType, selectedStyle, gridSize, scriptId,
      hasGeneratedPrompt, setIsLocked, t]);

  // Get current type info
  const currentTypeInfo = TYPE_OPTIONS.find((ti) => ti.type === generationType) || TYPE_OPTIONS[0];
  const CurrentTypeIcon = currentTypeInfo.icon;
  const currentTypeLabel = t(currentTypeInfo.labelKey);

  // ── Compute mentionable file assets from DynamicForm file params ──
  const mentionItems = useMemo(() => {
    if (!inputSchema) return [];
    const items: FileMentionItem[] = [];
    const counters: Record<string, number> = {};
    for (const param of inputSchema.params) {
      const mediaKind = FILE_PARAM_TYPES[param.type];
      if (!mediaKind) continue;
      const value = formValues[param.name];
      if (!value) continue;
      const files: FileValue[] = Array.isArray(value) ? value : [value as FileValue];
      for (const file of files) {
        if (!file?.url) continue;
        counters[mediaKind] = (counters[mediaKind] ?? 0) + 1;
        items.push({
          kind: "file",
          name: `${MEDIA_KIND_CN[mediaKind] ?? mediaKind}${counters[mediaKind]}`,
          mediaKind,
          thumbnailUrl: file.url,
          thumbnailType: mediaKind === "IMAGE" ? "image" : mediaKind === "VIDEO" ? "video" : "icon",
          fileUrl: file.url,
          mimeType: file.mimeType,
          iconFallback: mediaKind === "AUDIO" ? "\u266B" : undefined,
        });
      }
    }
    return items;
  }, [inputSchema, formValues]);

  const mentionItemMap = useMemo(
    () => new Map(mentionItems.map((a) => [a.name, a])),
    [mentionItems],
  );

  return (
    <Card className="relative flex h-full flex-col overflow-hidden pt-0">
      {/* Header */}
      <div className="flex h-14 shrink-0 items-center justify-between px-1">
        {/* Left: Title */}
        <div className="flex shrink-0 items-center gap-1.5">
          <Sparkles className="size-4 text-accent" />
          <span className="text-sm font-medium">{t("title")}</span>
        </div>

        {/* Right: Actions */}
        <div className="flex items-center gap-1">
          {scriptId && (
            <Tooltip delay={300}>
              <Button
                variant={isBatchMode ? "primary" : "ghost"}
                size="sm"
                className="h-8 gap-1.5 px-2.5 text-xs"
                onPress={() => setIsBatchMode(!isBatchMode)}
              >
                <Layers className="size-3.5" />
                {t("tabs.batchMode")}
              </Button>
              <Tooltip.Content>{t("tabs.batchMode")}</Tooltip.Content>
            </Tooltip>
          )}

          <Button
            variant="ghost"
            size="sm"
            className="h-8 gap-1.5 px-2.5 text-xs"
            onPress={switchToAgentPanel}
          >
            <Bot className="size-3.5" />
            Agent
          </Button>
        </div>
      </div>

      {/* Entity Strip */}
      {entityType && entityId && (
        <div className="flex shrink-0 items-center gap-3 border-b border-border/40 py-2.5">
          {/* Cover thumbnail */}
          <div className="relative size-10 shrink-0 overflow-hidden rounded-lg bg-muted/10">
            {entityCoverUrl ? (
              <NextImage src={entityCoverUrl} alt={entityName} fill className="object-cover" sizes="120px" />
            ) : (
              <div className="flex size-full items-center justify-center">
                <CurrentTypeIcon className="size-5 text-foreground-3" />
              </div>
            )}
          </div>

          {/* Name + type */}
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-1.5">
              <span className="truncate text-sm font-medium text-foreground">{entityName}</span>
              <Chip size="sm" variant="soft" className="shrink-0 text-xs">
                {currentTypeLabel}
              </Chip>
            </div>
            {(entityDescription || entityFixedDesc) && (
              <p className="mt-0.5 truncate text-xs text-foreground-3">
                {entityDescription || entityFixedDesc}
              </p>
            )}
          </div>

          {/* Lock toggle */}
          <Tooltip delay={0}>
            <Button
              variant={isLocked ? "secondary" : "ghost"}
              size="sm"
              isIconOnly
              aria-label={t("entity.lockToggle")}
              className="size-7 shrink-0"
              onPress={() => setIsLocked(!isLocked)}
            >
              {isLocked ? (
                <Lock className="size-3.5 text-accent" />
              ) : (
                <Unlock className="size-3.5" />
              )}
            </Button>
            <Tooltip.Content>
              {isLocked ? t("entity.locked") : t("entity.unlocked")}
            </Tooltip.Content>
          </Tooltip>

          {/* Clear entity button */}
          <Button
            variant="ghost"
            size="sm"
            isIconOnly
            aria-label={t("entity.clear")}
            className="size-7 shrink-0"
            onPress={clearEntity}
          >
            <X className="size-3.5" />
          </Button>
        </div>
      )}

      {/* Script mode strip — shown when there's a scriptId but no entity */}
      {!entityType && scriptId && (
        <div className="flex shrink-0 items-center gap-3 border-b border-border/40 py-2.5">
          <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-muted/10">
            <FolderOpen className="size-5 text-foreground-3" />
          </div>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-1.5">
              <span className="truncate text-sm font-medium text-foreground">
                {t("entity.scriptMode")}
              </span>
              <Chip size="sm" variant="soft" className="shrink-0 text-xs">
                {t("entity.scriptModeLabel")}
              </Chip>
            </div>
            <p className="mt-0.5 truncate text-xs text-foreground-3">
              {t("entity.scriptModeDesc")}
            </p>
          </div>
        </div>
      )}

      {/* Content Area */}
      <ScrollShadow className="min-h-0 flex-1" hideScrollBar>
        <div className="flex flex-col gap-3">
          {/* Dynamic params (image / video / advanced groups) */}
          {remainingSchema && (
            <DynamicForm
              key={selectedProviderId ?? "provider-form"}
              schema={remainingSchema}
              values={formValues}
              onChange={setFormValues}
              onFileUpload={handleFileUpload}
              errors={formErrors}
              disabled={isGenerating}
              compact
              scriptId={scriptId}
              episodeId={episodeId}
              styles={styles}
            />
          )}

          {/* Error Display */}
          {executionError && (
            <Alert status="danger">
              <Alert.Indicator />
              <Alert.Content>
                <Alert.Title>{t("error.title")}</Alert.Title>
                <Alert.Description>{executionError}</Alert.Description>
              </Alert.Content>
            </Alert>
          )}
        </div>
      </ScrollShadow>

      {/* Input Area / Batch Create Panel */}
      {isBatchMode && scriptId ? (
        <BatchCreatePanel
          scriptId={scriptId}
          entityType={entityType ?? null}
          entityId={entityId ?? null}
          entityName={entityName ?? ""}
          generationType={generationType}
          selectedProviderId={selectedProviderId}
          providers={providers}
          isLoadingProviders={isLoadingProviders}
          prompt={prompt}
          formValues={formValues}
          onGenerationTypeChange={setGenerationType}
          onProviderIdChange={setSelectedProviderId}
          onBatchCreated={() => {
            setIsBatchMode(false);
          }}
          onClose={() => setIsBatchMode(false)}
        />
      ) : (
      <div className="relative shrink-0">
        {/* Top Options Bar - Style, Grid, Basic Params */}
        <div className="pb-2">
          <ScrollShadow orientation="horizontal" hideScrollBar>
            <div className="flex gap-2">
            {/* Style Selector - Only for IMAGE */}
            {(generationType === "IMAGE" || generationType === "VIDEO") && (
              <Tooltip delay={300}>
                <Select
                  aria-label={t("style.label")}
                  variant="secondary"
                  value={selectedStyle}
                  onChange={(value) => setSelectedStyle(String(value ?? ""))}
                  isDisabled={isLoadingStyles || isGenerating}
                >
                  <Select.Trigger className="h-8 shrink-0 gap-1.5 border-0 bg-transparent px-2 hover:bg-muted/20">
                    <Select.Value>
                      {({ isPlaceholder }) => (
                        <span className="flex min-w-0 items-center gap-1.5 text-xs">
                          <Palette className="size-3.5 shrink-0 text-accent" />
                          <span className="truncate">
                            {isLoadingStyles
                              ? "..."
                              : isPlaceholder || !selectedStyle
                                ? t("style.label")
                                : styles.find(s => s.id === selectedStyle)?.name}
                          </span>
                        </span>
                      )}
                    </Select.Value>
                  </Select.Trigger>
                  <Select.Popover className="w-56">
                    <ListBox>
                      <ListBox.Item key="no-style" id="" textValue={t("style.noStyle")} className="data-[selected=true]:bg-accent/10">
                        <span className="text-muted">{t("style.noStyle")}</span>
                      </ListBox.Item>
                      {styles.map((style) => (
                        <ListBox.Item key={style.id} id={style.id} textValue={style.name} className="data-[selected=true]:bg-accent/10">
                          {style.coverUrl && <NextImage src={style.coverUrl} alt="" width={24} height={24} className="size-6 rounded object-cover" />}
                          <div className="flex flex-col">
                            <Label>{style.name}</Label>
                            {style.description && <Description className="text-xs line-clamp-1">{style.description}</Description>}
                          </div>
                        </ListBox.Item>
                      ))}
                    </ListBox>
                  </Select.Popover>
                </Select>
                <Tooltip.Content>{t("style.selectTooltip")}</Tooltip.Content>
              </Tooltip>
            )}

            {/* Grid Size Selector - Only for STORYBOARD */}
            {generationType === "IMAGE" && entityType === "STORYBOARD" && (
              <Tooltip delay={300}>
                <Select
                  aria-label={t("gridLayout.tooltip")}
                  variant="secondary"
                  value={gridSize}
                  onChange={(value) => value && setGridSize(String(value))}
                  isDisabled={isGenerating}
                >
                  <Select.Trigger className="h-8 shrink-0 gap-1.5 border-0 bg-transparent px-2 hover:bg-muted/20">
                    <Select.Value>
                      {() => (
                        <span className="flex min-w-0 items-center gap-1.5 text-xs">
                          <Grid3X3 className="size-3.5 shrink-0 text-accent" />
                          <span className="truncate">{gridSize}</span>
                        </span>
                      )}
                    </Select.Value>
                  </Select.Trigger>
                  <Select.Popover>
                    <ListBox>
                      {GRID_SIZE_OPTIONS.map((option) => (
                        <ListBox.Item key={option.value} id={option.value} textValue={t(option.labelKey)} className="data-[selected=true]:bg-accent/10">
                          <Label>{t(option.labelKey)}</Label>
                        </ListBox.Item>
                      ))}
                    </ListBox>
                  </Select.Popover>
                </Select>
                <Tooltip.Content>{t("gridLayout.tooltip")}</Tooltip.Content>
              </Tooltip>
            )}

            {/* Inline Basic Params (SELECT type only) with Tooltips */}
            {basicParams.map((param) => (
              <Tooltip key={param.name} delay={300}>
                <Select
                  aria-label={param.label}
                  variant="secondary"
                  value={String(formValues[param.name] ?? param.defaultValue ?? param.default ?? "")}
                  onChange={(value) => {
                    if (value !== null) {
                      setFormValues((prev) => ({ ...prev, [param.name]: value }));
                    }
                  }}
                  isDisabled={isGenerating}
                >
                  <Select.Trigger className="h-8 shrink-0 gap-1.5 border-0 bg-transparent px-2 hover:bg-muted/20">
                    <Select.Value>
                      {({ isPlaceholder }) => (
                        <span className="block min-w-0 truncate text-xs">
                          {isPlaceholder ? param.label : String(formValues[param.name] ?? param.defaultValue ?? param.default ?? param.label)}
                        </span>
                      )}
                    </Select.Value>
                  </Select.Trigger>
                  <Select.Popover>
                    <ListBox>
                      {(param.enum || []).map((opt) => (
                        <ListBox.Item key={opt} id={opt} textValue={opt} className="data-[selected=true]:bg-accent/10">
                          <Label>{opt}</Label>
                        </ListBox.Item>
                      ))}
                    </ListBox>
                  </Select.Popover>
                </Select>
                <Tooltip.Content>
                  <div>
                    <p className="font-medium">{param.label}</p>
                    {param.description && <p className="mt-0.5 text-xs text-muted">{param.description}</p>}
                  </div>
                </Tooltip.Content>
              </Tooltip>
            ))}

            {shouldShowPromptGridCount && promptGridCountParam && !shouldReusePromptGridSelector && (
              <Tooltip delay={300}>
                <Tooltip.Trigger>
                  <div className="flex h-8 shrink-0 items-center rounded-md border-0 bg-transparent px-2 hover:bg-muted/20">
                    <Grid3X3 className="mr-1.5 size-3.5 shrink-0 text-accent" />
                    <input
                      type="number"
                      className="w-14 bg-transparent text-xs text-foreground outline-none"
                      value={(textFormValues.gridCount as number) ?? (promptGridCountParam.defaultValue as number) ?? ""}
                      onChange={(e) =>
                        {
                          setTextFormValues((prev) => ({
                            ...prev,
                            gridCount: Number.isNaN(e.target.valueAsNumber) ? undefined : e.target.valueAsNumber,
                          }));
                          if (textFormErrors.gridCount) {
                            setTextFormErrors((prev) => {
                              const nextErrors = { ...prev };
                              delete nextErrors.gridCount;
                              return nextErrors;
                            });
                          }
                        }
                      }
                      min={promptGridCountParam.min ?? promptGridCountParam.validation?.min ?? 1}
                      max={promptGridCountParam.max ?? promptGridCountParam.validation?.max ?? 16}
                      step={1}
                      disabled={isGenerating || isOptimizingPrompt}
                    />
                  </div>
                </Tooltip.Trigger>
                <Tooltip.Content>
                  <div>
                    <p className="font-medium">{promptGridCountParam.label}</p>
                    {promptGridCountParam.description && (
                      <p className="mt-0.5 text-xs text-muted">{promptGridCountParam.description}</p>
                    )}
                  </div>
                </Tooltip.Content>
              </Tooltip>
            )}

            {/* Non-enum basic params - inline, same style as enum selects */}
            {inputSchema?.params
              .filter((p) => p.name !== "prompt" && p.group === "basic" && !(p.enum && p.enum.length > 0))
              .map((param) => {
                const rawType = param.type === "INTEGER" ? "NUMBER" : param.type === "STRING" ? "TEXT" : param.type;
                const value = formValues[param.name];

                const tooltipContent = (
                  <Tooltip.Content>
                    <div>
                      <p className="font-medium">{param.label}</p>
                      {param.description && <p className="mt-0.5 text-xs text-muted">{param.description}</p>}
                    </div>
                  </Tooltip.Content>
                );

                if (rawType === "NUMBER") {
                  return (
                    <Tooltip key={param.name} delay={300}>
                      <Tooltip.Trigger>
                        <div className="flex h-8 shrink-0 items-center rounded-md border-0 bg-transparent px-2 hover:bg-muted/20">
                          <input
                            type="number"
                            className="w-14 bg-transparent text-xs text-foreground outline-none"
                            value={value as number ?? (param.defaultValue as number) ?? ""}
                            onChange={(e) => setFormValues((prev) => ({ ...prev, [param.name]: e.target.valueAsNumber }))}
                            min={(param as { min?: number }).min}
                            max={(param as { max?: number }).max}
                            step={(param as { step?: number }).step ?? 1}
                            disabled={isGenerating}
                          />
                        </div>
                      </Tooltip.Trigger>
                      {tooltipContent}
                    </Tooltip>
                  );
                }

                if (rawType === "TEXT") {
                  return (
                    <Tooltip key={param.name} delay={300}>
                      <Tooltip.Trigger>
                        <div className="flex h-8 shrink-0 items-center rounded-md border-0 bg-transparent px-2 hover:bg-muted/20">
                          <input
                            type="text"
                            className="w-24 bg-transparent text-xs text-foreground outline-none"
                            value={value as string ?? (param.defaultValue as string) ?? ""}
                            onChange={(e) => setFormValues((prev) => ({ ...prev, [param.name]: e.target.value }))}
                            disabled={isGenerating}
                          />
                        </div>
                      </Tooltip.Trigger>
                      {tooltipContent}
                    </Tooltip>
                  );
                }

                if (rawType === "BOOLEAN") {
                  return (
                    <Tooltip key={param.name} delay={300}>
                      <Tooltip.Trigger>
                        <button
                          type="button"
                          className={`flex h-8 shrink-0 items-center rounded-md px-2 text-xs transition-colors ${
                            value ? "bg-accent/10 text-accent" : "bg-transparent text-foreground-2 hover:bg-muted/20"
                          }`}
                          onClick={() => setFormValues((prev) => ({ ...prev, [param.name]: !value }))}
                          disabled={isGenerating}
                        >
                          {String(param.name)}
                        </button>
                      </Tooltip.Trigger>
                      {tooltipContent}
                    </Tooltip>
                  );
                }

                return null;
              })}
            </div>
          </ScrollShadow>
        </div>

        {/* Prompt Input Card */}
        <Card variant="tertiary" className="relative overflow-hidden p-0 gap-0">
          {hasPromptParam && (
            <>
              {promptInputSchema && hasPromptConfigFields ? (
                <div className="border-b border-border/40 px-3 py-3">
                  <DynamicForm
                    key={promptProvider?.id ?? `prompt-${generationType}`}
                    schema={promptInputSchema}
                    values={textFormValues}
                    onChange={handleTextFormChange}
                    errors={textFormErrors}
                    disabled={isGenerating || isOptimizingPrompt}
                    compact
                    hideGroupLabels
                    scriptId={scriptId}
                    episodeId={episodeId}
                    styles={styles}
                    hiddenFields={promptHiddenFields}
                  />
                </div>
              ) : (
                !promptInputSchema && (
                <div className="border-b border-border/40 px-3 py-4 text-sm text-muted">
                  {isLoadingTextProviders ? t("provider.loading") : t("prompt.providerUnavailable")}
                </div>
                )
              )}

              <div className="relative overflow-hidden">
                <MentionPromptEditor
                  value={prompt}
                  onChange={(v) => {
                    setPrompt(v);
                    if (!v.trim()) setHasGeneratedPrompt(false);
                  }}
                  onSubmit={handleGenerate}
                  canSubmit={!isGenerating && !isOptimizingPrompt && !!selectedProviderId && (hasPromptParam ? !!prompt.trim() : true)}
                  placeholder={
                    shouldHidePromptEntityFields && promptContextEntityType
                      ? t("prompt.placeholderWithEntity", {
                          name: entityName || getEntityTypeLabel(promptContextEntityType),
                          type: currentTypeLabel,
                        })
                      : t("prompt.placeholder", { type: currentTypeLabel })
                  }
                  disabled={isGenerating || isOptimizingPrompt}
                  mentionItems={mentionItems}
                  mentionItemMap={mentionItemMap}
                  style={{ minHeight: 140, maxHeight: 320 }}
                />

                {isOptimizingPrompt && (
                  <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-background/80">
                    <Spinner size="md" color="accent" />
                    <span className="text-sm font-medium text-foreground">{t("prompt.generating")}</span>
                  </div>
                )}
              </div>
            </>
          )}

          {/* Bottom Action Bar */}
          <div className="flex items-center justify-between bg-muted/5 px-3 py-2">
            {/* Left: Type + Model + Credits + AI Prompt */}
            <div className="flex min-w-0 items-center gap-1">
              {/* Type Selector (compact) */}
              <Select
                aria-label={t("provider.typeLabel")}
                className="w-auto shrink-0"
                variant="secondary"
                value={generationType}
                onChange={(value) => value && setGenerationType(value as ProviderType)}
              >
                <Select.Trigger className="h-7 gap-1 border-0 bg-transparent px-1.5 hover:bg-muted/20">
                  <Select.Value>
                    {() => (
                      <span className="flex items-center gap-1 text-xs">
                        <CurrentTypeIcon className="size-3.5 shrink-0 text-accent" />
                        <span>{currentTypeLabel}</span>
                      </span>
                    )}
                  </Select.Value>
                </Select.Trigger>
                <Select.Popover>
                  <ListBox>
                    {TYPE_OPTIONS.filter((option) => option.type !== "TEXT").map((option) => {
                      const Icon = option.icon;
                      const optLabel = t(option.labelKey);
                      return (
                        <ListBox.Item
                          key={option.type}
                          id={option.type}
                          textValue={optLabel}
                          className="data-[selected=true]:bg-accent/10"
                        >
                          <Icon className="size-4 text-muted" />
                          <Label>{optLabel}</Label>
                        </ListBox.Item>
                      );
                    })}
                  </ListBox>
                </Select.Popover>
              </Select>

              {/* Model Selector (compact) */}
              <Select
                aria-label={t("provider.modelLabel")}
                className="shrink"
                variant="secondary"
                value={selectedProviderId}
                onChange={(value) => value && setSelectedProviderId(value as string)}
                isDisabled={isLoadingProviders}
                placeholder={t("provider.selectModel")}
              >
                <Select.Trigger className="h-7 gap-1 border-0 bg-transparent px-1.5 hover:bg-muted/20">
                  <Select.Value>
                    {({ isPlaceholder }) => (
                      <span className="flex min-w-0 items-center gap-1 text-xs">
                        {isLoadingProviders ? (
                          <Spinner size="sm" />
                        ) : selectedProvider?.iconUrl ? (
                          <img src={selectedProvider.iconUrl} alt="" className="size-3.5 shrink-0 rounded" />
                        ) : (
                          <Bot className="size-3.5 shrink-0" />
                        )}
                        <span className="truncate">
                          {isLoadingProviders ? t("provider.loading") : isPlaceholder ? t("provider.selectModel") : selectedProvider?.name}
                        </span>
                      </span>
                    )}
                  </Select.Value>
                </Select.Trigger>
                <Select.Popover>
                  {providers.length === 0 ? (
                    <div className="px-3 py-4 text-center text-sm text-muted">
                      {t("provider.noModels")}
                    </div>
                  ) : (
                    <ListBox>
                      {providers.map((provider) => (
                        <ListBox.Item
                          key={provider.id}
                          id={provider.id}
                          textValue={provider.name}
                          className="data-[selected=true]:bg-accent/10"
                        >
                          {provider.iconUrl ? (
                            <img src={provider.iconUrl} alt="" className="size-4 rounded" />
                          ) : (
                            <Bot className="size-4 text-muted" />
                          )}
                          <div className="flex flex-1 flex-col">
                            <Label>{provider.name}</Label>
                            {provider.creditCost > 0 && (
                              <Description>{t("provider.creditCost", { cost: provider.creditCost })}</Description>
                            )}
                          </div>
                        </ListBox.Item>
                      ))}
                    </ListBox>
                  )}
                </Select.Popover>
              </Select>

              {/* Credits */}
              {selectedProvider && (estimatedCost ?? selectedProvider.creditCost) > 0 && (
                <Tooltip delay={0}>
                  <Tooltip.Trigger>
                    <div className="flex shrink-0 items-center gap-1 rounded-full bg-warning/10 px-1.5 py-0.5 text-xs font-medium text-warning">
                      <Zap className="size-3" />
                      <span>{estimatedCost ?? selectedProvider.creditCost}</span>
                    </div>
                  </Tooltip.Trigger>
                  <Tooltip.Content>{t("provider.creditPerUse", { cost: estimatedCost ?? selectedProvider.creditCost })}</Tooltip.Content>
                </Tooltip>
              )}
            </div>

            {/* Right: Keyboard hint + Generate */}
            <div className="flex shrink-0 items-center gap-2">
              {hasPromptParam && !isGenerating && (
                <span className="text-xs text-muted">Enter {t("action.generate")}</span>
              )}

              {hasPromptParam && (
                <Tooltip delay={300}>
                  <Button
                    size="sm"
                    isIconOnly
                    aria-label={hasGeneratedPrompt && prompt.trim() ? t("prompt.optimize") : t("prompt.generate")}
                    variant="secondary"
                    onPress={handleOptimizePrompt}
                    isDisabled={isGenerating || isOptimizingPrompt || !promptProvider || isLoadingTextProviders}
                  >
                    {isOptimizingPrompt ? <Spinner size="sm" /> : <Wand2 className="size-4" />}
                  </Button>
                  <Tooltip.Content>
                    <div className="flex items-center gap-2">
                      <span>{hasGeneratedPrompt && prompt.trim() ? t("prompt.optimize") : t("prompt.generate")}</span>
                      {textEstimatedCost != null && textEstimatedCost > 0 && (
                        <span className="inline-flex items-center gap-1 text-warning">
                          <Zap className="size-3" />
                          {textEstimatedCost}
                        </span>
                      )}
                    </div>
                  </Tooltip.Content>
                </Tooltip>
              )}

              <Button
                size="sm"
                isIconOnly
                aria-label={t("action.generate")}
                isDisabled={isGenerating || isOptimizingPrompt || (hasPromptParam ? !prompt.trim() || !selectedProviderId : !selectedProviderId)}
                onPress={handleGenerate}
              >
                {isGenerating ? <Spinner size="sm" /> : <Send className="size-4" />}
              </Button>
            </div>
          </div>
        </Card>
      </div>
      )}
    </Card>
  );
}
