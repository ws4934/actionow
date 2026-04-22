package com.actionow.agent.interaction;

import com.actionow.agent.entity.AgentSessionEntity;
import com.actionow.agent.saa.session.SaaSessionService;
import com.actionow.common.core.context.UserContextHolder;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * HITL（Human-in-the-Loop）用户回答提交端点。
 *
 * <p>前端在接收到 {@code ask_user} SSE 事件并让用户完成选择后，调用本端点把答案回传。
 * 端点通过 {@link UserInteractionService#submitAnswer} 唤醒正在阻塞的工具线程，
 * 工具拿到答案后从 LLM 的视角作为普通工具返回值继续推理。
 *
 * <h2>鉴权</h2>
 * {@code @RequireWorkspaceMember} 只保证请求方属于本 workspace；额外校验
 * {@code session.userId == UserContextHolder.getUserId()}，防止同 workspace 他人
 * 仅凭 askId 越权回答会话的 HITL 提问。
 *
 * @author Actionow
 */
@Slf4j
@RestController
@RequestMapping("/agent/sessions/{sessionId}/ask")
@RequiredArgsConstructor
@RequireWorkspaceMember
@Tag(name = "HITL 交互", description = "Human-in-the-Loop 用户回答提交接口")
public class AskUserController {

    private final UserInteractionService interaction;
    private final SaaSessionService sessionService;
    private final AgentAskHistoryService askHistoryService;

    @GetMapping("/pending")
    @Operation(summary = "查询当前 pending 的 ask",
            description = "前端进入对话页 / 重连 SSE 后调用；用于精确恢复 HITL 弹窗。无等待时 pending=false")
    public Result<PendingAskResponse> getPending(@PathVariable String sessionId) {
        verifySessionOwner(sessionId);
        return Result.success(PendingAskResponse.of(askHistoryService.findLatestPending(sessionId)));
    }

    @PostMapping("/{askId}/answer")
    @Operation(summary = "提交用户回答", description = "前端把用户从 ask_user 弹窗中选择的答案回传给后端，唤醒正在阻塞的工具线程")
    public Result<Void> submitAnswer(
            @PathVariable String sessionId,
            @PathVariable String askId,
            @Valid @RequestBody UserAnswer answer) {
        verifySessionOwner(sessionId);
        log.info("HITL answer 收到: sessionId={}, askId={}, rejected={}",
                sessionId, askId, Boolean.TRUE.equals(answer != null ? answer.getRejected() : null));
        interaction.submitAnswer(sessionId, askId, answer);
        return Result.success();
    }

    /**
     * 取消一个 pending ask（例如前端关闭弹窗 / 用户显式取消）。
     * 区别于 submitAnswer：此方法不代表任何有效答案，工具会收到 INTERRUPTED 状态。
     */
    @DeleteMapping("/{askId}")
    @Operation(summary = "取消 ask", description = "取消一个等待中的 ask_user 请求")
    public Result<Void> cancelAsk(
            @PathVariable String sessionId,
            @PathVariable String askId,
            @RequestParam(required = false) String reason) {
        verifySessionOwner(sessionId);
        log.info("HITL ask 取消请求: sessionId={}, askId={}, reason={}", sessionId, askId, reason);
        interaction.cancelAsk(sessionId, askId, reason);
        return Result.success();
    }

    private void verifySessionOwner(String sessionId) {
        AgentSessionEntity session = sessionService.getSessionEntity(sessionId);
        String callerId = UserContextHolder.getUserId();
        if (callerId == null || !callerId.equals(session.getUserId())) {
            log.warn("HITL 越权访问被拒: sessionId={}, sessionOwner={}, caller={}",
                    sessionId, session.getUserId(), callerId);
            throw new BusinessException("0709040", "无权限访问他人的会话");
        }
    }
}
