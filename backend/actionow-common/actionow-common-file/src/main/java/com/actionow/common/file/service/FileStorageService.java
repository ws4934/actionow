package com.actionow.common.file.service;

import com.actionow.common.file.dto.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * 文件存储服务接口
 * 高层文件操作服务，基于 OssService 提供业务级文件管理能力
 *
 * @author Actionow
 */
public interface FileStorageService {

    // ==================== 直接上传 ====================

    /**
     * 直接上传文件
     *
     * @param file        文件
     * @param workspaceId 工作空间 ID
     * @param fileType    文件类型分类（可选）
     * @param customPath  自定义路径前缀（可选）
     * @return 上传结果
     */
    FileUploadResult upload(MultipartFile file, String workspaceId, String fileType, String customPath);

    /**
     * 直接上传文件（使用输入流）
     *
     * @param inputStream 输入流
     * @param fileName    原始文件名
     * @param mimeType    MIME 类型
     * @param fileSize    文件大小
     * @param workspaceId 工作空间 ID
     * @param fileType    文件类型分类（可选）
     * @param customPath  自定义路径前缀（可选）
     * @return 上传结果
     */
    FileUploadResult upload(InputStream inputStream, String fileName, String mimeType, long fileSize,
                            String workspaceId, String fileType, String customPath);

    // ==================== 预签名上传 ====================

    /**
     * 获取预签名上传 URL（客户端直传）
     *
     * @param request 请求参数
     * @return 预签名上传信息
     */
    PresignedUploadResult getPresignedUploadUrl(PresignedUploadRequest request);

    /**
     * 确认预签名上传完成
     *
     * @param fileKey 文件 Key
     * @return 是否存在
     */
    boolean confirmUpload(String fileKey);

    /**
     * 获取已上传文件的 URL
     *
     * @param fileKey 文件 Key
     * @return 文件 URL
     */
    String getFileUrl(String fileKey);

    // ==================== 分片上传 ====================

    /**
     * 初始化分片上传
     *
     * @param request 请求参数
     * @return 分片上传初始化结果
     */
    MultipartInitResult initMultipartUpload(MultipartInitRequest request);

    /**
     * 完成分片上传
     *
     * @param request 请求参数
     * @return 上传结果
     */
    FileUploadResult completeMultipartUpload(MultipartCompleteRequest request);

    /**
     * 取消分片上传
     *
     * @param uploadId OSS 分片上传 ID
     * @param fileKey  文件 Key
     */
    void abortMultipartUpload(String uploadId, String fileKey);

    // ==================== 文件转存 ====================

    /**
     * 从 URL 转存文件到本地 OSS
     *
     * @param request 转存请求
     * @return 上传结果
     */
    FileUploadResult transferFromUrl(FileTransferRequest request);

    // ==================== 下载相关 ====================

    /**
     * 获取预签名下载 URL
     *
     * @param fileKey       文件 Key
     * @param expireSeconds 有效期（秒）
     * @return 下载 URL
     */
    String getPresignedDownloadUrl(String fileKey, int expireSeconds);

    /**
     * 下载文件
     *
     * @param fileKey 文件 Key
     * @return 输入流
     */
    InputStream download(String fileKey);

    // ==================== 删除相关 ====================

    /**
     * 删除文件
     *
     * @param fileKey 文件 Key
     */
    void deleteFile(String fileKey);

    /**
     * 批量删除文件
     *
     * @param fileKeys 文件 Key 列表
     */
    void deleteFiles(List<String> fileKeys);

    /**
     * 将文件移动到回收站（软删除）
     * 文件会被移动到 tenant_{workspaceId}/trash/{date}/{originalPath}
     *
     * @param fileKey     原文件路径
     * @param workspaceId 工作空间ID
     * @return 回收站中的文件路径，失败返回 null
     */
    String moveToTrash(String fileKey, String workspaceId);

    /**
     * 从回收站恢复文件
     *
     * @param trashPath   回收站中的文件路径
     * @param workspaceId 工作空间ID
     * @return 恢复后的原文件路径，失败返回 null
     */
    String restoreFromTrash(String trashPath, String workspaceId);

    // ==================== 缩略图 ====================

    /**
     * 生成并上传缩略图（同步）
     *
     * @param fileKey  原文件 Key
     * @param mimeType MIME 类型
     * @return 缩略图 URL，如果不支持生成缩略图则返回 null
     */
    String generateThumbnail(String fileKey, String mimeType);

    /**
     * 异步生成缩略图（通过事件）
     *
     * @param fileKey      原文件 Key
     * @param mimeType     MIME 类型
     * @param workspaceId  工作空间 ID
     * @param businessId   业务 ID
     * @param businessType 业务类型
     */
    void generateThumbnailAsync(String fileKey, String mimeType, String workspaceId,
                                String businessId, String businessType);

    // ==================== 辅助方法 ====================

    /**
     * 检查文件是否存在
     *
     * @param fileKey 文件 Key
     * @return 是否存在
     */
    boolean exists(String fileKey);

    /**
     * 验证文件类型是否允许
     *
     * @param mimeType MIME 类型
     * @return 是否允许
     */
    boolean isAllowedMimeType(String mimeType);

    /**
     * 验证文件大小是否允许
     *
     * @param mimeType MIME 类型
     * @param fileSize 文件大小（字节）
     * @return 是否允许
     */
    boolean isAllowedFileSize(String mimeType, long fileSize);

    /**
     * 根据 MIME 类型获取文件类型分类
     *
     * @param mimeType MIME 类型
     * @return 文件类型分类
     */
    String getFileType(String mimeType);

    /**
     * 生成缩略图 Key
     *
     * @param fileKey 原文件 Key
     * @return 缩略图 Key
     */
    String generateThumbnailKey(String fileKey);
}
