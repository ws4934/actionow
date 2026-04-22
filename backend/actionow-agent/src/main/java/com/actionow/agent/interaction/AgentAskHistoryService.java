package com.actionow.agent.interaction;

import com.actionow.agent.entity.AgentAskHistory;
import com.actionow.agent.mapper.AgentAskHistoryMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * HITL ask/answer 审计服务。
 *
 * <p>职责：
 * <ul>
 *   <li>{@link #recordPending} — 工具线程发起 ask 时落一行 PENDING</li>
 *   <li>{@link #recordFinal}   — 工具线程拿到终态（ANSWERED / TIMEOUT / REJECTED / CANCELLED / ERROR）时更新</li>
 * </ul>
 *
 * <p>任何持久化失败只 WARN，不影响主流程（业务容忍审计丢一行 > HITL 主链路中断）。
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAskHistoryService {

    private final AgentAskHistoryMapper mapper;

    public void recordPending(String sessionId, String askId, String question, String inputType,
                              List<Map<String, Object>> choices, Map<String, Object> constraints,
                              int timeoutSec) {
        try {
            AgentAskHistory entity = new AgentAskHistory();
            entity.setSessionId(sessionId);
            entity.setAskId(askId);
            entity.setQuestion(question);
            entity.setInputType(inputType);
            entity.setChoices(choices);
            entity.setConstraints(constraints);
            entity.setStatus("PENDING");
            entity.setTimeoutSec(timeoutSec);
            mapper.insert(entity);
        } catch (Exception e) {
            log.warn("HITL ask 审计写入失败 sessionId={}, askId={}: {}", sessionId, askId, e.getMessage());
        }
    }

    /** 查询 session 当前 PENDING 的 ask，无则返回 null。 */
    public AgentAskHistory findLatestPending(String sessionId) {
        if (sessionId == null) return null;
        try {
            return mapper.selectLatestPendingBySession(sessionId);
        } catch (Exception e) {
            log.warn("HITL pending 查询失败 sessionId={}: {}", sessionId, e.getMessage());
            return null;
        }
    }

    public void recordFinal(String askId, String status, Map<String, Object> answer) {
        if (askId == null) return;
        try {
            // 必须用 (entity, wrapper) 变体，MP 才会走字段上的 JacksonTypeHandler 把
            // Map<String,Object> 序列化成 JSON。若改用 wrapper.set(getAnswer, map)，
            // 会旁路 TypeHandler，PG 驱动把 Map 当 hstore 处理，抛 "No hstore extension installed"。
            AgentAskHistory patch = new AgentAskHistory();
            patch.setStatus(status);
            patch.setRespondedAt(LocalDateTime.now());
            if (answer != null) {
                patch.setAnswer(answer);
            }
            LambdaUpdateWrapper<AgentAskHistory> w = new LambdaUpdateWrapper<AgentAskHistory>()
                    .eq(AgentAskHistory::getAskId, askId)
                    .eq(AgentAskHistory::getDeleted, 0);
            mapper.update(patch, w);
        } catch (Exception e) {
            log.warn("HITL ask 终态写入失败 askId={}, status={}: {}", askId, status, e.getMessage());
        }
    }
}
