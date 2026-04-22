package com.actionow.ai.dto.standard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 道具数据 DTO
 * 对应 actionow-script 模块的 Prop 实体
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropData {

    // ==================== 必填字段 ====================

    /**
     * 道具名称
     * 必填
     */
    private String name;

    // ==================== 推荐字段 ====================

    /**
     * 道具描述
     */
    private String description;

    // ==================== 可选字段 ====================

    /**
     * 固定描述词（用于 AI 生成时的固定 prompt）
     */
    private String fixedDesc;

    /**
     * 道具类型
     */
    private String propType;

    /**
     * 外观数据
     */
    private Map<String, Object> appearance;

    /**
     * 扩展信息
     */
    private Map<String, Object> extraInfo;
}
