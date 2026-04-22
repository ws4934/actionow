package com.actionow.ai.dto.standard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 场景数据 DTO
 * 对应 actionow-script 模块的 Scene 实体
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SceneData {

    // ==================== 必填字段 ====================

    /**
     * 场景名称
     * 必填
     */
    private String name;

    // ==================== 推荐字段 ====================

    /**
     * 场景描述
     */
    private String description;

    // ==================== 可选字段 ====================

    /**
     * 固定描述词（用于 AI 生成时的固定 prompt）
     */
    private String fixedDesc;

    /**
     * 时间（白天/夜晚/黄昏等）
     */
    private String timeOfDay;

    /**
     * 天气
     */
    private String weather;

    /**
     * 地点类型（室内/室外/城市/乡村等）
     */
    private String locationType;

    /**
     * 环境设定
     * 包含 timeOfDay, weather, locationType 等结构化数据
     */
    private Map<String, Object> environment;

    /**
     * 氛围设定
     * 包含 lighting, colorTone, mood 等
     */
    private Map<String, Object> atmosphere;

    /**
     * 扩展信息
     */
    private Map<String, Object> extraInfo;

    // ==================== 时间常量 ====================

    public static final String TIME_DAY = "DAY";
    public static final String TIME_NIGHT = "NIGHT";
    public static final String TIME_DAWN = "DAWN";
    public static final String TIME_DUSK = "DUSK";

    // ==================== 地点类型常量 ====================

    public static final String LOCATION_INDOOR = "INDOOR";
    public static final String LOCATION_OUTDOOR = "OUTDOOR";
    public static final String LOCATION_URBAN = "URBAN";
    public static final String LOCATION_RURAL = "RURAL";
}
