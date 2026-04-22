package com.actionow.agent.tool.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具输出元数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工具输出定义")
public class ToolOutput {

    @Schema(description = "返回类型")
    private String type;

    @Schema(description = "输出说明")
    private String description;

    @Schema(description = "Schema 对应的 Java 类")
    private String schemaClass;

    @Schema(description = "手写输出 Schema JSON")
    private String schemaJson;

    @Schema(description = "输出示例")
    private String example;
}
