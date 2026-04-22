package com.actionow.project.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 素材元数据的规范化结构。
 *
 * <p>数据库与 API 的 metaInfo 列/字段历史为 {@code Map<String, Object>}，不同写入方对
 * width/height/duration 的类型（Integer/Long/Double）偶有出入，读取方只能靠
 * {@code instanceof Number} 防御性解包。此类把三个规范键收敛到强类型字段，
 * provider 特有扩展键落到 {@link #extra} 里。
 *
 * <p>实体/DTO 的持久化/序列化形态仍为 Map，通过 {@link #fromMap} / {@link #toMap} 桥接，
 * 不涉及 DB 迁移或对外 JSON 形状变化。
 */
@Data
@NoArgsConstructor
public class AssetMetaInfo {

    private Integer width;
    private Integer height;
    private Double duration;

    /** 非规范键（AI provider 扩展字段等），序列化时与规范键合并输出。 */
    private Map<String, Object> extra;

    public static AssetMetaInfo of(Integer width, Integer height) {
        AssetMetaInfo m = new AssetMetaInfo();
        m.width = width;
        m.height = height;
        return m;
    }

    public static AssetMetaInfo ofVideo(Integer width, Integer height, Double duration) {
        AssetMetaInfo m = new AssetMetaInfo();
        m.width = width;
        m.height = height;
        m.duration = duration;
        return m;
    }

    /**
     * 从 Map 解析。
     *
     * @param map          原始 metaInfo Map（通常来自 {@code asset.meta_info} 列或 provider 输出）
     * @param keepExtras   {@code true}：未识别的键保留到 {@link #extra}（适用于 asset.meta_info 这类窄输入，
     *                     可保留 provider 扩展键）；{@code false}：仅抽取 width/height/duration，忽略其它字段
     *                     （适用于 provider 输出的 flat map，其中混杂 url/mimeType 等非 meta 字段）。
     */
    public static AssetMetaInfo fromMap(Map<String, Object> map, boolean keepExtras) {
        AssetMetaInfo m = new AssetMetaInfo();
        if (map == null || map.isEmpty()) {
            return m;
        }
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String key = e.getKey();
            Object val = e.getValue();
            switch (key) {
                case "width" -> { if (val instanceof Number n) m.width = n.intValue(); }
                case "height" -> { if (val instanceof Number n) m.height = n.intValue(); }
                case "duration" -> { if (val instanceof Number n) m.duration = n.doubleValue(); }
                default -> {
                    if (keepExtras) {
                        if (m.extra == null) m.extra = new LinkedHashMap<>();
                        m.extra.put(key, val);
                    }
                }
            }
        }
        return m;
    }

    /** 等价于 {@link #fromMap(Map, boolean) fromMap(map, true)}——常见用法捷径。 */
    public static AssetMetaInfo fromMap(Map<String, Object> map) {
        return fromMap(map, true);
    }

    /**
     * 序列化为 Map（仅包含非 null 的规范键与 extra 条目）。
     * 当无任何字段时返回 null，避免向 DB 写入空 JSON。
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (width != null) map.put("width", width);
        if (height != null) map.put("height", height);
        if (duration != null) map.put("duration", duration);
        if (extra != null && !extra.isEmpty()) {
            map.putAll(extra);
        }
        return map.isEmpty() ? null : map;
    }

    public boolean isEmpty() {
        return width == null && height == null && duration == null
                && (extra == null || extra.isEmpty());
    }
}
