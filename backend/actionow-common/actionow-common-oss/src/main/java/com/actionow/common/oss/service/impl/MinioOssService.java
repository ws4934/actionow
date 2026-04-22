package com.actionow.common.oss.service.impl;

import com.actionow.common.core.exception.ServiceException;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.oss.config.OssProperties;
import com.actionow.common.oss.service.OssService;
import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * MinIO OSS 服务实现
 *
 * @author Actionow
 */
@Slf4j
public class MinioOssService implements OssService {

    private final MinioClient minioClient;
    private final OssProperties ossProperties;
    private final OssProperties.MinioConfig minioConfig;

    public MinioOssService(OssProperties ossProperties) {
        this.ossProperties = ossProperties;
        this.minioConfig = ossProperties.getMinio();
        this.minioClient = MinioClient.builder()
                .endpoint(minioConfig.getEndpoint())
                .credentials(minioConfig.getAccessKey(), minioConfig.getSecretKey())
                .build();
        log.info("MinIO OSS 客户端初始化完成: endpoint={}, bucket={}",
                minioConfig.getEndpoint(), minioConfig.getBucket());
        // 启动时确保默认 bucket 存在并设置公共读策略
        ensureBucketExists(minioConfig.getBucket());
    }

    @Override
    public String upload(String objectName, InputStream inputStream, String contentType) {
        return upload(minioConfig.getBucket(), objectName, inputStream, contentType);
    }

    @Override
    public String upload(String bucket, String objectName, InputStream inputStream, String contentType) {
        try {
            ensureBucketExists(bucket);

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(inputStream, inputStream.available(), -1)
                    .contentType(contentType)
                    .build());

            log.info("文件上传成功: bucket={}, object={}", bucket, objectName);
            return getUrl(bucket, objectName);
        } catch (Exception e) {
            log.error("文件上传失败: bucket={}, object={}", bucket, objectName, e);
            throw new ServiceException(ResultCode.UPLOAD_FAILED, e);
        }
    }

    @Override
    public String upload(String objectName, InputStream inputStream, long size, String contentType) {
        try {
            String bucket = minioConfig.getBucket();
            ensureBucketExists(bucket);

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build());

            log.info("文件上传成功: bucket={}, object={}, size={}", bucket, objectName, size);
            return getUrl(bucket, objectName);
        } catch (Exception e) {
            log.error("文件上传失败: object={}", objectName, e);
            throw new ServiceException(ResultCode.UPLOAD_FAILED, e);
        }
    }

    @Override
    public InputStream download(String objectName) {
        return download(minioConfig.getBucket(), objectName);
    }

    @Override
    public InputStream download(String bucket, String objectName) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("文件下载失败: bucket={}, object={}", bucket, objectName, e);
            throw new ServiceException(ResultCode.ASSET_NOT_FOUND, e);
        }
    }

    @Override
    public void delete(String objectName) {
        delete(minioConfig.getBucket(), objectName);
    }

    @Override
    public void delete(String bucket, String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
            log.info("文件删除成功: bucket={}, object={}", bucket, objectName);
        } catch (Exception e) {
            log.error("文件删除失败: bucket={}, object={}", bucket, objectName, e);
            throw new ServiceException(ResultCode.FAIL, "文件删除失败");
        }
    }

    @Override
    public void copy(String sourceObjectName, String targetObjectName) {
        copy(minioConfig.getBucket(), sourceObjectName, minioConfig.getBucket(), targetObjectName);
    }

    @Override
    public void copy(String sourceBucket, String sourceObjectName, String targetBucket, String targetObjectName) {
        try {
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(targetBucket)
                    .object(targetObjectName)
                    .source(CopySource.builder()
                            .bucket(sourceBucket)
                            .object(sourceObjectName)
                            .build())
                    .build());
            log.info("文件复制成功: {}:{} -> {}:{}", sourceBucket, sourceObjectName, targetBucket, targetObjectName);
        } catch (Exception e) {
            log.error("文件复制失败: {}:{} -> {}:{}", sourceBucket, sourceObjectName, targetBucket, targetObjectName, e);
            throw new ServiceException(ResultCode.FAIL, "文件复制失败");
        }
    }

    @Override
    public boolean exists(String objectName) {
        return exists(minioConfig.getBucket(), objectName);
    }

    @Override
    public boolean exists(String bucket, String objectName) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getUrl(String objectName) {
        return getUrl(minioConfig.getBucket(), objectName);
    }

    @Override
    public String getUrl(String bucket, String objectName) {
        String domain = ossProperties.getDomain();
        String baseUrl;
        if (domain != null && !domain.isEmpty()) {
            baseUrl = domain;
        } else {
            baseUrl = minioConfig.getEndpoint();
        }
        // 确保 URL 包含协议前缀
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrl = "https://" + baseUrl;
        }
        return baseUrl + "/" + bucket + "/" + objectName;
    }

    @Override
    public String getPublicUrl(String objectName) {
        return getPublicUrl(minioConfig.getBucket(), objectName);
    }

    @Override
    public String getPublicUrl(String bucket, String objectName) {
        // 返回用于Gateway代理的相对路径
        return "/" + bucket + "/" + objectName;
    }

    @Override
    public String getPresignedUploadUrl(String objectName, int expireSeconds) {
        try {
            ensureBucketExists(minioConfig.getBucket());
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectName)
                    .method(Method.PUT)
                    .expiry(expireSeconds, TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            log.error("获取预签名上传URL失败: object={}", objectName, e);
            throw new ServiceException(ResultCode.FAIL, "获取上传URL失败");
        }
    }

    @Override
    public String getPresignedDownloadUrl(String objectName, int expireSeconds) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectName)
                    .method(Method.GET)
                    .expiry(expireSeconds, TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            log.error("获取预签名下载URL失败: object={}", objectName, e);
            throw new ServiceException(ResultCode.FAIL, "获取下载URL失败");
        }
    }

    @Override
    public void createBucket(String bucket) {
        try {
            if (!bucketExists(bucket)) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("存储桶创建成功: {}", bucket);
            }
            setBucketPublicReadPolicy(bucket);
        } catch (Exception e) {
            log.error("存储桶创建失败: {}", bucket, e);
            throw new ServiceException(ResultCode.FAIL, "存储桶创建失败");
        }
    }

    /**
     * 设置存储桶公共读策略（允许匿名 GetObject）。
     * 幂等操作 — 重复调用不会出错。
     */
    private void setBucketPublicReadPolicy(String bucket) {
        try {
            String policy = """
                    {
                      "Version": "2012-10-17",
                      "Statement": [
                        {
                          "Effect": "Allow",
                          "Principal": {"AWS": ["*"]},
                          "Action": ["s3:GetObject"],
                          "Resource": ["arn:aws:s3:::%s/*"]
                        }
                      ]
                    }
                    """.formatted(bucket);
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(bucket)
                    .config(policy)
                    .build());
            log.info("存储桶公共读策略设置成功: {}", bucket);
        } catch (Exception e) {
            log.warn("设置存储桶公共读策略失败（非致命，可手动配置）: bucket={}, error={}", bucket, e.getMessage());
        }
    }

    @Override
    public boolean bucketExists(String bucket) {
        try {
            return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        } catch (Exception e) {
            log.error("检查存储桶失败: {}", bucket, e);
            return false;
        }
    }

    // ==================== 分片上传实现 ====================
    // 注意: MinIO Java SDK 不公开低级别的分片上传API
    // 对于大文件上传，建议使用 Aliyun OSS 或配置更高的超时时间使用普通上传

    @Override
    public String initMultipartUpload(String objectName, String contentType) {
        // MinIO Java SDK 不支持低级别分片上传API
        // 对于大文件，可以使用普通上传（SDK会自动处理分片）
        // 或切换到 Aliyun OSS 实现
        log.warn("MinIO不支持客户端分片上传，建议使用Aliyun OSS或普通上传方式");
        throw new ServiceException(ResultCode.FAIL, "MinIO不支持客户端分片上传，请使用普通上传方式或切换到Aliyun OSS");
    }

    @Override
    public String getPresignedPartUploadUrl(String objectName, String uploadId, int partNumber, int expireSeconds) {
        log.warn("MinIO不支持客户端分片上传URL");
        throw new ServiceException(ResultCode.FAIL, "MinIO不支持客户端分片上传");
    }

    @Override
    public String completeMultipartUpload(String objectName, String uploadId, List<Map<String, Object>> parts) {
        log.warn("MinIO不支持客户端分片上传完成");
        throw new ServiceException(ResultCode.FAIL, "MinIO不支持客户端分片上传");
    }

    @Override
    public void abortMultipartUpload(String objectName, String uploadId) {
        log.warn("MinIO不支持取消客户端分片上传");
        throw new ServiceException(ResultCode.FAIL, "MinIO不支持客户端分片上传");
    }

    private void ensureBucketExists(String bucket) {
        if (!bucketExists(bucket)) {
            createBucket(bucket);
        }
    }
}
