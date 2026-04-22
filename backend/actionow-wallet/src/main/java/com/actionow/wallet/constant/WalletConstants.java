package com.actionow.wallet.constant;

/**
 * 钱包常量
 *
 * @author Actionow
 */
public final class WalletConstants {

    private WalletConstants() {}

    /**
     * 钱包状态
     */
    public static final class WalletStatus {
        public static final String ACTIVE = "ACTIVE";
        public static final String FROZEN = "FROZEN";
        public static final String CLOSED = "CLOSED";

        private WalletStatus() {}
    }

    /**
     * 交易类型
     */
    public static final class TransactionType {
        /** 充值 */
        public static final String TOPUP = "TOPUP";
        /** 消费 */
        public static final String CONSUME = "CONSUME";
        /** 退款 */
        public static final String REFUND = "REFUND";
        /** 冻结 */
        public static final String FREEZE = "FREEZE";
        /** 解冻 */
        public static final String UNFREEZE = "UNFREEZE";
        /** 赠送 */
        public static final String GIFT = "GIFT";
        /** 转入 */
        public static final String TRANSFER_IN = "TRANSFER_IN";
        /** 转出 */
        public static final String TRANSFER_OUT = "TRANSFER_OUT";

        private TransactionType() {}
    }

    /**
     * 交易状态
     */
    public static final class TransactionStatus {
        public static final String PENDING = "PENDING";
        public static final String SUCCESS = "SUCCESS";
        public static final String FAILED = "FAILED";
        public static final String CANCELLED = "CANCELLED";

        private TransactionStatus() {}
    }

    /**
     * 消费场景
     */
    public static final class ConsumeScene {
        /** 图片生成 */
        public static final String IMAGE_GENERATION = "IMAGE_GENERATION";
        /** 视频生成 */
        public static final String VIDEO_GENERATION = "VIDEO_GENERATION";
        /** 语音合成 */
        public static final String AUDIO_SYNTHESIS = "AUDIO_SYNTHESIS";
        /** AI 对话 */
        public static final String AI_CHAT = "AI_CHAT";

        private ConsumeScene() {}
    }

    /**
     * 默认值
     */
    public static final long DEFAULT_INITIAL_BALANCE = 0L;
    public static final long DEFAULT_MEMBER_QUOTA = 10000L; // 默认成员月配额：10000 积分
    public static final int DEFAULT_QUOTA_PERIOD_DAYS = 30; // 配额周期：30天

    /**
     * 各订阅计划的默认成员月配额（积分）
     * -1 表示无限制
     */
    public static final class PlanQuota {
        public static final long FREE = 10_000L;
        public static final long BASIC = 50_000L;
        public static final long PRO = 200_000L;
        public static final long ENTERPRISE = -1L; // 无限制

        private PlanQuota() {}

        /**
         * 根据计划类型获取对应的成员月配额
         */
        public static long getByPlanType(String planType) {
            if (planType == null) {
                return FREE;
            }
            return switch (planType.toUpperCase()) {
                case "FREE" -> FREE;
                case "BASIC" -> BASIC;
                case "PRO" -> PRO;
                case "ENTERPRISE" -> ENTERPRISE;
                default -> FREE;
            };
        }
    }

    /**
     * Redis Key 前缀
     */
    public static final String LOCK_WALLET_PREFIX = "actionow:lock:wallet:";
    public static final String CACHE_WALLET_PREFIX = "actionow:cache:wallet:";
}
