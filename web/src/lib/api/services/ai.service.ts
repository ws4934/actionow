/**
 * AI Service
 * Handles AI generation via Task API endpoints
 */

import { api } from "../client";
import { getAuthToken } from "@/lib/stores/auth-store";
import type {
  ProviderType,
  ResponseMode,
  AvailableProviderDTO,
  InputSchemaDTO,
  EntityGenerationRequestDTO,
  EntityGenerationResponseDTO,
  StreamGenerationRequestDTO,
  StreamGenerationEvent,
  ExecutionStatusDTO,
  ExecutionResultDTO,
  ValidationResult,
} from "../dto/ai.dto";

const TASK_BASE = "/api/tasks";

export const aiService = {
  // ============ Model Provider (via Task API) ============

  /** Get available providers by type */
  getProvidersByType: (type: ProviderType) =>
    api.get<AvailableProviderDTO[]>(`${TASK_BASE}/ai/providers`, {
      params: { providerType: type },
    }),

  /** Get input schema from provider (provider already includes schema) */
  getInputSchema: (provider: AvailableProviderDTO): InputSchemaDTO => ({
    params: provider.inputSchema || [],
    groups: provider.inputGroups || [],
    exclusiveGroups: provider.exclusiveGroups || [],
  }),

  /** Validate input data (client-side validation) */
  validateInput: (_providerId: string, inputs: Record<string, unknown>): Promise<ValidationResult> => {
    // Simple client-side validation
    const errors: ValidationResult["errors"] = [];

    // Check for empty prompt
    if (!inputs.prompt || (typeof inputs.prompt === "string" && !inputs.prompt.trim())) {
      errors.push({
        field: "prompt",
        message: "提示词不能为空",
        code: "REQUIRED",
      });
    }

    return Promise.resolve({
      valid: errors.length === 0,
      errors,
    });
  },

  /** Merge user inputs with defaults */
  mergeDefaults: (_providerId: string, inputs?: Record<string, unknown>): Promise<Record<string, unknown>> => {
    // Just return inputs as-is for now
    return Promise.resolve(inputs || {});
  },

  // ============ Entity Generation (Recommended) ============

  /** Submit entity generation task */
  submitEntityGeneration: (data: EntityGenerationRequestDTO) =>
    api.post<EntityGenerationResponseDTO>(`${TASK_BASE}/entity-generation`, data),

  /** Get entity generation status */
  getEntityGenerationStatus: (assetId: string) =>
    api.get<{
      success: boolean;
      assetId: string;
      generationStatus: string;
      taskId?: string;
      taskStatus?: string;
      taskProgress?: number;
      taskOutput?: Record<string, unknown>;
      retryCount: number;
      errorMessage?: string;
      failedAt?: string;
    }>(`${TASK_BASE}/entity-generation/${assetId}/status`),

  /** Retry entity generation */
  retryEntityGeneration: (data: {
    assetId: string;
    prompt?: string;
    negativePrompt?: string;
    providerId?: string;
    params?: Record<string, unknown>;
    priority?: number;
  }) =>
    api.post<EntityGenerationResponseDTO>(`${TASK_BASE}/entity-generation/retry`, data),

  // ============ Direct AI Generate (for TEXT providers) ============

  /** Submit AI generation task (direct, without auto-creating asset) */
  submitGenerate: (data: {
    providerId: string;
    generationType: ProviderType;
    params: Record<string, unknown>;
    assetId?: string;
    scriptId?: string;
    responseMode?: ResponseMode;
  }) =>
    api.post<{
      taskId: string;
      assetId?: string;
      providerId: string;
      status: string;
      creditCost: number;
    }>(`${TASK_BASE}/ai/generate`, data),

  // ============ Stream Generation (SSE) ============

  /** Execute stream generation via SSE */
  executeStream: (
    data: StreamGenerationRequestDTO,
    callbacks: {
      onStarted?: (taskId: string, executionId?: string) => void;
      onProgress?: (progress: number, currentStep?: string) => void;
      onTextChunk?: (textDelta: string) => void;
      onFinished?: (outputs: Record<string, unknown>) => void;
      onError?: (errorCode: string, errorMessage: string) => void;
    }
  ): { abort: () => void } => {
    const controller = new AbortController();

    const token = getAuthToken();

    const headers: Record<string, string> = {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
    };

    if (token) headers["Authorization"] = `Bearer ${token}`;

    const streamUrl = `${TASK_BASE}/ai/stream`;

    fetch(streamUrl, {
      method: "POST",
      headers,
      body: JSON.stringify(data),
      signal: controller.signal,
    })
      .then(async (response) => {
        if (!response.ok) {
          callbacks.onError?.("HTTP_ERROR", `HTTP ${response.status}: ${response.statusText}`);
          return;
        }

        const reader = response.body?.getReader();
        if (!reader) {
          callbacks.onError?.("NO_BODY", "No response body");
          return;
        }

        const decoder = new TextDecoder();
        let buffer = "";
        let currentEventType = "";

        const processEvent = (event: StreamGenerationEvent) => {
          const eventType = event.eventType || currentEventType;

          switch (eventType) {
            case "started":
            case "STARTED":
              callbacks.onStarted?.(event.taskId || "", event.executionId);
              break;
            case "progress":
            case "PROGRESS":
              callbacks.onProgress?.(event.progress || 0, event.currentStep);
              break;
            case "text_chunk":
            case "TEXT_CHUNK":
            case "text_delta":
            case "TEXT_DELTA":
              if (event.textDelta) {
                callbacks.onTextChunk?.(event.textDelta);
              }
              break;
            case "workflow_finished":
            case "WORKFLOW_FINISHED":
              if (event.textDelta) {
                callbacks.onTextChunk?.(event.textDelta);
              }
              callbacks.onFinished?.(event.outputs || {
                textAccumulated: event.textAccumulated,
                textDelta: event.textDelta,
                progress: event.progress,
              });
              break;
            case "complete":
            case "COMPLETE":
              callbacks.onFinished?.(event.outputs || {});
              break;
            case "error":
            case "ERROR":
              callbacks.onError?.(event.errorCode || "UNKNOWN", event.errorMessage || "Unknown error");
              break;
            case "connect":
            case "connected":
            case "heartbeat":
            case "done":
            case "completed":
              // Ignore system events
              break;
          }
        };

        const processLine = (line: string) => {
          const trimmedLine = line.trim();
          if (!trimmedLine) return;

          if (trimmedLine.startsWith("event:")) {
            currentEventType = trimmedLine.slice(6).trim();
            return;
          }

          if (trimmedLine.startsWith("id:")) {
            return;
          }

          let jsonStr = "";

          if (trimmedLine.startsWith("data:")) {
            // Support both "data: {...}" and "data:{...}" SSE formats.
            jsonStr = trimmedLine.slice(5).trim();
          } else if (trimmedLine.startsWith("{") && trimmedLine.endsWith("}")) {
            jsonStr = trimmedLine;
          } else {
            const jsonMatch = trimmedLine.match(/\{.*\}$/);
            if (jsonMatch) {
              jsonStr = jsonMatch[0];
            }
          }

          if (!jsonStr) return;

          try {
            const event = JSON.parse(jsonStr) as StreamGenerationEvent;
            processEvent(event);
          } catch {
            // Ignore parse errors for incomplete JSON
          }
        };

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split("\n");
          buffer = lines.pop() || "";

          for (const line of lines) {
            processLine(line);
          }
        }

        if (buffer.trim()) {
          for (const line of buffer.split("\n")) {
            processLine(line);
          }
        }
      })
      .catch((error) => {
        if (error.name !== "AbortError") {
          callbacks.onError?.("STREAM_ERROR", error.message || "Stream error");
        }
      });

    return {
      abort: () => controller.abort(),
    };
  },

  // ============ Cost Estimation ============

  /** Estimate credit cost for a provider with given params */
  estimateCost: (providerId: string, params?: Record<string, unknown>) =>
    api.post<{
      baseCost: number;
      finalCost: number;
      discountRate: number;
      discountDescription: string | null;
      source: string;
      breakdown: Record<string, unknown> | null;
    }>(`${TASK_BASE}/ai/estimate-cost`, params || {}, {
      params: { providerId },
    }),

  // ============ Task Management ============

  /** Cancel AI generation task */
  cancelTask: (taskId: string) =>
    api.post<null>(`${TASK_BASE}/${taskId}/cancel-ai`),

  /** Get task detail */
  getTask: (taskId: string) =>
    api.get<{
      id: string;
      workspaceId: string;
      type: string;
      title: string;
      status: string;
      progress: number;
      inputParams: Record<string, unknown>;
      outputResult?: Record<string, unknown>;
      errorMessage?: string;
      startedAt?: string;
      completedAt?: string;
      createdAt: string;
      updatedAt: string;
    }>(`${TASK_BASE}/${taskId}`),

  /** Get task queue position */
  getQueuePosition: (taskId: string) =>
    api.get<{
      taskId: string;
      position: number;
      inQueue: boolean;
    }>(`${TASK_BASE}/${taskId}/queue-position`),

  // ============ Utility Methods ============

  /**
   * Get best response mode for a provider
   * Priority: BLOCKING > POLLING > CALLBACK > STREAMING
   */
  getBestResponseMode: (provider: AvailableProviderDTO): ResponseMode => {
    if (provider.supportsBlocking) return "BLOCKING";
    if (provider.supportsPolling) return "POLLING";
    if (provider.supportsCallback) return "CALLBACK";
    if (provider.supportsStreaming) return "STREAMING";
    return "POLLING"; // fallback to most common mode
  },

  /**
   * Prefer submitting long-running work asynchronously so callers can return
   * immediately after the task is created.
   */
  getPreferredSubmissionMode: (provider: AvailableProviderDTO): ResponseMode => {
    if (provider.supportsPolling) return "POLLING";
    if (provider.supportsCallback) return "CALLBACK";
    if (provider.supportsBlocking) return "BLOCKING";
    if (provider.supportsStreaming) return "STREAMING";
    return "POLLING";
  },

  /** Check if provider supports a specific mode */
  supportsMode: (provider: AvailableProviderDTO, mode: ResponseMode): boolean => {
    switch (mode) {
      case "STREAMING":
        return provider.supportsStreaming ?? false;
      case "BLOCKING":
        return provider.supportsBlocking ?? false;
      case "POLLING":
        return provider.supportsPolling ?? false;
      case "CALLBACK":
        return provider.supportsCallback ?? false;
      default:
        return false;
    }
  },

  // Legacy methods for backward compatibility
  execute: async (): Promise<ExecutionResultDTO> => {
    // Use entity generation API instead
    throw new Error("Use submitEntityGeneration instead");
  },

  getExecutionStatus: async (executionId: string): Promise<ExecutionStatusDTO> => {
    const result = await api.get<{
      id: string;
      status: string;
      progress: number;
    }>(`${TASK_BASE}/${executionId}`);

    return {
      executionId,
      status: result.status as ExecutionStatusDTO["status"],
      completed: result.status === "COMPLETED" || result.status === "FAILED" || result.status === "CANCELLED",
      progress: result.progress,
    };
  },

  cancelExecution: (executionId: string) =>
    api.post<null>(`${TASK_BASE}/${executionId}/cancel-ai`),

  pollExecution: async (
    taskId: string,
    onProgress?: (status: ExecutionStatusDTO) => void,
    options?: {
      intervalMs?: number;
      maxAttempts?: number;
    }
  ): Promise<ExecutionResultDTO> => {
    const intervalMs = options?.intervalMs || 3000;
    const maxAttempts = options?.maxAttempts || 100;
    let attempts = 0;

    return new Promise((resolve, reject) => {
      const poll = async () => {
        try {
          attempts++;
          const task = await aiService.getTask(taskId);

          const status: ExecutionStatusDTO = {
            executionId: taskId,
            status: task.status as ExecutionStatusDTO["status"],
            completed: ["COMPLETED", "FAILED", "CANCELLED"].includes(task.status),
            progress: task.progress,
          };

          onProgress?.(status);

          if (task.status === "COMPLETED") {
            resolve({
              success: true,
              executionId: taskId,
              status: "SUCCEEDED",
              responseMode: "POLLING",
              fileUrl: task.outputResult?.fileUrl as string | undefined,
              outputs: task.outputResult,
            });
            return;
          }

          if (task.status === "FAILED" || task.status === "CANCELLED") {
            reject(new Error(task.errorMessage || `Task ${task.status.toLowerCase()}`));
            return;
          }

          if (attempts >= maxAttempts) {
            reject(new Error("Max polling attempts reached"));
            return;
          }

          setTimeout(poll, intervalMs);
        } catch (error) {
          reject(error);
        }
      };

      poll();
    });
  },
};

export default aiService;
