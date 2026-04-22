package com.actionow.ai.dto;

import com.actionow.ai.service.schema.SchemaValidator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Schema 验证响应
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaValidationResponse {

    /**
     * 是否验证通过
     */
    private boolean valid;

    /**
     * 错误列表
     */
    private List<ErrorItem> errors;

    /**
     * 从 SchemaValidator.ValidationResult 转换
     */
    public static SchemaValidationResponse from(SchemaValidator.ValidationResult result) {
        List<ErrorItem> errorItems = result.errors().stream()
                .map(e -> new ErrorItem(e.field(), e.message(), e.code()))
                .toList();
        return new SchemaValidationResponse(result.valid(), errorItems);
    }

    /**
     * 错误项
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorItem {

        /**
         * 字段名
         */
        private String field;

        /**
         * 错误信息
         */
        private String message;

        /**
         * 错误码
         */
        private String code;
    }
}
