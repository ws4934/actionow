package com.actionow.common.oss.service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * OSS 服务接口
 *
 * @author Actionow
 */
public interface OssService {

    /**
     * 上传文件
     *
     * @param objectName  对象名称（路径）
     * @param inputStream 文件流
     * @param contentType 文件类型
     * @return 文件访问URL
     */
    String upload(String objectName, InputStream inputStream, String contentType);

    /**
     * 上传文件
     *
     * @param bucket      存储桶
     * @param objectName  对象名称
     * @param inputStream 文件流
     * @param contentType 文件类型
     * @return 文件访问URL
     */
    String upload(String bucket, String objectName, InputStream inputStream, String contentType);

    /**
     * 上传文件（带大小）
     *
     * @param objectName  对象名称（路径）
     * @param inputStream 文件流
     * @param size        文件大小
     * @param contentType 文件类型
     * @return 文件访问URL
     */
    String upload(String objectName, InputStream inputStream, long size, String contentType);

    /**
     * 服务端分片上传（流式，适用于大文件）
     * 从 InputStream 读取数据，分片上传到 OSS
     *
     * @param objectName  对象名称
     * @param inputStream 数据流
     * @param totalSize   文件总大小
     * @param contentType 文件类型
     * @return 文件访问URL
     */
    default String uploadMultipartFromStream(String objectName, InputStream inputStream,
            long totalSize, String contentType) {
        // 默认回退到单次上传
        return upload(objectName, inputStream, totalSize, contentType);
    }

    /**
     * 下载文件
     *
     * @param objectName 对象名称
     * @return 文件流
     */
    InputStream download(String objectName);

    /**
     * 下载文件
     *
     * @param bucket     存储桶
     * @param objectName 对象名称
     * @return 文件流
     */
    InputStream download(String bucket, String objectName);

    /**
     * 删除文件
     *
     * @param objectName 对象名称
     */
    void delete(String objectName);

    /**
     * 删除文件
     *
     * @param bucket     存储桶
     * @param objectName 对象名称
     */
    void delete(String bucket, String objectName);

    /**
     * 复制文件
     *
     * @param sourceObjectName 源对象名称
     * @param targetObjectName 目标对象名称
     */
    void copy(String sourceObjectName, String targetObjectName);

    /**
     * 复制文件
     *
     * @param sourceBucket     源存储桶
     * @param sourceObjectName 源对象名称
     * @param targetBucket     目标存储桶
     * @param targetObjectName 目标对象名称
     */
    void copy(String sourceBucket, String sourceObjectName, String targetBucket, String targetObjectName);

    /**
     * 移动文件（复制后删除源文件）
     *
     * @param sourceObjectName 源对象名称
     * @param targetObjectName 目标对象名称
     */
    default void move(String sourceObjectName, String targetObjectName) {
        copy(sourceObjectName, targetObjectName);
        delete(sourceObjectName);
    }

    /**
     * 判断文件是否存在
     *
     * @param objectName 对象名称
     * @return 是否存在
     */
    boolean exists(String objectName);

    /**
     * 判断文件是否存在
     *
     * @param bucket     存储桶
     * @param objectName 对象名称
     * @return 是否存在
     */
    boolean exists(String bucket, String objectName);

    /**
     * 获取文件访问URL
     *
     * @param objectName 对象名称
     * @return 文件URL
     */
    String getUrl(String objectName);

    /**
     * 获取文件访问URL
     *
     * @param bucket     存储桶
     * @param objectName 对象名称
     * @return 文件URL
     */
    String getUrl(String bucket, String objectName);

    /**
     * 获取公开访问URL（用于通过Gateway代理访问）
     * 返回不带签名的公开路径，由Gateway进行token验证
     *
     * @param objectName 对象名称
     * @return 公开访问路径（不含域名，用于proxy）
     */
    String getPublicUrl(String objectName);

    /**
     * 获取公开访问URL（用于通过Gateway代理访问）
     *
     * @param bucket     存储桶
     * @param objectName 对象名称
     * @return 公开访问路径
     */
    String getPublicUrl(String bucket, String objectName);

    /**
     * 获取预签名上传URL
     *
     * @param objectName 对象名称
     * @param expireSeconds 过期时间（秒）
     * @return 预签名URL
     */
    String getPresignedUploadUrl(String objectName, int expireSeconds);

    /**
     * 获取预签名下载URL
     *
     * @param objectName 对象名称
     * @param expireSeconds 过期时间（秒）
     * @return 预签名URL
     */
    String getPresignedDownloadUrl(String objectName, int expireSeconds);

    /**
     * 创建存储桶
     *
     * @param bucket 存储桶名称
     */
    void createBucket(String bucket);

    /**
     * 判断存储桶是否存在
     *
     * @param bucket 存储桶名称
     * @return 是否存在
     */
    boolean bucketExists(String bucket);

    // ==================== 分片上传 ====================

    /**
     * 初始化分片上传
     *
     * @param objectName  对象名称
     * @param contentType 文件类型
     * @return uploadId
     */
    String initMultipartUpload(String objectName, String contentType);

    /**
     * 获取分片上传的预签名URL
     *
     * @param objectName    对象名称
     * @param uploadId      上传ID
     * @param partNumber    分片编号（从1开始）
     * @param expireSeconds 过期时间（秒）
     * @return 预签名URL
     */
    String getPresignedPartUploadUrl(String objectName, String uploadId, int partNumber, int expireSeconds);

    /**
     * 完成分片上传
     *
     * @param objectName 对象名称
     * @param uploadId   上传ID
     * @param parts      分片信息列表（partNumber -> etag）
     * @return 文件访问URL
     */
    String completeMultipartUpload(String objectName, String uploadId, List<Map<String, Object>> parts);

    /**
     * 取消分片上传
     *
     * @param objectName 对象名称
     * @param uploadId   上传ID
     */
    void abortMultipartUpload(String objectName, String uploadId);
}
