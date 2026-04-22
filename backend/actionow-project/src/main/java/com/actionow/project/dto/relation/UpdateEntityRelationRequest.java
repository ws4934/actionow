package com.actionow.project.dto.relation;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 更新实体关系请求
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEntityRelationRequest {

    /**
     * 关系标签（可选的显示名称）
     */
    @Size(max = 100, message = "关系标签不能超过100个字符")
    private String relationLabel;

    /**
     * 描述
     */
    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;

    /**
     * 排序序号
     */
    private Integer sequence;

    /**
     * 扩展信息（存储关系的元数据）
     * 如果提供，将替换现有的 extraInfo
     */
    private Map<String, Object> extraInfo;

    /**
     * 是否合并 extraInfo（默认 false，即替换）
     * 如果为 true，则将新的 extraInfo 合并到现有的 extraInfo 中
     */
    private Boolean mergeExtraInfo;
}
