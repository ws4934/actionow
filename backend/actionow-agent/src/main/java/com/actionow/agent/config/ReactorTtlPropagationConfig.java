package com.actionow.agent.config;

import com.alibaba.ttl.TtlRunnable;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Schedulers;

/**
 * 让 {@link com.alibaba.ttl.TransmittableThreadLocal}（TTL）自动跨 Reactor Scheduler 传播。
 *
 * <p>问题背景：
 * <ul>
 *   <li>Reactor 的 {@code Schedulers.boundedElastic()}、{@code parallel()} 在 subscribe 时把任务切换到
 *       独立线程池执行。普通 {@link ThreadLocal} 不传播；TTL 提供的 {@code InheritableThreadLocal}
 *       机制只有在线程首次创建时继承，池化线程不会继承新值。</li>
 *   <li>结果：Agent 上下文（UserContext / AgentContext / sessionId）在切换调度器后丢失，
 *       工具层只能依赖 {@code SessionContextHolder.findSingleActiveAgentContext()} 这种
 *       "单实例猜测"兜底，在多会话并发下降级为 null。</li>
 * </ul>
 *
 * <p>本配置在应用启动期注册一个 {@code Schedulers.onScheduleHook}，
 * 把每次被 Scheduler 接受的 {@code Runnable} 用 {@link TtlRunnable#get(Runnable)} 包一层。
 * TtlRunnable 在任务执行前把提交线程的 TTL 快照还原到执行线程，任务结束后恢复原状态。
 *
 * <p>代价：每个 Scheduler task 新增一次 TTL 快照复制（纳秒级），远小于维持两个 Holder + 多层 fallback 的复杂度。
 *
 * @author Actionow
 */
@Slf4j
@Configuration
public class ReactorTtlPropagationConfig {

    public static final String HOOK_KEY = "actionow-agent-ttl-propagation";

    @PostConstruct
    public void registerTtlHook() {
        Schedulers.onScheduleHook(HOOK_KEY, runnable -> {
            TtlRunnable wrapped = TtlRunnable.get(runnable);
            return wrapped != null ? wrapped : runnable;
        });
        log.info("已注册 Reactor Schedulers TTL 传播 Hook (key={})", HOOK_KEY);
    }
}
