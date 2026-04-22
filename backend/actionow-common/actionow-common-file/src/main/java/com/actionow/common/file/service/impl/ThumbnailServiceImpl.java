package com.actionow.common.file.service.impl;

import com.actionow.common.file.service.ThumbnailService;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 缩略图服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
public class ThumbnailServiceImpl implements ThumbnailService {

    /**
     * 缩略图质量（0.0-1.0）
     */
    private static final double THUMBNAIL_QUALITY = 0.8;

    /**
     * FFmpeg 超时时间（秒）
     */
    private static final int FFMPEG_TIMEOUT_SECONDS = 30;

    /**
     * 支持生成缩略图的MIME类型
     */
    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/bmp"
    );

    /**
     * 支持生成缩略图的视频MIME类型
     */
    private static final Set<String> SUPPORTED_VIDEO_TYPES = Set.of(
            "video/mp4",
            "video/webm",
            "video/quicktime",
            "video/x-msvideo",
            "video/x-matroska",
            "video/mpeg",
            "video/x-flv",
            "video/3gpp"
    );

    @Override
    public byte[] generateImageThumbnail(InputStream inputStream, String mimeType, int width, int height) {
        if (!supportsThumbnail(mimeType)) {
            log.warn("不支持的图片类型: {}", mimeType);
            return null;
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            String outputFormat = getOutputFormat(mimeType);

            Thumbnails.of(inputStream)
                    .size(width, height)
                    .keepAspectRatio(true)
                    .outputQuality(THUMBNAIL_QUALITY)
                    .outputFormat(outputFormat)
                    .toOutputStream(outputStream);

            log.debug("缩略图生成成功: {}x{}, format={}", width, height, outputFormat);
            return outputStream.toByteArray();

        } catch (Exception e) {
            log.error("生成缩略图失败: mimeType={}, error={}", mimeType, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public boolean supportsThumbnail(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        String lowerMimeType = mimeType.toLowerCase();
        return SUPPORTED_IMAGE_TYPES.contains(lowerMimeType)
                || SUPPORTED_VIDEO_TYPES.contains(lowerMimeType);
    }

    @Override
    public boolean supportsVideoThumbnail(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        return SUPPORTED_VIDEO_TYPES.contains(mimeType.toLowerCase());
    }

    @Override
    public byte[] generateVideoThumbnail(InputStream videoInputStream, String mimeType,
                                          double timeOffsetSeconds, int width, int height) {
        if (!supportsVideoThumbnail(mimeType)) {
            log.warn("不支持的视频类型: {}", mimeType);
            return null;
        }

        Path tempVideoFile = null;
        Path tempImageFile = null;
        try {
            // 创建临时视频文件
            String extension = getVideoExtension(mimeType);
            tempVideoFile = Files.createTempFile("video_", extension);
            Files.copy(videoInputStream, tempVideoFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // 创建临时图片输出文件
            tempImageFile = Files.createTempFile("thumb_", ".jpg");

            // 使用 FFmpeg 提取帧
            boolean success = extractFrameWithFfmpeg(tempVideoFile, tempImageFile, timeOffsetSeconds, width, height);
            if (!success) {
                log.warn("FFmpeg 提取视频帧失败: mimeType={}", mimeType);
                return null;
            }

            // 读取生成的缩略图
            byte[] thumbnailData = Files.readAllBytes(tempImageFile);
            if (thumbnailData.length == 0) {
                log.warn("生成的视频缩略图为空");
                return null;
            }

            log.debug("视频缩略图生成成功: {}x{}, size={} bytes", width, height, thumbnailData.length);
            return thumbnailData;

        } catch (Exception e) {
            log.error("生成视频缩略图失败: mimeType={}, error={}", mimeType, e.getMessage(), e);
            return null;
        } finally {
            // 清理临时文件
            deleteTempFile(tempVideoFile);
            deleteTempFile(tempImageFile);
        }
    }

    /**
     * 使用 FFmpeg 提取视频帧
     */
    private boolean extractFrameWithFfmpeg(Path videoFile, Path outputFile,
                                           double timeOffsetSeconds, int width, int height) {
        try {
            // 构建 FFmpeg 命令
            // ffmpeg -ss 1 -i input.mp4 -vframes 1 -vf "scale=300:300:force_original_aspect_ratio=decrease" output.jpg
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-y",                                    // 覆盖输出文件
                    "-ss", String.format("%.2f", timeOffsetSeconds), // 时间偏移
                    "-i", videoFile.toAbsolutePath().toString(),     // 输入文件
                    "-vframes", "1",                         // 只提取1帧
                    "-vf", String.format("scale=%d:%d:force_original_aspect_ratio=decrease,pad=%d:%d:(ow-iw)/2:(oh-ih)/2",
                            width, height, width, height),   // 缩放并保持比例，居中填充
                    "-q:v", "2",                             // 高质量 JPEG
                    outputFile.toAbsolutePath().toString()   // 输出文件
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取输出（避免缓冲区满导致阻塞）
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.trace("FFmpeg: {}", line);
                }
            }

            boolean completed = process.waitFor(FFMPEG_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                log.warn("FFmpeg 执行超时");
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.warn("FFmpeg 退出码非零: {}", exitCode);
                return false;
            }

            return Files.exists(outputFile) && Files.size(outputFile) > 0;

        } catch (IOException e) {
            log.error("FFmpeg 执行失败（可能未安装）: {}", e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("FFmpeg 执行被中断");
            return false;
        }
    }

    /**
     * 根据 MIME 类型获取视频文件扩展名
     */
    private String getVideoExtension(String mimeType) {
        return switch (mimeType.toLowerCase()) {
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            case "video/quicktime" -> ".mov";
            case "video/x-msvideo" -> ".avi";
            case "video/x-matroska" -> ".mkv";
            case "video/mpeg" -> ".mpeg";
            case "video/x-flv" -> ".flv";
            case "video/3gpp" -> ".3gp";
            default -> ".mp4";
        };
    }

    /**
     * 安全删除临时文件
     */
    private void deleteTempFile(Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                log.warn("删除临时文件失败: {}", file);
            }
        }
    }

    /**
     * 根据MIME类型获取输出格式
     */
    private String getOutputFormat(String mimeType) {
        return switch (mimeType.toLowerCase()) {
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/bmp" -> "bmp";
            default -> "jpg";
        };
    }
}
