/**
 * AI Generation DTOs (Task API)
 */

import type { EntityType, RelationType } from "./project.dto";

// Provider types for AI generation
export type ProviderType = "IMAGE" | "VIDEO" | "AUDIO" | "TEXT";

// Response modes
export type ResponseMode = "BLOCKING" | "STREAMING" | "CALLBACK" | "POLLING";

// Execution status
export type ExecutionStatus =
  | "PENDING"
  | "QUEUED"
  | "RUNNING"
  | "COMPLETED"
  | "SUCCEEDED"
  | "FAILED"
  | "CANCELLED"
  | "TIMEOUT";

// Note: EntityType and RelationType are imported from project.dto.ts
// to avoid duplicate exports

// Input parameter types (includes API aliases)
export type InputParamType =
  // Basic types
  | "TEXT"
  | "TEXTAREA"
  | "NUMBER"
  | "BOOLEAN"
  | "SELECT"
  | "STYLE"
  // API aliases (backend may return these instead)
  | "STRING"
  | "INTEGER"
  // Single file types
  | "IMAGE"
  | "VIDEO"
  | "AUDIO"
  | "DOCUMENT"
  // List types
  | "TEXT_LIST"
  | "NUMBER_LIST"
  | "IMAGE_LIST"
  | "VIDEO_LIST"
  | "AUDIO_LIST"
  | "DOCUMENT_LIST";

// Validation rules for input parameters
export interface InputValidation {
  minLength?: number;
  maxLength?: number;
  min?: number;
  max?: number;
  pattern?: string;
  required?: boolean;
}

// Depends on configuration for exclusive groups
export interface DependsOn {
  exclusiveGroup: string;
  selectedOption: string;
}

// File config for file upload fields
export interface FileConfig {
  accept?: string;
  maxSize?: number;
  maxSizeLabel?: string;
  maxCount?: number;
  uploadTip?: string;
  inputFormat?: "URL" | "BASE64";
  includeDataUriPrefix?: boolean;
}

// Input parameter definition
export interface InputParamDefinition {
  name: string;
  type: InputParamType;
  label: string;
  labelEn?: string;
  description?: string;
  placeholder?: string;
  required?: boolean;
  default?: unknown;
  defaultValue?: unknown;
  // For SELECT type - can use either enum or options
  enum?: string[];
  options?: Array<{
    value: string;
    label: string;
  }>;
  // For NUMBER type
  min?: number;
  max?: number;
  // For TEXT/TEXTAREA type
  maxLength?: number;
  // For file types
  fileConfig?: FileConfig;
  // Grouping
  group?: string;
  order?: number;
  // Component customization
  component?: string;
  componentProps?: Record<string, unknown>;
  // Validation
  validation?: InputValidation;
  // Conditional display
  dependsOn?: DependsOn;
}

// Input group definition
export interface InputGroupDefinition {
  name: string;
  label: string;
  labelEn?: string;
  order?: number;
  collapsed?: boolean;
  fields?: string[];
}

// Exclusive group option
export interface ExclusiveGroupOption {
  value: string;
  label: string;
  params: string[];
}

// Exclusive group definition
export interface ExclusiveGroupDefinition {
  name: string;
  label?: string;
  description?: string;
  defaultOption?: string;
  component?: string;
  group?: string;
  options: ExclusiveGroupOption[];
}

// Input schema DTO
export interface InputSchemaDTO {
  params: InputParamDefinition[];
  groups: InputGroupDefinition[];
  exclusiveGroups: ExclusiveGroupDefinition[];
}

// Available provider DTO (from GET /api/tasks/ai/providers)
export interface AvailableProviderDTO {
  id: string;
  name: string;
  description: string | null;
  providerType: ProviderType;
  iconUrl: string | null;
  creditCost: number;
  priority: number;
  supportsStreaming?: boolean;
  supportsBlocking?: boolean;
  supportsCallback?: boolean;
  supportsPolling?: boolean;
  inputSchema: InputParamDefinition[];
  inputGroups: InputGroupDefinition[];
  exclusiveGroups: ExclusiveGroupDefinition[];
}

// Entity generation request DTO
export interface EntityGenerationRequestDTO {
  entityType: EntityType;
  entityId: string;
  generationType: ProviderType;
  prompt: string;
  negativePrompt?: string;
  providerId?: string;
  params?: Record<string, unknown>;
  assetName?: string;
  relationType?: RelationType;
  scriptId?: string;
  priority?: number;
  referenceAssetIds?: string[];
  responseMode?: ResponseMode;
}

// Entity generation response DTO
export interface EntityGenerationResponseDTO {
  assetId: string;
  relationId: string | null;
  taskId: string;
  taskStatus: string;
  providerId: string;
  creditCost: number;
  generationParams?: Record<string, unknown>;
  success: boolean;
  errorMessage?: string;
  // For TEXT generation (BLOCKING mode)
  textContent?: string;
  outputs?: Record<string, unknown>;
}

// Stream generation request DTO
export interface StreamGenerationRequestDTO {
  providerId: string;
  prompt?: string;
  negativePrompt?: string;
  generationType?: ProviderType;
  params?: Record<string, unknown>;
  assetId?: string;
  scriptId?: string;
  businessId?: string;
  businessType?: string;
  responseMode?: ResponseMode;
}

// Stream generation event (SSE)
export interface StreamGenerationEvent {
  eventType?: string;
  taskId?: string;
  executionId?: string;
  progress?: number;
  currentStep?: string;
  textDelta?: string;
  textAccumulated?: string;
  status?: string;
  outputs?: Record<string, unknown>;
  errorCode?: string;
  errorMessage?: string;
}

// Generated asset info
export interface GeneratedAssetDTO {
  assetType: ProviderType;
  url: string;
  base64?: boolean;
  mimeType: string;
  fileName?: string;
  fileSize?: number;
  width?: number;
  height?: number;
  duration?: number;
  metadata?: Record<string, unknown>;
}

// Execution result DTO
export interface ExecutionResultDTO {
  success: boolean;
  executionId: string;
  externalRunId?: string;
  externalTaskId?: string;
  status: ExecutionStatus;
  responseMode: ResponseMode;
  fileUrl?: string;
  sourceUrl?: string;
  fileKey?: string;
  thumbnailUrl?: string;
  mimeType?: string;
  fileSize?: number;
  metaInfo?: Record<string, unknown>;
  outputs?: Record<string, unknown>;
  assets?: GeneratedAssetDTO[];
  creditCost?: number;
  elapsedTime?: number;
  errorCode?: string;
  errorMessage?: string;
}

// Execution status DTO (for polling)
export interface ExecutionStatusDTO {
  executionId: string;
  status: ExecutionStatus;
  completed: boolean;
  message?: string;
  progress?: number;
  currentStep?: string;
}

// Input validation result
export interface ValidationResult {
  valid: boolean;
  errors: Array<{
    field: string;
    message: string;
    code: string;
  }>;
}

// Paginated response
export interface PaginatedResponse<T> {
  current: number;
  size: number;
  total: number;
  records: T[];
}

// Legacy exports for backward compatibility
export type ModelProviderListDTO = AvailableProviderDTO;
export type ModelProviderDetailDTO = AvailableProviderDTO;
