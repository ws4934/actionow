import type { AgentToolCatalogDTO, AgentToolCatalogParamDTO } from "@/lib/api/dto";

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function firstNonEmpty(...values: Array<unknown>): string {
  for (const value of values) {
    if (typeof value === "string" && value.trim()) {
      return value.trim();
    }
  }
  return "";
}

function toBoolean(value: unknown): boolean | undefined {
  if (typeof value === "boolean") return value;
  return undefined;
}

export function getToolCatalogIdentifier(tool?: Partial<AgentToolCatalogDTO> | null): string {
  return firstNonEmpty(tool?.toolId, tool?.id, tool?.name);
}

export function getToolCatalogTitle(tool?: Partial<AgentToolCatalogDTO> | null): string {
  return (
    firstNonEmpty(tool?.displayName, tool?.toolName, tool?.name, tool?.title, tool?.toolId, tool?.id) ||
    "-"
  );
}

export function getToolCatalogDescription(tool?: Partial<AgentToolCatalogDTO> | null): string {
  return firstNonEmpty(tool?.description, tool?.summary, tool?.toolDescription);
}

export function getToolCatalogSummary(tool?: Partial<AgentToolCatalogDTO> | null): string {
  return firstNonEmpty(tool?.summary, tool?.description, tool?.toolDescription);
}

export function getToolCatalogActionType(tool?: Partial<AgentToolCatalogDTO> | null): string {
  return firstNonEmpty(tool?.actionType, tool?.category, tool?.toolCategory);
}

export function getToolCatalogCategory(tool?: Partial<AgentToolCatalogDTO> | null): string {
  return firstNonEmpty(tool?.category, tool?.toolCategory);
}

export function getToolCatalogAccessMode(tool?: Partial<AgentToolCatalogDTO> | null): string {
  return firstNonEmpty(tool?.accessMode);
}

export function getToolCatalogSourceType(tool?: Partial<AgentToolCatalogDTO> | null): string {
  return firstNonEmpty(tool?.sourceType);
}

export function getToolCatalogMachineName(tool?: Partial<AgentToolCatalogDTO> | null): string {
  return firstNonEmpty(tool?.toolName, tool?.callbackName, tool?.name);
}

export function getToolCatalogCallbackName(tool?: Partial<AgentToolCatalogDTO> | null): string {
  return firstNonEmpty(tool?.callbackName, tool?.toolName);
}

export function getToolCatalogMethodSignature(tool?: Partial<AgentToolCatalogDTO> | null): string {
  const toolClass = firstNonEmpty(tool?.toolClass);
  const toolMethod = firstNonEmpty(tool?.toolMethod);
  if (toolClass && toolMethod) {
    return `${toolClass}#${toolMethod}`;
  }
  return firstNonEmpty(toolClass, toolMethod);
}

export function getToolCatalogTags(tool?: Partial<AgentToolCatalogDTO> | null): string[] {
  if (Array.isArray(tool?.tags)) {
    return tool.tags.map((item) => String(item).trim()).filter(Boolean);
  }

  if (typeof tool?.tag === "string" && tool.tag.trim()) {
    return tool.tag
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean);
  }

  return [];
}

export function getToolCatalogSkillNames(tool?: Partial<AgentToolCatalogDTO> | null): string[] {
  if (!Array.isArray(tool?.skillNames)) return [];
  return tool.skillNames.map((item) => String(item).trim()).filter(Boolean);
}

export function getToolCatalogQuotaText(tool?: Partial<AgentToolCatalogDTO> | null): string {
  if (tool?.dailyQuota === -1) return "无限配额";
  if (typeof tool?.dailyQuota === "number") return `日配额 ${tool.dailyQuota}`;
  return "未设置配额";
}

export function getToolCatalogUsageText(tool?: Partial<AgentToolCatalogDTO> | null): string {
  if (typeof tool?.usedToday === "number") {
    return `今日 ${tool.usedToday} 次`;
  }
  return "今日未统计";
}

export function getToolCatalogParams(tool?: Partial<AgentToolCatalogDTO> | null): AgentToolCatalogParamDTO[] {
  if (Array.isArray(tool?.params)) {
    return tool.params;
  }

  if (Array.isArray(tool?.inputSchema)) {
    return tool.inputSchema.map((item) => {
      if (!isRecord(item)) {
        return { name: String(item) };
      }

      return {
        ...item,
        name: firstNonEmpty(item.name, item.paramName, item.key),
        type: firstNonEmpty(item.type, item.valueType, item.schemaType),
        description: firstNonEmpty(item.description, item.desc, item.summary),
        required: toBoolean(item.required),
      };
    });
  }

  return [];
}

export function getToolCatalogPurpose(tool?: Partial<AgentToolCatalogDTO> | null): string {
  return firstNonEmpty(tool?.purpose);
}

export function getToolCatalogOutputValue(tool?: Partial<AgentToolCatalogDTO> | null): unknown {
  return (
    tool?.output ??
    tool?.outputSchema ??
    tool?.returnSchema ??
    tool?.resultSchema ??
    (tool?.returnType ? { returnType: tool.returnType } : undefined)
  );
}

export function stringifyToolCatalogValue(value: unknown): string {
  if (value === undefined) return "";
  if (typeof value === "string") {
    const trimmed = value.trim();
    if (!trimmed) return "";
    try {
      return JSON.stringify(JSON.parse(trimmed), null, 2);
    } catch {
      return value;
    }
  }

  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}
