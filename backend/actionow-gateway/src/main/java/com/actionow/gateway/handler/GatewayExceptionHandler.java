package com.actionow.gateway.handler;

import com.actionow.common.core.result.Result;
import com.actionow.common.core.result.ResultCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 网关全局异常处理器
 *
 * @author Actionow
 */
@Slf4j
@Order(-1)
@Component
@RequiredArgsConstructor
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Result<Void> result;
        HttpStatus status;

        if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            result = switch (status) {
                case NOT_FOUND -> Result.fail(ResultCode.NOT_FOUND);
                case UNAUTHORIZED -> Result.fail(ResultCode.UNAUTHORIZED);
                case FORBIDDEN -> Result.fail(ResultCode.FORBIDDEN);
                case TOO_MANY_REQUESTS -> Result.fail(ResultCode.RATE_LIMITED);
                case SERVICE_UNAVAILABLE -> Result.fail(ResultCode.SERVICE_UNAVAILABLE);
                default -> Result.fail(ResultCode.FAIL.getCode(), rse.getReason());
            };
        } else {
            log.error("网关异常: {}", ex.getMessage(), ex);
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            result = Result.fail(ResultCode.INTERNAL_ERROR);
        }

        response.setStatusCode(status);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(result);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("JSON序列化失败", e);
            byte[] bytes = "{\"code\":\"0000900\",\"message\":\"系统内部错误\"}".getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        }
    }
}
