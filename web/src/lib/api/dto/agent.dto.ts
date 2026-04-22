/**
 * Agent Service DTOs
 */

// Stage2 runtime allows custom agentType values, not only built-in coordinator/universal.
export type AgentType = string;

export type AgentSkillLoadMode =
  | "ALL_ENABLED"
  | "DEFAULT_ONLY"
  | "REQUEST_SCOPED"
  | "DISABLED"
  | string;

export type AgentExecutionMode =
  | "CHAT"
  | "MISSION"
  | "BOTH"
  | string;

// Session status
export type SessionStatus = "active" | "generating" | "ended" | "archived";

// Scope types
export type ScopeType =
  | "global"
  | "script"
  | "episode"
  | "storyboard"
  | "character"
  | "scene"
  | "prop"
  | "style"
  | "asset";

// Message role — backend persists tool_call / tool_result as distinct roles
export type MessageRole = "user" | "assistant" | "tool" | "tool_call" | "tool_result" | "system";

// Message status
export type MessageStatus = "generating" | "completed" | "failed" | "cancelled";

// Event types — v2 actual: connect/done/thinking; tool_call/tool_result/message/error/cancelled
// HITL Batch: status / structured_data / ask_user
// Infra: heartbeat (every ~5s while generating) / resync_required (gap beyond buffer)
export type EventType =
  | "connect"
  | "thinking"
  | "tool_call"
  | "tool_result"
  | "message"
  | "done"
  | "error"
  | "cancelled"
  | "status"
  | "structured_data"
  | "ask_user"
  | "heartbeat"
  | "resync_required";

// ============ Request DTOs ============

export interface CreateSessionRequestDTO {
  agentType?: AgentType;
  scope?: ScopeType;
  scriptId?: string;
  episodeId?: string;
  storyboardId?: string;
  characterId?: string;
  sceneId?: string;
  propId?: string;
  styleId?: string;
  assetId?: string;
  initialContext?: Record<string, unknown>;
  /** Session-level Skill names. null=all enabled; []=disabled; ["a","b"]=specific */
  skillNames?: string[] | null;
}

export interface InlineAttachment {
  /** MIME type — required for base64 mode, optional for URL mode */
  mimeType?: string;
  /** base64-encoded data (no data URI prefix), mutually exclusive with url */
  data?: string;
  /** Media URL (backend downloads automatically), mutually exclusive with data */
  url?: string;
  /** Display file name (optional) */
  fileName?: string;
  /** File size in bytes (optional) */
  fileSize?: number;
}

export interface SendMessageRequestDTO {
  message: string;
  attachmentIds?: string[];
  /** Message-level Skill names. null=inherit session; []=disabled; ["a","b"]=specific */
  skillNames?: string[] | null;
  /** Inline attachments (base64 or URL), max 10 */
  attachments?: InlineAttachment[];
  stream?: boolean;
  scope?: ScopeType;
  scriptId?: string;
  episodeId?: string;
  storyboardId?: string;
  characterId?: string;
  sceneId?: string;
  propId?: string;
  styleId?: string;
  assetId?: string;
}

// ============ Response DTOs ============

export interface SessionResponseDTO {
  id: string;
  agentType: AgentType;
  userId: string;
  workspaceId: string;
  scriptId?: string;
  episodeId?: string;
  storyboardId?: string;
  title?: string;
  status: SessionStatus;
  messageCount: number;
  totalTokens?: number;
  extras?: Record<string, unknown>;
  createdAt: string;
  lastActiveAt?: string;
  archivedAt?: string;
  /** Whether the session is currently generating (in-memory signal, may be lost on restart) */
  generating?: boolean;
}

export interface TokenUsageDTO {
  promptTokens: number;
  completionTokens: number;
  cachedTokens?: number;
  thoughtTokens?: number;
  toolUsePromptTokens?: number;
  totalTokens: number;
  estimated?: boolean;
}

export interface ToolCallInfoDTO {
  toolName: string;
  arguments: Record<string, unknown>;
  success: boolean;
  result?: Record<string, unknown>;
}

export interface AgentResponseDTO {
  success: boolean;
  content: string;
  errorMessage?: string;
  toolCalls?: ToolCallInfoDTO[];
  iterations?: number;
  elapsedMs?: number;
  tokenUsage?: TokenUsageDTO;
  completedAt?: string;
  extras?: Record<string, unknown>;
}

// Response with messages array (used by sync message endpoint)
export interface AgentMessagesResponseDTO {
  sessionId: string;
  messages: MessageResponseDTO[];
}

// Prompt generation result from output_image_prompt tool
export interface PromptGenerationResultDTO {
  success: boolean;
  prompt: string;
  language: "en" | "zh";
  promptType: "CHARACTER" | "SCENE" | "PROP" | "STORYBOARD";
  entityIds: string[];
  generatedAt: string;
}

export interface MessageResponseDTO {
  id: string;
  sessionId: string;
  role: MessageRole;
  content: string;
  /** Message status for tracking generation state (generating/completed/failed/cancelled) */
  status?: MessageStatus;
  sequence?: number;
  eventType?: EventType;
  toolCallId?: string;
  toolName?: string;
  toolArguments?: Record<string, unknown>;
  toolResult?: Record<string, unknown>;
  iteration?: number;
  metadata?: Record<string, unknown>;
  elapsedMs?: number;
  totalToolCalls?: number;
  estimatedTokens?: number;
  timestamp?: string;
  createdAt: string;
  /** Attachment IDs associated with this message */
  attachmentIds?: string[];
  /** Full attachment details returned by backend */
  attachments?: MessageAttachmentDTO[];
}

export interface MessageAttachmentDTO {
  url: string;
  fileName: string;
  mimeType: string;
  fileSize: number;
  assetType: string;
  thumbnailUrl?: string | null;
}

// ============ SSE Event DTOs ============

export interface SSEBaseEvent {
  eventType: EventType;
  timestamp?: string;
  /** Per-session monotonically increasing id; used for Last-Event-ID reconnect */
  eventId?: number;
}

export interface SSEConnectEvent extends SSEBaseEvent {
  eventType: "connect";
  status?: string;
}

export interface SSEThinkingEvent extends SSEBaseEvent {
  eventType: "thinking";
  content: string;
  iteration?: number;
}

export interface SSEToolCallEvent extends SSEBaseEvent {
  eventType: "tool_call";
  toolCallId: string;
  toolName: string;
  toolArguments: Record<string, unknown>;
  iteration: number;
}

export interface SSEToolResultEvent extends SSEBaseEvent {
  eventType: "tool_result";
  toolCallId: string;
  toolName: string;
  toolResult: Record<string, unknown>;
  iteration: number;
}

export interface SSEMessageEvent extends SSEBaseEvent {
  eventType: "message";
  content: string;
}

export interface SSEErrorEvent extends SSEBaseEvent {
  eventType: "error";
  content: string;
}

export interface SSEDoneEvent extends SSEBaseEvent {
  eventType: "done";
  elapsedMs: number;
  totalToolCalls: number;
  estimatedTokens?: number;
  iteration?: number;
}

export interface SSECancelledEvent extends SSEBaseEvent {
  eventType: "cancelled";
  content: string;
  iterations: number;
}

// ===== HITL Batch: status / structured_data / ask_user =====

export type StatusPhase =
  | "skill_loading"
  | "rag_retrieval"
  | "llm_invoking"
  | "tool_batch_progress"
  | "mission_step"
  | "preflight"
  | "context_preparing"
  | string;

export interface StatusEventMetadata {
  phase: StatusPhase;
  label?: string;
  progress?: number | null;
  details?: Record<string, unknown>;
}

export interface SSEStatusEvent extends SSEBaseEvent {
  eventType: "status";
  content?: string;
  metadata: StatusEventMetadata;
}

export type RendererHint = "card" | "table" | "form" | "chart" | "markdown";

export interface StructuredDataMetadata {
  schemaRef: string;
  data: Record<string, unknown>;
  rendererHint?: RendererHint;
}

export interface SSEStructuredDataEvent extends SSEBaseEvent {
  eventType: "structured_data";
  metadata: StructuredDataMetadata;
}

export type AskUserInputType =
  | "single_choice"
  | "multi_choice"
  | "confirm"
  | "text"
  | "number";

export interface AskChoice {
  id: string;
  label: string;
  description?: string;
}

export interface AskConstraints {
  minSelect?: number;
  maxSelect?: number;
  minLength?: number;
  maxLength?: number;
  min?: number;
  max?: number;
}

export interface AskUserMetadata {
  askId: string;
  question: string;
  inputType: AskUserInputType;
  choices?: AskChoice[];
  constraints?: AskConstraints | null;
  deadlineMs?: number;
}

export interface SSEAskUserEvent extends SSEBaseEvent {
  eventType: "ask_user";
  content: string;
  metadata: AskUserMetadata;
}

// ===== Infra events =====

export interface HeartbeatMetadata {
  elapsedMs?: number;
  serverTime?: string;
}

export interface SSEHeartbeatEvent extends SSEBaseEvent {
  eventType: "heartbeat";
  metadata?: HeartbeatMetadata;
}

export interface ResyncRequiredMetadata {
  clientLastEventId?: number;
  oldestAvailableEventId?: number | null;
  serverMaxEventId?: number;
}

export interface SSEResyncRequiredEvent extends SSEBaseEvent {
  eventType: "resync_required";
  metadata?: ResyncRequiredMetadata;
}

export interface UserAnswerDTO {
  answer?: string;
  multiAnswer?: string[];
  rejected?: boolean;
  extras?: Record<string, unknown>;
}

export type AskStatus =
  | "PENDING"
  | "ANSWERED"
  | "REJECTED"
  | "TIMEOUT"
  | "CANCELLED"
  | "ERROR";

export type SSEEvent =
  | SSEConnectEvent
  | SSEThinkingEvent
  | SSEToolCallEvent
  | SSEToolResultEvent
  | SSEMessageEvent
  | SSEErrorEvent
  | SSEDoneEvent
  | SSECancelledEvent
  | SSEStatusEvent
  | SSEStructuredDataEvent
  | SSEAskUserEvent
  | SSEHeartbeatEvent
  | SSEResyncRequiredEvent;

// Cancel response
export interface CancelResponseDTO {
  sessionId: string;
  cancelled: boolean;
  elapsedMs?: number;
  /** Number of pending HITL asks that were cascaded-cancelled by this request */
  cancelledAsks?: number;
}

// ============ Session state (recovery / reconnect) ============

export type ResumeHint = "IDLE" | "RESUME_STREAM" | "ANSWER_ASK";

export interface SessionStateSessionDTO {
  id: string;
  agentType: AgentType;
  title?: string;
  status: SessionStatus | string;
  messageCount: number;
  totalTokens?: number;
  createdAt: string;
  lastActiveAt?: string;
}

export interface SessionStateGenerationDTO {
  inFlight: boolean;
  placeholderMessageId?: string;
  startedAt?: string;
  lastHeartbeatAt?: string;
  staleMs?: number;
}

export interface SessionStatePendingAskDTO {
  pending: boolean;
  askId?: string;
  question?: string;
  inputType?: AskUserInputType;
  choices?: AskChoice[];
  constraints?: AskConstraints | null;
  deadlineMs?: number;
  expiresAt?: string;
}

export interface SessionStateResponseDTO {
  session: SessionStateSessionDTO;
  generation: SessionStateGenerationDTO;
  pendingAsk: SessionStatePendingAskDTO;
  lastEventId: number;
  resumeHint: ResumeHint;
}

// ============ Query DTOs ============

/**
 * Session query parameters
 * Simplified to only support page, size, standalone filter, and scriptId
 * Backend defaults to lastActiveAt DESC ordering
 */
export interface SessionQueryParams {
  /** 页码（从1开始），默认 1 */
  page?: number;
  /** 每页大小（最大100），默认 20 */
  size?: number;
  /** 是否独立 Agent 会话筛选：true=独立Agent，false=协调者会话，不传=全部 */
  standalone?: boolean;
  /** 按剧本ID筛选会话 */
  scriptId?: string;
}

export interface SessionPageResult {
  records: SessionResponseDTO[];
  total: number;
  page: number;
  size: number;
  pages: number;
}

// ============ Agent Catalog / Resolution DTOs ============

export interface AgentCatalogItemDTO {
  id?: string;
  agentType: string;
  agentName?: string;
  displayName?: string;
  description?: string;
  scope?: string;
  iconUrl?: string;
  enabled?: boolean;
  isCoordinator?: boolean;
  standaloneEnabled?: boolean;
  executionMode?: AgentExecutionMode;
  skillLoadMode?: AgentSkillLoadMode;
  defaultSkillNames?: string[];
  allowedSkillNames?: string[];
  subAgentTypes?: string[];
  tags?: string[];
  [key: string]: unknown;
}

export interface ResolvedSkillInfoDTO {
  name: string;
  displayName?: string;
  description?: string;
  scope?: string;
  version?: number;
  groupedToolIds?: string[];
  toolIds?: string[];
  tags?: string[];
  [key: string]: unknown;
}

export interface ResolvedAgentProfileDTO {
  agentType: string;
  agentName?: string;
  displayName?: string;
  description?: string;
  enabled?: boolean;
  isCoordinator?: boolean;
  standaloneEnabled?: boolean;
  executionMode?: AgentExecutionMode;
  skillLoadMode?: AgentSkillLoadMode;
  defaultSkillNames?: string[];
  allowedSkillNames?: string[];
  subAgentTypes?: string[];
  resolvedSkillNames?: string[];
  resolvedToolIds?: string[];
  resolvedSkills?: ResolvedSkillInfoDTO[];
  [key: string]: unknown;
}
