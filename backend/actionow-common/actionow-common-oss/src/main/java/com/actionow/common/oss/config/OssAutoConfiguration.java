package com.actionow.common.oss.config;

import com.actionow.common.oss.service.OssService;
import com.actionow.common.oss.service.impl.AliyunOssService;
import com.actionow.common.oss.service.impl.MinioOssService;
import com.actionow.common.oss.service.impl.R2OssService;
import com.actionow.common.oss.service.impl.S3OssService;
import com.actionow.common.oss.service.impl.TosOssService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * OSS 自动配置类
 * 根据 actionow.oss.type 配置选择合适的 OssService 实现
 *
 * @author Actionow
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(OssProperties.class)
public class OssAutoConfiguration {

    /**
     * 启动期按 type 校验必填字段，fail-fast。
     */
    @Bean
    @ConditionalOnMissingBean(OssStartupValidator.class)
    public OssStartupValidator ossStartupValidator(OssProperties ossProperties) {
        return new OssStartupValidator(ossProperties);
    }

    /**
     * OSS 服务 Bean
     * 根据 actionow.oss.type 配置选择具体实现
     */
    @Bean
    @ConditionalOnMissingBean(OssService.class)
    public OssService ossService(OssProperties ossProperties) {
        String type = ossProperties.getType();
        log.info("Creating OssService bean for type: {}", type);

        return switch (type) {
            case "s3" -> {
                log.info("Initializing AWS S3 OssService");
                yield new S3OssService(ossProperties);
            }
            case "r2" -> {
                log.info("Initializing Cloudflare R2 OssService");
                yield new R2OssService(ossProperties);
            }
            case "aliyun" -> {
                log.info("Initializing Aliyun OSS OssService");
                yield new AliyunOssService(ossProperties);
            }
            case "tos" -> {
                log.info("Initializing Volcengine TOS OssService");
                yield new TosOssService(ossProperties);
            }
            default -> {
                // minio 或未指定时使用 MinIO
                log.info("Initializing MinIO OssService (default)");
                yield new MinioOssService(ossProperties);
            }
        };
    }
}
