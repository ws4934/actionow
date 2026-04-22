import type {
  MessageResponseDTO,
  MessageStatus,
  SendMessageRequestDTO,
  AssetType,
  StatusEventMetadata,
  StructuredDataMetadata,
  AskUserMetadata,
  UserAnswerDTO,
} from "@/lib/api/dto";

export interface AgentChatProps {
  scriptId: string;
}

export interface ChatAttachment {
  id: string;
  name: string;
  mimeType: string;
  fileSize: number;
  url: string;
  assetType: AssetType;
  status: "uploading" | "completed" | "failed";
  progress?: number;
  localFile?: File;
  errorMessage?: string | null;
}

export type EntityCategoryKey = "character" | "scene" | "prop" | "style" | "episode" | "storyboard";

export interface LinkedEntity {
  id: string;
  category: EntityCategoryKey;
  name: string;
  coverUrl?: string | null;
}

export interface EntityCategoryDef {
  key: EntityCategoryKey;
  icon: React.ComponentType<{ className?: string }>;
  labelKey: string;
  nameField: "name" | "title";
  requestField: keyof SendMessageRequestDTO;
}

export interface TokenUsage {
  totalTokens: number;
  promptTokens: number;
  thoughtTokens: number;
  completionTokens: number;
}

export interface MessageAttachment {
  url: string;
  fileName: string;
  mimeType: string;
  mediaCategory: string;
}

export type AskUserUiState = "pending" | "answered" | "cancelled" | "timeout";

export interface AskUserRuntime extends AskUserMetadata {
  state: AskUserUiState;
  answer?: UserAnswerDTO;
  answeredAt?: string;
}

export interface MessageMetadata {
  thinking?: string;
  elapsedMs?: number;
  tokenUsage?: TokenUsage;
  totalToolCalls?: number;
  attachments?: MessageAttachment[];
  // Latest status snapshot (diagnostic; streaming only renders live status separately)
  status?: StatusEventMetadata;
  // Per-turn structured data items surfaced from SSE or history
  structuredDataItems?: StructuredDataMetadata[];
  // Attached when this message carries a single structured_data payload (tool-role messages)
  structuredData?: StructuredDataMetadata;
  // Ask-user runtime state (only on tool-role ask_user messages)
  askUser?: AskUserRuntime;
}

export type RawMessage = MessageResponseDTO & {
  metadata?: MessageMetadata;
};

// ---- Per-turn segments ----
// A turn is a chronologically ordered sequence of these segments.
// Consecutive tool_call / tool_result messages collapse into a single "tools"
// segment; every other kind is one message = one segment.
export type TurnSegment =
  | { kind: "thinking"; id: string; content: string; done: boolean }
  | { kind: "text"; id: string; message: RawMessage | null; streamingContent?: string; done: boolean }
  | { kind: "tools"; id: string; messages: RawMessage[] }
  | { kind: "ask"; id: string; message: RawMessage }
  | { kind: "structured"; id: string; message: RawMessage };

// Conversation turn = user + segments (ordered assistant-side blocks)
export interface ConversationTurn {
  id: string;
  userMessage: RawMessage;
  // Segment-based view — optional until the segment refactor wires all consumers.
  segments?: TurnSegment[];
  // Flattened for downstream readers:
  //   toolCalls = flatten tools / ask / structured messages in order
  //   assistantMessage = last text segment's message
  toolCalls: RawMessage[];
  assistantMessage: RawMessage | null;
  isStreaming?: boolean;
  streamingStatus?: StatusEventMetadata;
}
