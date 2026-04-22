"use client";

import { useState, useEffect, useMemo } from "react";
import { skillService } from "@/lib/api";
import type { SkillResponseDTO } from "@/lib/api/dto";

/**
 * Fetches the list of enabled skills available in the current workspace.
 * Deduplicates by name (WORKSPACE overrides SYSTEM) and groups by scope.
 */
export function useSkills() {
  const [rawSkills, setRawSkills] = useState<SkillResponseDTO[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    skillService
      .listSkills({ page: 1, size: 50 })
      .then((result) => {
        if (!cancelled) {
          setRawSkills(result.records.filter((s) => s.enabled));
        }
      })
      .catch(() => {})
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // Deduplicate: WORKSPACE version wins over SYSTEM when names collide.
  // Track which SYSTEM skills have been overridden.
  const { skills, overriddenSystemNames } = useMemo(() => {
    const byName = new Map<string, SkillResponseDTO>();
    const overridden = new Set<string>();

    for (const s of rawSkills) {
      const existing = byName.get(s.name);
      if (!existing) {
        byName.set(s.name, s);
      } else if (s.scope === "WORKSPACE") {
        // WORKSPACE overrides SYSTEM
        if (existing.scope === "SYSTEM") overridden.add(s.name);
        byName.set(s.name, s);
      } else if (s.scope === "SYSTEM" && existing.scope === "WORKSPACE") {
        overridden.add(s.name);
        // keep existing WORKSPACE version
      }
    }

    // Sort: WORKSPACE first, then SYSTEM
    const deduped = Array.from(byName.values()).sort((a, b) => {
      if (a.scope === b.scope) return 0;
      return a.scope === "WORKSPACE" ? -1 : 1;
    });

    return { skills: deduped, overriddenSystemNames: overridden };
  }, [rawSkills]);

  // Group by scope for UI rendering
  const workspaceSkills = useMemo(
    () => skills.filter((s) => s.scope === "WORKSPACE"),
    [skills],
  );
  const systemSkills = useMemo(
    () => skills.filter((s) => s.scope === "SYSTEM"),
    [skills],
  );

  // Build toolId → skill mapping from groupedToolIds
  const toolToSkillMap = useMemo(() => {
    const map = new Map<string, SkillResponseDTO>();
    for (const skill of skills) {
      if (skill.groupedToolIds) {
        for (const toolId of skill.groupedToolIds) {
          map.set(toolId, skill);
        }
      }
    }
    return map;
  }, [skills]);

  return { skills, isLoading, workspaceSkills, systemSkills, overriddenSystemNames, toolToSkillMap };
}
