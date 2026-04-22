package com.actionow.gateway.util;

import com.actionow.common.core.result.Result;
import com.actionow.common.core.result.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 过滤器统一错误响应工具
 * 确保所有 Filter 返回与 GatewayExceptionHandler 一致的 Result JSON 格式
 *
 * @author Actionow
 */
@Slf4j
public final class FilterResponseUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private FilterResponseUtils() {}

    /**
     * 返回统一格式的错误响应
     *
     * @param exchange   交换对象
     * @param status     HTTP 状态码
     * @param resultCode 业务错误码
     * @return Mono<Void>
     */
    public static Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status,
                                                 ResultCode resultCode) {
        return writeErrorResponse(exchange, status, resultCode, null);
    }

    /**
     * 返回统一格式的错误响应（含额外 header）
     *
     * @param exchange     交换对象
     * @param status       HTTP 状态码
     * @param resultCode   业务错误码
     * @param errorHeader  可选的 X-Auth-Error 值
     * @return Mono<Void>
     */
    public static Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status,
                                                 ResultCode resultCode, String errorHeader) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (errorHeader != null) {
            response.getHeaders().add("X-Auth-Error", errorHeader);
        }

        // 使用 ObjectMapper 序列化，确保与 GatewayExceptionHandler 输出格式一致
        // Result 使用 @JsonInclude(NON_NULL)，null 字段会被排除
        Result<Void> result = Result.fail(resultCode);
        byte[] bytes;
        try {
            bytes = OBJECT_MAPPER.writeValueAsBytes(result);
        } catch (Exception e) {
            log.error("JSON序列化失败", e);
            bytes = ("{\"code\":\"" + resultCode.getCode()
                    + "\",\"message\":\"" + resultCode.getMessage() + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
