package com.actionow.agent.saa.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 自言自语兜底检测拦截器。
 *
 * <p>模型在输出中"自己扮演用户答复"是 LLM 常见的角色越界问题：
 * <pre>
 *   你希望进行哪一步操作？请选择 A、B 或 C。
 *   好的，我们选择 A，拆分场景。   ← 这是模型替用户说的
 * </pre>
 * 正确姿势是调用 {@code ask_user_*} 工具进入 HITL 阻塞，而不是在纯文本里
 * "自问自答"。提示词已加硬约束（{@code SaaAgentFactory.DEFAULT_*_PROMPT}），
 * 本拦截器作为兜底：检测到疑似模式时 WARN 上报，便于回溯与度量。
 *
 * <p>出于可靠性考虑，当前实现只做"检测 + 日志告警"，不在流中截断 —
 * 流内截断一旦误伤正常输出会直接造成用户体验断裂。提示词层面若仍失效，
 * 再升级为流内截断。
 *
 * @author Actionow
 */
@Slf4j
public class SelfDialogueGuardInterceptor extends ModelInterceptor {

    /** 典型自言自语句式；按出现频率由高到低。 */
    private static final List<Pattern> SELF_DIALOGUE_PATTERNS = List.of(
            Pattern.compile("好的[，,\\s]*(?:我们)?选择\\s*[A-Za-z\\d一二三四五六七八九十]"),
            Pattern.compile("^\\s*确认[。.!！]"),
            Pattern.compile("就(?:按|照|这)(?:个|样)(?:来|办|做)"),
            Pattern.compile("(?:好|没问题|可以)[，,\\s]*(?:就|那就|让我们)?继续"),
            Pattern.compile("我(?:选|选择|决定选)\\s*[A-Za-z\\d]")
    );

    private static final AtomicInteger HIT_COUNTER = new AtomicInteger();

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler next) {
        ModelResponse response = next.call(request);
        Object message = response.getMessage();
        if (message instanceof AssistantMessage am) {
            scan(am.getText());
        } else if (message instanceof Flux<?> flux) {
            @SuppressWarnings("unchecked")
            Flux<ChatResponse> wrapped = ((Flux<ChatResponse>) flux)
                    .doOnNext(cr -> {
                        if (cr != null && cr.getResult() != null
                                && cr.getResult().getOutput() != null) {
                            scan(cr.getResult().getOutput().getText());
                        }
                    });
            return ModelResponse.of(wrapped);
        }
        return response;
    }

    private static void scan(String text) {
        if (text == null || text.isEmpty()) return;
        for (Pattern p : SELF_DIALOGUE_PATTERNS) {
            if (p.matcher(text).find()) {
                int total = HIT_COUNTER.incrementAndGet();
                log.warn("[SelfDialogueGuard] 检测到模型疑似替用户作答，pattern={}, totalHits={}, snippet=\"{}\"",
                        p.pattern(), total, snippet(text));
                return;
            }
        }
    }

    private static String snippet(String text) {
        String one = text.replaceAll("\\s+", " ").trim();
        return one.length() > 120 ? one.substring(0, 120) + "..." : one;
    }

    public static int getHitCount() {
        return HIT_COUNTER.get();
    }

    @Override
    public String getName() {
        return "SelfDialogueGuardInterceptor";
    }
}
