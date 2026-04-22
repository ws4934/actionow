package com.actionow.ai.plugin.groovy;

import com.actionow.ai.config.AiRuntimeConfigService;
import com.actionow.ai.plugin.groovy.exception.GroovyScriptException;
import com.actionow.ai.plugin.groovy.exception.GroovySecurityException;
import com.actionow.common.core.context.UserContext;
import com.actionow.common.core.context.UserContextHolder;
import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.GString;
import groovy.lang.Script;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.groovy.sandbox.GroovyInterceptor;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Groovy脚本执行引擎
 * 提供安全沙箱、编译缓存、上下文绑定功能
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroovyScriptEngine {

    private final GroovyScriptCache scriptCache;
    private final GroovySandboxConfig sandboxConfig;
    private final GroovyScriptValidator scriptValidator;
    private final AiRuntimeConfigService runtimeConfig;

    private ExecutorService executorService;

    @PostConstruct
    public void init() {
        // 创建执行线程池
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        log.info("GroovyScriptEngine initialized with sandbox={}", sandboxConfig.isEnabled());
    }

    /**
     * 执行请求构建脚本
     *
     * @param script  脚本内容
     * @param context 执行上下文
     * @return 构建的请求体
     */
    public Object executeRequestBuilder(String script, GroovyScriptContext context) {
        return execute(script, context, "RequestBuilder");
    }

    /**
     * 执行响应映射脚本
     *
     * @param script   脚本内容
     * @param response 原始响应
     * @param context  执行上下文
     * @return 映射后的输出
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> executeResponseMapper(String script, Object response, GroovyScriptContext context) {
        // 设置响应到上下文
        context.setResponse(response);
        Object result = execute(script, context, "ResponseMapper");

        if (result instanceof Map) {
            return (Map<String, Object>) result;
        } else if (result != null) {
            return Map.of("output", result);
        }
        return Map.of();
    }

    /**
     * 执行自定义逻辑脚本
     *
     * @param script  脚本内容
     * @param context 执行上下文
     * @return 执行结果
     */
    public Object executeCustomLogic(String script, GroovyScriptContext context) {
        return execute(script, context, "CustomLogic");
    }

    /**
     * 核心执行方法
     */
    private Object execute(String script, GroovyScriptContext context, String scriptType) {
        if (script == null || script.isBlank()) {
            throw new GroovyScriptException("Script content cannot be empty");
        }

        long startTime = System.currentTimeMillis();

        // 捕获当前线程的用户上下文（用于传播到虚拟线程）
        final UserContext currentContext = UserContextHolder.getContext();

        try {
            // 带超时的执行，并传播上下文到虚拟线程
            Future<Object> future = executorService.submit(() -> {
                try {
                    // 在虚拟线程中恢复用户上下文
                    if (currentContext != null) {
                        UserContextHolder.setContext(currentContext);
                    }

                    return executeInSandbox(script, context);
                } finally {
                    // 清理虚拟线程上下文
                    UserContextHolder.clear();
                }
            });

            Object result = future.get(runtimeConfig.getGroovyMaxExecutionTimeMs(), TimeUnit.MILLISECONDS);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[GroovyScriptEngine] {}脚本执行成功, 耗时={}ms", scriptType, elapsed);

            return result;

        } catch (TimeoutException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            long timeoutMs = runtimeConfig.getGroovyMaxExecutionTimeMs();
            log.error("[GroovyScriptEngine] {}脚本执行超时, 已执行{}ms, 限制{}ms",
                scriptType, elapsed, timeoutMs);
            throw new GroovyScriptException(
                    String.format("%s script execution timeout after %dms (limit: %dms)",
                            scriptType, elapsed, timeoutMs));

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            log.error("[GroovyScriptEngine] {}脚本执行失败: {}", scriptType, cause.getMessage());
            if (cause instanceof GroovyScriptException) {
                throw (GroovyScriptException) cause;
            } else if (cause instanceof GroovySecurityException) {
                throw (GroovySecurityException) cause;
            } else {
                throw new GroovyScriptException(scriptType + " script execution failed: " + cause.getMessage(), cause);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[GroovyScriptEngine] {}脚本执行被中断", scriptType);
            throw new GroovyScriptException(scriptType + " script execution interrupted");
        }
    }

    /**
     * 在沙箱中执行脚本
     */
    private Object executeInSandbox(String scriptContent, GroovyScriptContext context) {
        // 获取编译后的脚本类
        Class<? extends Script> scriptClass = scriptCache.getOrCompile(scriptContent);

        // 创建绑定
        Binding binding = new Binding(context.toBindings());

        // 实例化脚本
        Script script;
        try {
            script = scriptClass.getDeclaredConstructor().newInstance();
            script.setBinding(binding);
        } catch (Exception e) {
            throw new GroovyScriptException("Failed to instantiate script", e);
        }

        // 根据沙箱配置执行
        if (sandboxConfig.isEnabled()) {
            return executeWithSandbox(script);
        } else {
            return script.run();
        }
    }

    /**
     * 使用沙箱执行
     */
    private Object executeWithSandbox(Script script) {
        // 创建沙箱拦截器
        GroovyInterceptor interceptor = new SecurityInterceptor(sandboxConfig);

        try {
            // 注册拦截器
            interceptor.register();
            // 执行脚本
            return script.run();
        } finally {
            // 移除拦截器
            interceptor.unregister();
        }
    }

    /**
     * 验证脚本
     */
    public GroovyScriptValidator.ValidationResult validate(String scriptContent) {
        return scriptValidator.validate(scriptContent);
    }

    /**
     * 清除脚本缓存
     */
    public void clearCache() {
        scriptCache.clear();
    }

    /**
     * 获取缓存统计
     */
    public GroovyScriptCache.CacheStats getCacheStats() {
        return scriptCache.getStats();
    }

    /**
     * 安全拦截器
     */
    private static class SecurityInterceptor extends GroovyInterceptor {

        private final GroovySandboxConfig config;

        public SecurityInterceptor(GroovySandboxConfig config) {
            this.config = config;
        }

        @Override
        public Object onMethodCall(GroovyInterceptor.Invoker invoker, Object receiver, String method,
                                   Object... args) throws Throwable {
            // 检查方法是否被禁止
            if (config.isMethodBlocked(method)) {
                throw new GroovySecurityException("Blocked method call: " + method, "METHOD_BLOCKED", method);
            }

            // 检查接收者类是否被禁止
            if (receiver != null && config.isClassBlocked(receiver.getClass().getName())) {
                throw new GroovySecurityException(
                        "Blocked class method call: " + receiver.getClass().getName() + "." + method,
                        "CLASS_BLOCKED",
                        receiver.getClass().getName());
            }

            // 放行脚本内部生成的语言构造：闭包、GString、脚本本体
            // 这些类的方法调用是脚本自身运行所必需的（例如 list.each { ... } 会触发 Script$_closure 的 call）
            if (isScriptIntrinsic(receiver)) {
                return super.onMethodCall(invoker, receiver, method, args);
            }

            // 白名单模式：只允许已注册类的方法调用
            if (receiver != null && !config.isMethodCallAllowed(receiver.getClass().getName(), method)) {
                throw new GroovySecurityException(
                        "Method not in whitelist: " + receiver.getClass().getName() + "." + method,
                        "WHITELIST_BLOCKED",
                        receiver.getClass().getName() + "." + method);
            }

            return super.onMethodCall(invoker, receiver, method, args);
        }

        private static boolean isScriptIntrinsic(Object receiver) {
            if (receiver == null) {
                return false;
            }
            if (receiver instanceof Closure || receiver instanceof GString || receiver instanceof Script) {
                return true;
            }
            Class<?> cls = receiver.getClass();
            // Groovy 为脚本内闭包生成的内部类：Script1$_run_closure5 等
            String name = cls.getName();
            if (name.startsWith("Script") && name.contains("$_") && name.contains("_closure")) {
                return true;
            }
            return false;
        }

        @Override
        public Object onNewInstance(GroovyInterceptor.Invoker invoker, Class receiver, Object... args)
                throws Throwable {
            // 检查类是否被禁止实例化
            if (config.isClassBlocked(receiver.getName())) {
                throw new GroovySecurityException(
                        "Blocked class instantiation: " + receiver.getName(),
                        "CLASS_BLOCKED",
                        receiver.getName());
            }

            return super.onNewInstance(invoker, receiver, args);
        }

        @Override
        public Object onStaticCall(GroovyInterceptor.Invoker invoker, Class receiver, String method,
                                   Object... args) throws Throwable {
            // 检查静态方法调用
            if (config.isClassBlocked(receiver.getName())) {
                throw new GroovySecurityException(
                        "Blocked static method call: " + receiver.getName() + "." + method,
                        "CLASS_BLOCKED",
                        receiver.getName());
            }

            if (config.isMethodBlocked(method)) {
                throw new GroovySecurityException("Blocked static method: " + method, "METHOD_BLOCKED", method);
            }

            // 白名单模式：只允许已注册类的静态方法调用
            if (!config.isMethodCallAllowed(receiver.getName(), method)) {
                throw new GroovySecurityException(
                        "Static method not in whitelist: " + receiver.getName() + "." + method,
                        "WHITELIST_BLOCKED",
                        receiver.getName() + "." + method);
            }

            return super.onStaticCall(invoker, receiver, method, args);
        }

        @Override
        public Object onGetProperty(GroovyInterceptor.Invoker invoker, Object receiver, String property)
                throws Throwable {
            // 阻止 .class 属性访问（防止通过 obj.class 获取 Class 对象进行反射逃逸）
            if ("class".equals(property)) {
                throw new GroovySecurityException(
                        "Blocked .class property access on " + (receiver != null ? receiver.getClass().getName() : "null"),
                        "PROPERTY_BLOCKED",
                        "class");
            }

            // 检查属性访问
            if (receiver != null && config.isClassBlocked(receiver.getClass().getName())) {
                throw new GroovySecurityException(
                        "Blocked property access: " + receiver.getClass().getName() + "." + property,
                        "CLASS_BLOCKED",
                        receiver.getClass().getName());
            }

            return super.onGetProperty(invoker, receiver, property);
        }
    }
}
