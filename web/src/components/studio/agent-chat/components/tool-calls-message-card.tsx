"use client";

import { useTranslations, useLocale } from "next-intl";
import {
  Check,
  Loader2,
  Wrench,
  Zap,
} from "lucide-react";
import type { RawMessage } from "../types";
import type { SkillResponseDTO } from "@/lib/api/dto";
import { getToolDisplayName } from "../utils";
import { AssistantSegmentShell } from "./assistant-segment-shell";
import { SegmentCardShell, type SegmentPosition } from "./segment-card-shell";

interface ToolCallGroup {
  toolCallId: string;
  toolName: string;
  hasResult: boolean;
  skillName?: string;
  skillDisplayName?: string;
}

function groupToolCalls(
  toolCalls: RawMessage[],
  toolToSkillMap?: Map<string, SkillResponseDTO>,
): ToolCallGroup[] {
  const groups: ToolCallGroup[] = [];
  const byToolCallId = new Map<string, ToolCallGroup>();

  for (const msg of toolCalls) {
    const isCall = msg.role === "tool_call" || msg.eventType === "tool_call";
    const isResult = msg.role === "tool_result" || msg.eventType === "tool_result";

    if (isCall) {
      const key = msg.toolCallId || msg.id;
      const toolName = msg.toolName || "unknown";
      const skill = toolToSkillMap?.get(toolName);
      const group: ToolCallGroup = {
        toolCallId: key,
        toolName,
        hasResult: false,
        skillName: skill?.name,
        skillDisplayName: skill?.displayName ?? skill?.name,
      };
      groups.push(group);
      if (msg.toolCallId) byToolCallId.set(msg.toolCallId, group);
    } else if (isResult) {
      const byId = msg.toolCallId ? byToolCallId.get(msg.toolCallId) : undefined;
      if (byId) {
        byId.hasResult = true;
      } else {
        const match = groups.find(g => !g.hasResult && g.toolName === (msg.toolName || "unknown"));
        if (match) match.hasResult = true;
      }
    }
  }

  return groups;
}

interface SkillToolGroup {
  skillName: string | null;
  skillDisplayName: string;
  tools: ToolCallGroup[];
}

function groupBySkill(groups: ToolCallGroup[], t: (key: string) => string): SkillToolGroup[] {
  const bySkill = new Map<string | null, SkillToolGroup>();

  for (const g of groups) {
    const key = g.skillName ?? null;
    let skillGroup = bySkill.get(key);
    if (!skillGroup) {
      skillGroup = {
        skillName: key,
        skillDisplayName: key ? (g.skillDisplayName || key) : t("toolCallGeneral"),
        tools: [],
      };
      bySkill.set(key, skillGroup);
    }
    skillGroup.tools.push(g);
  }

  return Array.from(bySkill.values()).sort((a, b) => {
    if (a.skillName && !b.skillName) return -1;
    if (!a.skillName && b.skillName) return 1;
    return 0;
  });
}

export function ToolCallsMessageCard({
  toolCalls,
  isStreaming,
  toolToSkillMap,
  showAvatar,
  position,
}: {
  toolCalls: RawMessage[];
  isStreaming?: boolean;
  toolToSkillMap?: Map<string, SkillResponseDTO>;
  showAvatar?: boolean;
  position?: SegmentPosition;
}) {
  const t = useTranslations("workspace.agent");
  const locale = useLocale();
  const groups = groupToolCalls(toolCalls, toolToSkillMap);
  if (groups.length === 0) return null;

  const skillGroups = groupBySkill(groups, t);
  const hasSkillGrouping = skillGroups.some((sg) => sg.skillName !== null);
  const toolNames = groups.map((group) => getToolDisplayName(group.toolName, locale));
  const summary = hasSkillGrouping
    ? skillGroups
        .filter((sg) => sg.skillName !== null)
        .map((sg) => sg.skillDisplayName)
        .join(", ") || t("toolCallLabel")
    : toolNames.length === 1
      ? t("toolCallSummarySingle", { tool: toolNames[0] })
      : t("toolCallSummaryMultiple", {
          first: toolNames[0],
          second: toolNames[1],
          count: toolNames.length,
        });
  const completedCount = groups.filter((group) => group.hasResult).length;
  const allComplete = !isStreaming && completedCount === groups.length;
  const toolProgressText = isStreaming && completedCount < groups.length
    ? t("toolCallRunning", { count: groups.length - completedCount })
    : t("toolCallCompleted", { count: groups.length });

  return (
    <AssistantSegmentShell showAvatar={showAvatar}>
      <SegmentCardShell
        icon={<Wrench className="size-3.5 text-blue-400" />}
        bgClass="bg-blue-400/10"
        position={position}
        title={<span className="truncate">{summary}</span>}
        subtitle={toolProgressText}
        collapsible
        defaultCollapsed={false}
        forceCollapsed={allComplete}
        pulse={isStreaming && completedCount < groups.length}
      >
        <div className="max-h-72 space-y-3 overflow-y-auto rounded-xl bg-background/45 p-2.5">
          {skillGroups.map((sg) => (
            <div key={sg.skillName ?? "__general"}>
              <div className="mb-1.5 flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-wider text-muted">
                {sg.skillName ? (
                  <Zap className="size-2.5 text-accent" />
                ) : (
                  <Wrench className="size-2.5" />
                )}
                <span className={sg.skillName ? "text-accent/70" : ""}>
                  {sg.skillDisplayName}
                </span>
                <span className="text-muted/50">({sg.tools.length})</span>
              </div>
              <div className="space-y-1">
                {sg.tools.map((group, index) => (
                  <div
                    key={group.toolCallId}
                    className="rounded-lg bg-background/70 px-3 py-1.5"
                  >
                    <div className="flex items-center gap-2">
                      <div className="text-[11px] text-muted">#{index + 1}</div>
                      <div className="truncate text-xs font-medium text-foreground/90">
                        {getToolDisplayName(group.toolName, locale)}
                      </div>
                      {group.hasResult ? (
                        <Check className="ml-auto size-3 shrink-0 text-success" />
                      ) : isStreaming ? (
                        <Loader2 className="ml-auto size-3 shrink-0 animate-spin text-muted" />
                      ) : null}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      </SegmentCardShell>
    </AssistantSegmentShell>
  );
}
