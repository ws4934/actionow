package com.actionow.common.oss.service.impl;

import com.actionow.common.core.exception.ServiceException;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.oss.config.OssProperties;
import com.actionow.common.oss.service.OssService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * AWS S3 OSS 服务实现
 * <p>
 * 仅用于 AWS 原生 S3 服务，配置独立于其他存储提供商
 * <p>
 * 支持功能：
 * - 基础文件操作（上传、下载、删除、存在判断）
 * - 预签名 URL（上传、下载）
 * - 分片上传（大文件）
 * - Transfer Acceleration（可选）
 * - CloudFront CDN 集成（可选）
 *
 * @author Actionow
 */
@Slf4j
public class S3OssService implements OssService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final OssProperties ossProperties;
    private final OssProperties.S3Config s3Config;
    private final String bucketName;
    private final String regionName;

    public S3OssService(OssProperties ossProperties) {
        this.ossProperties = ossProperties;
        this.s3Config = ossProperties.getS3();
        this.bucketName = s3Config.getBucket();
        this.regionName = s3Config.getRegion();

        // 构建凭证提供者
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                        s3Config.getAccessKeyId(),
                        s3Config.getSecretAccessKey()
                )
        );

        // 构建 S3 客户端
        S3ClientBuilder clientBuilder = S3Client.builder()
                .region(Region.of(regionName))
                .credentialsProvider(credentialsProvider);

        // S3 配置
        S3Configuration.Builder s3ConfigBuilder = S3Configuration.builder();

        // Transfer Acceleration
        if (s3Config.isTransferAcceleration()) {
            s3ConfigBuilder.accelerateModeEnabled(true);
            log.info("S3 Transfer Acceleration 已启用");
        }

        clientBuilder.serviceConfiguration(s3ConfigBuilder.build());
        this.s3Client = clientBuilder.build();

        // 构建 S3 Presigner
        this.s3Presigner = S3Presigner.builder()
                .region(Region.of(regionName))
                .credentialsProvider(credentialsProvider)
                .build();

        log.info("AWS S3 客户端初始化完成: region={}, bucket={}", regionName, bucketName);
    }

    @PreDestroy
    public void destroy() {
        if (s3Client != null) {
            s3Client.close();
        }
        if (s3Presigner != null) {
            s3Presigner.close();
        }
        log.info("AWS S3 客户端已关闭");
    }

    // ==================== 基础文件操作 ====================

    @Override
    public String upload(String objectName, InputStream inputStream, String contentType) {
        return upload(bucketName, objectName, inputStream, contentType);
    }

    @Override
    public String upload(String bucket, String objectName, InputStream inputStream, String contentType) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            return uploadBytes(bucket, objectName, bytes, contentType);
        } catch (IOException e) {
            log.error("读取文件流失败: object={}", objectName, e);
            throw new ServiceException(ResultCode.UPLOAD_FAILED, e);
        }
    }

    @Override
    public String upload(String objectName, InputStream inputStream, long size, String contentType) {
        try {
            ensureBucketExists(bucketName);

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .contentType(contentType)
                    .contentLength(size)
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, size));

            log.info("文件上传成功: bucket={}, object={}, size={}", bucketName, objectName, size);
            return getUrl(bucketName, objectName);
        } catch (Exception e) {
            log.error("文件上传失败: object={}", objectName, e);
            throw new ServiceException(ResultCode.UPLOAD_FAILED, e);
        }
    }

    private String uploadBytes(String bucket, String objectName, byte[] bytes, String contentType) {
        try {
            ensureBucketExists(bucket);

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectName)
                    .contentType(contentType)
                    .contentLength((long) bytes.length)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(bytes));

            log.info("文件上传成功: bucket={}, object={}, size={}", bucket, objectName, bytes.length);
            return getUrl(bucket, objectName);
        } catch (Exception e) {
            log.error("文件上传失败: bucket={}, object={}", bucket, objectName, e);
            throw new ServiceException(ResultCode.UPLOAD_FAILED, e);
        }
    }

    @Override
    public InputStream download(String objectName) {
        return download(bucketName, objectName);
    }

    @Override
    public InputStream download(String bucket, String objectName) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectName)
                    .build();

            return s3Client.getObject(request);
        } catch (NoSuchKeyException e) {
            log.warn("文件不存在: bucket={}, object={}", bucket, objectName);
            throw new ServiceException(ResultCode.ASSET_NOT_FOUND, "文件不存在");
        } catch (Exception e) {
            log.error("文件下载失败: bucket={}, object={}", bucket, objectName, e);
            throw new ServiceException(ResultCode.FAIL.getCode(), "文件下载失败", e);
        }
    }

    @Override
    public void delete(String objectName) {
        delete(bucketName, objectName);
    }

    @Override
    public void delete(String bucket, String objectName) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectName)
                    .build();

            s3Client.deleteObject(request);
            log.info("文件删除成功: bucket={}, object={}", bucket, objectName);
        } catch (Exception e) {
            log.error("文件删除失败: bucket={}, object={}", bucket, objectName, e);
            throw new ServiceException(ResultCode.FAIL, "文件删除失败");
        }
    }

    @Override
    public void copy(String sourceObjectName, String targetObjectName) {
        copy(bucketName, sourceObjectName, bucketName, targetObjectName);
    }

    @Override
    public void copy(String sourceBucket, String sourceObjectName, String targetBucket, String targetObjectName) {
        try {
            CopyObjectRequest request = CopyObjectRequest.builder()
                    .sourceBucket(sourceBucket)
                    .sourceKey(sourceObjectName)
                    .destinationBucket(targetBucket)
                    .destinationKey(targetObjectName)
                    .build();

            s3Client.copyObject(request);
            log.info("文件复制成功: {}:{} -> {}:{}", sourceBucket, sourceObjectName, targetBucket, targetObjectName);
        } catch (Exception e) {
            log.error("文件复制失败: {}:{} -> {}:{}", sourceBucket, sourceObjectName, targetBucket, targetObjectName, e);
            throw new ServiceException(ResultCode.FAIL, "文件复制失败");
        }
    }

    @Override
    public boolean exists(String objectName) {
        return exists(bucketName, objectName);
    }

    @Override
    public boolean exists(String bucket, String objectName) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectName)
                    .build();

            s3Client.headObject(request);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.warn("检查文件存在性失败: bucket={}, object={}", bucket, objectName, e);
            return false;
        }
    }

    @Override
    public String getUrl(String objectName) {
        return getUrl(bucketName, objectName);
    }

    @Override
    public String getUrl(String bucket, String objectName) {
        // 优先使用 CloudFront CDN 域名
        String cloudfrontDomain = s3Config.getCloudfrontDomain();
        if (StringUtils.hasText(cloudfrontDomain)) {
            return String.format("https://%s/%s", cloudfrontDomain, objectName);
        }

        // 使用通用自定义域名
        String domain = ossProperties.getDomain();
        if (StringUtils.hasText(domain)) {
            // 确保 URL 包含协议前缀
            if (!domain.startsWith("http://") && !domain.startsWith("https://")) {
                domain = "https://" + domain;
            }
            return String.format("%s/%s", domain, objectName);
        }

        // 使用 S3 标准 URL
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, regionName, objectName);
    }

    @Override
    public String getPublicUrl(String objectName) {
        return getPublicUrl(bucketName, objectName);
    }

    @Override
    public String getPublicUrl(String bucket, String objectName) {
        // 返回用于Gateway代理的相对路径
        return "/" + bucket + "/" + objectName;
    }

    // ==================== 预签名 URL ====================

    @Override
    public String getPresignedUploadUrl(String objectName, int expireSeconds) {
        try {
            ensureBucketExists(bucketName);

            PutObjectRequest objectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expireSeconds))
                    .putObjectRequest(objectRequest)
                    .build();

            PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
            String url = presignedRequest.url().toString();

            log.debug("生成预签名上传URL: object={}, expireSeconds={}", objectName, expireSeconds);
            return url;
        } catch (Exception e) {
            log.error("获取预签名上传URL失败: object={}", objectName, e);
            throw new ServiceException(ResultCode.FAIL, "获取上传URL失败");
        }
    }

    @Override
    public String getPresignedDownloadUrl(String objectName, int expireSeconds) {
        try {
            GetObjectRequest objectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expireSeconds))
                    .getObjectRequest(objectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            String url = presignedRequest.url().toString();

            log.debug("生成预签名下载URL: object={}, expireSeconds={}", objectName, expireSeconds);
            return url;
        } catch (Exception e) {
            log.error("获取预签名下载URL失败: object={}", objectName, e);
            throw new ServiceException(ResultCode.FAIL, "获取下载URL失败");
        }
    }

    // ==================== 存储桶管理 ====================

    @Override
    public void createBucket(String bucket) {
        try {
            if (bucketExists(bucket)) {
                log.debug("存储桶已存在: {}", bucket);
                return;
            }

            CreateBucketRequest request = CreateBucketRequest.builder()
                    .bucket(bucket)
                    .build();

            s3Client.createBucket(request);
            log.info("存储桶创建成功: {}", bucket);
        } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException e) {
            log.debug("存储桶已存在: {}", bucket);
        } catch (Exception e) {
            log.error("存储桶创建失败: {}", bucket, e);
            throw new ServiceException(ResultCode.FAIL, "存储桶创建失败");
        }
    }

    @Override
    public boolean bucketExists(String bucket) {
        try {
            HeadBucketRequest request = HeadBucketRequest.builder()
                    .bucket(bucket)
                    .build();

            s3Client.headBucket(request);
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        } catch (Exception e) {
            log.warn("检查存储桶失败: {}", bucket, e);
            return false;
        }
    }

    // ==================== 分片上传 ====================

    @Override
    public String initMultipartUpload(String objectName, String contentType) {
        try {
            ensureBucketExists(bucketName);

            CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .contentType(contentType)
                    .build();

            CreateMultipartUploadResponse response = s3Client.createMultipartUpload(request);
            String uploadId = response.uploadId();

            log.info("分片上传初始化成功: object={}, uploadId={}", objectName, uploadId);
            return uploadId;
        } catch (Exception e) {
            log.error("分片上传初始化失败: object={}", objectName, e);
            throw new ServiceException(ResultCode.FAIL, "分片上传初始化失败");
        }
    }

    @Override
    public String getPresignedPartUploadUrl(String objectName, String uploadId, int partNumber, int expireSeconds) {
        try {
            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .build();

            UploadPartPresignRequest presignRequest = UploadPartPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expireSeconds))
                    .uploadPartRequest(uploadPartRequest)
                    .build();

            PresignedUploadPartRequest presignedRequest = s3Presigner.presignUploadPart(presignRequest);
            String url = presignedRequest.url().toString();

            log.debug("生成分片上传URL: object={}, partNumber={}", objectName, partNumber);
            return url;
        } catch (Exception e) {
            log.error("获取分片上传URL失败: object={}, partNumber={}", objectName, partNumber, e);
            throw new ServiceException(ResultCode.FAIL, "获取分片上传URL失败");
        }
    }

    @Override
    public String completeMultipartUpload(String objectName, String uploadId, List<Map<String, Object>> parts) {
        try {
            List<CompletedPart> completedParts = parts.stream()
                    .map(part -> CompletedPart.builder()
                            .partNumber(((Number) part.get("partNumber")).intValue())
                            .eTag((String) part.get("etag"))
                            .build())
                    .sorted(Comparator.comparingInt(CompletedPart::partNumber))
                    .toList();

            CompleteMultipartUploadRequest request = CompleteMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder()
                            .parts(completedParts)
                            .build())
                    .build();

            s3Client.completeMultipartUpload(request);

            log.info("分片上传完成: object={}, uploadId={}, parts={}", objectName, uploadId, parts.size());
            return getUrl(bucketName, objectName);
        } catch (Exception e) {
            log.error("完成分片上传失败: object={}, uploadId={}", objectName, uploadId, e);
            throw new ServiceException(ResultCode.FAIL, "完成分片上传失败");
        }
    }

    @Override
    public void abortMultipartUpload(String objectName, String uploadId) {
        try {
            AbortMultipartUploadRequest request = AbortMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .uploadId(uploadId)
                    .build();

            s3Client.abortMultipartUpload(request);
            log.info("分片上传已取消: object={}, uploadId={}", objectName, uploadId);
        } catch (Exception e) {
            log.error("取消分片上传失败: object={}, uploadId={}", objectName, uploadId, e);
            throw new ServiceException(ResultCode.FAIL, "取消分片上传失败");
        }
    }

    // ==================== 私有方法 ====================

    private void ensureBucketExists(String bucket) {
        if (!bucketExists(bucket)) {
            createBucket(bucket);
        }
    }
}
