package com.actionow.agent.interaction;

import com.actionow.agent.core.agent.AgentStreamEvent;
import com.actionow.agent.core.execution.ExecutionRegistry;
import com.actionow.agent.entity.AgentMessage;
import com.actionow.agent.saa.session.SaaSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 定期心跳调度器。
 *
 * <p>每 {@code intervalMs} 毫秒扫描一次 {@link AgentStreamBridge#activeSessions()}，
 * 对每个仍在线且有活跃执行的 session 发一条 {@code heartbeat} 事件，并同步更新
 * 该 session in-flight 占位消息的 {@code lastHeartbeatAt}。
 *
 * <h2>为什么需要心跳</h2>
 * 纯文本模型在长工具链场景下会有数十秒无 message / tool_event 下发的空档，
 * 前端若无心跳无法区分"仍在生成"与"后端挂了"。心跳给前端一个稳定的"脉搏"：
 * <ul>
 *   <li>看到心跳 → 状态栏显示"正在思考..."</li>
 *   <li>超过阈值未见心跳 → 调 {@code /agent/sessions/{id}/state} 重新对齐</li>
 * </ul>
 *
 * <h2>心跳与占位消息 DB 同步</h2>
 * 心跳发射时把 {@code last_heartbeat_at} 更新到 DB，使得跨 pod 重连场景下
 * {@code /state} 端点能从 DB 读到最新心跳时间（结合 P3 Redis Stream 后心跳
 * 可跨 pod 可见；当前版本仅单 pod）。
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentHeartbeatScheduler {

    private final AgentStreamBridge streamBridge;
    private final ExecutionRegistry executionRegistry;
    private final SaaSessionService sessionService;

    @Value("${actionow.agent.stream.heartbeat.interval-ms:5000}")
    private long intervalMs;

    /**
     * skip-placeholder 路径：心跳落到 {@code t_agent_session.last_heartbeat_at}，
     * 不再依赖 in-flight placeholder 行。开关与 AgentPreflightService / AgentController
     * 共用，保证三侧行为一致。
     */
    @Value("${actionow.agent.message.skip-placeholder.enabled:false}")
    private boolean skipPlaceholderEnabled;

    /**
     * 周期性扫描活跃会话发心跳。
     * 固定周期由 fixedDelayString 绑定同一配置项，运维可同步调整。
     */
    @Scheduled(fixedDelayString = "${actionow.agent.stream.heartbeat.interval-ms:5000}")
    public void tick() {
        Set<String> active = streamBridge.activeSessions();
        if (active.isEmpty()) return;
        // 把本轮所有需要刷心跳的 id 攒起来 —— 旧路径收集 placeholder id，新路径收集 session id。
        // 最后一次性 UPDATE ... WHERE id IN (...) 把写放大从 N 降到 1。
        List<String> placeholderIds = new ArrayList<>(active.size());
        List<String> sessionIdsForHeartbeat = new ArrayList<>(active.size());
        for (String sessionId : active) {
            try {
                if (!executionRegistry.hasActiveExecution(sessionId)) {
                    // 客户端仍连着 SSE，但后端已无活跃执行 —— 不发心跳，留给 done/cancelled 收尾。
                    continue;
                }
                if (skipPlaceholderEnabled) {
                    // 新路径：elapsedMs 无法从 placeholder.createdAt 推 —— 用 0 即可，
                    // 前端主要依赖 /state 端点的 generating_since 计算真实时长；心跳本身只做"活着"信号。
                    streamBridge.publish(sessionId, AgentStreamEvent.heartbeat(0L));
                    sessionIdsForHeartbeat.add(sessionId);
                } else {
                    AgentMessage placeholder = sessionService.findInFlightPlaceholder(sessionId);
                    long elapsedMs = placeholder != null && placeholder.getCreatedAt() != null
                            ? java.time.Duration.between(
                                    placeholder.getCreatedAt(), LocalDateTime.now(ZoneOffset.UTC)).toMillis()
                            : 0L;
                    streamBridge.publish(sessionId, AgentStreamEvent.heartbeat(elapsedMs));
                    if (placeholder != null) {
                        placeholderIds.add(placeholder.getId());
                    }
                }
            } catch (Exception e) {
                log.warn("Heartbeat tick failed sessionId={}: {}", sessionId, e.getMessage());
            }
        }
        if (!placeholderIds.isEmpty()) {
            sessionService.touchHeartbeatBatch(placeholderIds);
        }
        if (!sessionIdsForHeartbeat.isEmpty()) {
            sessionService.touchSessionHeartbeatBatch(sessionIdsForHeartbeat);
        }
    }
}
