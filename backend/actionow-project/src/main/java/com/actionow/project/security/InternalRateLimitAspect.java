package com.actionow.project.security;

import com.actionow.common.core.constant.CommonConstants;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.redis.limiter.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class InternalRateLimitAspect {

    private static final String GLOBAL_KEY = "_global";
    private static final String UNKNOWN_KEY = "_unknown";

    private final RateLimiterService rateLimiterService;

    @Around("@within(com.actionow.project.security.InternalRateLimit) "
            + "|| @annotation(com.actionow.project.security.InternalRateLimit)")
    public Object enforce(ProceedingJoinPoint joinPoint) throws Throwable {
        InternalRateLimit config = resolveConfig(joinPoint);
        if (config == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = currentRequest();
        String callerKey = resolveCallerKey(config.keyBy(), request);
        String limiterName = config.name().isEmpty() ? methodName(joinPoint) : config.name();
        String limiterKey = "internal:rl:" + limiterName + ":" + config.keyBy().name() + ":" + callerKey;

        boolean acquired = rateLimiterService.tryAcquire(
                limiterKey, config.permits(), config.intervalSeconds());

        String path = request != null ? request.getRequestURI() : "(no-request)";
        if (!acquired) {
            log.warn("Internal rate limit hit: name={}, keyBy={}, caller={}, path={}, permits={}/{}s",
                    limiterName, config.keyBy(), callerKey, path,
                    config.permits(), config.intervalSeconds());
            throw new BusinessException(ResultCode.RATE_LIMITED);
        }

        if (log.isInfoEnabled()) {
            log.info("Internal API audit: path={}, caller={} ({}), method={}",
                    path, callerKey, config.keyBy(), limiterName);
        }
        return joinPoint.proceed();
    }

    private InternalRateLimit resolveConfig(ProceedingJoinPoint joinPoint) {
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        Method method = sig.getMethod();
        InternalRateLimit methodLevel = method.getAnnotation(InternalRateLimit.class);
        if (methodLevel != null) {
            return methodLevel;
        }
        return method.getDeclaringClass().getAnnotation(InternalRateLimit.class);
    }

    private String resolveCallerKey(InternalRateLimit.KeyBy keyBy, HttpServletRequest request) {
        if (keyBy == InternalRateLimit.KeyBy.GLOBAL || request == null) {
            return GLOBAL_KEY;
        }
        String header = keyBy == InternalRateLimit.KeyBy.USER
                ? CommonConstants.HEADER_USER_ID
                : CommonConstants.HEADER_WORKSPACE_ID;
        String value = request.getHeader(header);
        return (value == null || value.isBlank()) ? UNKNOWN_KEY : value;
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }

    private String methodName(ProceedingJoinPoint joinPoint) {
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        return sig.getDeclaringType().getSimpleName() + "." + sig.getName();
    }
}
