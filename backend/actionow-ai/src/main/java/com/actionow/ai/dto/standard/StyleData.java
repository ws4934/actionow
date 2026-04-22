package com.actionow.ai.dto.standard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 风格数据 DTO
 * 对应 actionow-script 模块的 Style 实体
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StyleData {

    // ==================== 必填字段 ====================

    /**
     * 风格名称
     * 必填
     */
    private String name;

    // ==================== 推荐字段 ====================

    /**
     * 风格描述
     */
    private String description;

    // ==================== 可选字段 ====================

    /**
     * 固定描述词（用于 AI 生成时的固定 prompt）
     */
    private String fixedDesc;

    /**
     * 风格参数
     * 包含画风、色彩、纹理等 AI 绘图参数
     */
    private Map<String, Object> styleParams;

    /**
     * 扩展信息
     */
    private Map<String, Object> extraInfo;
}
