/**
 * WebSocket Types - 基于 websocket-api.md v1.1
 */

// =============================================================================
// 基础类型
// =============================================================================

/** 消息业务域 */
export type MessageDomain = "system" | "project" | "task" | "collab" | "canvas" | "wallet";

/** 实体操作类型 */
export type EntityAction = "CREATED" | "UPDATED" | "DELETED";

/** 实体类型 (小写) */
export type EntityType =
  | "script"
  | "episode"
  | "storyboard"
  | "character"
  | "scene"
  | "prop"
  | "style"
  | "asset"
  | "task";

/** Tab 类型 */
export type CollabTab =
  | "DETAIL"
  | "EPISODES"
  | "STORYBOARDS"
  | "CHARACTERS"
  | "SCENES"
  | "PROPS";

/** 协作状态 */
export type CollabStatus = "VIEWING" | "EDITING";

/** 任务状态 */
export type TaskStatus = "PENDING" | "RUNNING" | "COMPLETED" | "FAILED" | "CANCELLED";

// =============================================================================
// 客户端 → 服务器消息类型
// =============================================================================

export type ClientMessageType =
  | "PING"
  | "HEARTBEAT"
  | "ENTER_WORKSPACE"
  | "LEAVE_WORKSPACE"
  | "ENTER_SCRIPT"
  | "LEAVE_SCRIPT"
  | "SWITCH_TAB"
  | "FOCUS_ENTITY"
  | "BLUR_ENTITY"
  | "START_EDITING"
  | "STOP_EDITING";

/** 客户端发送的消息 */
export interface ClientMessage {
  type: ClientMessageType;
  workspaceId?: string;
  scriptId?: string;
  tab?: CollabTab;
  entityType?: string;
  entityId?: string;
}

// =============================================================================
// 服务器 → 客户端消息类型
// =============================================================================

export type ServerMessageType =
  | "CONNECTED"
  | "DISCONNECTED"
  | "PONG"
  | "ERROR"
  // Project domain
  | "ENTITY_CHANGED"
  | "ENTITY_UPDATED"
  | "ASSET_UPLOADED"
  // Task domain
  | "TASK_STATUS_CHANGED"
  | "TASK_PROGRESS"
  | "TASK_COMPLETED"
  | "TASK_FAILED"
  // Collab domain
  | "USER_JOINED"
  | "USER_LEFT"
  | "USER_LOCATION_CHANGED"
  | "SCRIPT_COLLABORATION"
  | "ENTITY_COLLABORATION"
  | "EDITING_LOCKED"
  | "EDITING_UNLOCKED"
  | "AGENT_ACTIVITY"
  // Comment domain (broadcast to script members)
  | "COMMENT_CREATED"
  | "COMMENT_UPDATED"
  | "COMMENT_DELETED"
  | "COMMENT_RESOLVED"
  | "COMMENT_REOPENED"
  | "COMMENT_REACTION"
  // Wallet domain (workspace-level broadcast)
  | "WALLET_BALANCE_CHANGED"
  // Notification domain (directed to individual user)
  | "NOTIFICATION"
  | "NOTIFICATION_COUNT";

// =============================================================================
// 统一服务器消息格式 (新格式)
// =============================================================================

export interface WebSocketMessage<T = unknown> {
  /** 消息类型 */
  type: ServerMessageType | string;
  /** 业务域 */
  domain?: MessageDomain;
  /** 操作类型 */
  action?: EntityAction;
  /** 实体类型 (小写) */
  entityType?: EntityType | string;
  /** 实体 ID */
  entityId?: string;
  /** 工作空间 ID */
  workspaceId?: string;
  /** 剧本 ID */
  scriptId?: string;
  /** 消息数据 */
  data?: T;
  /** 时间戳 (毫秒或 ISO 字符串) */
  timestamp?: number | string;
  /** 事件 ID (用于幂等性) */
  eventId?: string;
}

// =============================================================================
// 消息数据类型 - System Domain
// =============================================================================

/** CONNECTED 消息数据 */
export interface ConnectedData {
  sessionId: string;
  userId: string;
  workspaceId: string;
}

/** DISCONNECTED 消息数据 */
export interface DisconnectedData {
  reason: "TOKEN_EXPIRED" | "KICKED" | "DUPLICATE_SESSION" | "SERVER_SHUTDOWN";
  message: string;
}

/** ERROR 消息数据 */
export interface ErrorData {
  message: string;
  code?: string;
}

// =============================================================================
// 消息数据类型 - Project Domain
// =============================================================================

/** ENTITY_CHANGED 消息数据 */
export interface EntityChangedData {
  operatorId?: string;
  changedFields?: string[];
  entity?: Record<string, unknown>;
}

/** ASSET_UPLOADED 消息数据 */
export interface AssetUploadedData {
  assetId: string;
  fileName: string;
  fileUrl: string;
  thumbnailUrl?: string;
  fileSize: number;
  mimeType: string;
}

// =============================================================================
// 消息数据类型 - Task Domain
// =============================================================================

/** TASK_STATUS_CHANGED 消息数据 */
export interface TaskStatusData {
  status: TaskStatus;
  progress?: number;
  taskType?: string;
  taskName?: string;
}

/** TASK_PROGRESS 消息数据 */
export interface TaskProgressData {
  progress: number;
  currentStep?: string;
  totalSteps?: number;
  currentStepIndex?: number;
}

/** TASK_COMPLETED 消息数据 */
export interface TaskCompletedData {
  status: "COMPLETED";
  taskType?: string;
  taskName?: string;
  result?: {
    assetId?: string;
    fileUrl?: string;
    [key: string]: unknown;
  };
}

/** TASK_FAILED 消息数据 */
export interface TaskFailedData {
  errorMessage?: string;
}

// =============================================================================
// 消息数据类型 - Collab Domain
// =============================================================================

/** 协作用户信息 */
export interface CollabUser {
  userId: string;
  nickname: string;
  avatar?: string;
  tab?: CollabTab;
  focusedEntityType?: string | null;
  focusedEntityId?: string | null;
  collabStatus?: CollabStatus;
}

/** USER_JOINED 消息数据 */
export interface UserJoinedData {
  scriptId: string;
  page?: string;
  user: CollabUser;
}

/** USER_LEFT 消息数据 */
export interface UserLeftData {
  scriptId: string;
  userId: string;
  nickname?: string;
}

/** USER_LOCATION_CHANGED 消息数据 */
export interface UserLocationChangedData {
  scriptId: string;
  user: CollabUser;
  previousTab?: CollabTab;
  previousEntityType?: string;
  previousEntityId?: string;
}

/** SCRIPT_COLLABORATION 消息数据 */
export interface ScriptCollaborationData {
  scriptId: string;
  totalUsers: number;
  users: CollabUser[];
  tabUserCounts: Partial<Record<CollabTab, number>>;
}

/** ENTITY_COLLABORATION 消息数据 */
export interface EntityCollaborationData {
  scriptId: string;
  entityType: string;
  entityId: string;
  viewers: CollabUser[];
  editor: CollabUser | null;
}

/** EDITING_LOCKED 消息数据 */
export interface EditingLockedData {
  entityType: string;
  entityId: string;
  lockedBy: {
    userId: string;
    nickname: string;
    avatar?: string;
  };
}

/** EDITING_UNLOCKED 消息数据 */
export interface EditingUnlockedData {
  entityType: string;
  entityId: string;
  previousEditor?: {
    userId: string;
    nickname: string;
  };
}

/** AGENT_ACTIVITY 消息数据 */
export interface AgentActivityData {
  agentId: string;
  agentName: string;
  activityType: string;
  targetEntityType?: string;
  targetEntityId?: string;
  message?: string;
}

// =============================================================================
// Provider 状态类型
// =============================================================================

/** 连接状态 */
export type ConnectionStatus = "connecting" | "connected" | "disconnected" | "error";

/** 脚本协作状态 */
export interface ScriptCollabState {
  scriptId: string;
  users: CollabUser[];
  totalUsers: number;
  tabUserCounts: Partial<Record<CollabTab, number>>;
}

/** WebSocket Provider 上下文值 */
export interface WebSocketContextValue {
  // 连接状态
  status: ConnectionStatus;
  /** @deprecated 使用 status === "connected" 代替 */
  connected: boolean;
  sessionId: string | null;
  /** Increments on each reconnect (not initial connect). Use as a dependency to trigger re-fetch. */
  reconnectCount: number;

  // 协作状态
  currentScriptId: string | null;
  scriptCollabs: Map<string, ScriptCollabState>;

  // 连接方法
  connect: () => void;
  disconnect: () => void;

  // 工作空间方法
  enterWorkspace: (workspaceId: string) => void;
  leaveWorkspace: (workspaceId: string) => void;

  // 剧本方法
  enterScript: (scriptId: string, tab?: CollabTab) => void;
  leaveScript: () => void;
  switchTab: (tab: CollabTab) => void;

  // 实体方法
  focusEntity: (entityType: string, entityId: string) => void;
  blurEntity: (entityType: string, entityId: string) => void;
  startEditing: (entityType: string, entityId: string) => void;
  stopEditing: (entityType: string, entityId: string) => void;

  // 消息订阅
  subscribe: (handler: MessageHandler) => () => void;

  // 辅助方法
  getScriptUsers: (scriptId: string) => CollabUser[];
}

/** 消息处理函数类型 */
export type MessageHandler = (message: WebSocketMessage) => void;

// =============================================================================
// 消息数据类型 - Comment Domain
// =============================================================================

/** COMMENT_CREATED 消息数据 */
export interface CommentCreatedData {
  comment: {
    id: string;
    targetType: string;
    targetId: string;
    scriptId: string | null;
    parentId: string | null;
    content: string;
    status: string;
    author: { id: string; nickname: string | null; avatar: string | null };
    replyCount: number;
    reactions: Array<{ emoji: string; count: number; reacted: boolean }>;
    latestReplies: unknown[];
    mentions?: Array<{ type: string; id: string; name: string; offset: number; length: number }>;
    attachments?: Array<{ assetId: string; assetType?: string; fileName?: string; fileUrl?: string; thumbnailUrl?: string; fileSize?: number; mimeType?: string }>;
    createdAt: string;
  };
}

/** COMMENT_UPDATED 消息数据 */
export interface CommentUpdatedData {
  comment: CommentCreatedData["comment"] & { updatedAt: string };
}

/** COMMENT_DELETED 消息数据 */
export interface CommentDeletedData {
  commentId: string;
  targetType: string;
  targetId: string;
}

/** COMMENT_RESOLVED 消息数据 */
export interface CommentResolvedData {
  commentId: string;
  resolvedBy: string;
}

/** COMMENT_REOPENED 消息数据 */
export interface CommentReopenedData {
  commentId: string;
}

/** COMMENT_REACTION 消息数据 */
export interface CommentReactionData {
  commentId: string;
  emoji: string;
  action: "ADDED" | "REMOVED";
  userId: string;
}

// =============================================================================
// 消息数据类型 - Notification Domain
// =============================================================================

/** NOTIFICATION 消息数据（定向推送给被提及/关注用户） */
export interface NotificationMessageData {
  id: string;
  type: "COMMENT_MENTION" | "COMMENT_REPLY";
  title: string;
  sender: { id: string; name: string };
  payload: {
    commentId?: string;
    targetType?: string;
    targetId?: string;
    scriptId?: string;
  };
}

/** NOTIFICATION_COUNT 消息数据（未读数实时更新） */
export interface NotificationCountData {
  total: number;
  delta: number;
}

// =============================================================================
// 消息数据类型 - Wallet Domain
// =============================================================================

/** WALLET_BALANCE_CHANGED 交易类型 */
export type WalletTransactionType = "TOPUP" | "CONSUME" | "FREEZE" | "UNFREEZE";

/** WALLET_BALANCE_CHANGED 消息数据 */
export interface WalletBalanceChangedData {
  /** 变动后可用余额 */
  balance: number;
  /** 变动后冻结金额 */
  frozen: number;
  /** 本次变动金额（正=增加，负=减少） */
  delta: number;
  /** 交易类型 */
  transactionType: WalletTransactionType;
  /** 交易记录 ID */
  transactionId: string;
}

// =============================================================================
// 配置常量
// =============================================================================

/** 心跳间隔 (毫秒) - API 文档建议 45 秒 */
export const HEARTBEAT_INTERVAL = 45000;

/** 最大重连次数 */
export const MAX_RECONNECT_ATTEMPTS = 5;

/** 消息去重缓存大小 */
export const MAX_PROCESSED_EVENTS = 1000;
