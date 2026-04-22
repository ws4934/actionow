package com.actionow.ai.dto.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 完整的输入参数Schema
 * 包含参数定义、分组、互斥组
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InputSchema {

    /**
     * 参数定义列表
     */
    private List<InputParamDefinition> params;

    /**
     * 参数分组列表
     */
    private List<InputParamGroup> groups;

    /**
     * 互斥参数组列表
     */
    private List<ExclusiveGroup> exclusiveGroups;

    /**
     * Schema 版本
     */
    private String version;

    /**
     * 创建空 Schema
     */
    public static InputSchema empty() {
        return InputSchema.builder()
                .params(List.of())
                .groups(List.of())
                .exclusiveGroups(List.of())
                .version("1.0")
                .build();
    }
}
