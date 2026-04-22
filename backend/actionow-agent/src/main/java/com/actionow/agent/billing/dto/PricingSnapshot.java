package com.actionow.agent.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 定价快照
 * 记录计费会话创建时的价格信息，用于结算时保证价格一致性
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingSnapshot {

    /**
     * 模型厂商
     */
    private String modelProvider;

    /**
     * 模型 ID
     */
    private String modelId;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 输入价格（积分/1K tokens）
     */
    private BigDecimal inputPrice;

    /**
     * 输出价格（积分/1K tokens）
     */
    private BigDecimal outputPrice;

    /**
     * 计费规则 ID（可为空，表示使用默认价格）
     */
    private String ruleId;

    /**
     * 快照生成时间
     */
    private LocalDateTime snapshotAt;

    /**
     * 从 Map 创建（用于从数据库 JSON 字段解析）
     */
    public static PricingSnapshot fromMap(java.util.Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        PricingSnapshot snapshot = new PricingSnapshot();
        snapshot.setModelProvider((String) map.get("modelProvider"));
        snapshot.setModelId((String) map.get("modelId"));
        snapshot.setModelName((String) map.get("modelName"));

        Object inputPrice = map.get("inputPrice");
        if (inputPrice != null) {
            snapshot.setInputPrice(new BigDecimal(inputPrice.toString()));
        }

        Object outputPrice = map.get("outputPrice");
        if (outputPrice != null) {
            snapshot.setOutputPrice(new BigDecimal(outputPrice.toString()));
        }

        snapshot.setRuleId((String) map.get("ruleId"));

        Object snapshotAt = map.get("snapshotAt");
        if (snapshotAt != null) {
            snapshot.setSnapshotAt(LocalDateTime.parse(snapshotAt.toString()));
        }

        return snapshot;
    }

    /**
     * 转换为 Map（用于存储到数据库 JSON 字段）
     */
    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("modelProvider", modelProvider);
        map.put("modelId", modelId);
        map.put("modelName", modelName);
        if (inputPrice != null) {
            map.put("inputPrice", inputPrice.toString());
        }
        if (outputPrice != null) {
            map.put("outputPrice", outputPrice.toString());
        }
        map.put("ruleId", ruleId);
        if (snapshotAt != null) {
            map.put("snapshotAt", snapshotAt.toString());
        }
        return map;
    }
}
