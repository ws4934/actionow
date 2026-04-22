package com.actionow.task.constant;

/**
 * 补偿任务类型枚举
 *
 * @author Actionow
 */
public enum CompensationType {

    /**
     * 解冻积分
     * 当 AI 生成任务失败或取消时，需要解冻之前冻结的积分
     */
    UNFREEZE("UNFREEZE", "解冻积分"),

    /**
     * 确认消费
     * 当 AI 生成任务成功完成后，需要确认扣减冻结的积分
     */
    CONFIRM_CONSUME("CONFIRM_CONSUME", "确认消费"),

    /**
     * 退还配额
     * 当 AI 生成任务失败或取消时，需要退还成员配额
     */
    REFUND_QUOTA("REFUND_QUOTA", "退还配额");

    private final String code;
    private final String description;

    CompensationType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据 code 获取枚举
     */
    public static CompensationType fromCode(String code) {
        for (CompensationType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown CompensationType code: " + code);
    }
}
