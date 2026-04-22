/**
 * Workspace Skill Service
 * Corresponds to backend WorkspaceSkillController (/agent/skills)
 * X-Workspace-Id is automatically attached by the API client.
 */

import { api } from "../client";
import { getAuthToken } from "@/lib/stores/auth-store";
import type {
  SkillResponseDTO,
  SkillCreateRequestDTO,
  SkillUpdateRequestDTO,
  SkillListParamsDTO,
  SkillPageDTO,
  SkillImportResult,
} from "../dto/skill.dto";

const SKILL_BASE = "/api/agent/skills";

export const skillService = {
  /** Paginated list — GET /api/agent/skills */
  listSkills: (params?: SkillListParamsDTO) =>
    api.get<SkillPageDTO>(SKILL_BASE, {
      params: params as Record<string, string | number | boolean | undefined>,
    }),

  /** Skill detail (includes content) — GET /api/agent/skills/{name} */
  getSkill: (name: string) =>
    api.get<SkillResponseDTO>(`${SKILL_BASE}/${name}`),

  /** Create workspace skill — POST /api/agent/skills */
  createSkill: (data: SkillCreateRequestDTO) =>
    api.post<SkillResponseDTO>(SKILL_BASE, data),

  /** Update workspace skill — PUT /api/agent/skills/{name} */
  updateSkill: (name: string, data: SkillUpdateRequestDTO) =>
    api.put<SkillResponseDTO>(`${SKILL_BASE}/${name}`, data),

  /** Soft-delete workspace skill — DELETE /api/agent/skills/{name} */
  deleteSkill: (name: string) =>
    api.delete<void>(`${SKILL_BASE}/${name}`),

  /** Toggle skill enabled/disabled — PATCH /api/agent/skills/{name}/toggle */
  toggleSkill: (name: string) =>
    api.patch<SkillResponseDTO>(`${SKILL_BASE}/${name}/toggle`),

  /** Output JSON-Schema for structured_data — GET /api/agent/skills/{name}/output-schema */
  getOutputSchema: (name: string) =>
    api.get<Record<string, unknown> | null>(`${SKILL_BASE}/${encodeURIComponent(name)}/output-schema`),

  /**
   * Import skills from a ZIP file — POST /api/agent/skills/import
   * ZIP may contain flat .md files or SAA-standard skill directories.
   * Returns an import summary (total / success / failed / errors[]).
   * Uses raw fetch because the API client only handles JSON bodies.
   */
  importSkill: async (file: File): Promise<SkillImportResult> => {
    const token = getAuthToken();
    const formData = new FormData();
    formData.append("file", file);

    const response = await fetch(`${SKILL_BASE}/import`, {
      method: "POST",
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        // Do NOT set Content-Type — browser sets multipart boundary automatically
      },
      body: formData,
    });

    const result = await response.json() as { code: string | number; message?: string; data: SkillImportResult };

    if (!response.ok || (String(result.code) !== "0" && String(result.code) !== "200")) {
      throw new Error(result.message || `Upload failed: ${response.statusText}`);
    }

    return result.data;
  },
};

