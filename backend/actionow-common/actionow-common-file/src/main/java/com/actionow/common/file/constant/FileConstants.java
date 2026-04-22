package com.actionow.common.file.constant;

/**
 * 文件服务常量
 *
 * @author Actionow
 */
public final class FileConstants {

    private FileConstants() {
    }

    /**
     * 存储命名空间
     * 定义公共空间和租户空间的顶级目录
     */
    public static final class Namespace {
        /** 公共空间 - 系统级资源 */
        public static final String PUBLIC = "public";
        /** 租户空间前缀 - 工作空间隔离 */
        public static final String TENANT_PREFIX = "tenant_";
        /** 全局临时空间 */
        public static final String TEMP = "temp";

        private Namespace() {
        }

        /**
         * 构建租户命名空间
         * @param workspaceId 工作空间ID
         * @return tenant_{workspaceId}
         */
        public static String tenant(String workspaceId) {
            return TENANT_PREFIX + workspaceId;
        }
    }

    /**
     * 文件来源
     * 区分用户上传和AI生成
     */
    public static final class Source {
        /** 用户上传 */
        public static final String UPLOADS = "uploads";
        /** AI生成 */
        public static final String AI_GENERATED = "ai-generated";
        /** 外部转存 */
        public static final String TRANSFERRED = "transferred";

        private Source() {
        }
    }

    /**
     * 媒体类型目录
     * 按文件类型分类存储
     */
    public static final class MediaDir {
        public static final String IMAGES = "images";
        public static final String VIDEOS = "videos";
        public static final String AUDIOS = "audios";
        public static final String DOCUMENTS = "documents";
        public static final String MODELS = "models";
        public static final String THUMBNAILS = "thumbnails";

        private MediaDir() {
        }

        /**
         * 根据 FileType 获取对应的媒体目录
         */
        public static String fromFileType(String fileType) {
            return switch (fileType) {
                case FileType.IMAGE -> IMAGES;
                case FileType.VIDEO -> VIDEOS;
                case FileType.AUDIO -> AUDIOS;
                case FileType.MODEL -> MODELS;
                default -> DOCUMENTS;
            };
        }
    }

    /**
     * 特殊目录
     */
    public static final class SpecialDir {
        /** 回收站 - 软删除文件存放处，90天自动清理 */
        public static final String TRASH = "trash";
        /** 临时文件 - 7天自动清理 */
        public static final String TEMP = "temp";
        /** 创作元素 */
        public static final String ELEMENTS = "elements";
        /** 剧本资源 */
        public static final String SCRIPTS = "scripts";
        /** 系统资源 */
        public static final String SYSTEM = "system";
        /** 分镜资源 */
        public static final String STORYBOARDS = "storyboards";
        /** 导出文件 */
        public static final String EXPORTS = "exports";
        /** 分片上传 */
        public static final String MULTIPART = "multipart";

        private SpecialDir() {
        }
    }

    /**
     * 公共空间子目录
     */
    public static final class PublicDir {
        /** 系统头像 */
        public static final String AVATARS = "system/avatars";
        /** 系统图标 */
        public static final String ICONS = "system/icons";
        /** 系统模板 */
        public static final String TEMPLATES = "system/templates";
        /** 系统角色 */
        public static final String CHARACTERS = "elements/characters";
        /** 系统场景 */
        public static final String SCENES = "elements/scenes";
        /** 系统道具 */
        public static final String PROPS = "elements/props";
        /** 系统风格 */
        public static final String STYLES = "elements/styles";
        /** 模型缩略图 */
        public static final String MODEL_THUMBNAILS = "models/thumbnails";

        private PublicDir() {
        }
    }

    /**
     * 文件类型分类
     */
    public static final class FileType {
        public static final String IMAGE = "IMAGE";
        public static final String VIDEO = "VIDEO";
        public static final String AUDIO = "AUDIO";
        public static final String DOCUMENT = "DOCUMENT";
        public static final String MODEL = "MODEL";
        public static final String OTHER = "OTHER";

        private FileType() {
        }
    }

    /**
     * 允许的图片 MIME 类型
     */
    public static final String[] ALLOWED_IMAGE_TYPES = {
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp", "image/svg+xml"
    };

    /**
     * 允许的视频 MIME 类型
     */
    public static final String[] ALLOWED_VIDEO_TYPES = {
            "video/mp4", "video/webm", "video/quicktime", "video/x-msvideo"
    };

    /**
     * 允许的音频 MIME 类型
     */
    public static final String[] ALLOWED_AUDIO_TYPES = {
            "audio/mpeg", "audio/wav", "audio/ogg", "audio/aac", "audio/flac", "audio/x-m4a"
    };

    /**
     * 允许的文档 MIME 类型
     */
    public static final String[] ALLOWED_DOCUMENT_TYPES = {
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain"
    };

    /**
     * 允许的 3D 模型 MIME 类型
     */
    public static final String[] ALLOWED_MODEL_TYPES = {
            "model/gltf-binary", "model/gltf+json", "application/octet-stream"
    };

    /**
     * 最大文件大小（字节）
     */
    public static final class MaxFileSize {
        public static final long IMAGE = 20 * 1024 * 1024;    // 20MB
        public static final long VIDEO = 500 * 1024 * 1024;   // 500MB
        public static final long AUDIO = 50 * 1024 * 1024;    // 50MB
        public static final long DOCUMENT = 50 * 1024 * 1024; // 50MB
        public static final long MODEL = 100 * 1024 * 1024;   // 100MB

        private MaxFileSize() {
        }
    }

    /**
     * 预签名 URL 有效期（秒）
     */
    public static final int PRESIGNED_URL_EXPIRE_SECONDS = 3600;

    /**
     * 下载 URL 默认有效期（秒）
     */
    public static final int DOWNLOAD_URL_EXPIRE_SECONDS = 3600;

    /**
     * 分片上传最小分片大小（字节）
     */
    public static final long MIN_PART_SIZE = 5 * 1024 * 1024; // 5MB

    /**
     * 回收站保留天数
     */
    public static final int TRASH_RETENTION_DAYS = 90;

    /**
     * 临时文件保留天数
     */
    public static final int TEMP_RETENTION_DAYS = 7;

    /**
     * OSS 存储路径前缀
     * @deprecated 使用 {@link MediaDir}、{@link Source}、{@link Namespace} 替代
     */
    @Deprecated
    public static final class StoragePath {
        public static final String IMAGES = "images";
        public static final String VIDEOS = "videos";
        public static final String AUDIOS = "audios";
        public static final String DOCUMENTS = "documents";
        public static final String MODELS = "models";
        public static final String THUMBNAILS = "thumbnails";
        public static final String TEMP = "temp";
        public static final String AI_GENERATED = "ai-generated";
        public static final String TRANSFERRED = "transferred";

        private StoragePath() {
        }
    }
}
