package com.actionow.task.constant;

/**
 * 任务服务常量
 *
 * @author Actionow
 */
public final class TaskConstants {

    private TaskConstants() {
    }

    /**
     * 任务状态
     * @deprecated 使用 {@link com.actionow.task.constant.enums.TaskStatusEnum} 代替
     */
    @Deprecated
    public static final class TaskStatus {
        public static final String PENDING = "PENDING";
        public static final String RUNNING = "RUNNING";
        public static final String COMPLETED = "COMPLETED";
        public static final String FAILED = "FAILED";
        public static final String CANCELLED = "CANCELLED";
    }

    /**
     * 任务类型
     * @deprecated 使用 {@link com.actionow.task.constant.enums.TaskTypeEnum} 代替
     */
    @Deprecated
    public static final class TaskType {
        public static final String IMAGE_GENERATION = "IMAGE_GENERATION";
        public static final String VIDEO_GENERATION = "VIDEO_GENERATION";
        public static final String AUDIO_GENERATION = "AUDIO_GENERATION";
        public static final String TEXT_GENERATION = "TEXT_GENERATION";
        public static final String TTS_GENERATION = "TTS_GENERATION";
        public static final String BATCH_EXPORT = "BATCH_EXPORT";
        public static final String FILE_PROCESSING = "FILE_PROCESSING";
    }

    /**
     * 任务优先级
     */
    public static final class Priority {
        public static final int LOW = 1;
        public static final int NORMAL = 5;
        public static final int HIGH = 10;
    }

    /**
     * 默认重试次数
     */
    public static final int DEFAULT_MAX_RETRY = 3;

    /**
     * 默认超时时间（秒）
     */
    public static final int DEFAULT_TIMEOUT_SECONDS = 300;

    /**
     * 补偿任务状态
     */
    public static final class CompensationStatus {
        public static final String PENDING = "PENDING";
        public static final String PROCESSING = "PROCESSING";
        public static final String COMPLETED = "COMPLETED";
        public static final String EXHAUSTED = "EXHAUSTED";
    }

    /**
     * 补偿任务配置
     */
    public static final class Compensation {
        /** 最大重试次数 */
        public static final int MAX_RETRY_COUNT = 5;
        /** 扫描间隔（毫秒） */
        public static final long SCAN_INTERVAL_MS = 30000L;
        /** 每次扫描数量 */
        public static final int BATCH_SIZE = 50;
        /** 首次重试延迟（秒） */
        public static final int INITIAL_RETRY_DELAY_SECONDS = 30;
        /** 最大重试延迟（秒） */
        public static final int MAX_RETRY_DELAY_SECONDS = 3600;
    }

    /**
     * 实体类型
     */
    public static final class EntityType {
        public static final String SCRIPT = "SCRIPT";
        public static final String EPISODE = "EPISODE";
        public static final String STORYBOARD = "STORYBOARD";
        public static final String CHARACTER = "CHARACTER";
        public static final String SCENE = "SCENE";
        public static final String PROP = "PROP";
        public static final String STYLE = "STYLE";
        public static final String ASSET = "ASSET";
    }

    /**
     * 任务来源
     */
    public static final class TaskSource {
        public static final String MANUAL = "MANUAL";
        public static final String BATCH = "BATCH";
        public static final String RETRY = "RETRY";
        public static final String SCHEDULED = "SCHEDULED";
    }

    /**
     * AI 生成锁配置
     */
    public static final class GenerationLock {
        /** 锁超时时间（秒） */
        public static final long LOCK_EXPIRE_SECONDS = 600L;
        /** 锁获取重试次数 */
        public static final int LOCK_RETRY_COUNT = 3;
    }

    /**
     * 批量作业类型
     */
    public static final class BatchType {
        public static final String SIMPLE = "SIMPLE";
        public static final String PIPELINE = "PIPELINE";
        public static final String VARIATION = "VARIATION";
        public static final String SCOPE = "SCOPE";
        public static final String AB_TEST = "AB_TEST";
    }

    /**
     * 批量作业状态
     * @deprecated 使用 {@link com.actionow.task.constant.enums.BatchStatusEnum} 代替
     */
    @Deprecated
    public static final class BatchStatus {
        public static final String CREATED = "CREATED";
        public static final String RUNNING = "RUNNING";
        public static final String PAUSED = "PAUSED";
        public static final String COMPLETED = "COMPLETED";
        public static final String FAILED = "FAILED";
        public static final String CANCELLED = "CANCELLED";
    }

    /**
     * 批量作业子项状态
     * @deprecated 使用 {@link com.actionow.task.constant.enums.BatchItemStatusEnum} 代替
     */
    @Deprecated
    public static final class BatchItemStatus {
        public static final String PENDING = "PENDING";
        public static final String SUBMITTED = "SUBMITTED";
        public static final String RUNNING = "RUNNING";
        public static final String COMPLETED = "COMPLETED";
        public static final String FAILED = "FAILED";
        public static final String SKIPPED = "SKIPPED";
        public static final String CANCELLED = "CANCELLED";
    }

    /**
     * 错误处理策略
     */
    public static final class ErrorStrategy {
        public static final String CONTINUE = "CONTINUE";
        public static final String STOP = "STOP";
        public static final String RETRY_THEN_CONTINUE = "RETRY_THEN_CONTINUE";
    }

    /**
     * 批量作业来源
     */
    public static final class BatchSource {
        public static final String API = "API";
        public static final String AGENT = "AGENT";
        public static final String SCHEDULED = "SCHEDULED";
    }

    /**
     * 条件跳过
     */
    public static final class SkipCondition {
        public static final String NONE = "NONE";
        public static final String ASSET_EXISTS = "ASSET_EXISTS";
    }

    /**
     * 批量作业默认配置
     */
    public static final class BatchDefaults {
        public static final int MAX_CONCURRENCY = 5;
        public static final int DEFAULT_PRIORITY = 5;
        public static final int MAX_ITEMS_PER_BATCH = 200;
    }

    /**
     * Pipeline 状态
     */
    public static final class PipelineStatus {
        public static final String CREATED = "CREATED";
        public static final String RUNNING = "RUNNING";
        public static final String COMPLETED = "COMPLETED";
        public static final String FAILED = "FAILED";
    }

    /**
     * Pipeline 步骤状态
     */
    public static final class PipelineStepStatus {
        public static final String PENDING = "PENDING";
        public static final String RUNNING = "RUNNING";
        public static final String COMPLETED = "COMPLETED";
        public static final String FAILED = "FAILED";
    }

    /**
     * Pipeline 步骤类型
     */
    public static final class PipelineStepType {
        public static final String GENERATE_TEXT = "GENERATE_TEXT";
        public static final String GENERATE_IMAGE = "GENERATE_IMAGE";
        public static final String GENERATE_VIDEO = "GENERATE_VIDEO";
        public static final String GENERATE_AUDIO = "GENERATE_AUDIO";
        public static final String GENERATE_TTS = "GENERATE_TTS";
        public static final String TRANSFORM = "TRANSFORM";
        public static final String EXPAND = "EXPAND";
    }

    /**
     * 预定义 Pipeline 模板
     */
    public static final class PipelineTemplate {
        /** 先生提示词再生图 */
        public static final String TEXT_TO_PROMPT_TO_IMAGE = "TEXT_TO_PROMPT_TO_IMAGE";
        /** 先生提示词再生视频 */
        public static final String TEXT_TO_PROMPT_TO_VIDEO = "TEXT_TO_PROMPT_TO_VIDEO";
        /** 先生提示词再生音频 */
        public static final String TEXT_TO_PROMPT_TO_AUDIO = "TEXT_TO_PROMPT_TO_AUDIO";
        /** 文生图再图生视频 */
        public static final String TEXT_TO_IMAGE_TO_VIDEO = "TEXT_TO_IMAGE_TO_VIDEO";
        /** 生成关键帧再生视频 */
        public static final String TEXT_TO_KEYFRAMES_TO_VIDEO = "TEXT_TO_KEYFRAMES_TO_VIDEO";
        /** 分镜全流程: 文 → 图 → 视频 */
        public static final String FULL_STORYBOARD = "FULL_STORYBOARD";
    }
}
