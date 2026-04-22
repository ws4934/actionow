package com.actionow.common.oss.service.impl;

import com.actionow.common.core.exception.ServiceException;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.oss.config.OssProperties;
import com.actionow.common.oss.service.OssService;
import com.volcengine.tos.TOSV2;
import com.volcengine.tos.TOSV2ClientBuilder;
import com.volcengine.tos.TosClientException;
import com.volcengine.tos.TosServerException;
import com.volcengine.tos.comm.HttpMethod;
import com.volcengine.tos.model.bucket.*;
import com.volcengine.tos.model.object.*;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 火山引擎 TOS (Tinder Object Storage) 服务实现
 * <p>
 * 使用 Volcengine TOS 原生 SDK (ve-tos-java-sdk 2.8.8)
 * <p>
 * 支持功能：
 * - 基础文件操作（上传、下载、删除、存在判断）
 * - 预签名 URL（上传、下载）
 * - 分片上传（大文件）
 * - CORS 配置
 *
 * @author Actionow
 */
@Slf4j
public class TosOssService implements OssService {

    private final TOSV2 tosClient;
    private final OssProperties ossProperties;
    private final OssProperties.TosConfig tosConfig;
    private final String bucketName;
    private final String endpoint;

    public TosOssService(OssProperties ossProperties) {
        this.ossProperties = ossProperties;
        this.tosConfig = ossProperties.getTos();
        this.bucketName = tosConfig.getBucket();
        this.endpoint = tosConfig.getEndpoint();

        if (!StringUtils.hasText(endpoint)) {
            throw new IllegalStateException("Volcengine TOS Endpoint 未配置");
        }

        // 构建 TOS 客户端
        this.tosClient = new TOSV2ClientBuilder().build(
                tosConfig.getRegion(),
                endpoint,
                tosConfig.getAccessKeyId(),
                tosConfig.getSecretAccessKey()
        );

        log.info("Volcengine TOS 客户端初始化完成: endpoint={}, bucket={}, region={}",
                endpoint, bucketName, tosConfig.getRegion());

        // 确保存储桶存在并配置 CORS
        ensureBucketExists(bucketName);
    }

    @PreDestroy
    public void destroy() {
        if (tosClient != null) {
            try {
                tosClient.close();
            } catch (IOException e) {
                log.warn("关闭 TOS 客户端失败", e);
            }
        }
        log.info("Volcengine TOS 客户端已关闭");
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
            ObjectMetaRequestOptions options = new ObjectMetaRequestOptions()
                    .setContentType(contentType);

            PutObjectInput input = new PutObjectInput()
                    .setBucket(bucketName)
                    .setKey(objectName)
                    .setContentLength(size)
                    .setContent(inputStream)
                    .setOptions(options);

            tosClient.putObject(input);

            log.info("文件上传成功: bucket={}, object={}, size={}", bucketName, objectName, size);
            return getUrl(bucketName, objectName);
        } catch (TosClientException | TosServerException e) {
            log.error("文件上传失败: object={}", objectName, e);
            throw new ServiceException(ResultCode.UPLOAD_FAILED, e);
        }
    }

    private String uploadBytes(String bucket, String objectName, byte[] bytes, String contentType) {
        try {
            ObjectMetaRequestOptions options = new ObjectMetaRequestOptions()
                    .setContentType(contentType);

            PutObjectInput input = new PutObjectInput()
                    .setBucket(bucket)
                    .setKey(objectName)
                    .setContentLength(bytes.length)
                    .setContent(new ByteArrayInputStream(bytes))
                    .setOptions(options);

            tosClient.putObject(input);

            log.info("文件上传成功: bucket={}, object={}, size={}", bucket, objectName, bytes.length);
            return getUrl(bucket, objectName);
        } catch (TosClientException | TosServerException e) {
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
            GetObjectV2Input input = new GetObjectV2Input()
                    .setBucket(bucket)
                    .setKey(objectName);

            GetObjectV2Output output = tosClient.getObject(input);
            return output.getContent();
        } catch (TosServerException e) {
            if (e.getStatusCode() == 404) {
                log.warn("文件不存在: bucket={}, object={}", bucket, objectName);
                throw new ServiceException(ResultCode.ASSET_NOT_FOUND, "文件不存在");
            }
            log.error("文件下载失败: bucket={}, object={}", bucket, objectName, e);
            throw new ServiceException(ResultCode.FAIL.getCode(), "文件下载失败", e);
        } catch (TosClientException e) {
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
            DeleteObjectInput input = new DeleteObjectInput()
                    .setBucket(bucket)
                    .setKey(objectName);

            tosClient.deleteObject(input);
            log.info("文件删除成功: bucket={}, object={}", bucket, objectName);
        } catch (TosClientException | TosServerException e) {
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
            CopyObjectV2Input input = new CopyObjectV2Input()
                    .setSrcBucket(sourceBucket)
                    .setSrcKey(sourceObjectName)
                    .setBucket(targetBucket)
                    .setKey(targetObjectName);

            tosClient.copyObject(input);
            log.info("文件复制成功: {}:{} -> {}:{}", sourceBucket, sourceObjectName, targetBucket, targetObjectName);
        } catch (TosClientException | TosServerException e) {
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
            HeadObjectV2Input input = new HeadObjectV2Input()
                    .setBucket(bucket)
                    .setKey(objectName);

            tosClient.headObject(input);
            return true;
        } catch (TosServerException e) {
            if (e.getStatusCode() == 404) {
                return false;
            }
            log.warn("检查文件存在性失败: bucket={}, object={}", bucket, objectName, e);
            return false;
        } catch (TosClientException e) {
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
        // 优先使用 TOS 公开访问域名
        String publicDomain = tosConfig.getPublicDomain();
        if (StringUtils.hasText(publicDomain)) {
            String domain = publicDomain.endsWith("/")
                    ? publicDomain.substring(0, publicDomain.length() - 1)
                    : publicDomain;
            // 确保 URL 包含协议前缀
            if (!domain.startsWith("http://") && !domain.startsWith("https://")) {
                domain = "https://" + domain;
            }
            return String.format("%s/%s", domain, objectName);
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

        // 使用 TOS 标准 URL (virtual-hosted style)
        return String.format("https://%s.%s/%s", bucket, endpoint, objectName);
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
            PreSignedURLInput input = new PreSignedURLInput()
                    .setBucket(bucketName)
                    .setKey(objectName)
                    .setHttpMethod(HttpMethod.PUT)
                    .setExpires(expireSeconds);

            PreSignedURLOutput output = tosClient.preSignedURL(input);
            String url = output.getSignedUrl();

            log.debug("生成预签名上传URL: object={}, expireSeconds={}", objectName, expireSeconds);
            return url;
        } catch (TosClientException | TosServerException e) {
            log.error("获取预签名上传URL失败: object={}", objectName, e);
            throw new ServiceException(ResultCode.FAIL, "获取上传URL失败");
        }
    }

    @Override
    public String getPresignedDownloadUrl(String objectName, int expireSeconds) {
        try {
            PreSignedURLInput input = new PreSignedURLInput()
                    .setBucket(bucketName)
                    .setKey(objectName)
                    .setHttpMethod(HttpMethod.GET)
                    .setExpires(expireSeconds);

            PreSignedURLOutput output = tosClient.preSignedURL(input);
            String url = output.getSignedUrl();

            log.debug("生成预签名下载URL: object={}, expireSeconds={}", objectName, expireSeconds);
            return url;
        } catch (TosClientException | TosServerException e) {
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
                // 确保 CORS 配置正确
                configureBucketCors(bucket);
                return;
            }

            CreateBucketV2Input input = new CreateBucketV2Input()
                    .setBucket(bucket);

            tosClient.createBucket(input);
            log.info("存储桶创建成功: {}", bucket);

            // 配置 CORS 规则（支持浏览器直传）
            configureBucketCors(bucket);
        } catch (TosServerException e) {
            if (e.getStatusCode() == 409) {
                // Bucket already exists
                log.debug("存储桶已存在: {}", bucket);
                configureBucketCors(bucket);
            } else {
                log.error("存储桶创建失败: {}", bucket, e);
                throw new ServiceException(ResultCode.FAIL, "存储桶创建失败");
            }
        } catch (TosClientException e) {
            log.error("存储桶创建失败: {}", bucket, e);
            throw new ServiceException(ResultCode.FAIL, "存储桶创建失败");
        }
    }

    /**
     * 配置存储桶 CORS 规则
     * 允许浏览器直接上传文件到 TOS
     */
    private void configureBucketCors(String bucket) {
        try {
            CORSRule corsRule = new CORSRule()
                    .setAllowedOrigins(List.of("*"))
                    .setAllowedMethods(List.of("GET", "PUT", "POST", "DELETE", "HEAD"))
                    .setAllowedHeaders(List.of("*"))
                    .setExposeHeaders(List.of("ETag", "x-tos-request-id", "x-tos-version-id"))
                    .setMaxAgeSeconds(3600);

            PutBucketCORSInput input = new PutBucketCORSInput()
                    .setBucket(bucket)
                    .setRules(List.of(corsRule));

            tosClient.putBucketCORS(input);
            log.info("存储桶 CORS 配置成功: {}", bucket);
        } catch (TosServerException e) {
            log.warn("存储桶 CORS 配置失败: bucket={}, statusCode={}, code={}, message={}",
                    bucket, e.getStatusCode(), e.getCode(), e.getMessage());
            log.warn("请在火山引擎控制台手动配置 CORS: TOS -> {} -> 权限管理 -> 跨域访问设置", bucket);
        } catch (TosClientException e) {
            log.warn("存储桶 CORS 配置失败: {}, error={}", bucket, e.getMessage());
            log.warn("请在火山引擎控制台手动配置 CORS: TOS -> {} -> 权限管理 -> 跨域访问设置", bucket);
        }
    }

    @Override
    public boolean bucketExists(String bucket) {
        try {
            HeadBucketV2Input input = new HeadBucketV2Input()
                    .setBucket(bucket);

            tosClient.headBucket(input);
            return true;
        } catch (TosServerException e) {
            if (e.getStatusCode() == 404) {
                return false;
            } else if (e.getStatusCode() == 403) {
                // 403 表示桶存在但没有 HeadBucket 权限
                log.debug("存储桶存在但无 HeadBucket 权限: {}", bucket);
                return true;
            }
            log.warn("检查存储桶失败: {}, statusCode={}", bucket, e.getStatusCode());
            return false;
        } catch (TosClientException e) {
            log.warn("检查存储桶失败: {}", bucket, e);
            return false;
        }
    }

    // ==================== 分片上传 ====================

    @Override
    public String initMultipartUpload(String objectName, String contentType) {
        try {
            CreateMultipartUploadInput input = new CreateMultipartUploadInput()
                    .setBucket(bucketName)
                    .setKey(objectName);

            CreateMultipartUploadOutput output = tosClient.createMultipartUpload(input);
            String uploadId = output.getUploadID();

            log.info("分片上传初始化成功: object={}, uploadId={}", objectName, uploadId);
            return uploadId;
        } catch (TosClientException | TosServerException e) {
            log.error("分片上传初始化失败: object={}", objectName, e);
            throw new ServiceException(ResultCode.FAIL, "分片上传初始化失败");
        }
    }

    @Override
    public String getPresignedPartUploadUrl(String objectName, String uploadId, int partNumber, int expireSeconds) {
        try {
            PreSignedURLInput input = new PreSignedURLInput()
                    .setBucket(bucketName)
                    .setKey(objectName)
                    .setHttpMethod(HttpMethod.PUT)
                    .setExpires(expireSeconds)
                    .setQuery(Map.of(
                            "uploadId", uploadId,
                            "partNumber", String.valueOf(partNumber)
                    ));

            PreSignedURLOutput output = tosClient.preSignedURL(input);
            String url = output.getSignedUrl();

            log.debug("生成分片上传URL: object={}, partNumber={}", objectName, partNumber);
            return url;
        } catch (TosClientException | TosServerException e) {
            log.error("获取分片上传URL失败: object={}, partNumber={}", objectName, partNumber, e);
            throw new ServiceException(ResultCode.FAIL, "获取分片上传URL失败");
        }
    }

    @Override
    public String completeMultipartUpload(String objectName, String uploadId, List<Map<String, Object>> parts) {
        try {
            List<UploadedPartV2> uploadedParts = parts.stream()
                    .map(part -> new UploadedPartV2()
                            .setPartNumber(((Number) part.get("partNumber")).intValue())
                            .setEtag((String) part.get("etag")))
                    .sorted(Comparator.comparingInt(UploadedPartV2::getPartNumber))
                    .toList();

            CompleteMultipartUploadV2Input input = new CompleteMultipartUploadV2Input()
                    .setBucket(bucketName)
                    .setKey(objectName)
                    .setUploadID(uploadId)
                    .setUploadedParts(new ArrayList<>(uploadedParts));

            tosClient.completeMultipartUpload(input);

            log.info("分片上传完成: object={}, uploadId={}, parts={}", objectName, uploadId, parts.size());
            return getUrl(bucketName, objectName);
        } catch (TosClientException | TosServerException e) {
            log.error("完成分片上传失败: object={}, uploadId={}", objectName, uploadId, e);
            throw new ServiceException(ResultCode.FAIL, "完成分片上传失败");
        }
    }

    @Override
    public void abortMultipartUpload(String objectName, String uploadId) {
        try {
            AbortMultipartUploadInput input = new AbortMultipartUploadInput()
                    .setBucket(bucketName)
                    .setKey(objectName)
                    .setUploadID(uploadId);

            tosClient.abortMultipartUpload(input);
            log.info("分片上传已取消: object={}, uploadId={}", objectName, uploadId);
        } catch (TosClientException | TosServerException e) {
            log.error("取消分片上传失败: object={}, uploadId={}", objectName, uploadId, e);
            throw new ServiceException(ResultCode.FAIL, "取消分片上传失败");
        }
    }

    // ==================== 服务端分片流式上传 ====================

    private static final long MULTIPART_PART_SIZE = 10 * 1024 * 1024; // 10MB per part

    @Override
    public String uploadMultipartFromStream(String objectName, InputStream inputStream,
            long totalSize, String contentType) {
        String uploadId = null;
        try {
            // 1. 初始化分片上传
            CreateMultipartUploadInput initInput = new CreateMultipartUploadInput()
                    .setBucket(bucketName).setKey(objectName);
            CreateMultipartUploadOutput initOutput = tosClient.createMultipartUpload(initInput);
            uploadId = initOutput.getUploadID();

            log.info("开始分片流式上传: object={}, uploadId={}, totalSize={}", objectName, uploadId, totalSize);

            // 2. 按10MB分块读取并上传
            List<UploadedPartV2> completedParts = new ArrayList<>();
            byte[] buffer = new byte[(int) MULTIPART_PART_SIZE];
            int partNumber = 1;
            long totalUploaded = 0;

            while (true) {
                int bytesRead = readFully(inputStream, buffer);
                if (bytesRead <= 0) break;

                UploadPartV2Input partInput = new UploadPartV2Input()
                        .setBucket(bucketName).setKey(objectName)
                        .setUploadID(uploadId).setPartNumber(partNumber)
                        .setContentLength(bytesRead)
                        .setContent(new ByteArrayInputStream(buffer, 0, bytesRead));

                UploadPartV2Output partOutput = tosClient.uploadPart(partInput);
                completedParts.add(new UploadedPartV2()
                        .setPartNumber(partNumber).setEtag(partOutput.getEtag()));

                totalUploaded += bytesRead;
                log.debug("分片上传 part {}: size={}, total={}/{}", partNumber, bytesRead, totalUploaded, totalSize);
                partNumber++;
            }

            // 3. 完成分片上传
            completedParts.sort(Comparator.comparingInt(UploadedPartV2::getPartNumber));
            CompleteMultipartUploadV2Input completeInput = new CompleteMultipartUploadV2Input()
                    .setBucket(bucketName).setKey(objectName).setUploadID(uploadId)
                    .setUploadedParts(new ArrayList<>(completedParts));
            tosClient.completeMultipartUpload(completeInput);

            log.info("分片流式上传完成: object={}, parts={}, totalSize={}", objectName, completedParts.size(), totalUploaded);
            return getUrl(bucketName, objectName);

        } catch (Exception e) {
            // 失败时清理未完成的分片上传
            if (uploadId != null) {
                try {
                    AbortMultipartUploadInput abortInput = new AbortMultipartUploadInput()
                            .setBucket(bucketName).setKey(objectName).setUploadID(uploadId);
                    tosClient.abortMultipartUpload(abortInput);
                    log.info("已取消失败的分片上传: object={}, uploadId={}", objectName, uploadId);
                } catch (Exception abortEx) {
                    log.warn("取消分片上传失败: {}", abortEx.getMessage());
                }
            }
            log.error("分片流式上传失败: object={}", objectName, e);
            throw new ServiceException(ResultCode.UPLOAD_FAILED, e);
        }
    }

    /** 从流中读满 buffer 或直到 EOF */
    private int readFully(InputStream is, byte[] buffer) throws IOException {
        int totalRead = 0;
        while (totalRead < buffer.length) {
            int read = is.read(buffer, totalRead, buffer.length - totalRead);
            if (read < 0) break;
            totalRead += read;
        }
        return totalRead;
    }

    // ==================== 私有方法 ====================

    private void ensureBucketExists(String bucket) {
        if (!bucketExists(bucket)) {
            createBucket(bucket);
        } else {
            // 桶已存在，确保 CORS 配置正确
            configureBucketCors(bucket);
        }
    }
}
