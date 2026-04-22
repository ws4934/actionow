/**
 * Admin DTOs
 * For admin console pages: agent billing/config and system configs.
 */

import type { PaginatedResponseDTO } from "./workspace.dto";

export interface AgentBillingSessionDTO {
  id: string;
  conversationId: string;
  workspaceId?: string;
  userId?: string;
  modelProvider?: string;
  modelId?: string;
  modelName?: string;
  llmProvider?: string;
  llmModelId?: string;
  frozenAmount?: number;
  settledAmount?: number;
  settledAt?: string;
  llmCost?: number;
  aiToolCalls?: number;
  aiToolCost?: number;
  totalCost?: number;
  totalInputTokens?: number;
  totalOutputTokens?: number;
  /** Total thinking/reasoning tokens (model internal) */
  totalThoughtTokens?: number;
  /** Total cached tokens (reused from cache) */
  totalCachedTokens?: number;
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
  creditsConsumed?: number;
  creditsFrozen?: number;
  /** Pricing snapshot at creation time */
  pricingSnapshot?: Record<string, unknown>;
  /** Settlement error reason */
  settleError?: string;
  status?: string;
  lastActivityAt?: string;
  startedAt?: string;
  completedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface AgentConfigDTO {
  id: string;
  agentType: string;
  agentName: string;
  scope?: "SYSTEM" | "WORKSPACE" | string;
  promptContent?: string;
  includes?: string[];
  llmProviderId?: string;
  llmProvider?: string;
  temperature?: number;
  maxOutputTokens?: number;
  contextWindow?: number;
  enabled?: boolean;
  isSystem?: boolean;
  isCoordinator?: boolean;
  subAgentTypes?: string[];
  standaloneEnabled?: boolean;
  defaultSkillNames?: string[];
  allowedSkillNames?: string[];
  skillLoadMode?: "ALL_ENABLED" | "DEFAULT_ONLY" | "REQUEST_SCOPED" | "DISABLED" | string;
  executionMode?: "CHAT" | "MISSION" | "BOTH" | string;
  iconUrl?: string;
  tags?: string[];
  currentVersion?: number;
  description?: string;
  workspaceId?: string;
  creatorId?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface SaveAgentConfigRequestDTO {
  agentType: string;
  agentName: string;
  scope?: "SYSTEM" | "WORKSPACE" | string;
  promptContent?: string;
  includes?: string[];
  llmProviderId?: string;
  temperature?: number;
  maxOutputTokens?: number;
  contextWindow?: number;
  enabled?: boolean;
  isSystem?: boolean;
  isCoordinator?: boolean;
  subAgentTypes?: string[];
  standaloneEnabled?: boolean;
  defaultSkillNames?: string[];
  allowedSkillNames?: string[];
  skillLoadMode?: "ALL_ENABLED" | "DEFAULT_ONLY" | "REQUEST_SCOPED" | "DISABLED" | string;
  executionMode?: "CHAT" | "MISSION" | "BOTH" | string;
  iconUrl?: string;
  tags?: string[];
  description?: string;
  workspaceId?: string;
  changeSummary?: string;
}

export type SystemConfigType = "SYSTEM" | "FEATURE" | "LIMIT" | "AI_PROVIDER";
export type SystemConfigScope = "GLOBAL" | "WORKSPACE" | "USER";

export interface SystemConfigDTO {
  id: string;
  configKey: string;
  configValue: string;
  configType: SystemConfigType | string;
  scope?: SystemConfigScope | string;
  scopeId?: string | null;
  workspaceId?: string;
  description?: string | null;
  defaultValue?: string | null;
  valueType?: "STRING" | "BOOLEAN" | "INTEGER" | "JSON" | string;
  validation?: unknown;
  enabled?: boolean;
  sensitive?: boolean;
  module?: string;
  groupName?: string;
  displayName?: string | null;
  sortOrder?: number;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface SaveSystemConfigRequestDTO {
  configKey: string;
  configValue: string;
  configType?: SystemConfigType | string;
  scope?: SystemConfigScope | string;
  scopeId?: string;
  workspaceId?: string;
  description?: string;
  defaultValue?: string;
  valueType?: string;
  module?: string;
  groupName?: string;
  displayName?: string;
  validation?: unknown;
  enabled?: boolean;
  sensitive?: boolean;
  sortOrder?: number;
}

export interface SystemConfigGroupDTO {
  groupName: string;
  configs: SystemConfigDTO[];
}

export interface SystemConfigModuleDTO {
  module: string;
  moduleDisplayName: string;
  groups: SystemConfigGroupDTO[];
}

export type AdminPageDTO<T> = PaginatedResponseDTO<T>;

// ============ Agent Skill DTOs (v2) ============

export interface AgentSkillDTO {
  id: string;
  name: string;
  displayName?: string;
  description: string;
  /** Full system prompt content — only present in detail response, omitted in list */
  content?: string;
  groupedToolIds?: string[];
  outputSchema?: Record<string, unknown> | null;
  /** Tags for classification and search */
  tags?: string[];
  /** Reference materials list, each: {title, url, description} */
  references?: Record<string, unknown>[];
  /** Usage examples list, each: {title, content, input, output} */
  examples?: Record<string, unknown>[];
  enabled: boolean;
  version: number;
  scope?: "SYSTEM" | "WORKSPACE";
  workspaceId?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateAgentSkillRequestDTO {
  /** Identifier — unique, pattern: ^[a-z][a-z0-9_]{1,63}$ */
  name: string;
  displayName?: string;
  description: string;
  /** Full System Prompt content */
  content: string;
  groupedToolIds?: string[];
  outputSchema?: Record<string, unknown> | null;
  /** Tags for classification and search */
  tags?: string[];
  /** Reference materials list, each: {title, url, description} */
  references?: Record<string, unknown>[];
  /** Usage examples list, each: {title, content, input, output} */
  examples?: Record<string, unknown>[];
  /** Scope: SYSTEM or WORKSPACE */
  scope?: "SYSTEM" | "WORKSPACE";
  /** Workspace ID when scope=WORKSPACE */
  workspaceId?: string;
}

export interface UpdateAgentSkillRequestDTO {
  displayName?: string;
  description?: string;
  content?: string;
  groupedToolIds?: string[];
  outputSchema?: Record<string, unknown> | null;
  /** Tags for classification and search */
  tags?: string[];
  /** Reference materials list, each: {title, url, description} */
  references?: Record<string, unknown>[];
  /** Usage examples list, each: {title, content, input, output} */
  examples?: Record<string, unknown>[];
}

export interface AgentSkillListResult {
  total: number;
  page: number;
  size: number;
  items: AgentSkillDTO[];
}

// ============ Agent Tool Catalog DTOs (query-only) ============

export interface AgentToolCatalogParamDTO {
  name?: string;
  type?: string;
  description?: string;
  required?: boolean;
  defaultValue?: unknown;
  example?: unknown;
  enumValues?: unknown[];
  [key: string]: unknown;
}

export interface AgentToolCatalogOutputDTO {
  type?: string;
  description?: string | null;
  schemaClass?: string | null;
  schemaJson?: string | null;
  example?: unknown;
  [key: string]: unknown;
}

export interface AgentToolCatalogDTO {
  id?: string;
  toolId?: string;
  name?: string;
  title?: string;
  displayName?: string;
  toolName?: string;
  toolClass?: string;
  toolMethod?: string;
  description?: string;
  summary?: string;
  toolDescription?: string;
  purpose?: string | null;
  actionType?: string;
  category?: string;
  toolCategory?: string;
  sourceType?: string;
  accessMode?: "FULL" | "READONLY" | "DISABLED" | string;
  callbackName?: string;
  tag?: string;
  tags?: string[];
  params?: AgentToolCatalogParamDTO[];
  inputSchema?: AgentToolCatalogParamDTO[] | Record<string, unknown> | string | null;
  output?: AgentToolCatalogOutputDTO | null;
  outputSchema?: Record<string, unknown> | string | null;
  returnSchema?: Record<string, unknown> | string | null;
  resultSchema?: Record<string, unknown> | string | null;
  returnType?: string;
  enabled?: boolean;
  dailyQuota?: number;
  usedToday?: number | null;
  available?: boolean | null;
  usageNotes?: string[];
  errorCases?: string[];
  exampleInput?: unknown;
  exampleOutput?: unknown;
  skillNames?: string[];
  metadata?: Record<string, unknown> | null;
  createdAt?: string;
  updatedAt?: string;
  [key: string]: unknown;
}

// ============ Agent Tool Access DTOs (v4.0 — replaces AgentAiToolConfig) ============

export interface AgentToolAccessDTO {
  id: string;
  agentType: string;
  toolCategory: "PROJECT" | "AI" | string;
  toolId: string;
  toolName?: string;
  toolDescription?: string;
  accessMode?: "FULL" | "READONLY" | "DISABLED" | string;
  dailyQuota?: number;
  priority?: number;
  enabled?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface SaveAgentToolAccessRequestDTO {
  agentType: string;
  toolCategory: string;
  toolId: string;
  toolName?: string;
  toolDescription?: string;
  accessMode?: string;
  dailyQuota?: number;
  priority?: number;
  enabled?: boolean;
}

export interface ToolParamDTO {
  name: string;
  type: string;
  description?: string;
  required?: boolean;
  defaultValue?: string;
  example?: string;
}

export interface ToolInfoDTO {
  toolId: string;
  toolClass?: string;
  toolMethod?: string;
  toolName: string;
  description?: string;
  category: "PROJECT" | "AI" | string;
  accessMode?: "FULL" | "READONLY" | "DISABLED" | string;
  params?: ToolParamDTO[];
  returnType?: string;
  enabled?: boolean;
  dailyQuota?: number;
  usedToday?: number;
  available?: boolean;
  metadata?: Record<string, unknown>;
}

/** @deprecated Use AgentToolAccessDTO instead — kept for backwards compatibility during migration */
export type AgentAiToolConfigDTO = AgentToolAccessDTO;
/** @deprecated Use SaveAgentToolAccessRequestDTO instead */
export type SaveAgentAiToolConfigRequestDTO = SaveAgentToolAccessRequestDTO;

// ============ LLM Billing Rule DTOs (v4.0) ============

export interface LlmBillingRuleDTO {
  id: string;
  llmProviderId: string;
  inputPrice: number;
  outputPrice: number;
  effectiveFrom?: string;
  effectiveTo?: string;
  rateLimitRpm?: number;
  rateLimitTpm?: number;
  enabled?: boolean;
  priority?: number;
  description?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface SaveLlmBillingRuleRequestDTO {
  llmProviderId: string;
  inputPrice: number;
  outputPrice: number;
  effectiveFrom?: string;
  effectiveTo?: string;
  rateLimitRpm?: number;
  rateLimitTpm?: number;
  enabled?: boolean;
  priority?: number;
  description?: string;
}

// ============ Agent Config Version DTO (v4.0) ============

export interface AgentConfigVersionDTO {
  id: string;
  agentConfigId: string;
  versionNumber: number;
  promptContent?: string;
  includes?: string[];
  llmProviderId?: string;
  defaultSkillNames?: string[];
  allowedSkillNames?: string[];
  skillLoadMode?: "ALL_ENABLED" | "DEFAULT_ONLY" | "REQUEST_SCOPED" | "DISABLED" | string;
  executionMode?: "CHAT" | "MISSION" | "BOTH" | string;
  isCoordinator?: boolean;
  subAgentTypes?: string[];
  standaloneEnabled?: boolean;
  changeSummary?: string;
  createdAt?: string;
}
