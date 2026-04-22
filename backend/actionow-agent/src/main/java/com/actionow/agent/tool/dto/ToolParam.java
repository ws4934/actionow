package com.actionow.agent.tool.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 工具参数
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工具参数")
public class ToolParam {

    @Schema(description = "参数名称")
    private String name;

    @Schema(description = "参数类型")
    private String type;

    @Schema(description = "参数描述")
    private String description;

    @Schema(description = "是否必填")
    private Boolean required;

    @Schema(description = "默认值")
    private String defaultValue;

    @Schema(description = "示例值")
    private String example;

    @Schema(description = "枚举值列表")
    private List<String> enumValues;
}
