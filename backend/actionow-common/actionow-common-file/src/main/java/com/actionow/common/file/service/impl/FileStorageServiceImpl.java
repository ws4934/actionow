package com.actionow.common.file.service.impl;

import com.actionow.common.file.constant.FileConstants;
import com.actionow.common.file.dto.*;
import com.actionow.common.file.event.FileUploadedEvent;
import com.actionow.common.file.exception.FileErrorCode;
import com.actionow.common.file.exception.FileException;
import com.actionow.common.file.service.FileEventPublisher;
import com.actionow.common.file.service.FileStorageService;
import com.actionow.common.file.service.ThumbnailService;
import com.actionow.common.file.util.StoragePathBuilder;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.oss.service.OssService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文件存储服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
public class FileStorageServiceImpl implements FileStorageService {

    private final OssService ossService;
    private final ThumbnailService thumbnailService;
    private final FileEventPublisher eventPublisher;

    @Autowired
    public FileStorageServiceImpl(OssService ossService,
                                   ThumbnailService thumbnailService,
                                   @Autowired(required = false) FileEventPublisher eventPublisher) {
        this.ossService = ossService;
        this.thumbnailService = thumbnailService;
        this.eventPublisher = eventPublisher;
    }

    // ==================== 直接上传 ====================

    @Override
    public FileUploadResult upload(MultipartFile file, String workspaceId, String fileType, String customPath) {
        String mimeType = file.getContentType();
        validateFile(mimeType, file.getSize());

        String resolvedFileType = StringUtils.hasText(fileType) ? fileType : getFileType(mimeType);
        String fileKey = generateFileKey(workspaceId, resolvedFileType, file.getOriginalFilename(), customPath);

        String fileUrl;
        try {
            fileUrl = ossService.upload(fileKey, file.getInputStream(), file.getSize(), mimeType);
        } catch (IOException e) {
            log.error("文件上传失败: {}", e.getMessage());
            throw new FileException(FileErrorCode.UPLOAD_FAILED, e);
        }

        String thumbnailUrl = null;
        if (thumbnailService.supportsThumbnail(mimeType)) {
            thumbnailUrl = generateThumbnailFromFile(file, fileKey, mimeType);
        }

        log.info("文件上传成功: fileKey={}, workspaceId={}", fileKey, workspaceId);

        return FileUploadResult.builder()
                .fileKey(fileKey)
                .fileUrl(fileUrl)
                .thumbnailUrl(thumbnailUrl)
                .fileSize(file.getSize())
                .mimeType(mimeType)
                .build();
    }

    @Override
    public FileUploadResult upload(InputStream inputStream, String fileName, String mimeType, long fileSize,
                                   String workspaceId, String fileType, String customPath) {
        validateFile(mimeType, fileSize);

        String resolvedFileType = StringUtils.hasText(fileType) ? fileType : getFileType(mimeType);
        String fileKey = generateFileKey(workspaceId, resolvedFileType, fileName, customPath);

        String fileUrl = ossService.upload(fileKey, inputStream, fileSize, mimeType);

        log.info("文件上传成功: fileKey={}, workspaceId={}", fileKey, workspaceId);

        return FileUploadResult.builder()
                .fileKey(fileKey)
                .fileUrl(fileUrl)
                .fileSize(fileSize)
                .mimeType(mimeType)
                .build();
    }

    // ==================== 预签名上传 ====================

    @Override
    public PresignedUploadResult getPresignedUploadUrl(PresignedUploadRequest request) {
        validateFile(request.getMimeType(), request.getFileSize());

        String resolvedFileType = StringUtils.hasText(request.getFileType())
                ? request.getFileType() : getFileType(request.getMimeType());
        String fileKey = generateFileKey(request.getWorkspaceId(), resolvedFileType,
                request.getFileName(), request.getCustomPath());

        String uploadUrl = ossService.getPresignedUploadUrl(fileKey, FileConstants.PRESIGNED_URL_EXPIRE_SECONDS);
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(FileConstants.PRESIGNED_URL_EXPIRE_SECONDS);

        log.info("预签名上传URL生成成功: fileKey={}, workspaceId={}", fileKey, request.getWorkspaceId());

        return PresignedUploadResult.builder()
                .uploadUrl(uploadUrl)
                .method("PUT")
                .headers(Map.of("Content-Type", request.getMimeType()))
                .fileKey(fileKey)
                .expiresAt(expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();
    }

    @Override
    public boolean confirmUpload(String fileKey) {
        return ossService.exists(fileKey);
    }

    @Override
    public String getFileUrl(String fileKey) {
        return ossService.getUrl(fileKey);
    }

    // ==================== 分片上传 ====================

    @Override
    public MultipartInitResult initMultipartUpload(MultipartInitRequest request) {
        validateFile(request.getMimeType(), request.getFileSize());

        String resolvedFileType = StringUtils.hasText(request.getFileType())
                ? request.getFileType() : getFileType(request.getMimeType());
        String fileKey = generateFileKey(request.getWorkspaceId(), resolvedFileType,
                request.getFileName(), request.getCustomPath());

        String uploadId = ossService.initMultipartUpload(fileKey, request.getMimeType());

        List<MultipartInitResult.PartUploadUrl> partUrls = new ArrayList<>();
        for (int i = 1; i <= request.getPartCount(); i++) {
            String partUrl = ossService.getPresignedPartUploadUrl(fileKey, uploadId, i,
                    FileConstants.PRESIGNED_URL_EXPIRE_SECONDS);
            partUrls.add(MultipartInitResult.PartUploadUrl.builder()
                    .partNumber(i)
                    .uploadUrl(partUrl)
                    .build());
        }

        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(FileConstants.PRESIGNED_URL_EXPIRE_SECONDS);

        log.info("分片上传初始化成功: uploadId={}, fileKey={}, partCount={}",
                uploadId, fileKey, request.getPartCount());

        return MultipartInitResult.builder()
                .uploadId(uploadId)
                .fileKey(fileKey)
                .partUrls(partUrls)
                .expiresAt(expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();
    }

    @Override
    public FileUploadResult completeMultipartUpload(MultipartCompleteRequest request) {
        List<Map<String, Object>> parts = request.getParts().stream()
                .map(part -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("partNumber", part.getPartNumber());
                    map.put("etag", part.getEtag());
                    return map;
                })
                .collect(Collectors.toList());

        String fileUrl = ossService.completeMultipartUpload(request.getFileKey(), request.getUploadId(), parts);

        log.info("分片上传完成: fileKey={}", request.getFileKey());

        return FileUploadResult.builder()
                .fileKey(request.getFileKey())
                .fileUrl(fileUrl)
                .build();
    }

    @Override
    public void abortMultipartUpload(String uploadId, String fileKey) {
        ossService.abortMultipartUpload(fileKey, uploadId);
        log.info("分片上传已取消: uploadId={}, fileKey={}", uploadId, fileKey);
    }

    // ==================== 文件转存 ====================

    @Override
    public FileUploadResult transferFromUrl(FileTransferRequest request) {
        byte[] fileData;
        String mimeType = request.getMimeType();

        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(request.getSourceUrl()))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                log.error("下载文件失败: url={}, statusCode={}", request.getSourceUrl(), response.statusCode());
                throw new FileException(FileErrorCode.DOWNLOAD_EXTERNAL_FILE_FAILED);
            }

            fileData = response.body();

            if (!StringUtils.hasText(mimeType)) {
                mimeType = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
            }
        } catch (FileException e) {
            throw e;
        } catch (Exception e) {
            log.error("下载外部文件失败: url={}, error={}", request.getSourceUrl(), e.getMessage(), e);
            throw new FileException(FileErrorCode.DOWNLOAD_EXTERNAL_FILE_FAILED, e);
        }

        String resolvedFileType = StringUtils.hasText(request.getFileType())
                ? request.getFileType() : getFileType(mimeType);
        String extension = getExtensionFromMimeType(mimeType);

        // 使用新的路径生成方式
        String fileKey;
        if (StringUtils.hasText(request.getCustomPath())) {
            // 自定义路径（向后兼容）
            fileKey = String.format("%s/%s/%s/%s%s",
                    request.getCustomPath(),
                    request.getWorkspaceId(),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")),
                    UuidGenerator.generateUuidV7(),
                    extension);
        } else {
            // 使用新的路径构建器
            fileKey = StoragePathBuilder.tenant(request.getWorkspaceId())
                    .mediaType(resolvedFileType)
                    .transferred()
                    .withDatePath()
                    .extension(extension.isEmpty() ? "bin" : extension.substring(1))
                    .build();
        }

        String fileUrl;
        try (InputStream inputStream = new ByteArrayInputStream(fileData)) {
            fileUrl = ossService.upload(fileKey, inputStream, fileData.length, mimeType);
        } catch (IOException e) {
            log.error("文件转存到OSS失败: error={}", e.getMessage(), e);
            throw new FileException(FileErrorCode.UPLOAD_FAILED, e);
        }

        String thumbnailUrl = null;
        // 异步生成缩略图
        if (Boolean.TRUE.equals(request.getAsyncThumbnail()) && thumbnailService.supportsThumbnail(mimeType)) {
            generateThumbnailAsync(fileKey, mimeType, request.getWorkspaceId(), null, null);
        }
        // 同步生成缩略图
        else if (Boolean.TRUE.equals(request.getGenerateThumbnail()) && thumbnailService.supportsThumbnail(mimeType)) {
            try (InputStream thumbInputStream = new ByteArrayInputStream(fileData)) {
                byte[] thumbnailData = thumbnailService.generateImageThumbnail(thumbInputStream, mimeType);
                if (thumbnailData != null && thumbnailData.length > 0) {
                    String thumbnailKey = generateThumbnailKey(fileKey);
                    try (InputStream thumbnailInputStream = new ByteArrayInputStream(thumbnailData)) {
                        thumbnailUrl = ossService.upload(thumbnailKey, thumbnailInputStream,
                                thumbnailData.length, "image/jpeg");
                        log.info("缩略图生成并上传成功: fileKey={}, thumbnailUrl={}", fileKey, thumbnailUrl);
                    }
                }
            } catch (IOException e) {
                log.warn("生成缩略图失败，继续处理: fileKey={}, error={}", fileKey, e.getMessage());
            }
        }

        log.info("文件转存成功: sourceUrl={}, fileKey={}", request.getSourceUrl(), fileKey);

        return FileUploadResult.builder()
                .fileKey(fileKey)
                .fileUrl(fileUrl)
                .thumbnailUrl(thumbnailUrl)
                .fileSize((long) fileData.length)
                .mimeType(mimeType)
                .build();
    }

    // ==================== 下载相关 ====================

    @Override
    public String getPresignedDownloadUrl(String fileKey, int expireSeconds) {
        return ossService.getPresignedDownloadUrl(fileKey, expireSeconds);
    }

    @Override
    public InputStream download(String fileKey) {
        return ossService.download(fileKey);
    }

    // ==================== 删除相关 ====================

    @Override
    public void deleteFile(String fileKey) {
        try {
            ossService.delete(fileKey);
            log.info("文件删除成功: fileKey={}", fileKey);
        } catch (Exception e) {
            log.warn("删除文件失败: fileKey={}, error={}", fileKey, e.getMessage());
        }
    }

    /**
     * 将文件移动到回收站（软删除）
     * 文件会被移动到 tenant_{workspaceId}/trash/{date}/{originalPath}
     *
     * @param fileKey     原文件路径
     * @param workspaceId 工作空间ID
     * @return 回收站中的文件路径
     */
    @Override
    public String moveToTrash(String fileKey, String workspaceId) {
        if (!StringUtils.hasText(fileKey)) {
            return null;
        }

        try {
            String trashPath = StoragePathBuilder.trashPath(workspaceId, fileKey);
            ossService.copy(fileKey, trashPath);
            ossService.delete(fileKey);
            log.info("文件已移动到回收站: fileKey={}, trashPath={}", fileKey, trashPath);
            return trashPath;
        } catch (Exception e) {
            log.warn("移动文件到回收站失败: fileKey={}, error={}", fileKey, e.getMessage());
            return null;
        }
    }

    /**
     * 从回收站恢复文件
     *
     * @param trashPath   回收站中的文件路径
     * @param workspaceId 工作空间ID
     * @return 恢复后的原文件路径
     */
    @Override
    public String restoreFromTrash(String trashPath, String workspaceId) {
        if (!StringUtils.hasText(trashPath)) {
            return null;
        }

        try {
            String originalPath = StoragePathBuilder.restoreFromTrash(trashPath);
            ossService.copy(trashPath, originalPath);
            ossService.delete(trashPath);
            log.info("文件已从回收站恢复: trashPath={}, originalPath={}", trashPath, originalPath);
            return originalPath;
        } catch (Exception e) {
            log.warn("从回收站恢复文件失败: trashPath={}, error={}", trashPath, e.getMessage());
            return null;
        }
    }

    /**
     * 永久删除回收站中的文件
     *
     * @param trashPath 回收站中的文件路径
     */
    public void deleteFromTrash(String trashPath) {
        deleteFile(trashPath);
    }

    @Override
    public void deleteFiles(List<String> fileKeys) {
        for (String fileKey : fileKeys) {
            deleteFile(fileKey);
        }
    }

    // ==================== 缩略图 ====================

    @Override
    public String generateThumbnail(String fileKey, String mimeType) {
        if (!thumbnailService.supportsThumbnail(mimeType)) {
            return null;
        }

        try {
            try (InputStream originalStream = ossService.download(fileKey)) {
                byte[] thumbnailData;

                // 根据文件类型选择不同的缩略图生成方式
                if (thumbnailService.supportsVideoThumbnail(mimeType)) {
                    // 视频文件：使用 FFmpeg 提取帧
                    thumbnailData = thumbnailService.generateVideoThumbnail(originalStream, mimeType);
                } else {
                    // 图片文件：使用 Thumbnailator
                    thumbnailData = thumbnailService.generateImageThumbnail(originalStream, mimeType);
                }

                if (thumbnailData != null && thumbnailData.length > 0) {
                    String thumbnailKey = generateThumbnailKey(fileKey);
                    try (InputStream thumbnailInputStream = new ByteArrayInputStream(thumbnailData)) {
                        String thumbnailUrl = ossService.upload(thumbnailKey, thumbnailInputStream,
                                thumbnailData.length, "image/jpeg");
                        log.info("缩略图生成并上传成功: fileKey={}, thumbnailUrl={}", fileKey, thumbnailUrl);
                        return thumbnailUrl;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("生成缩略图失败: fileKey={}, error={}", fileKey, e.getMessage());
        }
        return null;
    }

    @Override
    public void generateThumbnailAsync(String fileKey, String mimeType, String workspaceId,
                                        String businessId, String businessType) {
        if (eventPublisher == null) {
            log.warn("事件发布者未配置，无法异步生成缩略图，fallback 到同步生成");
            generateThumbnail(fileKey, mimeType);
            return;
        }

        if (!thumbnailService.supportsThumbnail(mimeType)) {
            log.debug("不支持的缩略图类型: mimeType={}", mimeType);
            return;
        }

        FileUploadedEvent event = FileUploadedEvent.builder()
                .eventId(UuidGenerator.generateUuidV7())
                .fileKey(fileKey)
                .workspaceId(workspaceId)
                .mimeType(mimeType)
                .needThumbnail(true)
                .businessId(businessId)
                .businessType(businessType)
                .timestamp(LocalDateTime.now())
                .build();

        eventPublisher.publishFileUploaded(event);
        log.info("已发布异步缩略图生成事件: fileKey={}", fileKey);
    }

    // ==================== 辅助方法 ====================

    @Override
    public boolean exists(String fileKey) {
        return ossService.exists(fileKey);
    }

    @Override
    public boolean isAllowedMimeType(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        return Arrays.asList(FileConstants.ALLOWED_IMAGE_TYPES).contains(mimeType)
                || Arrays.asList(FileConstants.ALLOWED_VIDEO_TYPES).contains(mimeType)
                || Arrays.asList(FileConstants.ALLOWED_AUDIO_TYPES).contains(mimeType)
                || Arrays.asList(FileConstants.ALLOWED_DOCUMENT_TYPES).contains(mimeType)
                || Arrays.asList(FileConstants.ALLOWED_MODEL_TYPES).contains(mimeType);
    }

    @Override
    public boolean isAllowedFileSize(String mimeType, long fileSize) {
        String fileType = getFileType(mimeType);
        long maxSize = switch (fileType) {
            case FileConstants.FileType.IMAGE -> FileConstants.MaxFileSize.IMAGE;
            case FileConstants.FileType.VIDEO -> FileConstants.MaxFileSize.VIDEO;
            case FileConstants.FileType.AUDIO -> FileConstants.MaxFileSize.AUDIO;
            case FileConstants.FileType.MODEL -> FileConstants.MaxFileSize.MODEL;
            default -> FileConstants.MaxFileSize.DOCUMENT;
        };
        return fileSize <= maxSize;
    }

    @Override
    public String getFileType(String mimeType) {
        if (mimeType == null) {
            return FileConstants.FileType.OTHER;
        }
        if (mimeType.startsWith("image/")) {
            return FileConstants.FileType.IMAGE;
        }
        if (mimeType.startsWith("video/")) {
            return FileConstants.FileType.VIDEO;
        }
        if (mimeType.startsWith("audio/")) {
            return FileConstants.FileType.AUDIO;
        }
        if (mimeType.startsWith("model/") || mimeType.equals("application/octet-stream")) {
            return FileConstants.FileType.MODEL;
        }
        if (Arrays.asList(FileConstants.ALLOWED_DOCUMENT_TYPES).contains(mimeType)) {
            return FileConstants.FileType.DOCUMENT;
        }
        return FileConstants.FileType.OTHER;
    }

    @Override
    public String generateThumbnailKey(String fileKey) {
        // 使用新的路径构建器生成缩略图路径
        String thumbnailPath = StoragePathBuilder.thumbnailFor(fileKey);
        if (thumbnailPath != null) {
            return thumbnailPath;
        }

        // 回退到旧逻辑（向后兼容）
        int lastDotIndex = fileKey.lastIndexOf('.');
        String basePath = lastDotIndex > 0 ? fileKey.substring(0, lastDotIndex) : fileKey;

        for (String storagePath : new String[]{
                FileConstants.StoragePath.IMAGES,
                FileConstants.StoragePath.VIDEOS,
                FileConstants.StoragePath.AI_GENERATED,
                FileConstants.StoragePath.TRANSFERRED
        }) {
            if (basePath.startsWith(storagePath)) {
                basePath = basePath.replace(storagePath, FileConstants.StoragePath.THUMBNAILS);
                break;
            }
        }

        if (!basePath.startsWith(FileConstants.StoragePath.THUMBNAILS)) {
            basePath = FileConstants.StoragePath.THUMBNAILS + "/" + basePath;
        }

        return basePath + "_thumb.jpg";
    }

    // ==================== 私有方法 ====================

    private void validateFile(String mimeType, long fileSize) {
        if (!isAllowedMimeType(mimeType)) {
            throw new FileException(FileErrorCode.FILE_TYPE_NOT_ALLOWED, mimeType);
        }
        if (!isAllowedFileSize(mimeType, fileSize)) {
            throw new FileException(FileErrorCode.FILE_SIZE_EXCEEDED);
        }
    }

    private String generateFileKey(String workspaceId, String fileType, String originalName, String customPath) {
        // 如果提供了自定义路径，使用旧逻辑（向后兼容）
        if (StringUtils.hasText(customPath)) {
            String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String uuid = UuidGenerator.generateUuidV7();
            String extension = getFileExtension(originalName);
            return String.format("%s/%s/%s/%s%s", customPath, workspaceId, dateDir, uuid, extension);
        }

        // 使用新的路径构建器
        return StoragePathBuilder.tenant(workspaceId)
                .mediaType(fileType)
                .uploads()
                .withDatePath()
                .filename(originalName)
                .build();
    }

    /**
     * 生成AI生成文件的路径
     *
     * @param workspaceId 工作空间ID
     * @param fileType    文件类型
     * @param extension   文件扩展名
     * @return 文件路径
     */
    public String generateAiGeneratedFileKey(String workspaceId, String fileType, String extension) {
        return StoragePathBuilder.tenant(workspaceId)
                .mediaType(fileType)
                .aiGenerated()
                .withDatePath()
                .extension(extension)
                .build();
    }

    /**
     * 生成转存文件的路径
     *
     * @param workspaceId 工作空间ID
     * @param fileType    文件类型
     * @param extension   文件扩展名
     * @return 文件路径
     */
    public String generateTransferredFileKey(String workspaceId, String fileType, String extension) {
        return StoragePathBuilder.tenant(workspaceId)
                .mediaType(fileType)
                .transferred()
                .withDatePath()
                .extension(extension)
                .build();
    }

    private String getStoragePath(String fileType) {
        return switch (fileType) {
            case FileConstants.FileType.IMAGE -> FileConstants.StoragePath.IMAGES;
            case FileConstants.FileType.VIDEO -> FileConstants.StoragePath.VIDEOS;
            case FileConstants.FileType.AUDIO -> FileConstants.StoragePath.AUDIOS;
            case FileConstants.FileType.MODEL -> FileConstants.StoragePath.MODELS;
            default -> FileConstants.StoragePath.DOCUMENTS;
        };
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    private String getExtensionFromMimeType(String mimeType) {
        if (mimeType == null) {
            return "";
        }
        return switch (mimeType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            case "audio/mpeg" -> ".mp3";
            case "audio/wav" -> ".wav";
            case "application/pdf" -> ".pdf";
            default -> "";
        };
    }

    private String generateThumbnailFromFile(MultipartFile file, String fileKey, String mimeType) {
        try {
            byte[] thumbnailData = thumbnailService.generateImageThumbnail(file.getInputStream(), mimeType);
            if (thumbnailData != null && thumbnailData.length > 0) {
                String thumbnailKey = generateThumbnailKey(fileKey);
                try (InputStream thumbnailInputStream = new ByteArrayInputStream(thumbnailData)) {
                    return ossService.upload(thumbnailKey, thumbnailInputStream, thumbnailData.length, "image/jpeg");
                }
            }
        } catch (IOException e) {
            log.warn("生成缩略图失败: fileKey={}, error={}", fileKey, e.getMessage());
        }
        return null;
    }
}
