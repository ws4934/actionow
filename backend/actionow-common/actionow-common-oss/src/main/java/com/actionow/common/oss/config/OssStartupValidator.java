package com.actionow.common.oss.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 按激活的 actionow.oss.type 校验对应提供商的必填字段，启动期 fail-fast。
 * 不做远端连通性检查（docker-compose 启动顺序可能瞬时不通），仅做配置形状校验。
 */
@Slf4j
@RequiredArgsConstructor
public class OssStartupValidator {

    private final OssProperties props;

    @PostConstruct
    public void validate() {
        String type = props.getType();
        if (type == null || type.isBlank()) {
            throw new IllegalStateException("actionow.oss.type 未配置");
        }

        List<String> missing = switch (type) {
            case "minio" -> checkMinio(props.getMinio());
            case "s3" -> checkS3(props.getS3());
            case "aliyun" -> checkAliyun(props.getAliyun());
            case "r2" -> checkR2(props.getR2());
            case "tos" -> checkTos(props.getTos());
            default -> throw new IllegalStateException("未知的 actionow.oss.type: " + type);
        };

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "OSS 配置不完整 (type=" + type + ")，缺失字段: " + String.join(", ", missing));
        }
        log.info("OSS config validated: type={}", type);
    }

    private List<String> checkMinio(OssProperties.MinioConfig c) {
        List<String> miss = new ArrayList<>();
        if (isBlank(c.getEndpoint())) miss.add("minio.endpoint");
        if (isBlank(c.getAccessKey())) miss.add("minio.access-key");
        if (isBlank(c.getSecretKey())) miss.add("minio.secret-key");
        if (isBlank(c.getBucket())) miss.add("minio.bucket");
        return miss;
    }

    private List<String> checkS3(OssProperties.S3Config c) {
        List<String> miss = new ArrayList<>();
        if (isBlank(c.getAccessKeyId())) miss.add("s3.access-key-id");
        if (isBlank(c.getSecretAccessKey())) miss.add("s3.secret-access-key");
        if (isBlank(c.getRegion())) miss.add("s3.region");
        if (isBlank(c.getBucket())) miss.add("s3.bucket");
        return miss;
    }

    private List<String> checkAliyun(OssProperties.AliyunConfig c) {
        List<String> miss = new ArrayList<>();
        if (isBlank(c.getEndpoint())) miss.add("aliyun.endpoint");
        if (isBlank(c.getAccessKeyId())) miss.add("aliyun.access-key-id");
        if (isBlank(c.getAccessKeySecret())) miss.add("aliyun.access-key-secret");
        if (isBlank(c.getBucket())) miss.add("aliyun.bucket");
        return miss;
    }

    private List<String> checkR2(OssProperties.R2Config c) {
        List<String> miss = new ArrayList<>();
        if (isBlank(c.getAccountId())) miss.add("r2.account-id");
        if (isBlank(c.getAccessKeyId())) miss.add("r2.access-key-id");
        if (isBlank(c.getSecretAccessKey())) miss.add("r2.secret-access-key");
        if (isBlank(c.getBucket())) miss.add("r2.bucket");
        return miss;
    }

    private List<String> checkTos(OssProperties.TosConfig c) {
        List<String> miss = new ArrayList<>();
        if (isBlank(c.getEndpoint())) miss.add("tos.endpoint");
        if (isBlank(c.getAccessKeyId())) miss.add("tos.access-key-id");
        if (isBlank(c.getSecretAccessKey())) miss.add("tos.secret-access-key");
        if (isBlank(c.getBucket())) miss.add("tos.bucket");
        if (isBlank(c.getRegion())) miss.add("tos.region");
        return miss;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
