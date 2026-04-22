/**
 * Admin Service
 * Management APIs for admin console.
 */

import { api } from "../client";
import { getAuthToken } from "@/lib/stores/auth-store";
import type {
  AdminPageDTO,
  AgentBillingSessionDTO,
  AgentConfigDTO,
  AgentConfigVersionDTO,
  SaveAgentConfigRequestDTO,
  SaveSystemConfigRequestDTO,
  SystemConfigDTO,
  SystemConfigModuleDTO,
  AgentSkillDTO,
  CreateAgentSkillRequestDTO,
  UpdateAgentSkillRequestDTO,
  AgentSkillListResult,
  AgentToolCatalogDTO,
  AgentToolAccessDTO,
  SaveAgentToolAccessRequestDTO,
  ToolInfoDTO,
  LlmBillingRuleDTO,
  SaveLlmBillingRuleRequestDTO,
} from "../dto/admin.dto";
import type { SkillImportResult } from "../dto/skill.dto";

const AGENT_BASE = "/api/agent";
const AGENT_V1_BASE = "/api/agent";
const AGENT_ADMIN_BASE = "/api/agent/admin";
const AGENT_QUERY_BASE = "/api/agent";
const AGENT_TOOL_ACCESS_BASE = "/api/agent/tool-access";
const AGENT_BILLING_RULES_BASE = "/api/agent/llm-billing-rules";
const SYSTEM_BASE = "/api/system";

function normalizePageResponse<T>(
  raw: AdminPageDTO<T> | T[] | null | undefined,
  fallbackCurrent: number,
  fallbackSize: number
): AdminPageDTO<T> {
  if (!raw) {
    return {
      records: [],
      total: 0,
      current: fallbackCurrent,
      size: fallbackSize,
      pages: 0,
    };
  }

  if (Array.isArray(raw)) {
    const pages = raw.length === 0 ? 0 : Math.ceil(raw.length / fallbackSize);
    return {
      records: raw,
      total: raw.length,
      current: fallbackCurrent,
      size: fallbackSize,
      pages,
    };
  }

  const records = (raw.records ?? (raw as unknown as { list?: T[]; items?: T[] }).list ?? (raw as unknown as { items?: T[] }).items ?? []) as T[];
  const total = Number((raw as unknown as { total?: number }).total ?? records.length);
  const current = Number(
    (raw as unknown as { current?: number; pageNum?: number; page?: number }).current ??
      (raw as unknown as { pageNum?: number }).pageNum ??
      (raw as unknown as { page?: number }).page ??
      fallbackCurrent
  );
  const size = Number(
    (raw as unknown as { size?: number; pageSize?: number }).size ??
      (raw as unknown as { pageSize?: number }).pageSize ??
      fallbackSize
  );
  const pages = Number(
    (raw as unknown as { pages?: number }).pages ?? (size > 0 ? Math.ceil(total / size) : 0)
  );

  return {
    records,
    total,
    current,
    size,
    pages,
  };
}

function paginateRecords<T>(records: T[], current: number, size: number): AdminPageDTO<T> {
  const start = Math.max((current - 1) * size, 0);
  const end = start + size;
  const total = records.length;
  const pages = total === 0 ? 0 : Math.ceil(total / size);
  return {
    records: records.slice(start, end),
    total,
    current,
    size,
    pages,
  };
}

export const adminService = {
  // ============ Agent Billing ============
  getAgentBillingPage: async (params?: {
    current?: number;
    size?: number;
    status?: string;
    userId?: string;
    workspaceId?: string;
  }): Promise<AdminPageDTO<AgentBillingSessionDTO>> => {
    const current = params?.current ?? 1;
    const size = params?.size ?? 10;

    const raw = await api.get<AdminPageDTO<AgentBillingSessionDTO> | AgentBillingSessionDTO[]>(
      `${AGENT_BASE}/billing`,
      {
        params: {
          current,
          page: current,
          size,
          status: params?.status,
          userId: params?.userId,
          workspaceId: params?.workspaceId,
        },
      }
    );

    return normalizePageResponse(raw, current, size);
  },

  /** Get billing session detail by conversationId (user-facing) */
  getBillingSession: (conversationId: string) =>
    api.get<AgentBillingSessionDTO>(`${AGENT_BASE}/billing/sessions/${conversationId}`),

  /** Get user's billing session history (user-facing) */
  getBillingSessionHistory: (limit: number = 20) =>
    api.get<AgentBillingSessionDTO[]>(`${AGENT_BASE}/billing/sessions`, {
      params: { limit },
    }),

  /** Manually settle a billing session (user-facing) */
  settleBillingSession: (conversationId: string) =>
    api.post<AgentBillingSessionDTO>(`${AGENT_BASE}/billing/sessions/${conversationId}/settle`),

  /** Cancel a billing session (user-facing) */
  cancelBillingSession: (conversationId: string) =>
    api.post<null>(`${AGENT_BASE}/billing/sessions/${conversationId}/cancel`),

  // ============ Agent Config ============
  getAgentConfigPage: async (params?: {
    current?: number;
    size?: number;
    agentType?: string;
    enabled?: boolean;
    scope?: string;
    scopeType?: string;
    keyword?: string;
    llmProviderId?: string;
  }): Promise<AdminPageDTO<AgentConfigDTO>> => {
    const current = params?.current ?? 1;
    const size = params?.size ?? 10;

    const raw = await api.get<AdminPageDTO<AgentConfigDTO> | AgentConfigDTO[]>(
      `${AGENT_V1_BASE}/configs`,
      {
        params: {
          current,
          page: current,
          size,
          agentType: params?.agentType,
          enabled: params?.enabled,
          scope: params?.scope,
          scopeType: params?.scopeType,
          keyword: params?.keyword,
          llmProviderId: params?.llmProviderId,
        },
      }
    );

    return normalizePageResponse(raw, current, size);
  },

  getAgentConfigById: (id: string) =>
    api.get<AgentConfigDTO>(`${AGENT_V1_BASE}/configs/${id}`),

  createAgentConfig: (data: SaveAgentConfigRequestDTO) =>
    api.post<AgentConfigDTO>(`${AGENT_V1_BASE}/configs`, data),

  updateAgentConfig: (id: string, data: SaveAgentConfigRequestDTO) =>
    api.put<AgentConfigDTO>(`${AGENT_V1_BASE}/configs/${id}`, data),

  deleteAgentConfig: (id: string) =>
    api.delete<null>(`${AGENT_V1_BASE}/configs/${id}`),

  toggleAgentConfig: (id: string, enabled: boolean) =>
    api.put<null>(`${AGENT_V1_BASE}/configs/${id}/toggle`, undefined, {
      params: { enabled },
    }),

  // ============ System Config ============
  getSystemConfigPage: async (params?: {
    current?: number;
    size?: number;
    configType?: string;
    configKey?: string;
    keyword?: string;
    module?: string;
    workspaceId?: string;
  }): Promise<AdminPageDTO<SystemConfigDTO>> => {
    const current = params?.current ?? 1;
    const size = params?.size ?? 10;

    try {
      const raw = await api.get<AdminPageDTO<SystemConfigDTO> | SystemConfigDTO[]>(
        `${SYSTEM_BASE}/configs`,
        {
          params: {
            current,
            page: current,
            size,
            configType: params?.configType,
            configKey: params?.configKey,
            keyword: params?.keyword,
            module: params?.module,
            workspaceId: params?.workspaceId,
          },
        }
      );
      return normalizePageResponse(raw, current, size);
    } catch {
      let list: SystemConfigDTO[] = [];

      if (params?.workspaceId) {
        list = await api.get<SystemConfigDTO[]>(
          `${SYSTEM_BASE}/configs/workspace/${params.workspaceId}`
        );
      } else if (params?.configType) {
        list = await api.get<SystemConfigDTO[]>(`${SYSTEM_BASE}/configs/by-type`, {
          params: { type: params.configType },
        });
      } else {
        list = await api.get<SystemConfigDTO[]>(`${SYSTEM_BASE}/configs/global`);
      }

      const normalizedKey = params?.configKey?.trim().toLowerCase();
      const filtered = normalizedKey
        ? list.filter((item) => item.configKey?.toLowerCase().includes(normalizedKey))
        : list;

      return paginateRecords(filtered, current, size);
    }
  },

  getSystemConfigById: (id: string) =>
    api.get<SystemConfigDTO>(`${SYSTEM_BASE}/configs/${id}`),

  getGroupedConfigs: () =>
    api.get<SystemConfigModuleDTO[]>(`${SYSTEM_BASE}/configs/grouped`),

  getAiProviderConfigs: () =>
    api.get<SystemConfigDTO[]>(`${SYSTEM_BASE}/configs/by-type`, {
      params: { configType: "AI_PROVIDER", scope: "GLOBAL" },
    }),

  refreshConfigCache: () =>
    api.post<void>(`${SYSTEM_BASE}/configs/refresh-cache`),

  createSystemConfig: (data: SaveSystemConfigRequestDTO) =>
    api.post<SystemConfigDTO>(`${SYSTEM_BASE}/configs`, data),

  updateSystemConfig: (id: string, data: Partial<SaveSystemConfigRequestDTO>) =>
    api.put<SystemConfigDTO>(`${SYSTEM_BASE}/configs/${id}`, data),

  deleteSystemConfig: (id: string) =>
    api.delete<null>(`${SYSTEM_BASE}/configs/${id}`),

  // ============ Agent Skill Management (v2) ============

  getSkillList: async (params?: {
    page?: number;
    size?: number;
    keyword?: string;
  }): Promise<AgentSkillListResult> => {
    const page = params?.page ?? 1;
    const size = params?.size ?? 20;
    // API returns PaginatedResponseDTO shape: { records, total, current, size, pages }
    const raw = await api.get<{
      records?: AgentSkillDTO[];
      items?: AgentSkillDTO[];
      total?: number;
      current?: number;
      page?: number;
      size?: number;
    }>(`${AGENT_ADMIN_BASE}/skills`, {
      params: { page, size, keyword: params?.keyword },
    });
    return {
      total: raw.total ?? 0,
      page: raw.current ?? raw.page ?? page,
      size: raw.size ?? size,
      items: raw.records ?? raw.items ?? [],
    };
  },

  getSkillByName: (name: string) =>
    api.get<AgentSkillDTO>(`${AGENT_ADMIN_BASE}/skills/${name}`),

  createSkill: (data: CreateAgentSkillRequestDTO) =>
    api.post<AgentSkillDTO>(`${AGENT_ADMIN_BASE}/skills`, data),

  updateSkill: (name: string, data: UpdateAgentSkillRequestDTO) =>
    api.put<AgentSkillDTO>(`${AGENT_ADMIN_BASE}/skills/${name}`, data),

  deleteSkill: (name: string) =>
    api.delete<null>(`${AGENT_ADMIN_BASE}/skills/${name}`),

  toggleSkill: (name: string) =>
    api.patch<{ name: string; enabled: boolean }>(`${AGENT_ADMIN_BASE}/skills/${name}/toggle`),

  reloadSkills: () =>
    api.post<string>(`${AGENT_ADMIN_BASE}/skills/reload`),

  /** Import system skills from ZIP — POST /api/agent/admin/skills/import (scope=SYSTEM) */
  importSkills: async (file: File): Promise<SkillImportResult> => {
    const token = getAuthToken();
    const formData = new FormData();
    formData.append("file", file);
    const response = await fetch(`${AGENT_ADMIN_BASE}/skills/import`, {
      method: "POST",
      headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: formData,
    });
    const result = await response.json() as { code: string | number; message?: string; data: SkillImportResult };
    if (!response.ok || (String(result.code) !== "0" && String(result.code) !== "200")) {
      throw new Error(result.message || `Import failed: ${response.statusText}`);
    }
    return result.data;
  },

  // ============ Agent Tool Catalog (query-only) ============

  getToolCatalogPage: async (params?: {
    current?: number;
    size?: number;
    keyword?: string;
    actionType?: string;
    tag?: string;
  }): Promise<AdminPageDTO<AgentToolCatalogDTO>> => {
    const current = params?.current ?? 1;
    const size = params?.size ?? 12;

    const raw = await api.get<AdminPageDTO<AgentToolCatalogDTO> | AgentToolCatalogDTO[]>(
      `${AGENT_QUERY_BASE}/tools`,
      {
        params: {
          current,
          size,
          keyword: params?.keyword,
          actionType: params?.actionType,
          tag: params?.tag,
        },
      }
    );

    return normalizePageResponse(raw, current, size);
  },

  getToolCatalogByToolId: (toolId: string) =>
    api.get<AgentToolCatalogDTO>(`${AGENT_QUERY_BASE}/tools/${toolId}`),

  getSkillVisibleTools: (name: string) =>
    api.get<AgentToolCatalogDTO[]>(`${AGENT_QUERY_BASE}/skills/${name}/tools`),

  // ============ Agent Tool Access (v4.0 — replaces ai-tools) ============

  getToolAccessPage: async (params?: {
    page?: number;
    size?: number;
    agentType?: string;
    toolCategory?: string;
    toolId?: string;
    enabled?: boolean;
  }): Promise<AdminPageDTO<AgentToolAccessDTO>> => {
    const page = params?.page ?? 1;
    const size = params?.size ?? 12;
    const raw = await api.get<AdminPageDTO<AgentToolAccessDTO> | AgentToolAccessDTO[]>(
      AGENT_TOOL_ACCESS_BASE,
      {
        params: {
          current: page,
          size,
          agentType: params?.agentType,
          toolCategory: params?.toolCategory,
          toolId: params?.toolId,
          enabled: params?.enabled,
        },
      }
    );
    return normalizePageResponse(raw, page, size);
  },

  getToolAccessById: (id: string) =>
    api.get<AgentToolAccessDTO>(`${AGENT_TOOL_ACCESS_BASE}/${id}`),

  getToolAccessByAgentType: (agentType: string) =>
    api.get<AgentToolAccessDTO[]>(`${AGENT_TOOL_ACCESS_BASE}/agent/${agentType}`),

  getToolAccessByAgentTypeAndCategory: (agentType: string, category: string) =>
    api.get<AgentToolAccessDTO[]>(`${AGENT_TOOL_ACCESS_BASE}/agent/${agentType}/category/${category}`),

  getToolAccessByToolId: (toolId: string) =>
    api.get<AgentToolAccessDTO[]>(`${AGENT_TOOL_ACCESS_BASE}/tool/${toolId}`),

  getAvailableTools: (agentType: string, userId?: string) =>
    api.get<ToolInfoDTO[]>(`${AGENT_TOOL_ACCESS_BASE}/agent/${agentType}/tools`, {
      params: { userId },
    }),

  createToolAccess: (data: SaveAgentToolAccessRequestDTO) =>
    api.post<AgentToolAccessDTO>(AGENT_TOOL_ACCESS_BASE, data),

  batchCreateToolAccess: (data: SaveAgentToolAccessRequestDTO[]) =>
    api.post<AgentToolAccessDTO[]>(`${AGENT_TOOL_ACCESS_BASE}/batch`, data),

  updateToolAccess: (id: string, data: SaveAgentToolAccessRequestDTO) =>
    api.put<AgentToolAccessDTO>(`${AGENT_TOOL_ACCESS_BASE}/${id}`, data),

  deleteToolAccess: (id: string) =>
    api.delete<null>(`${AGENT_TOOL_ACCESS_BASE}/${id}`),

  toggleToolAccess: (id: string, enabled: boolean) =>
    api.put<null>(`${AGENT_TOOL_ACCESS_BASE}/${id}/toggle`, undefined, {
      params: { enabled },
    }),

  checkToolAccess: (agentType: string, toolCategory: string, toolId: string) =>
    api.get<boolean>(`${AGENT_TOOL_ACCESS_BASE}/check`, {
      params: { agentType, toolCategory, toolId },
    }),

  refreshToolAccessCache: () =>
    api.post<null>(`${AGENT_TOOL_ACCESS_BASE}/cache/refresh`),

  /** @deprecated Use getToolAccessPage instead */
  getAiToolConfigPage: async (params?: {
    page?: number;
    size?: number;
    agentType?: string;
    enabled?: boolean;
  }): Promise<AdminPageDTO<AgentToolAccessDTO>> => {
    return adminService.getToolAccessPage(params);
  },

  /** @deprecated Use getToolAccessById instead */
  getAiToolConfigById: (id: string) =>
    api.get<AgentToolAccessDTO>(`${AGENT_TOOL_ACCESS_BASE}/${id}`),

  /** @deprecated Use createToolAccess instead */
  createAiToolConfig: (data: SaveAgentToolAccessRequestDTO) =>
    api.post<AgentToolAccessDTO>(AGENT_TOOL_ACCESS_BASE, data),

  /** @deprecated Use updateToolAccess instead */
  updateAiToolConfig: (id: string, data: SaveAgentToolAccessRequestDTO) =>
    api.put<AgentToolAccessDTO>(`${AGENT_TOOL_ACCESS_BASE}/${id}`, data),

  /** @deprecated Use deleteToolAccess instead */
  deleteAiToolConfig: (id: string) =>
    api.delete<null>(`${AGENT_TOOL_ACCESS_BASE}/${id}`),

  /** @deprecated Use toggleToolAccess instead */
  toggleAiToolConfig: (id: string, enabled: boolean) =>
    api.put<null>(`${AGENT_TOOL_ACCESS_BASE}/${id}/toggle`, undefined, {
      params: { enabled },
    }),

  // ============ Agent Runtime ============

  /** Force-rebuild all agents + evict all LLM caches on agent-v2 side */
  reloadAgentConfigs: () =>
    api.post<null>(`${AGENT_BASE}/configs/reload`),

  // ============ LLM Billing Rules (v4.0) ============

  getLlmBillingRulePage: async (params?: {
    current?: number;
    size?: number;
    llmProviderId?: string;
    enabled?: boolean;
  }): Promise<AdminPageDTO<LlmBillingRuleDTO>> => {
    const current = params?.current ?? 1;
    const size = params?.size ?? 10;
    const raw = await api.get<AdminPageDTO<LlmBillingRuleDTO> | LlmBillingRuleDTO[]>(
      AGENT_BILLING_RULES_BASE,
      {
        params: { current, size, llmProviderId: params?.llmProviderId, enabled: params?.enabled },
      }
    );
    return normalizePageResponse(raw, current, size);
  },

  getEffectiveLlmBillingRules: () =>
    api.get<LlmBillingRuleDTO[]>(`${AGENT_BILLING_RULES_BASE}/effective`),

  getLlmBillingRuleById: (id: string) =>
    api.get<LlmBillingRuleDTO>(`${AGENT_BILLING_RULES_BASE}/${id}`),

  getLlmBillingRulesByProvider: (llmProviderId: string) =>
    api.get<LlmBillingRuleDTO[]>(`${AGENT_BILLING_RULES_BASE}/provider/${llmProviderId}`),

  getEffectiveLlmBillingRuleByProvider: (llmProviderId: string) =>
    api.get<LlmBillingRuleDTO | null>(`${AGENT_BILLING_RULES_BASE}/provider/${llmProviderId}/effective`),

  createLlmBillingRule: (data: SaveLlmBillingRuleRequestDTO) =>
    api.post<LlmBillingRuleDTO>(AGENT_BILLING_RULES_BASE, data),

  updateLlmBillingRule: (id: string, data: SaveLlmBillingRuleRequestDTO) =>
    api.put<LlmBillingRuleDTO>(`${AGENT_BILLING_RULES_BASE}/${id}`, data),

  deleteLlmBillingRule: (id: string) =>
    api.delete<null>(`${AGENT_BILLING_RULES_BASE}/${id}`),

  toggleLlmBillingRule: (id: string, enabled: boolean) =>
    api.put<null>(`${AGENT_BILLING_RULES_BASE}/${id}/toggle`, undefined, {
      params: { enabled },
    }),

  refreshLlmBillingRuleCache: (llmProviderId?: string) =>
    api.post<null>(`${AGENT_BILLING_RULES_BASE}/cache/refresh`, undefined, {
      params: { llmProviderId },
    }),

  // ============ Agent Config — Additional Endpoints (v4.0) ============

  getEnabledAgentConfigs: () =>
    api.get<AgentConfigDTO[]>(`${AGENT_V1_BASE}/configs/enabled`),

  getAgentConfigByType: (agentType: string) =>
    api.get<AgentConfigDTO>(`${AGENT_V1_BASE}/configs/type/${agentType}`),

  getAgentConfigPrompt: (agentType: string) =>
    api.get<string>(`${AGENT_V1_BASE}/configs/type/${agentType}/prompt`),

  getAgentConfigVersions: (id: string) =>
    api.get<AgentConfigVersionDTO[]>(`${AGENT_V1_BASE}/configs/${id}/versions`),

  rollbackAgentConfig: (id: string, version: number) =>
    api.post<AgentConfigDTO>(`${AGENT_V1_BASE}/configs/${id}/rollback/${version}`),
};

export default adminService;
