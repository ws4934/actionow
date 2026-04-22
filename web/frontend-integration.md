# Actionow Agent 前端接入说明

> 适用版本：actionow-agent 当前主干分支
> 覆盖：HITL Batch1/2/3、status / structured_data / ask_user SSE 事件、SSE↔DB 段落化一致性（per-segment-write / skip-placeholder）
> 面向读者：前端 Web / 移动 H5 / 桌面端对接开发者

本文档覆盖 agent 模块所有**前端直连**接口与 SSE 事件协议。内部 Feign / Internal Controller 不在范围内。

**核心心智模型**：SSE 事件流与 DB 消息行 1:1 对应（同一 `eventId`）。前端只有一套渲染逻辑 —— 不管是实时消费 SSE 还是刷新历史（`GET /messages`），看到的段落粒度、合并规则、状态语义完全一致。详见 §1.3。

---

## 0. 鉴权与公共约定

### 0.1 Header

所有接口都需经网关鉴权，前端只关心以下 header：

| Header | 说明 |
|---|---|
| `Authorization: Bearer <jwt>` | 登录 token，由网关下发 |
| `X-Workspace-Id: <workspaceId>` | 工作区 ID；网关会自动注入，但部分场景允许前端显式覆盖 |

### 0.2 Result 响应包装

除 SSE 流式接口外，所有接口返回统一 `Result<T>`：

```ts
interface Result<T> {
  code: string;        // "0000000" 表示成功；其它为业务错误码
  message: string;     // 操作成功 | 错误描述
  data?: T;            // code == "0000000" 时才存在
  requestId?: string;
  timestamp: number;   // ms epoch
}
```

前端统一判定 `code === "0000000"` 作为业务成功；HTTP 状态码大多为 200，业务错误通过 code/message 区分。

### 0.3 分页

列表类接口统一 `PageResult<T>`：

```ts
interface PageResult<T> {
  current: number;    // 当前页（1-based）
  size: number;       // 每页条数
  total: number;      // 总条数
  pages: number;      // 总页数
  records: T[];
}
```

### 0.4 时间

所有 `ISO8601` / `LocalDateTime` 字段按 `2026-04-19T15:30:00` 格式返回（UTC 无时区后缀，需前端按本地时区处理）。
毫秒级时间戳字段统一以 `xxxMs` 结尾。

### 0.5 常见错误码

| code | 含义 | HTTP |
|---|---|---|
| `0000000` | 成功 | 200 |
| `0709003` | 该 Agent 不支持直接创建独立会话 | 400 |
| `0709010` | 该端点不支持流式调用（或反之） | 400 |
| `0709020` | 当前没有正在进行的生成任务 | 409 |
| `0709021` | 取消请求已发送或执行已完成 | 409 |
| `0709030` | 会话无法归档（可能已经是归档状态） | 409 |
| `0709031` | 会话无法恢复（可能不是归档状态） | 409 |
| `0709040` | 无权限访问他人的会话 | 403 |

### 0.6 SSE 事件 ID 与重连

所有通过 `/messages/stream` 或 `/stream` 端点下发的 SSE 事件都带有单调递增的 **eventId**（per-session）。事件帧格式：

```
id: 12
event: message
data: {"type":"message","eventId":12,"content":"...","timestamp":"..."}
```

前端必须记录最近一次收到的 `eventId`，断线重连时通过 `Last-Event-ID` 头或 `lastEventId` 查询参数传回，服务端会从缓冲回放"大于该 id"的事件。详见 §2.3 / §2.4 / §3.11。

---

## 1. 会话（Session）生命周期

会话是一次对话上下文的容器，绑定 `userId + workspaceId + agentType`，可以反复发送消息。

### 1.1 创建会话

```
POST /agent/sessions
```

请求体：

```ts
interface CreateSessionRequest {
  agentType?: string;                // 默认 "COORDINATOR"
  skillNames?: string[];             // 会话级技能白名单（可选）
  scriptId?: string;                 // 兼容字段，等价于 scopeContext.scriptId
  scopeContext?: {                   // 动态作用域
    scriptId?: string;
    episodeId?: string;
    storyboardId?: string;
    characterId?: string;
    sceneId?: string;
    propId?: string;
    styleId?: string;
    assetId?: string;
  };
}
```

返回：`Result<SessionResponse>`

```ts
interface SessionResponse {
  id: string;
  agentType: string;
  userId: string;
  workspaceId: string;
  scriptId?: string;
  title?: string;
  status: "active" | "generating" | "completed" | "archived" | "expired";
  messageCount: number;
  totalTokens?: number;
  generating: boolean;               // 是否正在出字（仅供 UI 兜底展示）
  createdAt: string;
  lastActiveAt: string;
  archivedAt?: string;
}
```

### 1.2 会话详情 / 列表 / 活跃 / 归档

| 接口 | 说明 |
|---|---|
| `GET /agent/sessions/{sessionId}` | 单会话详情 |
| `GET /agent/sessions?page=1&size=20&standalone=&scriptId=` | 分页列表 |
| `GET /agent/sessions/active?limit=20` | 活跃会话快照 |
| `GET /agent/sessions/archived?limit=50` | 归档会话列表 |

### 1.3 消息历史

```
GET /agent/sessions/{sessionId}/messages
```

返回：`Result<MessageResponse[]>`（按 `sequence` 升序）

```ts
interface MessageResponse {
  id: string;
  sessionId: string;
  role: "user" | "assistant" | "tool" | "system" | "tool_call" | "tool_result";
  content: string;
  sequence: number;
  status: "generating" | "completed" | "failed" | "cancelled";
  eventType?: string;                // 与 SSE 事件 type 对齐（message / thinking / tool_call / ...）

  // 工具相关（role=tool_call / tool_result 时出现）
  toolCallId?: string;
  toolName?: string;
  skillName?: string;
  agentName?: string;
  toolArguments?: Record<string, any>;
  toolSuccess?: boolean;
  toolResult?: Record<string, any>;

  iteration?: number;
  metadata?: Record<string, any>;    // 含 structured_data / ask_user 的原始载荷
  attachmentIds?: string[];
  attachments?: AttachmentInfo[];
  createdAt: string;
}
```

> **注意**：`metadata` 中会保留 `status` / `structured_data` / `ask_user` 事件的完整 payload；前端刷新历史后，可据此还原这三类事件的 UI 状态（如已完成的结构化表格、已回答的 ask 弹窗）。

#### 1.3.1 SSE↔DB 对应关系总览

| 实时流（SSE 事件） | 历史（`GET /messages` 行） |
|---|---|
| `message`（per-segment-write 开启） | 一条 `role=assistant`、`status=completed`、`metadata.kind=assistant_segment` 的 DB 行，同 `eventId` |
| `message`（legacy 模式） | 写入同一 placeholder 行的累积 content，读时按空行切成多段 `legacy_split` |
| `tool_call` / `tool_result` | 一条 `role=tool_call` / `role=tool_result` 的 DB 行（订阅回调实时落库，非终态批写） |
| `status` / `structured_data` / `ask_user` | `metadata` 中保留完整 payload 的 assistant 投影行 |
| `heartbeat` | 不落 message 行（仅更新 session `last_heartbeat_at`） |
| `done` / `error` / `cancelled` | 终态转换，推动占位行 / session generating 状态收尾 |

**不变量**：同一 `eventId` 在 SSE 上只下发一次、在 DB 上只对应一行（由 `(session_id, event_id)` 索引保证幂等，跨 pod 重放也不会产生重复行）。

#### 1.3.2 assistant 消息的段落化约定

为让历史回放与 SSE 流展现一致（每次 LLM 输出的文本紧跟着它触发的 tool_call / tool_result），后端将单个 turn 的 assistant 文本拆成多条 `role="assistant"` 行。**无论新旧数据，前端只按 `sequence` + `metadata.segmentIndex` 稳定排序后逐行渲染**：

- **新数据（per-segment-write 开启）**：每个 SSE `message` 事件在下发的同一时刻独立入库为一条 `status=completed` 的 assistant 行。后端 `GET /messages` 会自动过滤"status=completed + content 空 + 无 tool payload"的占位行 —— 前端不会看到它们。段落行 `metadata`：
  ```ts
  metadata: {
    kind: "assistant_segment";
    eventId: number;         // 与 SSE 事件的 eventId 一一对应，可反查原始流
    iteration?: number;      // ReAct 迭代轮次
  }
  ```
  若 `skip-placeholder` 未启用，"正在生成"期间会返回一条空的 `status=generating` 行用于显示光标；skip-placeholder 启用后此行也不会出现（改由 `/state` 承载 in-flight 状态，见 §1.3.4）。

- **旧数据（per-segment-write 关闭 / 历史 turn）**：整段 blob 存在一条 placeholder 行里。后端返回前按空行启发式切成多段 `MessageResponse`（同 `sequence`，`id` 形如 `{原id}#{index}`），`metadata`：
  ```ts
  metadata: {
    segmentSource: "legacy_split";
    segmentIndex: number;    // 子段序号
    segmentTotal: number;    // 子段总数
  }
  ```
  可通过 `actionow.agent.message.segment-split.enabled=false` 关闭读时拆分得到原始 blob（调试用）。

#### 1.3.3 相邻同类合并（默认开启）

纯事件粒度会把"一气呵成的回答"切成多个小气泡。后端在返回前将相邻的段落行重新缝合：

- 分组键：`role=assistant` + `status=completed` + `metadata.kind=assistant_segment`（或 `metadata.segmentSource=legacy_split`）
- 边界（强制 flush）：`role=user` / `role=tool` / 正在生成的 assistant（`status=generating`）/ 其它 assistant 投影行（MissionCard 等）
- 合并后的行：
    - `id` / `sequence` / `createdAt` / `iteration` 取首条
    - `content` 用 `\n\n` 连接
    - `metadata` 追加：
      ```ts
      metadata: {
        collapsed: true;
        collapsedCount: number;                   // 合并进来的原始行数
        collapsedEventIds: (number | null)[];     // 原始 eventId 列表，可埋点 / 反查
      }
      ```

可通过 `actionow.agent.message.collapse-adjacent.enabled=false` 关闭得到原始事件粒度（调试 / 对拍场景）。**LLM 历史回灌也使用同一套合并规则**，保证模型看到的上下文与用户看到的一致。

#### 1.3.4 "正在生成"状态的承载位置

开启 `actionow.agent.message.skip-placeholder.enabled=true` 后，后端不再为每次生成写空 placeholder 行；`/sessions/{id}/state` 返回的 `generation` 字段改由 `t_agent_session.generating_since` / `last_heartbeat_at` 推导：

| 字段 | skip-placeholder OFF（旧） | skip-placeholder ON（新） |
|---|---|---|
| `placeholderMessageId` | placeholder 行的 `message.id` | **恒为 `null`** |
| `startedAt` | placeholder `created_at` | `session.generating_since` |
| `lastHeartbeatAt` | placeholder `last_heartbeat_at` | `session.last_heartbeat_at` |
| `staleMs` | now − startedAt | 同左，语义不变 |
| `inFlight` | `placeholderMessageId != null \|\| registryActive` | `session.generating_since != null \|\| registryActive` |

**前端契约**：`generation.inFlight` 是"是否正在生成"的唯一判据；不要再依赖 `placeholderMessageId` 是否存在来判断状态。`generation.inFlight === false` 就可以停止订阅 SSE。

#### 1.3.5 特性开关速查

| 开关（`actionow.agent.message.*`） | 默认 | 作用侧 | 依赖 |
|---|---|---|---|
| `per-segment-write.enabled` | false | 写入（SSE 每 `message` 事件独立入库） | — |
| `segment-split.enabled` | true | 读（把 legacy blob 切成多段） | — |
| `collapse-adjacent.enabled` | true | 读 + LLM 回灌（合并相邻段落） | — |
| `skip-placeholder.enabled` | false | 写入（不再为每次生成建 placeholder 行） | **必须** `per-segment-write.enabled=true` |

前端**不感知**这些开关的具体状态 —— 读到的数据形态都统一：按 `sequence` 升序逐行渲染，合并行当单条气泡处理，`inFlight` 看 `/state.generation`。上述灰度切换对现有客户端保持向后兼容。

### 1.4 控制操作

| 接口 | 说明 | 注意 |
|---|---|---|
| `POST /agent/sessions/{sessionId}/end` | 结束会话 | 幂等 |
| `POST /agent/sessions/{sessionId}/cancel` | **取消正在进行的生成**（见下） | 并发控制 |
| `POST /agent/sessions/{sessionId}/archive` | 归档 | 409 `0709030` |
| `POST /agent/sessions/{sessionId}/resume` | 从归档恢复 | 409 `0709031` |
| `DELETE /agent/sessions/{sessionId}` | 删除（软删） | — |

#### cancel 返回体

```ts
interface Result<{
  sessionId: string;
  cancelled: boolean;
  cancelledAsks: number;   // 同时被级联取消的 HITL 待答数量
  elapsedMs?: number;
}> {}
```

> **级联行为**：cancel 会同时终止该 session 下所有 pending HITL `ask_user`，并让工具线程以“被取消”异常结束；前端收到 `cancelled` SSE 事件后应关闭所有 ask 弹窗。

---

## 2. 消息发送（同步 + 流式）

### 2.1 同步消息

```
POST /agent/sessions/{sessionId}/messages
Content-Type: application/json
```

适合**非交互式**/短任务。会一直阻塞直到 agent 产出完整 `AgentResponse`。

```ts
interface SendMessageRequest {
  message: string;                   // 必填
  attachmentIds?: string[];          // 已上传资产 ID
  attachments?: InlineAttachment[];  // 内联附件（最多 10 个）
  skillNames?: string[] | null;      // null=沿用 session 配置；[]=纯对话；其它=覆盖
  stream?: false;                    // 此端点必须 false / 省略
  executionMode?: "CHAT" | "MISSION";
  missionId?: string;
  missionStepId?: string;
  scope?: string;                    // "global" | "script" | "episode" | ... | "asset"
  scriptId?: string;                 // 一次性覆盖作用域锚点
  episodeId?: string;
  storyboardId?: string;
  characterId?: string;
  sceneId?: string;
  propId?: string;
  styleId?: string;
  assetId?: string;
}

interface InlineAttachment {
  mimeType: string;                  // e.g. "image/jpeg"
  data?: string;                     // base64，不含 data: 前缀；与 url 二选一
  url?: string;
  fileName?: string;
  fileSize?: number;
}
```

返回：`Result<AgentResponse>`，`AgentResponse` 字段见 2.2 末尾。

### 2.2 **流式消息**（推荐 / HITL 必须）

```
POST /agent/sessions/{sessionId}/messages/stream
Content-Type: application/json
Accept: text/event-stream
```

请求体与同步一致，但 `stream: true`。

响应体为 SSE 流，结构：

```
event: <event-name>
data: <json-payload>

event: <event-name>
data: <json-payload>

...
```

**所有事件的 data 字段结构统一**（均为 `AgentStreamEvent` 的 JSON 序列化），可按 `type` 字段分发：

```ts
interface AgentStreamEvent {
  type: "message" | "thinking" | "tool_call" | "tool_result" |
        "status" | "structured_data" | "ask_user" |
        "heartbeat" | "resync_required" |
        "done" | "error" | "cancelled";
  eventId?: number;                  // per-session 单调递增；前端记录后用于 Last-Event-ID 重连
  agentName?: string;
  content?: string;
  toolCallId?: string;
  toolName?: string;
  skillName?: string;
  toolArguments?: Record<string, any>;
  toolSuccess?: boolean;
  toolResult?: Record<string, any>;
  iteration?: number;
  timestamp: string;                 // ISO8601
  metadata?: Record<string, any>;

  // done 专属统计
  elapsedMs?: number;
  totalToolCalls?: number;
  estimatedTokens?: number;

  // error 专属
  partialDueToError?: boolean;
  errorMessage?: string;
}
```

**AgentResponse**（同步端点 / done 事件的聚合视图）：

```ts
interface AgentResponse {
  content: string;
  toolCalls: Array<{ toolCallId; toolName; toolArguments; skillName?; agentName? }>;
  toolResults: Array<{ toolCallId; toolName; toolSuccess; toolResult; skillName? }>;
  iteration: number;
  elapsedMs: number;
  totalToolCalls: number;
  estimatedTokens: number;
}
```

### 2.3 会话恢复状态（进入对话页 / SSE 重连前必调）

```
GET /agent/sessions/{sessionId}/state
```

用于"进入对话页"或"SSE 断线后恢复"前做一次性权威探测，避免前端在无上下文情况下盲目重连。

返回：`Result<SessionStateResponse>`

```ts
interface SessionStateResponse {
  session: {
    id: string;
    agentType: string;
    title?: string;
    status: "active" | "generating" | "completed" | "archived" | "expired";
    messageCount: number;
    totalTokens?: number;
    createdAt: string;
    lastActiveAt: string;
  };
  generation: {
    inFlight: boolean;                 // true = 还有 in-flight 生成，前端应订阅 /stream
    placeholderMessageId?: string;     // 对应 DB 里 generating 状态的 assistant 占位消息
    startedAt?: string;
    lastHeartbeatAt?: string;          // 最近一次心跳写入 DB 的时间（跨 pod 可见）
    staleMs?: number;                  // now - startedAt，毫秒
  };
  pendingAsk: {
    pending: boolean;
    askId?: string;
    question?: string;
    inputType?: string;
    choices?: any[];
    constraints?: any;
    deadlineMs?: number;
    expiresAt?: string;
  };
  lastEventId: number;                 // 服务端当前最大 eventId —— 重连时作为 Last-Event-ID 使用
  resumeHint: "IDLE" | "RESUME_STREAM" | "ANSWER_ASK";
}
```

**resumeHint 前端分发逻辑**：

| resumeHint | 前端动作 |
|---|---|
| `IDLE` | 仅渲染历史消息，不订阅 SSE |
| `RESUME_STREAM` | 渲染历史 → `GET /stream?lastEventId={lastEventId}` 继续消费增量 |
| `ANSWER_ASK` | 渲染历史 → 恢复 HITL 弹窗（见 §4）→ SSE 重连保持挂起态，等待用户提交 |

**错误**：`code=0709040` / HTTP 403 — 调用者不是该 session 的创建者。

### 2.4 SSE 重连（恢复 in-flight 流）

```
GET /agent/sessions/{sessionId}/stream
Accept: text/event-stream
```

**请求头或查询参数二选一指定 lastEventId**：

```
Last-Event-ID: 12
```

或

```
GET /agent/sessions/{sessionId}/stream?lastEventId=12
```

> 浏览器原生 `EventSource` 会自动带上 `Last-Event-ID`；用 `fetch + ReadableStream` 手动解析时也要带。`lastEventId` 查询参数是给非原生客户端 / 调试用的 fallback。未携带时视为 `0`。

**行为**：
1. 立即回放缓冲中"> lastEventId" 的事件（按序）；
2. 若 lastEventId 已滚出缓冲窗口 → 回放替换为单条 `resync_required`（见 §3.12），后续实时事件照常推送；
3. 继续推送原 in-flight 流的新事件，直到收到终态 `done` / `error` / `cancelled`；
4. 若原 stream 已经终止 → 流几乎立即完成；前端应结合 §2.3 `/state` 判定（`inFlight=false` 表示无需订阅）。

**注意事项**：
- 不会触发新的 LLM 调用、不会消费积分；纯订阅。
- 同一会话同一时刻只有一个活跃 sink；再次调用 `/stream` 会替换既有 sink（老 HTTP 连接由前端关闭或超时自然回收）。
- 跨 pod 场景下，事件通过 Redis Streams 在所有 pod 间同步，任意 pod 收到的重连请求都能看到完整事件流。

---

## 3. SSE 事件分类详解

### 3.1 `message` — 文本增量
Agent 的正式回复，按 token 流式下发。前端直接拼接 `content` 即可。

### 3.2 `thinking` — 思考过程
仅当模型开启 reasoning 时出现。建议折叠展示，不与 `message` 混排。

### 3.3 `tool_call` — 工具调用开始
```json
{
  "type": "tool_call",
  "toolCallId": "call_abc",
  "toolName": "image_gen",
  "skillName": "分镜生成",
  "toolArguments": { "prompt": "..." },
  "iteration": 2
}
```
UI 建议：显示 "调用 {toolName}..." 占位；保存 `toolCallId` 便于和 `tool_result` 匹配。

### 3.4 `tool_result` — 工具调用结果
`toolSuccess=false` 时 `toolResult` 一般包含 `error` 字段；UI 应给出错误态样式。

### 3.5 `status` — 阶段性状态（结构化）
```json
{
  "type": "status",
  "content": "加载技能中",
  "metadata": {
    "phase": "skill_loading",
    "label": "加载技能中",
    "progress": 0.3,
    "details": { "skillName": "分镜生成" }
  }
}
```

当前已实现的 `phase`：`skill_loading` / `rag_retrieval` / `llm_invoking` / `tool_batch_progress` / `mission_step` / `preflight` / `context_preparing`。前端按 `label` 直接展示即可；`progress` 为 null 时显示不确定进度条。

### 3.6 `structured_data` — 结构化输出
```json
{
  "type": "structured_data",
  "metadata": {
    "schemaRef": "storyboard_gen",
    "data": { "storyboardId": "sb_1", "imageUrl": "https://..." },
    "rendererHint": "card"
  }
}
```

> **前端规范**：收到此事件后，按 `schemaRef` 去 `GET /agent/skills/{schemaRef}/output-schema` 拉取 JSON Schema（建议缓存）；根据 `rendererHint`（`card` / `table` / `form` / `chart` / `markdown`）选择对应的渲染组件。

### 3.7 `ask_user` — HITL 询问（阻塞）

```json
{
  "type": "ask_user",
  "content": "需要您确认这个版本是否保留",
  "metadata": {
    "askId": "ask-1a2b3c4d",
    "question": "需要您确认这个版本是否保留",
    "inputType": "single_choice",
    "choices": [
      { "id": "v1", "label": "保留当前版本" },
      { "id": "v2", "label": "采用新版本", "description": "将覆盖旧正文" }
    ],
    "constraints": null,
    "deadlineMs": 180000
  }
}
```

`inputType` 共 5 种，`choices` / `constraints` 字段按类型存在与否见下表：

| inputType | choices | constraints 字段 | 前端控件 |
|---|---|---|---|
| `single_choice` | ✔ | — | 单选列表 / 按钮组 |
| `multi_choice` | ✔ | `{ minSelect, maxSelect }` | 多选列表（按约束校验） |
| `confirm` | 固定 `[yes/no]` | — | 确认对话框 |
| `text` | — | `{ minLength, maxLength }` | 输入框 |
| `number` | — | `{ min, max }` | 数字输入框 |

**前端流程**：
1. 收到 `ask_user` 后弹出对应形态的对话框；
2. 启动本地倒计时（按 `deadlineMs`），超时自动关闭（后端也会超时）；
3. 用户选择/输入后，调用 **§4.1 提交答案**；
4. SSE 流会继续推进；若后端接收到答案前用户主动关闭弹窗，调用 **§4.2 取消**，可传 `rejected: true` 或直接 DELETE。

### 3.8 `done` — 执行完成
```json
{
  "type": "done",
  "iteration": 5,
  "elapsedMs": 8421,
  "totalToolCalls": 3,
  "estimatedTokens": 2100
}
```

### 3.9 `error` — 执行出错
```json
{
  "type": "error",
  "content": "模型调用失败：timeout",
  "partialDueToError": true,
  "errorMessage": "Upstream timeout after 30s"
}
```

`partialDueToError=true` 时代表前序 `message` 已经产出了部分内容，UI 可选择保留并追加错误标记。

### 3.10 `cancelled` — 被用户取消
```json
{
  "type": "cancelled",
  "content": "...已生成的部分...",
  "iteration": 3
}
```

### 3.11 `heartbeat` — 生成中脉搏

Agent 进入 generating 状态后，后端每 `~5s` 下发一次心跳，告知前端"仍在活跃处理"。

```json
{
  "type": "heartbeat",
  "eventId": 42,
  "metadata": {
    "elapsedMs": 17320,
    "serverTime": "2026-04-19T15:30:17.320"
  }
}
```

**前端规范**：
- 不计入消息列表；仅更新"后端仍在工作"的状态栏 UI。
- 若 **>15s** 未收到任何事件（心跳或其它），视为疑似卡死 → 调用 `GET /sessions/{id}/state` 探测，必要时走 `GET /sessions/{id}/stream` 重连。
- 心跳事件也有 `eventId`，会占用 Last-Event-ID 序列，不必特殊跳过。

### 3.12 `resync_required` — 回放间隙（必须走全量对齐）

客户端用过期的 `Last-Event-ID` 重连时，如果要回放的段落已经滚出服务端缓冲环（默认 200 条 / 120s），后端会**立即下发**一条 `resync_required` 事件并继续推送新事件。**前端收到此事件后必须丢弃所有增量缓存，走 §2.3 `/state` + §1.3 `/messages` 全量对齐**。

```json
{
  "type": "resync_required",
  "metadata": {
    "clientLastEventId": 5,
    "oldestAvailableEventId": 180,
    "serverMaxEventId": 370
  }
}
```

字段释义：

| 字段 | 含义 |
|---|---|
| `clientLastEventId` | 客户端本次重连声明的 lastEventId |
| `oldestAvailableEventId` | 当前缓冲最老 eventId（为 null 表示缓冲已空） |
| `serverMaxEventId` | 服务端当前最大 eventId —— 对齐后应从此值继续 |

**前端推荐流程**：

```
1. 收到 resync_required
   └─> 清空本地增量缓存 / 占位消息待定态
2. GET /agent/sessions/{id}/state
   └─> 读 session 状态、pendingAsk、lastEventId、resumeHint
3. GET /agent/sessions/{id}/messages
   └─> 重建消息时间线（含 status / structured_data / ask_user metadata）
4. 按 resumeHint 决定：
   - ANSWER_ASK   → 恢复 HITL 弹窗
   - RESUME_STREAM→ 以 state.lastEventId 再次发起 /stream 重连
   - IDLE         → 停留在会话页，不再订阅
```

---

## 4. HITL 交互（Ask / Answer）

### 4.1 查询 pending 状态

```
GET /agent/sessions/{sessionId}/ask/pending
```

进入对话页 / SSE 断线重连后调用，**精确恢复 HITL 弹窗**。相比 §2.3 `/state`（一次拉全量会话状态），此端点更轻量，可单独用于"只刷新弹窗"的轮询 / 补偿场景。

返回：`Result<PendingAskResponse>`

```ts
interface PendingAskResponse {
  pending: boolean;              // false 时其余字段可能为 null
  askId?: string;
  question?: string;
  inputType?: "single_choice" | "multi_choice" | "confirm" | "text" | "number";
  choices?: Array<{ id: string; label: string; description?: string }>;
  constraints?: {                 // 见 §3.7
    minSelect?: number; maxSelect?: number;
    minLength?: number; maxLength?: number;
    min?: number; max?: number;
  };
  timeoutSec?: number;            // 服务端生效的超时秒数
  createdAt?: string;             // PENDING 行落库时间（UTC）
  remainingSec?: number;          // max(0, timeoutSec − (now − createdAt))；null 表示未配置超时
}
```

**前端使用建议**：
- 进入会话页 → 先调 §2.3 `/state`；若 `resumeHint = ANSWER_ASK`，`state.pendingAsk` 已经足够。
- 仅弹窗状态需要刷新（如用户切了 Tab 回来）→ 调本端点即可，避免重拉全量 state。
- `remainingSec === 0` 但 `pending = true`：服务端尚未把该行置为 `TIMEOUT`（通常 ≤ 5s 延迟），前端可提示"即将超时"并停止提交。

**错误**：`code=0709040` / HTTP 403 — 调用者不是该 session 的创建者。

> **与 `/state.pendingAsk` 的字段差异**：`/state.pendingAsk` 采用 `deadlineMs` / `expiresAt`（绝对时间）；本端点采用 `timeoutSec` / `createdAt` / `remainingSec`（相对时间 + 起点）。功能等价，按场景选一即可。

### 4.2 提交答案

```
POST /agent/sessions/{sessionId}/ask/{askId}/answer
Content-Type: application/json
```

```ts
interface UserAnswer {
  answer?: string;           // single_choice / confirm / text / number
  multiAnswer?: string[];    // multi_choice
  rejected?: boolean;        // 用户关闭弹窗/拒绝作答
  extras?: Record<string, any>;
}
```

各 inputType 的示例：

```jsonc
// single_choice
{ "answer": "v2" }

// multi_choice
{ "multiAnswer": ["tag_action", "tag_drama"] }

// confirm
{ "answer": "yes" }    // 或 "no"

// text
{ "answer": "开场改成黄昏时分的码头。" }

// number
{ "answer": "42" }

// 拒绝作答（任意类型）
{ "rejected": true }
```

返回：`Result<Void>`（code=`0000000` 即成功）

**错误**：`code=0709040`（HTTP 403）— 调用者不是该 session 的创建者。

### 4.3 取消询问

```
DELETE /agent/sessions/{sessionId}/ask/{askId}?reason=user_closed_dialog
```

与提交 `{rejected: true}` 语义等价，但**不把用户答案写入业务链路**，推荐在"用户点了 × 关闭弹窗"等场景使用。

### 4.4 服务端超时行为

- **默认超时**：`single_choice` / `multi_choice` / `text` / `number` = 180s；`confirm` = 120s。
- **最大上限**：600s。
- 超时后 Agent 的工具调用返回 `{ success: true, status: "TIMEOUT", askId }`，模型根据此继续推理（通常降级为默认行为）。
- 服务端在 `t_agent_ask_history` 表保留终态审计（`PENDING` / `ANSWERED` / `REJECTED` / `TIMEOUT` / `CANCELLED` / `ERROR`）。

### 4.5 多 Pod 容错

前端无需特殊处理。后端通过 Redis Pub/Sub 广播 + 15s 兜底轮询保证任何 Pod 收到的 answer 都能唤醒正确的工具线程。

---

## 5. 技能（Skill）查询

Agent 执行时会引用技能；前端需用以下接口展示技能信息、渲染结构化输出。

### 5.1 列表 / 详情

| 接口 | 说明 |
|---|---|
| `GET /agent/skills?page=1&size=20&keyword=` | 工作区可见技能分页 |
| `GET /agent/skills/{name}` | 单个技能详情（含 `content` / `references` / `examples`） |
| `GET /agent/skills/{name}/tools` | 该技能下的工具列表 |

`SkillResponse` 关键字段：

```ts
interface SkillResponse {
  id: string;
  name: string;                      // 唯一键，对应 schemaRef
  displayName: string;
  description: string;
  groupedToolIds: string[];
  outputSchema?: Record<string, any>; // 可为 null
  tags: string[];
  enabled: boolean;
  version: number;
  scope: "SYSTEM" | "WORKSPACE";
  content?: string;                  // 仅详情接口返回
  references?: any[];
  examples?: any[];
  createdAt: string;
  updatedAt: string;
}
```

### 5.2 获取 outputSchema

```
GET /agent/skills/{name}/output-schema
```

返回：`Result<JsonSchema | null>`。用于渲染 `structured_data` 事件。示例：

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "title": "分镜生成结果",
  "properties": {
    "storyboardId": { "type": "string" },
    "imageUrl":     { "type": "string", "format": "uri" },
    "description":  { "type": "string" }
  },
  "required": ["storyboardId", "imageUrl"]
}
```

前端实现建议：按 skill `name` 预拉取并缓存 schema；接收 `structured_data` 时直接查缓存。

---

## 6. Mission（长任务）接入

Mission 是由 Agent 自主切分的异步长任务（分镜批量生成、剧本全流程推进等）。前端典型流程：**Agent 对话中触发 → 返回 `missionId` → 订阅进度流 → 展示步骤明细**。

### 6.1 基础查询

所有接口前缀 `/agent/missions`：

| 接口 | 说明 |
|---|---|
| `GET /{id}` | 详情（含 `plan` / `status` / `progress` / `resultSummary` 等） |
| `GET ?current=1&size=20&status=` | 分页列表 |
| `GET /{id}/progress` | 轻量进度（含 `pendingTasks` 统计） |
| `GET /{id}/steps` | 全部 step 明细 |
| `GET /{id}/tasks` | 全部 task 明细 |
| `GET /{id}/events` | 事件流（历史回放） |
| `GET /{id}/traces` | Trace（用于调试） |
| `POST /{id}/cancel` | 取消 mission |

`MissionResponse`：

```ts
interface MissionResponse {
  id: string;
  workspaceId: string;
  runtimeSessionId: string;
  creatorId: string;
  title: string;
  goal: string;
  plan?: Record<string, any>;
  status: "CREATED" | "RUNNING" | "PAUSED" | "COMPLETED" | "FAILED" | "CANCELLED";
  currentStep: number;
  totalSteps: number;
  progress: number;                  // 0..100
  totalCreditCost: number;
  errorMessage?: string;
  resultSummary?: string;
  resultPayload?: Record<string, any>;
  failureCode?: string;
  startedAt: string;
  completedAt?: string;
  createdAt: string;
  updatedAt: string;
}
```

### 6.2 进度 SSE

```
GET /agent/missions/{id}/progress/stream
Accept: text/event-stream
```

事件类型：

| event | 含义 |
|---|---|
| `step_started` | 新步骤开始 |
| `step_completed` | 步骤完成 |
| `task_progress` | 任务进度更新 |
| `mission_completed` | 全部完成 |
| `mission_failed` | 失败 |
| `mission_cancelled` | 被取消 |

payload：

```ts
interface MissionSseEvent {
  missionId: string;
  eventType: string;
  status: string;
  message: string;
  progress?: number;
  currentStep?: number;
  data?: Record<string, any>;
  timestamp: string;
}
```

前端可与 `GET /progress` 组合：先拉一次快照，然后订阅流；断线重连后再拉一次快照兜底。

---

## 7. 前端实现最佳实践

### 7.1 SSE 接入要点

1. **EventSource 不支持 POST**。首发 `POST /messages/stream` 必须用 `fetch` + `ReadableStream` 手动解析，或使用 [`@microsoft/fetch-event-source`](https://github.com/Azure/fetch-event-source)。重连 `GET /stream` 可以直接用原生 `EventSource`（它自动携带 `Last-Event-ID`）。
2. **事件解析**：按 `event:` 与 `data:` 行分组；同一事件可跨多行 `data:`，拼接后再 `JSON.parse`。`id:` 行对应 `eventId`，务必记录下来。
3. **记录 lastEventId**：每收到一个事件（包括 `heartbeat`）就把 `event.eventId` 写入本地状态；断线时用它作为 `Last-Event-ID` 发起 §2.4 重连。
4. **心跳/超时判定**：
    - 后端每 ~5s 下发一次 `heartbeat` 事件（§3.11）；
    - 若 **15s** 内无任何事件到达，视为疑似断连 → 调 §2.3 `/state` 探测 → 按 `resumeHint` 分发（`RESUME_STREAM` 则走 §2.4 重连）；
    - 服务端 `: keep-alive` 注释行仍会发，前端忽略即可。
5. **gap 回退**：收到 §3.12 `resync_required` 必须丢弃所有增量缓存，按 §2.3 / §1.3 全量对齐，之后再用 `state.lastEventId` 再次 `/stream` 重连。
6. **终态判定**：收到 `done` / `error` / `cancelled` 即关闭连接，无需重连。
7. **pendingAsk 优先级**：`/state` 返回 `resumeHint = ANSWER_ASK` 时，先还原 HITL 弹窗再发起 SSE，避免用户看到"仍在生成"却不知道被什么阻塞。
8. **in-flight 判据只认 `generation.inFlight`**：skip-placeholder 开启后 `placeholderMessageId` 恒为 null，旧逻辑"存在 placeholder 即生成中"在新模式下失效。迁移期统一改读 `generation.inFlight` 即可兼容新旧两种模式。

### 7.2 HITL 弹窗约定

- 同一时刻一个 session 只会有**一个** pending ask；前端按 askId 唯一对齐。
- 用户按 ESC / 点遮罩关闭 → 调用 `DELETE /ask/{askId}?reason=user_dismiss`。
- 倒计时到点但尚未收到服务端 `error` / `tool_result` 继续事件时，仍可允许用户提交（可能成功可能已超时），服务端以 `0709040` / 静默丢弃兜底。
- `multi_choice` 的 `minSelect` / `maxSelect` 需前端本地校验：提交时服务端也会二次校验，失败会返回 `status: INVALID_ANSWER` 的工具结果（Agent 自行重问或兜底）。

### 7.3 结构化输出渲染

| rendererHint | 建议实现 |
|---|---|
| `table` | 按 schema `properties` 生成列定义 |
| `card` | key-value 卡片 |
| `form` | 只读表单（schema 驱动） |
| `chart` | 取 `data.series` / `data.categories` 等约定字段 |
| `markdown` | 直接渲染 `data.markdown` |
| （空） | fallback 为 JSON 折叠展示 |

### 7.4 错误处理

- 业务错误统一看 `result.code`：**非 `0000000` 全部视作失败**，展示 `result.message`。
- HTTP 4xx/5xx 需兜底通用错误文案；`requestId` 记入日志便于后端排查。
- SSE 的 `error` 事件和 `Result` 不冲突：前者是"生成过程中出错"，后者是"请求层出错"，UI 建议用不同提示样式。

---

## 8. 端点速查

### 会话
```
POST   /agent/sessions
GET    /agent/sessions/{sessionId}
GET    /agent/sessions/{sessionId}/state              # 恢复 / 重连前探测
GET    /agent/sessions?page=&size=&standalone=&scriptId=
GET    /agent/sessions/active?limit=
GET    /agent/sessions/archived?limit=
GET    /agent/sessions/{sessionId}/messages
POST   /agent/sessions/{sessionId}/messages            # 同步
POST   /agent/sessions/{sessionId}/messages/stream     # 流式 SSE（首发）
GET    /agent/sessions/{sessionId}/stream              # SSE 重连，支持 Last-Event-ID
POST   /agent/sessions/{sessionId}/end
POST   /agent/sessions/{sessionId}/cancel
POST   /agent/sessions/{sessionId}/archive
POST   /agent/sessions/{sessionId}/resume
DELETE /agent/sessions/{sessionId}
```

### HITL
```
GET    /agent/sessions/{sessionId}/ask/pending
POST   /agent/sessions/{sessionId}/ask/{askId}/answer
DELETE /agent/sessions/{sessionId}/ask/{askId}?reason=
```

### 技能
```
GET    /agent/skills?page=&size=&keyword=
GET    /agent/skills/{name}
GET    /agent/skills/{name}/tools
GET    /agent/skills/{name}/output-schema
```

### Mission
```
GET    /agent/missions/{id}
GET    /agent/missions?current=&size=&status=
GET    /agent/missions/{id}/progress
GET    /agent/missions/{id}/steps
GET    /agent/missions/{id}/tasks
GET    /agent/missions/{id}/events
GET    /agent/missions/{id}/traces
POST   /agent/missions/{id}/cancel
GET    /agent/missions/{id}/progress/stream            # SSE
```

---

## 9. 典型调用链示例

### 9.1 常规对话 + 一次 HITL

```
1. POST /agent/sessions                → { id: "sess_1" }
2. POST /agent/sessions/sess_1/messages/stream
   → event: status           (phase=skill_loading)
   → event: message          (content="好的，我来...")
   → event: tool_call        (toolName=storyboard_gen)
   → event: status           (phase=tool_batch_progress, progress=0.5)
   → event: ask_user         (askId=ask-xxx, inputType=single_choice)
3. ⬅ 用户选择
   POST /agent/sessions/sess_1/ask/ask-xxx/answer  { answer: "v2" }
4. → event: tool_result
   → event: structured_data  (schemaRef=storyboard_gen)
5. GET /agent/skills/storyboard_gen/output-schema  (首次，后续读缓存)
6. → event: message ("已完成")
   → event: done
```

### 9.2 生成中用户取消

```
1. POST /agent/sessions/sess_1/messages/stream  (流开始)
2. （弹出 ask_user 后用户立刻取消）
3. POST /agent/sessions/sess_1/cancel
   ← { cancelled: true, cancelledAsks: 1 }
4. 流上收到 event: cancelled → 关闭所有对话框、清理占位消息
```

### 9.3 断线重连（典型）

```
1. 原流进行中：POST /messages/stream
   前端记录 lastEventId=18
2. 网络断连（Wi-Fi 切换 / 后台 tab 被冻结）
3. 网络恢复：
   a. GET /agent/sessions/sess_1/state
      ← { generation.inFlight: true, resumeHint: "RESUME_STREAM", lastEventId: 47 }
   b. GET /agent/sessions/sess_1/stream
      Last-Event-ID: 18
   → event 19..47 立即回放
   → event 48 起继续实时推送
   → ... → event: done
```

### 9.4 长时间离线后 gap 回退

```
1. lastEventId=2，离线 10 分钟后恢复
2. GET /agent/sessions/sess_1/stream  Last-Event-ID: 2
   → event: resync_required            (oldestAvailableEventId=180, serverMaxEventId=370)
3. 前端丢弃本地增量缓存
4. GET /agent/sessions/sess_1/state    → lastEventId: 370, resumeHint: RESUME_STREAM
5. GET /agent/sessions/sess_1/messages → 重建时间线
6. GET /agent/sessions/sess_1/stream   Last-Event-ID: 370
   → 继续消费 371..
```

### 9.5 Mission 订阅

```
1. 对话中 Agent 触发 create_mission 工具 → 返回 missionId
2. GET /agent/missions/{id}/progress          (快照)
3. GET /agent/missions/{id}/progress/stream   (SSE)
   → step_started / task_progress / step_completed ...
   → mission_completed { data.resultPayload }
4. GET /agent/missions/{id}                   (最终状态)
```
