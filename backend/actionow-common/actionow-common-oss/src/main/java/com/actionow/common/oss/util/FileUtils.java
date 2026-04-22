package com.actionow.common.oss.util;

import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.core.util.TimeUtils;

import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * 文件工具类
 *
 * @author Actionow
 */
public final class FileUtils {

    private FileUtils() {
    }

    /**
     * 允许的图片类型
     */
    private static final Set<String> IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/svg+xml"
    );

    /**
     * 允许的视频类型
     */
    private static final Set<String> VIDEO_TYPES = Set.of(
            "video/mp4", "video/webm", "video/ogg"
    );

    /**
     * 允许的音频类型
     */
    private static final Set<String> AUDIO_TYPES = Set.of(
            "audio/mpeg", "audio/wav", "audio/ogg", "audio/mp3"
    );

    /**
     * 生成存储路径
     * 格式: {type}/{date}/{uuid}.{ext}
     */
    public static String generateObjectName(String type, String originalFilename) {
        String ext = getFileExtension(originalFilename);
        String date = TimeUtils.today().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String filename = UuidGenerator.generateShortId();
        return String.format("%s/%s/%s.%s", type, date, filename, ext);
    }

    /**
     * 获取文件扩展名
     */
    public static String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase();
    }

    /**
     * 获取文件名（不含扩展名）
     */
    public static String getFilenameWithoutExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1) {
            return filename;
        }
        return filename.substring(0, dotIndex);
    }

    /**
     * 验证文件类型
     */
    public static void validateContentType(String contentType, Set<String> allowedTypes) {
        if (contentType == null || !allowedTypes.contains(contentType.toLowerCase())) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        }
    }

    /**
     * 验证文件大小
     */
    public static void validateFileSize(long size, long maxSizeMb) {
        long maxBytes = maxSizeMb * 1024 * 1024;
        if (size > maxBytes) {
            throw new BusinessException(ResultCode.FILE_SIZE_EXCEEDED,
                    String.format("文件大小超出限制，最大允许 %d MB", maxSizeMb));
        }
    }

    /**
     * 判断是否为图片类型
     */
    public static boolean isImage(String contentType) {
        return contentType != null && IMAGE_TYPES.contains(contentType.toLowerCase());
    }

    /**
     * 判断是否为视频类型
     */
    public static boolean isVideo(String contentType) {
        return contentType != null && VIDEO_TYPES.contains(contentType.toLowerCase());
    }

    /**
     * 判断是否为音频类型
     */
    public static boolean isAudio(String contentType) {
        return contentType != null && AUDIO_TYPES.contains(contentType.toLowerCase());
    }

    /**
     * 获取文件类型分类
     */
    public static String getFileCategory(String contentType) {
        if (isImage(contentType)) {
            return "image";
        }
        if (isVideo(contentType)) {
            return "video";
        }
        if (isAudio(contentType)) {
            return "audio";
        }
        return "file";
    }

    /**
     * 格式化文件大小
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        }
        if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        }
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
