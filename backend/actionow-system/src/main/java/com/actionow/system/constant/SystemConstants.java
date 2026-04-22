package com.actionow.system.constant;

/**
 * 系统服务常量
 *
 * @author Actionow
 */
public final class SystemConstants {

    private SystemConstants() {}

    /**
     * 配置类型
     */
    public static final class ConfigType {
        public static final String SYSTEM = "SYSTEM";
        public static final String FEATURE = "FEATURE";
        public static final String LIMIT = "LIMIT";
        public static final String NOTIFICATION = "NOTIFICATION";
        public static final String AI = "AI";
        public static final String STORAGE = "STORAGE";

        private ConfigType() {}
    }

    /**
     * 配置作用域
     */
    public static final class ConfigScope {
        public static final String GLOBAL = "GLOBAL";
        public static final String WORKSPACE = "WORKSPACE";
        public static final String USER = "USER";

        private ConfigScope() {}
    }

    /**
     * 数据字典类型
     */
    public static final class DictType {
        public static final String SCRIPT_STATUS = "SCRIPT_STATUS";
        public static final String TASK_STATUS = "TASK_STATUS";
        public static final String ASSET_TYPE = "ASSET_TYPE";
        public static final String MEMBER_ROLE = "MEMBER_ROLE";
        public static final String GENERATION_TYPE = "GENERATION_TYPE";

        private DictType() {}
    }

    /**
     * 统计周期
     */
    public static final class StatsPeriod {
        public static final String HOURLY = "HOURLY";
        public static final String DAILY = "DAILY";
        public static final String WEEKLY = "WEEKLY";
        public static final String MONTHLY = "MONTHLY";

        private StatsPeriod() {}
    }

    /**
     * 统计指标类型
     */
    public static final class StatsMetric {
        public static final String USER_COUNT = "USER_COUNT";
        public static final String WORKSPACE_COUNT = "WORKSPACE_COUNT";
        public static final String SCRIPT_COUNT = "SCRIPT_COUNT";
        public static final String TASK_COUNT = "TASK_COUNT";
        public static final String AI_GENERATION_COUNT = "AI_GENERATION_COUNT";
        public static final String CREDIT_CONSUMED = "CREDIT_CONSUMED";
        public static final String STORAGE_USED = "STORAGE_USED";

        private StatsMetric() {}
    }

    /**
     * 缓存 Key 前缀
     */
    public static final class CacheKey {
        public static final String CONFIG_PREFIX = "system:config:";
        public static final String DICT_PREFIX = "system:dict:";
        public static final String STATS_PREFIX = "system:stats:";

        private CacheKey() {}
    }

    /**
     * Pub/Sub 通道
     * 注意：消费者侧 RuntimeConfigService 也使用此常量值，二者必须保持一致。
     * 由于 actionow-common-redis 不依赖 actionow-system，消费者侧无法直接 import；
     * 修改此值时务必同步修改 RuntimeConfigService.CHANNEL。
     */
    public static final class Channel {
        public static final String CONFIG_CHANGED = "system:config:changed";

        private Channel() {}
    }

    /**
     * 配置值类型（与 SystemConfig.valueType 一致）
     */
    public static final class ValueType {
        public static final String STRING = "STRING";
        public static final String INTEGER = "INTEGER";
        public static final String LONG = "LONG";
        public static final String BOOLEAN = "BOOLEAN";
        public static final String FLOAT = "FLOAT";
        public static final String JSON = "JSON";

        private ValueType() {}
    }
}
