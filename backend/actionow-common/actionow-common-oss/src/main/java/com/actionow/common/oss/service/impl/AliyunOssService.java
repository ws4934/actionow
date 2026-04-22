package com.actionow.common.oss.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.*;
import com.actionow.common.core.exception.ServiceException;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.oss.config.OssProperties;
import com.actionow.common.oss.service.OssService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 阿里云 OSS 服务实现
 * <p>
 * 使用阿里云 OSS 原生 SDK，支持全部 OSS 功能
 * <p>
 * 支持功能：
 * - 基础文件操作（上传、下载、删除、存在判断）
 * - 预签名 URL（上传、下载）
 * - 分片上传（大文件）
 * - 内网端点（ECS 内网访问）
 *
 * @author Actionow
 */
@Slf4j
public class AliyunOssService implements OssService {

    private final OSS ossClient;
    private final OssProperties ossProperties;
    private final OssProperties.AliyunConfig aliyunConfig;
    private final String bucketName;

    public AliyunOssService(OssProperties ossProperties) {
        this.ossProperties = ossProperties;
        this.aliyunConfig = ossProperties.getAliyun();
        this.bucketName = aliyunConfig.getBucket();

        String endpoint = aliyunConfig.getEndpoint();
        if (!StringUtils.hasText(endpoint)) {
            throw new IllegalStateException("Aliyun OSS Endpoint 未配置");
        }

        this.ossClient = new OSSClientBuilder().build(
                endpoint,
                aliyunConfig.getAccessKeyId(),
                aliyunConfig.getAccessKeySecret()
        );

        log.info("Aliyun OSS 客户端初始化完成: endpoint={}, bucket={}", endpoint, bucketName);
    }

    @PreDestroy
    public void shutdown() {
        if (ossClient != null) {
            ossClient.shutdown();
            log.info("Aliyun OSS 客户端已关闭");
        }
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

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength(size);

            ossClient.putObject(bucketName, objectName, inputStream, metadata);

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

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength(bytes.length);

            ossClient.putObject(bucket, objectName, new ByteArrayInputStream(bytes), metadata);

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
            OSSObject ossObject = ossClient.getObject(bucket, objectName);
            return ossObject.getObjectContent();
        } catch (com.aliyun.oss.OSSException e) {
            if ("NoSuchKey".equals(e.getErrorCode())) {
                log.warn("文件不存在: bucket={}, object={}", bucket, objectName);
                throw new ServiceException(ResultCode.ASSET_NOT_FOUND, "文件不存在");
            }
            log.error("文件下载失败: bucket={}, object={}", bucket, objectName, e);
            throw new ServiceException(ResultCode.FAIL.getCode(), "文件下载失败", e);
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
            ossClient.deleteObject(bucket, objectName);
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
            ossClient.copyObject(sourceBucket, sourceObjectName, targetBucket, targetObjectName);
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
            return ossClient.doesObjectExist(bucket, objectName);
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
        // 使用通用自定义域名
        String domain = ossProperties.getDomain();
        if (StringUtils.hasText(domain)) {
            // 确保 URL 包含协议前缀
            if (!domain.startsWith("http://") && !domain.startsWith("https://")) {
                domain = "https://" + domain;
            }
            return String.format("%s/%s", domain, objectName);
        }

        // 使用阿里云标准 URL
        String endpoint = aliyunConfig.getEndpoint();
        // 移除协议前缀以构建标准 URL
        String host = endpoint.replace("https://", "").replace("http://", "");
        return String.format("https://%s.%s/%s", bucket, host, objectName);
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

            Date expiration = new Date(System.currentTimeMillis() + expireSeconds * 1000L);
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                    bucketName, objectName, com.aliyun.oss.HttpMethod.PUT);
            request.setExpiration(expiration);

            URL url = ossClient.generatePresignedUrl(request);
            String presignedUrl = url.toString();

            log.debug("生成预签名上传URL: object={}, expireSeconds={}", objectName, expireSeconds);
            return presignedUrl;
        } catch (Exception e) {
            log.error("获取预签名上传URL失败: object={}", objectName, e);
            throw new ServiceException(ResultCode.FAIL, "获取上传URL失败");
        }
    }

    @Override
    public String getPresignedDownloadUrl(String objectName, int expireSeconds) {
        try {
            Date expiration = new Date(System.currentTimeMillis() + expireSeconds * 1000L);
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                    bucketName, objectName, com.aliyun.oss.HttpMethod.GET);
            request.setExpiration(expiration);

            URL url = ossClient.generatePresignedUrl(request);
            String presignedUrl = url.toString();

            log.debug("生成预签名下载URL: object={}, expireSeconds={}", objectName, expireSeconds);
            return presignedUrl;
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

            ossClient.createBucket(bucket);
            log.info("存储桶创建成功: {}", bucket);
        } catch (com.aliyun.oss.OSSException e) {
            if ("BucketAlreadyExists".equals(e.getErrorCode())) {
                log.debug("存储桶已存在: {}", bucket);
            } else {
                log.error("存储桶创建失败: {}", bucket, e);
                throw new ServiceException(ResultCode.FAIL, "存储桶创建失败");
            }
        } catch (Exception e) {
            log.error("存储桶创建失败: {}", bucket, e);
            throw new ServiceException(ResultCode.FAIL, "存储桶创建失败");
        }
    }

    @Override
    public boolean bucketExists(String bucket) {
        try {
            return ossClient.doesBucketExist(bucket);
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

            InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(bucketName, objectName);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            request.setObjectMetadata(metadata);

            InitiateMultipartUploadResult result = ossClient.initiateMultipartUpload(request);
            String uploadId = result.getUploadId();

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
            Date expiration = new Date(System.currentTimeMillis() + expireSeconds * 1000L);

            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                    bucketName, objectName, com.aliyun.oss.HttpMethod.PUT);
            request.setExpiration(expiration);
            request.addQueryParameter("uploadId", uploadId);
            request.addQueryParameter("partNumber", String.valueOf(partNumber));

            URL url = ossClient.generatePresignedUrl(request);
            String presignedUrl = url.toString();

            log.debug("生成分片上传URL: object={}, partNumber={}", objectName, partNumber);
            return presignedUrl;
        } catch (Exception e) {
            log.error("获取分片上传URL失败: object={}, partNumber={}", objectName, partNumber, e);
            throw new ServiceException(ResultCode.FAIL, "获取分片上传URL失败");
        }
    }

    @Override
    public String completeMultipartUpload(String objectName, String uploadId, List<Map<String, Object>> parts) {
        try {
            List<PartETag> partETags = parts.stream()
                    .map(part -> new PartETag(
                            ((Number) part.get("partNumber")).intValue(),
                            (String) part.get("etag")
                    ))
                    .sorted(Comparator.comparingInt(PartETag::getPartNumber))
                    .toList();

            CompleteMultipartUploadRequest request = new CompleteMultipartUploadRequest(
                    bucketName, objectName, uploadId, partETags);

            ossClient.completeMultipartUpload(request);

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
            AbortMultipartUploadRequest request = new AbortMultipartUploadRequest(bucketName, objectName, uploadId);
            ossClient.abortMultipartUpload(request);
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
