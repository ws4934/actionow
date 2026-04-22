package com.actionow.common.oss.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OSS 配置属性
 * 支持四种存储提供商：MinIO、AWS S3、Aliyun OSS、Cloudflare R2
 * 每种提供商有独立的配置区块，不混用
 *
 * @author Actionow
 */
@Data
@ConfigurationProperties(prefix = "actionow.oss")
public class OssProperties {

    /**
     * 存储类型：minio / s3 / aliyun / r2
     */
    private String type = "minio";

    /**
     * 外部访问域名（所有提供商通用，可选）
     */
    private String domain;

    /**
     * 上传文件大小限制（MB）
     */
    private Long maxSize = 100L;

    /**
     * 允许的文件类型
     */
    private String[] allowedTypes = {"image/jpeg", "image/png", "image/gif", "image/webp",
            "video/mp4", "video/webm", "audio/mp3", "audio/wav",
            "application/pdf", "application/json"};

    /**
     * MinIO 配置 (type=minio)
     */
    private MinioConfig minio = new MinioConfig();

    /**
     * AWS S3 配置 (type=s3)
     */
    private S3Config s3 = new S3Config();

    /**
     * 阿里云 OSS 配置 (type=aliyun)
     */
    private AliyunConfig aliyun = new AliyunConfig();

    /**
     * Cloudflare R2 配置 (type=r2)
     */
    private R2Config r2 = new R2Config();

    /**
     * 火山引擎 TOS 配置 (type=tos)
     */
    private TosConfig tos = new TosConfig();

    // ==================== MinIO 配置 ====================

    /**
     * MinIO 配置类
     * 适用于本地开发和私有部署
     */
    @Data
    public static class MinioConfig {

        /**
         * MinIO 服务端点
         * 示例: http://localhost:9000
         */
        private String endpoint = "http://localhost:9000";

        /**
         * Access Key (用户名)
         */
        private String accessKey = "minioadmin";

        /**
         * Secret Key (密码)
         */
        private String secretKey = "minioadmin";

        /**
         * 存储桶名称
         */
        private String bucket = "actionow";
    }

    // ==================== AWS S3 配置 ====================

    /**
     * AWS S3 配置类
     * 适用于 AWS 原生 S3 服务
     */
    @Data
    public static class S3Config {

        /**
         * AWS Access Key ID
         */
        private String accessKeyId;

        /**
         * AWS Secret Access Key
         */
        private String secretAccessKey;

        /**
         * AWS Region (e.g., us-east-1, ap-northeast-1)
         */
        private String region = "us-east-1";

        /**
         * S3 存储桶名称
         */
        private String bucket;

        /**
         * 是否启用 Transfer Acceleration
         */
        private boolean transferAcceleration = false;

        /**
         * CloudFront 分发域名 (可选)
         */
        private String cloudfrontDomain;

        /**
         * CloudFront Key Pair ID (签名 URL 用)
         */
        private String cloudfrontKeyPairId;

        /**
         * CloudFront Private Key 路径
         */
        private String cloudfrontPrivateKeyPath;

        /**
         * 预签名 URL 过期时间 (秒)
         */
        private int presignedUrlExpireSeconds = 3600;

        /**
         * 分片上传最小 Part 大小 (MB)
         */
        private int minPartSizeMb = 5;
    }

    // ==================== 阿里云 OSS 配置 ====================

    /**
     * 阿里云 OSS 配置类
     */
    @Data
    public static class AliyunConfig {

        /**
         * 阿里云 OSS Endpoint
         * 示例: https://oss-cn-hangzhou.aliyuncs.com
         */
        private String endpoint;

        /**
         * 阿里云 AccessKey ID
         */
        private String accessKeyId;

        /**
         * 阿里云 AccessKey Secret
         */
        private String accessKeySecret;

        /**
         * 存储桶名称
         */
        private String bucket;

        /**
         * 内网端点（可选，用于 ECS 内网访问）
         * 示例: https://oss-cn-hangzhou-internal.aliyuncs.com
         */
        private String internalEndpoint;

        /**
         * 预签名 URL 过期时间 (秒)
         */
        private int presignedUrlExpireSeconds = 3600;
    }

    // ==================== Cloudflare R2 配置 ====================

    /**
     * Cloudflare R2 配置类
     * R2 是 S3 兼容的对象存储服务
     */
    @Data
    public static class R2Config {

        /**
         * Cloudflare Account ID
         */
        private String accountId;

        /**
         * R2 Access Key ID
         */
        private String accessKeyId;

        /**
         * R2 Secret Access Key
         */
        private String secretAccessKey;

        /**
         * R2 存储桶名称
         */
        private String bucket;

        /**
         * 自定义公开访问域名（可选）
         * 如果配置了 R2 公开访问或自定义域名
         */
        private String publicDomain;

        /**
         * 预签名 URL 过期时间 (秒)
         */
        private int presignedUrlExpireSeconds = 3600;

        /**
         * 获取 R2 端点 URL
         * 格式: https://<account_id>.r2.cloudflarestorage.com
         */
        public String getEndpoint() {
            if (accountId == null || accountId.isBlank()) {
                return null;
            }
            return "https://" + accountId + ".r2.cloudflarestorage.com";
        }
    }

    // ==================== 火山引擎 TOS 配置 ====================

    /**
     * 火山引擎 TOS 配置类
     * TOS 是 S3 兼容的对象存储服务
     */
    @Data
    public static class TosConfig {

        /**
         * TOS Endpoint
         * 示例: tos-cn-beijing.volces.com
         */
        private String endpoint;

        /**
         * 火山引擎 Access Key ID
         */
        private String accessKeyId;

        /**
         * 火山引擎 Secret Access Key
         */
        private String secretAccessKey;

        /**
         * TOS 存储桶名称
         */
        private String bucket;

        /**
         * 区域 (e.g., cn-beijing)
         */
        private String region = "cn-beijing";

        /**
         * 自定义公开访问域名（可选）
         */
        private String publicDomain;

        /**
         * 预签名 URL 过期时间 (秒)
         */
        private int presignedUrlExpireSeconds = 3600;
    }
}
