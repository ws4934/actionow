package com.actionow.agent.feign;

import com.actionow.common.core.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

/**
 * Feign FallbackFactory 抽象基类
 * <p>
 * 通过 JDK 动态代理自动为所有方法生成降级响应，消除子类逐方法实现的样板代码。
 * <ul>
 *   <li>返回 {@code Result<List<…>>} 的方法 → {@code Result.success(emptyList)}</li>
 *   <li>返回 {@code Result<Boolean>} 的方法 → 可由子类配置默认值</li>
 *   <li>其他 Result 返回 → {@code Result.fail(errorCode, serviceName + "暂时不可用")}</li>
 * </ul>
 *
 * 子类仅需提供 {@link #serviceName()} 和 {@link #errorCode()}，
 * 并可通过 {@link #customFallback(Method, Object[], Throwable)} 覆盖特定方法的降级逻辑。
 *
 * @param <T> Feign Client 接口类型
 * @author Actionow
 */
@Slf4j
public abstract class AbstractFeignFallbackFactory<T> implements FallbackFactory<T> {

    /** 服务名称（用于日志和错误消息） */
    protected abstract String serviceName();

    /** 降级错误码 */
    protected abstract String errorCode();

    /**
     * 自定义特定方法的降级逻辑。
     * 返回 null 表示使用默认降级策略。
     *
     * @param method 被调用的方法
     * @param args   方法参数
     * @param cause  触发降级的异常
     * @return 自定义降级结果，null 表示走默认
     */
    protected Object customFallback(Method method, Object[] args, Throwable cause) {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T create(Throwable cause) {
        log.error("调用{}失败: {}", serviceName(), cause.getMessage());

        Class<T> clientType = resolveClientType();
        return (T) Proxy.newProxyInstance(
                clientType.getClassLoader(),
                new Class<?>[]{clientType},
                new FallbackInvocationHandler(cause));
    }

    @SuppressWarnings("unchecked")
    private Class<T> resolveClientType() {
        Type superType = getClass().getGenericSuperclass();
        if (superType instanceof ParameterizedType pt) {
            return (Class<T>) pt.getActualTypeArguments()[0];
        }
        throw new IllegalStateException("Cannot resolve Feign client type for " + getClass().getName());
    }

    private class FallbackInvocationHandler implements InvocationHandler {
        private final Throwable cause;

        FallbackInvocationHandler(Throwable cause) {
            this.cause = cause;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            // Object 方法（toString, hashCode, equals）直接处理
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(method, args);
            }

            log.warn("{}降级: method={}, cause={}", serviceName(), method.getName(), cause.getMessage());

            // 优先使用子类自定义降级
            Object custom = customFallback(method, args, cause);
            if (custom != null) {
                return custom;
            }

            // 默认降级策略：基于返回类型推断
            return defaultFallbackResult(method);
        }

        private Object defaultFallbackResult(Method method) {
            Type returnType = method.getGenericReturnType();

            // 仅处理 Result<T> 返回类型
            if (returnType instanceof ParameterizedType pt && pt.getRawType() == Result.class) {
                Type innerType = pt.getActualTypeArguments()[0];

                // Result<List<…>> → 返回空列表
                if (innerType instanceof ParameterizedType innerPt && innerPt.getRawType() == List.class) {
                    return Result.success(Collections.emptyList());
                }

                // Result<Boolean> → 返回 true（降级时放行）
                if (innerType == Boolean.class) {
                    return Result.success(true);
                }
            }

            // 其他情况：返回 fail
            String message = serviceName() + "暂时不可用: " + cause.getMessage();
            return Result.fail(errorCode(), message);
        }

        private Object handleObjectMethod(Method method, Object[] args) {
            return switch (method.getName()) {
                case "toString" -> serviceName() + "FallbackProxy";
                case "hashCode" -> System.identityHashCode(this);
                case "equals" -> this == args[0];
                default -> null;
            };
        }
    }
}
