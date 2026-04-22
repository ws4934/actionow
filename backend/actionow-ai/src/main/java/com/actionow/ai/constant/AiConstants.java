package com.actionow.ai.constant;

/**
 * AI 服务常量
 *
 * @author Actionow
 */
public final class AiConstants {

    private AiConstants() {}

    /**
     * 工作流类型
     */
    public static final class WorkflowType {
        public static final String IMAGE = "IMAGE";
        public static final String VIDEO = "VIDEO";
        public static final String AUDIO = "AUDIO";
        public static final String TEXT = "TEXT";

        private WorkflowType() {}
    }

    /**
     * 生成类型（兼容旧代码）
     */
    public static final class GenerationType {
        public static final String IMAGE = "IMAGE";
        public static final String VIDEO = "VIDEO";
        public static final String AUDIO = "AUDIO";
        public static final String TEXT = "TEXT";

        private GenerationType() {}
    }

    /**
     * 同步状态
     */
    public static final class SyncStatus {
        public static final String PENDING = "PENDING";
        public static final String SUCCESS = "SUCCESS";
        public static final String FAILED = "FAILED";

        private SyncStatus() {}
    }

    /**
     * 执行状态
     */
    public static final class ExecutionStatus {
        public static final String PENDING = "PENDING";
        public static final String RUNNING = "RUNNING";
        public static final String SUCCEEDED = "SUCCEEDED";
        public static final String FAILED = "FAILED";
        public static final String CANCELLED = "CANCELLED";

        private ExecutionStatus() {}
    }

    /**
     * 模型提供商
     */
    public static final class Provider {
        public static final String OPENAI = "OPENAI";
        public static final String ANTHROPIC = "ANTHROPIC";
        public static final String MIDJOURNEY = "MIDJOURNEY";
        public static final String STABLE_DIFFUSION = "STABLE_DIFFUSION";
        public static final String RUNWAY = "RUNWAY";
        public static final String ELEVENLABS = "ELEVENLABS";
        public static final String AZURE_TTS = "AZURE_TTS";

        private Provider() {}
    }

    /**
     * 图像模型
     */
    public static final class ImageModel {
        public static final String DALLE_3 = "dall-e-3";
        public static final String DALLE_2 = "dall-e-2";
        public static final String MIDJOURNEY_V6 = "midjourney-v6";
        public static final String SDXL = "sdxl-1.0";
        public static final String SD_3 = "sd3";

        private ImageModel() {}
    }

    /**
     * 视频模型
     */
    public static final class VideoModel {
        public static final String RUNWAY_GEN2 = "gen-2";
        public static final String RUNWAY_GEN3 = "gen-3";

        private VideoModel() {}
    }

    /**
     * 语音模型
     */
    public static final class AudioModel {
        public static final String ELEVENLABS_V2 = "eleven_multilingual_v2";
        public static final String AZURE_TTS = "azure-tts";

        private AudioModel() {}
    }

    /**
     * 图像尺寸
     */
    public static final class ImageSize {
        public static final String SIZE_1024X1024 = "1024x1024";
        public static final String SIZE_1024X1792 = "1024x1792";
        public static final String SIZE_1792X1024 = "1792x1024";
        public static final String SIZE_512X512 = "512x512";

        private ImageSize() {}
    }

    /**
     * 图像质量
     */
    public static final class ImageQuality {
        public static final String STANDARD = "standard";
        public static final String HD = "hd";

        private ImageQuality() {}
    }

    /**
     * 模板状态
     */
    public static final class TemplateStatus {
        public static final String ACTIVE = "ACTIVE";
        public static final String INACTIVE = "INACTIVE";

        private TemplateStatus() {}
    }

    /**
     * 模板作用域
     */
    public static final class TemplateScope {
        public static final String SYSTEM = "SYSTEM";
        public static final String WORKSPACE = "WORKSPACE";

        private TemplateScope() {}
    }

    /**
     * 积分消耗（单位：积分）
     */
    public static final class CreditCost {
        public static final long IMAGE_DALLE3_STANDARD = 100;
        public static final long IMAGE_DALLE3_HD = 200;
        public static final long IMAGE_MIDJOURNEY = 150;
        public static final long IMAGE_SDXL = 50;
        public static final long VIDEO_RUNWAY_5S = 500;
        public static final long VIDEO_RUNWAY_10S = 1000;
        public static final long AUDIO_PER_SECOND = 5;

        private CreditCost() {}
    }
}
