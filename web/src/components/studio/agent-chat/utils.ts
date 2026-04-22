import type {
  RawMessage,
  ConversationTurn,
  MessageMetadata,
  AskUserRuntime,
  AskUserUiState,
  TurnSegment,
} from "./types";
import type {
  AskUserInputType,
  AskUserMetadata,
  StatusEventMetadata,
  StructuredDataMetadata,
  UserAnswerDTO,
} from "@/lib/api/dto";

/**
 * Normalize a single historical message's metadata so UI reads the same
 * shape regardless of whether the payload came from SSE or history API.
 * - Parses snake_case `status` / `structured_data` / `ask_user` keys.
 * - Derives askUser runtime state from `answeredAt` / `cancelledAt` / `timedOutAt`.
 */
export function normalizeHistoryMetadata(msg: RawMessage): RawMessage {
  const raw = (msg.metadata ?? {}) as Record<string, unknown>;
  const next: MessageMetadata = { ...(msg.metadata ?? {}) };
  let changed = false;

  // status (last snapshot only — not rendered in history, but retained for diagnostics)
  const rawStatus = raw.status ?? raw["status_event"];
  if (rawStatus && typeof rawStatus === "object" && !next.status) {
    next.status = rawStatus as StatusEventMetadata;
    changed = true;
  }

  // structured_data (single) + structured_data_items (list)
  const rawSd = raw.structured_data ?? raw["structuredData"];
  if (rawSd && typeof rawSd === "object" && !next.structuredData) {
    next.structuredData = rawSd as StructuredDataMetadata;
    changed = true;
  }
  const rawItems = raw.structured_data_items ?? raw["structuredDataItems"];
  if (Array.isArray(rawItems) && !next.structuredDataItems) {
    next.structuredDataItems = rawItems as StructuredDataMetadata[];
    changed = true;
  }

  // ask_user
  const rawAsk = raw.ask_user ?? raw["askUser"];
  if (rawAsk && typeof rawAsk === "object" && !next.askUser) {
    const a = rawAsk as AskUserMetadata & {
      state?: AskUserUiState;
      answer?: UserAnswerDTO;
      answeredAt?: string;
      cancelledAt?: string;
      timedOutAt?: string;
    };
    let state: AskUserUiState = a.state ?? "pending";
    if (!a.state) {
      if (a.answeredAt) state = "answered";
      else if (a.cancelledAt) state = "cancelled";
      else if (a.timedOutAt) state = "timeout";
    }
    const runtime: AskUserRuntime = {
      askId: a.askId,
      question: a.question,
      inputType: a.inputType,
      choices: a.choices,
      constraints: a.constraints,
      deadlineMs: a.deadlineMs,
      state,
      answer: a.answer,
      answeredAt: a.answeredAt,
    };
    next.askUser = runtime;
    changed = true;
  }

  return changed ? { ...msg, metadata: next as Record<string, unknown> } : msg;
}

/**
 * Returns true for framework-internal tool calls that should not be shown to the user.
 * In v2 (SAA), read_skill is called internally to load expert skills.
 */
export function isInternalTool(toolName: string): boolean {
  return toolName === "read_skill";
}

/**
 * Returns true for any ask_user tool variant. These tool_call / tool_result
 * messages are rendered via the dedicated "ask" segment and must not appear
 * as regular tool-call cards.
 *
 * Covers naming conventions:
 *   askuser_askUserText, ask_user_choice, ask user confirm, …
 */
function isAskUserTool(toolName: string | undefined): boolean {
  if (!toolName) return false;
  const n = toolName.toLowerCase().replaceAll(" ", "_");
  return n.startsWith("askuser_") || n.startsWith("ask_user_") || n.startsWith("askuser");
}

/** Derive AskUserInputType from tool name. */
function askToolInputType(toolName: string): AskUserInputType {
  const n = toolName.toLowerCase().replaceAll(" ", "_");
  if (n.includes("confirm")) return "confirm";
  if (n.includes("multi")) return "multi_choice";
  if (n.includes("choice")) return "single_choice";
  if (n.includes("number")) return "number";
  return "text";
}

/**
 * Build an "ask" segment message from an ask_user tool_call message.
 * Extracts question/choices/constraints from toolArguments and constructs
 * the AskUserRuntime metadata so AskUserCard can render it.
 */
function buildAskFromToolCall(msg: RawMessage): RawMessage {
  const args = (msg.toolArguments ?? {}) as Record<string, unknown>;
  const inputType = askToolInputType(msg.toolName ?? "");
  const askId = (args.askId as string) ?? `ask-${msg.id}`;

  const askUser: AskUserRuntime = {
    askId,
    question: (args.question as string) ?? "",
    inputType,
    choices: args.choices as AskUserMetadata["choices"],
    constraints: args.constraints as AskUserMetadata["constraints"],
    deadlineMs: args.deadlineMs as number | undefined,
    state: "pending",
  };

  return { ...msg, metadata: { ...(msg.metadata ?? {}), askUser } };
}

/**
 * Apply a tool_result message to the most recent pending ask segment,
 * updating its state (answered / cancelled / timeout) and answer payload.
 */
function applyAskResult(segments: TurnSegment[], msg: RawMessage): void {
  // Parse result from content or toolResult.output
  let result: Record<string, unknown> = {};
  try {
    if (msg.content) result = JSON.parse(msg.content);
  } catch {
    const tr = (msg.toolResult ?? {}) as Record<string, unknown>;
    if (typeof tr.output === "string") {
      try { result = JSON.parse(tr.output); } catch { /* ignore */ }
    }
  }

  const status = (result.status as string ?? "").toUpperCase();
  const answer = result.answer as string | undefined;
  const multiAnswer = result.multiAnswer as string[] | undefined;

  // Walk backwards to find the matching pending ask segment
  for (let i = segments.length - 1; i >= 0; i--) {
    const seg = segments[i];
    if (seg.kind !== "ask") continue;
    const meta = seg.message.metadata as MessageMetadata | undefined;
    const askUser = meta?.askUser;
    if (!askUser || askUser.state !== "pending") continue;

    if (status === "ANSWERED" || answer != null || multiAnswer != null) {
      askUser.state = "answered";
      askUser.answer = multiAnswer ? { multiAnswer } : { answer: answer ?? "" };
    } else if (status === "CANCELLED") {
      askUser.state = "cancelled";
    } else if (status === "TIMEOUT" || status === "TIMED_OUT") {
      askUser.state = "timeout";
    }
    break;
  }
}

// Format time display
export const formatTime = (date: Date, locale: string = "en") => {
  return date.toLocaleTimeString(locale, { hour: "2-digit", minute: "2-digit" });
};

// Format date display
export const formatDate = (date: Date, locale: string = "en") => {
  return date.toLocaleDateString(locale, { month: "short", day: "numeric" });
};

// Format token count
export const formatTokens = (count: number) => {
  if (count >= 1000) {
    return `${(count / 1000).toFixed(1)}k`;
  }
  return count.toString();
};

// ---- Tool display name mapping ----
// Complete mapping of all 59 agent tool names (LLM-facing snake_case)
// to user-friendly labels in zh/en.

const TOOL_LABELS: Record<string, Record<string, string>> = {
  // ── Framework internal ──
  "read_skill":                       { zh: "正在加载技能...", en: "Loading skill..." },

  // ── ScriptTools (4) ──
  "create_script":                    { zh: "创建剧本", en: "Create Script" },
  "get_script":                       { zh: "查看剧本", en: "Get Script" },
  "update_script":                    { zh: "更新剧本", en: "Update Script" },
  "query_scripts":                     { zh: "列出剧本", en: "Query Scripts" },

  // ── EpisodeTools (4) ──
  "get_episode":                      { zh: "查看剧集", en: "Get Episode" },
  "query_episodes":                    { zh: "列出剧集", en: "Query Episodes" },
  "update_episode":                   { zh: "更新剧集", en: "Update Episode" },
  "batch_create_episodes":            { zh: "批量创建剧集", en: "Batch Create Episodes" },

  // ── StoryboardTools (4) ──
  "get_storyboard":                   { zh: "查看分镜", en: "Get Storyboard" },
  "query_storyboards":                 { zh: "列出分镜", en: "Query Storyboards" },
  "update_storyboard":                { zh: "更新分镜", en: "Update Storyboard" },
  "batch_create_storyboards":         { zh: "批量创建分镜", en: "Batch Create Storyboards" },

  // ── CharacterTools (4) ──
  "batch_create_characters":          { zh: "批量创建角色", en: "Batch Create Characters" },
  "query_characters":                  { zh: "列出角色", en: "Query Characters" },
  "get_character":                    { zh: "查看角色", en: "Get Character" },
  "update_character":                 { zh: "更新角色", en: "Update Character" },

  // ── SceneTools (4) ──
  "get_scene":                        { zh: "查看场景", en: "Get Scene" },
  "query_scenes":                      { zh: "列出场景", en: "Query Scenes" },
  "update_scene":                     { zh: "更新场景", en: "Update Scene" },
  "batch_create_scenes":              { zh: "批量创建场景", en: "Batch Create Scenes" },

  // ── PropTools (4) ──
  "get_prop":                         { zh: "查看道具", en: "Get Prop" },
  "query_props":                       { zh: "列出道具", en: "Query Props" },
  "update_prop":                      { zh: "更新道具", en: "Update Prop" },
  "batch_create_props":               { zh: "批量创建道具", en: "Batch Create Props" },

  // ── StyleTools (4) ──
  "query_styles":                      { zh: "列出风格", en: "Query Styles" },
  "get_style":                        { zh: "查看风格", en: "Get Style" },
  "update_style":                     { zh: "更新风格", en: "Update Style" },
  "batch_create_styles":              { zh: "批量创建风格", en: "Batch Create Styles" },

  // ── EntityQueryTools (3) ──
  "batch_get_entities":               { zh: "批量查看实体", en: "Batch Get Entities" },
  "get_storyboard_with_entities":     { zh: "查看分镜全景", en: "Get Storyboard Full View" },
  "get_storyboard_relations":         { zh: "查看分镜关系", en: "Get Storyboard Relations" },

  // ── EntityRelationTools (11) ──
  "create_relation":                  { zh: "创建关系", en: "Create Relation" },
  "batch_create_relations":           { zh: "批量创建关系", en: "Batch Create Relations" },
  "update_relation":                  { zh: "更新关系", en: "Update Relation" },
  "delete_relation":                  { zh: "删除关系", en: "Delete Relation" },
  "list_relations_by_source":         { zh: "查询关系（按来源）", en: "List Relations by Source" },
  "list_relations_by_source_and_type": { zh: "查询关系（按来源和类型）", en: "List Relations by Source & Type" },
  "list_relations_by_target":         { zh: "查询关系（按目标）", en: "List Relations by Target" },
  "add_character_to_storyboard":      { zh: "关联角色→分镜", en: "Link Character → Storyboard" },
  "set_storyboard_scene":             { zh: "设置分镜场景", en: "Set Storyboard Scene" },
  "add_prop_to_storyboard":           { zh: "关联道具→分镜", en: "Link Prop → Storyboard" },
  "add_dialogue_to_storyboard":       { zh: "添加对白→分镜", en: "Add Dialogue → Storyboard" },

  // ── MissionTools (6) ──
  "create_mission":                   { zh: "创建任务", en: "Create Mission" },
  "get_mission_status":               { zh: "查看任务状态", en: "Get Mission Status" },
  "delegate_batch_generation":        { zh: "委派批量生成", en: "Delegate Batch Generation" },
  "delegate_scope_generation":        { zh: "委派范围生成", en: "Delegate Scope Generation" },
  "delegate_pipeline_generation":     { zh: "委派流水线生成", en: "Delegate Pipeline Generation" },
  "update_mission_plan":              { zh: "更新任务计划", en: "Update Mission Plan" },
  "complete_mission":                 { zh: "完成任务", en: "Complete Mission" },
  "fail_mission":                     { zh: "任务失败", en: "Fail Mission" },

  // ── MultimodalTools (10) ──
  "get_asset":                        { zh: "获取素材", en: "Get Asset" },
  "batch_get_assets":                 { zh: "批量获取素材", en: "Batch Get Assets" },
  "get_entity_assets":                { zh: "查询素材", en: "Get Entity Assets" },
  "get_entity_assets_by_type":        { zh: "按类型查询素材", en: "Get Assets by Type" },
  "list_ai_providers":                { zh: "查询AI模型", en: "List AI Providers" },
  "get_ai_provider_detail":           { zh: "获取AI模型详情", en: "Get AI Provider Detail" },
  "generate_entity_asset":            { zh: "生成素材", en: "Generate Entity Asset" },
  "batch_generate_entity_assets":     { zh: "批量生成素材", en: "Batch Generate Entity Assets" },
  "retry_generation":                 { zh: "重试生成", en: "Retry Generation" },
  "get_generation_status":            { zh: "查看生成状态", en: "Get Generation Status" },

  // ── StructuredOutputTools (1) ──
  "output_structured_result":         { zh: "输出结构化结果", en: "Output Structured Result" },
};

/**
 * Convert raw tool name to user-friendly display label.
 *
 * Looks up the complete TOOL_LABELS map first, then falls back to
 * humanizing the raw snake_case name.
 */
export function getToolDisplayName(rawName: string, locale: string = "zh"): string {
  const lang = locale.startsWith("en") ? "en" : "zh";

  // 1. Exact match in the complete tool labels map
  if (TOOL_LABELS[rawName]) {
    return TOOL_LABELS[rawName][lang];
  }

  // 2. Fallback: humanize the raw name
  return rawName
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .replace(/_/g, " ")
    .trim();
}

/**
 * Closes any unclosed code fences and block-math delimiters in streaming markdown.
 * Prevents layout glitching when react-markdown processes an incomplete token stream.
 */
export function preprocessStreamingMarkdown(text: string): string {
  let result = text;

  // Count opening backtick fences (``` at line start) — odd count means one is unclosed
  const backtickFences = (result.match(/^```/gm) || []).length;
  if (backtickFences % 2 !== 0) result += "\n```";

  // Count opening tilde fences (~~~ at line start)
  const tildeFences = (result.match(/^~~~/gm) || []).length;
  if (tildeFences % 2 !== 0) result += "\n~~~";

  // Count block-math delimiters ($$) — odd count means one is unclosed
  const blockMathDelimiters = (result.match(/\$\$/g) || []).length;
  if (blockMathDelimiters % 2 !== 0) result += "\n$$";

  return result;
}

/**
 * True for assistant placeholder rows that only carry generating→completed
 * status for the backend and MUST NOT render as an empty text bubble (§1.3).
 * The per-segment rows and legacy-split rows always carry real content.
 */
function isAssistantPlaceholder(msg: RawMessage): boolean {
  if (msg.role !== "assistant") return false;
  if ((msg.content ?? "").length > 0) return false;
  // Special assistant event rows (thinking) are not placeholders.
  if (msg.eventType && msg.eventType !== "message") return false;
  // Safety net: rows carrying attachments or metadata we render shouldn't be dropped.
  const meta = msg.metadata as Record<string, unknown> | undefined;
  if (meta?.attachments || meta?.structuredData || meta?.askUser) return false;
  return true;
}

// Group messages into conversation turns preserving segment order.
export function groupIntoTurns(messages: RawMessage[]): ConversationTurn[] {
  // Deduplicate by id (client append race vs API reload)
  const unique = Array.from(new Map(messages.map(m => [m.id, m])).values());
  // Primary: sequence asc.  Secondary: metadata.segmentIndex asc — legacy blob-split
  // rows share a sequence and rely on segmentIndex for stable intra-turn order (§1.3).
  const sorted = [...unique]
    .filter(m => !isAssistantPlaceholder(m))
    .sort((a, b) => {
      const s = (a.sequence ?? 0) - (b.sequence ?? 0);
      if (s !== 0) return s;
      const ai = (a.metadata as { segmentIndex?: number } | undefined)?.segmentIndex ?? 0;
      const bi = (b.metadata as { segmentIndex?: number } | undefined)?.segmentIndex ?? 0;
      return ai - bi;
    });

  const turns: ConversationTurn[] = [];

  interface TurnAccumulator {
    id: string;
    userMessage: RawMessage;
    segments: TurnSegment[];
  }

  let current: TurnAccumulator | null = null;

  for (const msg of sorted) {
    if (msg.role === "user") {
      if (current) turns.push(finalizeTurn(current));
      current = { id: `turn-${msg.id}`, userMessage: msg, segments: [] };
      continue;
    }
    if (!current) continue; // orphan before any user message

    const segments = current.segments;
    const last = segments.at(-1);

    if (msg.eventType === "thinking") {
      // Each thinking message is a distinct logical block (one per LLM iteration).
      segments.push({ kind: "thinking", id: msg.id, content: msg.content, done: true });
      continue;
    }

    if (msg.eventType === "ask_user") {
      segments.push({ kind: "ask", id: msg.id, message: msg });
      continue;
    }

    if (msg.eventType === "structured_data") {
      segments.push({ kind: "structured", id: msg.id, message: msg });
      continue;
    }

    const isToolMsg =
      msg.role === "tool" ||
      msg.role === "tool_call" ||
      msg.role === "tool_result" ||
      msg.eventType === "tool_call" ||
      msg.eventType === "tool_result";

    if (isToolMsg) {
      // ask_user tool messages → dedicated "ask" segment (not tool cards).
      // tool_call creates the ask segment; tool_result updates its state.
      if (isAskUserTool(msg.toolName)) {
        const isCall = msg.eventType === "tool_call" || msg.role === "tool_call";
        if (isCall) {
          const askMsg = buildAskFromToolCall(msg);
          segments.push({ kind: "ask", id: msg.id, message: askMsg });
        } else {
          applyAskResult(segments, msg);
        }
        continue;
      }

      if (last && last.kind === "tools") {
        last.messages.push(msg);
      } else {
        segments.push({ kind: "tools", id: msg.id, messages: [msg] });
      }
      continue;
    }

    if (msg.role === "assistant") {
      // One assistant message = one text segment (ReAct may emit multiple per turn).
      segments.push({ kind: "text", id: msg.id, message: msg, done: true });
      continue;
    }
  }

  if (current) turns.push(finalizeTurn(current));
  return turns;
}

/**
 * Finalize a turn accumulator:
 * - Derive toolCalls (flattened from tools/ask/structured segments) for legacy readers.
 * - Pick the last text segment's message as assistantMessage.
 * - Hoist elapsedMs / totalToolCalls from DTO top-level into its metadata.
 */
function finalizeTurn(acc: { id: string; userMessage: RawMessage; segments: TurnSegment[] }): ConversationTurn {
  const toolCalls: RawMessage[] = [];
  let lastTextIdx = -1;
  for (let i = 0; i < acc.segments.length; i++) {
    const seg = acc.segments[i];
    if (seg.kind === "tools") toolCalls.push(...seg.messages);
    else if (seg.kind === "ask" || seg.kind === "structured") toolCalls.push(seg.message);
    else if (seg.kind === "text") lastTextIdx = i;
  }

  let assistantMessage: RawMessage | null = null;
  if (lastTextIdx >= 0) {
    const lastTextSeg = acc.segments[lastTextIdx] as Extract<TurnSegment, { kind: "text" }>;
    const msg = lastTextSeg.message;
    if (msg) {
      const meta = { ...(msg.metadata ?? {}) } as Record<string, unknown>;
      let changed = false;
      if (msg.elapsedMs != null && meta.elapsedMs == null) {
        meta.elapsedMs = msg.elapsedMs;
        changed = true;
      }
      if (msg.totalToolCalls != null && meta.totalToolCalls == null) {
        meta.totalToolCalls = msg.totalToolCalls;
        changed = true;
      }
      assistantMessage = changed ? { ...msg, metadata: meta } : msg;
      // Reflect back into the segment so rendering sees the hoisted metadata.
      acc.segments[lastTextIdx] = { ...lastTextSeg, message: assistantMessage };
    }
  }

  return {
    id: acc.id,
    userMessage: acc.userMessage,
    segments: acc.segments,
    toolCalls,
    assistantMessage,
  };
}
