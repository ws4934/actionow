package com.actionow.agent.interaction;

import com.actionow.agent.entity.AgentAskHistory;
import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * "当前是否有等待用户回答的 HITL 请求" 的响应体。
 *
 * <p>由 {@link AskUserController#getPending} 返回；前端进入对话页 / 重连后
 * 调用以精确恢复 HITL 弹窗。
 *
 * @author Actionow
 */
@Data
@Builder
public class PendingAskResponse {

    /** 是否存在 PENDING 状态的 ask。false 时其他字段可能为 null。 */
    private boolean pending;

    private String askId;
    private String question;
    /** single_choice / multi_choice / confirm / text / number */
    private String inputType;
    /** single_choice / multi_choice / confirm 时的可选项；text/number 时为 null */
    private List<Map<String, Object>> choices;
    /** minSelect / maxSelect / min / max / minLength / maxLength 等约束 */
    private Map<String, Object> constraints;

    private Integer timeoutSec;
    private LocalDateTime createdAt;
    /** 剩余秒数（负数表示已超时但尚未被后端终态化） */
    private Long remainingSec;

    public static PendingAskResponse empty() {
        return PendingAskResponse.builder().pending(false).build();
    }

    public static PendingAskResponse of(AgentAskHistory h) {
        if (h == null) return empty();
        Long remaining = null;
        if (h.getTimeoutSec() != null && h.getCreatedAt() != null) {
            long elapsedSec = Duration.between(h.getCreatedAt(), LocalDateTime.now(ZoneOffset.UTC)).toSeconds();
            remaining = Math.max(0, h.getTimeoutSec() - elapsedSec);
        }
        return PendingAskResponse.builder()
                .pending(true)
                .askId(h.getAskId())
                .question(h.getQuestion())
                .inputType(h.getInputType())
                .choices(h.getChoices())
                .constraints(h.getConstraints())
                .timeoutSec(h.getTimeoutSec())
                .createdAt(h.getCreatedAt())
                .remainingSec(remaining)
                .build();
    }
}
