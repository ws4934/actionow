package com.actionow.common.web.interceptor;

import com.actionow.common.core.context.UserContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 请求日志拦截器
 *
 * @author Actionow
 */
@Slf4j
@Component
public class RequestLogInterceptor implements HandlerInterceptor {

    private static final String START_TIME_KEY = "requestStartTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_KEY, System.currentTimeMillis());

        if (log.isDebugEnabled()) {
            log.debug("[{}] {} {} - User: {}",
                    UserContextHolder.getRequestId(),
                    request.getMethod(),
                    request.getRequestURI(),
                    UserContextHolder.getUserId());
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute(START_TIME_KEY);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;

            if (log.isInfoEnabled()) {
                log.info("[{}] {} {} - Status: {} - Duration: {}ms",
                        UserContextHolder.getRequestId(),
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        duration);
            }

            // 慢请求告警
            if (duration > 3000) {
                log.warn("[{}] 慢请求警告: {} {} - Duration: {}ms",
                        UserContextHolder.getRequestId(),
                        request.getMethod(),
                        request.getRequestURI(),
                        duration);
            }
        }
    }
}
