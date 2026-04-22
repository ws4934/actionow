package com.actionow.ai.plugin.groovy.binding;

import com.actionow.ai.dto.AssetInfo;
import com.actionow.ai.service.AssetInputResolver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.List;

/**
 * 资产处理工具绑定
 * 提供图片、视频、文本、文档的处理能力
 *
 * @author Actionow
 */
@Slf4j
public class AssetBinding {

    /**
     * 最大下载文件大小（默认100MB）
     */
    private static final long MAX_DOWNLOAD_SIZE_BYTES = 100 * 1024 * 1024;

    /**
     * Jackson ObjectMapper（线程安全，可复用）
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient httpClient;

    /**
     * 素材解析器（可选，用于从素材ID获取信息）
     */
    private AssetInputResolver assetInputResolver;

    /**
     * 工作空间ID
     */
    private String workspaceId;

    /**
     * 执行ID
     */
    private String executionId;

    public AssetBinding() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * 设置上下文信息
     */
    public void setContext(String workspaceId, String executionId) {
        this.workspaceId = workspaceId;
        this.executionId = executionId;
    }

    /**
     * 设置素材解析器
     */
    public void setAssetInputResolver(AssetInputResolver assetInputResolver) {
        this.assetInputResolver = assetInputResolver;
    }

    // ==================== 素材解析方法 ====================

    /**
     * 根据素材ID获取素材信息
     *
     * @param assetId 素材ID
     * @return 素材信息，如果未找到返回null
     */
    public AssetInfo getAssetInfo(String assetId) {
        log.debug("[AssetBinding] getAssetInfo called with assetId={}", assetId);

        if (assetInputResolver == null) {
            log.warn("[AssetBinding] Asset resolver not configured, cannot get asset info");
            return null;
        }

        Map<String, AssetInfo> infoMap = assetInputResolver.batchGetAssetInfo(List.of(assetId));
        log.debug("[AssetBinding] getAssetInfo: batchGetAssetInfo returned {} entries", infoMap.size());

        AssetInfo result = infoMap.get(assetId);
        if (result == null) {
            log.warn("[AssetBinding] getAssetInfo: 素材未找到, assetId={}", assetId);
        } else {
            log.debug("[AssetBinding] getAssetInfo: 找到素材, id={}, fileUrl={}", result.getId(), result.getFileUrl());
        }
        return result;
    }

    /**
     * 批量获取素材信息
     *
     * @param assetIds 素材ID列表
     * @return 素材信息Map（assetId -> AssetInfo）
     */
    public Map<String, AssetInfo> batchGetAssetInfo(List<String> assetIds) {
        if (assetInputResolver == null) {
            log.warn("[AssetBinding] Asset resolver not configured, cannot get asset info");
            return Map.of();
        }
        return assetInputResolver.batchGetAssetInfo(assetIds);
    }

    /**
     * 根据素材ID或URL获取文件URL
     * 支持两种输入：
     * 1. 素材ID - 通过 AssetInputResolver 获取素材的 fileUrl
     * 2. URL - 直接返回该 URL
     *
     * @param assetIdOrUrl 素材ID或URL
     * @return 文件URL
     */
    public String getAssetUrl(String assetIdOrUrl) {
        if (assetIdOrUrl == null || assetIdOrUrl.isBlank()) {
            return null;
        }

        // 如果已经是 URL，直接返回
        if (assetIdOrUrl.startsWith("http://") || assetIdOrUrl.startsWith("https://")) {
            log.debug("[AssetBinding] Input is already a URL: {}", assetIdOrUrl);
            return assetIdOrUrl;
        }

        // 作为 asset ID 处理
        AssetInfo info = getAssetInfo(assetIdOrUrl);
        return info != null ? info.getFileUrl() : null;
    }

    /**
     * 根据素材ID下载文件并转换为Base64
     *
     * @param assetId 素材ID
     * @return Base64编码的数据
     */
    public String getAssetAsBase64(String assetId) {
        return getAssetAsBase64(assetId, false);
    }

    /**
     * 根据素材ID或URL下载文件并转换为Base64
     * 支持两种输入：
     * 1. 素材ID - 通过 AssetInputResolver 获取素材信息后下载
     * 2. URL - 直接下载该 URL 的内容
     *
     * @param assetIdOrUrl    素材ID或URL
     * @param includeDataUri  是否包含Data URI前缀
     * @return Base64编码的数据，如果素材不存在或下载失败则返回null
     */
    public String getAssetAsBase64(String assetIdOrUrl, boolean includeDataUri) {
        if (assetIdOrUrl == null || assetIdOrUrl.isBlank()) {
            return null;
        }

        String fileUrl;
        String mimeType = null;

        // 检查是否是 URL
        if (assetIdOrUrl.startsWith("http://") || assetIdOrUrl.startsWith("https://")) {
            log.debug("[AssetBinding] Input is a URL, downloading directly: {}", assetIdOrUrl);
            fileUrl = assetIdOrUrl;
            // 从 URL 推断 MIME 类型
            mimeType = detectMimeTypeFromUrl(assetIdOrUrl);
        } else {
            // 作为 asset ID 处理
            AssetInfo info = getAssetInfo(assetIdOrUrl);
            if (info == null || info.getFileUrl() == null) {
                log.warn("[AssetBinding] Asset not found or has no URL: {}", assetIdOrUrl);
                return null;
            }
            fileUrl = info.getFileUrl();
            mimeType = info.getMimeType();
        }

        try {
            byte[] data = downloadBytes(fileUrl);
            String base64 = Base64.getEncoder().encodeToString(data);

            if (includeDataUri) {
                if (mimeType == null || mimeType.isBlank()) {
                    mimeType = detectMimeType(data);
                }
                return "data:" + mimeType + ";base64," + base64;
            }
            return base64;
        } catch (RuntimeException e) {
            log.error("[AssetBinding] Failed to convert to base64: input={}, url={}, error={}",
                    assetIdOrUrl, fileUrl, e.getMessage());
            return null;
        }
    }

    /**
     * 从 URL 推断 MIME 类型
     */
    private String detectMimeTypeFromUrl(String url) {
        if (url == null) {
            return "application/octet-stream";
        }
        String lower = url.toLowerCase();
        if (lower.contains(".png")) return "image/png";
        if (lower.contains(".jpg") || lower.contains(".jpeg")) return "image/jpeg";
        if (lower.contains(".gif")) return "image/gif";
        if (lower.contains(".webp")) return "image/webp";
        if (lower.contains(".mp4")) return "video/mp4";
        if (lower.contains(".webm")) return "video/webm";
        if (lower.contains(".mp3")) return "audio/mpeg";
        if (lower.contains(".wav")) return "audio/wav";
        return "application/octet-stream";
    }

    /**
     * 根据素材ID下载文件字节
     *
     * @param assetId 素材ID
     * @return 文件字节数据
     */
    public byte[] downloadAsset(String assetId) {
        AssetInfo info = getAssetInfo(assetId);
        if (info == null || info.getFileUrl() == null) {
            log.warn("[AssetBinding] Asset not found or has no URL: {}", assetId);
            return null;
        }
        return downloadBytes(info.getFileUrl());
    }

    /**
     * 批量获取素材URL
     *
     * @param assetIds 素材ID列表
     * @return URL列表
     */
    public List<String> getAssetUrls(List<String> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return List.of();
        }
        Map<String, AssetInfo> infoMap = batchGetAssetInfo(assetIds);
        return assetIds.stream()
                .map(id -> {
                    AssetInfo info = infoMap.get(id);
                    return info != null ? info.getFileUrl() : null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 批量获取素材Base64
     *
     * @param assetIds       素材ID列表
     * @param includeDataUri 是否包含Data URI前缀
     * @return Base64列表
     */
    public List<String> getAssetsAsBase64(List<String> assetIds, boolean includeDataUri) {
        if (assetIds == null || assetIds.isEmpty()) {
            return List.of();
        }
        return assetIds.stream()
                .map(id -> getAssetAsBase64(id, includeDataUri))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 判断是否为素材ID（UUID格式）
     *
     * @param value 待检查的值
     * @return 是否为素材ID
     */
    public boolean isAssetId(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        // 排除已经是URL或Base64的情况
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return false;
        }
        if (value.startsWith("data:")) {
            return false;
        }
        if (value.length() > 100) {
            return false;
        }
        // 简单的UUID格式检查
        return value.matches("^[0-9a-fA-F-]{36}$");
    }

    /**
     * 智能解析输入值
     * 如果是素材ID则获取URL，否则原样返回
     *
     * @param value 输入值（可能是素材ID或URL）
     * @return URL或原始值
     */
    public String resolveToUrl(String value) {
        if (isAssetId(value)) {
            String url = getAssetUrl(value);
            return url != null ? url : value;
        }
        return value;
    }

    /**
     * 智能解析输入值为Base64
     * 如果是素材ID则下载并转换，如果是URL则下载转换，否则假定已经是Base64
     *
     * @param value          输入值
     * @param includeDataUri 是否包含Data URI前缀
     * @return Base64数据
     */
    public String resolveToBase64(String value, boolean includeDataUri) {
        if (value == null || value.isBlank()) {
            return null;
        }

        // 已经是Data URI
        if (value.startsWith("data:")) {
            return value;
        }

        byte[] data;
        String mimeType = "application/octet-stream";

        if (isAssetId(value)) {
            // 素材ID
            AssetInfo info = getAssetInfo(value);
            if (info == null || info.getFileUrl() == null) {
                return null;
            }
            data = downloadBytes(info.getFileUrl());
            mimeType = info.getMimeType() != null ? info.getMimeType() : detectMimeType(data);
        } else if (value.startsWith("http://") || value.startsWith("https://")) {
            // URL
            data = downloadBytes(value);
            mimeType = detectMimeType(data);
        } else {
            // 假定已经是Base64
            return includeDataUri ? "data:" + mimeType + ";base64," + value : value;
        }

        String base64 = Base64.getEncoder().encodeToString(data);
        return includeDataUri ? "data:" + mimeType + ";base64," + base64 : base64;
    }

    // ==================== 图片处理 ====================

    /**
     * 调整图片大小
     *
     * @param imageBytes 原始图片字节
     * @param width      目标宽度
     * @param height     目标高度
     * @param format     输出格式 (png, jpg, webp)
     * @return 处理后的图片字节
     */
    public byte[] resizeImage(byte[] imageBytes, int width, int height, String format) {
        log.debug("[AssetBinding] Resizing image to {}x{}, format={}", width, height, format);
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (original == null) {
                throw new IllegalArgumentException("无法读取图片数据");
            }

            BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = resized.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawImage(original, 0, 0, width, height, null);
            g2d.dispose();

            return writeImage(resized, format);
        } catch (IOException e) {
            log.error("[AssetBinding] Failed to resize image", e);
            throw new RuntimeException("图片缩放失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按比例调整图片大小
     *
     * @param imageBytes 原始图片字节
     * @param maxWidth   最大宽度
     * @param maxHeight  最大高度
     * @param format     输出格式
     * @return 处理后的图片字节
     */
    public byte[] resizeImageKeepRatio(byte[] imageBytes, int maxWidth, int maxHeight, String format) {
        log.debug("[AssetBinding] Resizing image keeping ratio, max={}x{}", maxWidth, maxHeight);
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (original == null) {
                throw new IllegalArgumentException("无法读取图片数据");
            }

            int originalWidth = original.getWidth();
            int originalHeight = original.getHeight();

            double ratio = Math.min((double) maxWidth / originalWidth, (double) maxHeight / originalHeight);
            int newWidth = (int) (originalWidth * ratio);
            int newHeight = (int) (originalHeight * ratio);

            return resizeImage(imageBytes, newWidth, newHeight, format);
        } catch (IOException e) {
            log.error("[AssetBinding] Failed to resize image", e);
            throw new RuntimeException("图片缩放失败: " + e.getMessage(), e);
        }
    }

    /**
     * 压缩图片
     *
     * @param imageBytes 原始图片字节
     * @param quality    压缩质量 (0.0-1.0)
     * @param format     输出格式 (jpg 最佳)
     * @return 压缩后的图片字节
     */
    public byte[] compressImage(byte[] imageBytes, float quality, String format) {
        log.debug("[AssetBinding] Compressing image, quality={}, format={}", quality, format);
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (original == null) {
                throw new IllegalArgumentException("无法读取图片数据");
            }

            // 对于JPEG，需要转换为RGB模式
            BufferedImage processedImage = original;
            if ("jpg".equalsIgnoreCase(format) || "jpeg".equalsIgnoreCase(format)) {
                processedImage = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g2d = processedImage.createGraphics();
                g2d.setColor(Color.WHITE);
                g2d.fillRect(0, 0, original.getWidth(), original.getHeight());
                g2d.drawImage(original, 0, 0, null);
                g2d.dispose();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
            if (!writers.hasNext()) {
                throw new IllegalArgumentException("不支持的图片格式: " + format);
            }

            ImageWriter writer = writers.next();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(quality);
                }
                writer.write(null, new IIOImage(processedImage, null, null), param);
            }
            writer.dispose();

            return baos.toByteArray();
        } catch (IOException e) {
            log.error("[AssetBinding] Failed to compress image", e);
            throw new RuntimeException("图片压缩失败: " + e.getMessage(), e);
        }
    }

    /**
     * 裁剪图片
     *
     * @param imageBytes 原始图片字节
     * @param x          起始X坐标
     * @param y          起始Y坐标
     * @param width      裁剪宽度
     * @param height     裁剪高度
     * @param format     输出格式
     * @return 裁剪后的图片字节
     */
    public byte[] cropImage(byte[] imageBytes, int x, int y, int width, int height, String format) {
        log.debug("[AssetBinding] Cropping image at ({},{}) size {}x{}", x, y, width, height);
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (original == null) {
                throw new IllegalArgumentException("无法读取图片数据");
            }

            // 边界检查
            int actualWidth = Math.min(width, original.getWidth() - x);
            int actualHeight = Math.min(height, original.getHeight() - y);
            if (x < 0 || y < 0 || actualWidth <= 0 || actualHeight <= 0) {
                throw new IllegalArgumentException("裁剪区域无效");
            }

            BufferedImage cropped = original.getSubimage(x, y, actualWidth, actualHeight);
            return writeImage(cropped, format);
        } catch (IOException e) {
            log.error("[AssetBinding] Failed to crop image", e);
            throw new RuntimeException("图片裁剪失败: " + e.getMessage(), e);
        }
    }

    /**
     * 居中裁剪图片为正方形
     *
     * @param imageBytes 原始图片字节
     * @param size       目标边长
     * @param format     输出格式
     * @return 裁剪后的图片字节
     */
    public byte[] cropSquare(byte[] imageBytes, int size, String format) {
        log.debug("[AssetBinding] Cropping image to square {}x{}", size, size);
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (original == null) {
                throw new IllegalArgumentException("无法读取图片数据");
            }

            int originalWidth = original.getWidth();
            int originalHeight = original.getHeight();
            int cropSize = Math.min(originalWidth, originalHeight);

            int x = (originalWidth - cropSize) / 2;
            int y = (originalHeight - cropSize) / 2;

            BufferedImage cropped = original.getSubimage(x, y, cropSize, cropSize);

            // 如果需要缩放到目标大小
            if (cropSize != size) {
                BufferedImage resized = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d = resized.createGraphics();
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.drawImage(cropped, 0, 0, size, size, null);
                g2d.dispose();
                cropped = resized;
            }

            return writeImage(cropped, format);
        } catch (IOException e) {
            log.error("[AssetBinding] Failed to crop square", e);
            throw new RuntimeException("图片裁剪失败: " + e.getMessage(), e);
        }
    }

    /**
     * 转换图片格式
     *
     * @param imageBytes   原始图片字节
     * @param targetFormat 目标格式 (png, jpg, gif, webp)
     * @return 转换后的图片字节
     */
    public byte[] convertFormat(byte[] imageBytes, String targetFormat) {
        log.debug("[AssetBinding] Converting image to format={}", targetFormat);
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                throw new IllegalArgumentException("无法读取图片数据");
            }
            return writeImage(image, targetFormat);
        } catch (IOException e) {
            log.error("[AssetBinding] Failed to convert format", e);
            throw new RuntimeException("图片格式转换失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取图片信息
     *
     * @param imageBytes 图片字节
     * @return 图片信息 Map
     */
    public Map<String, Object> getImageInfo(byte[] imageBytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                throw new IllegalArgumentException("无法读取图片数据");
            }

            Map<String, Object> info = new HashMap<>();
            info.put("width", image.getWidth());
            info.put("height", image.getHeight());
            info.put("type", image.getType());
            info.put("colorModel", image.getColorModel().getColorSpace().getType());
            info.put("hasAlpha", image.getColorModel().hasAlpha());
            info.put("sizeBytes", imageBytes.length);

            return info;
        } catch (IOException e) {
            log.error("[AssetBinding] Failed to get image info", e);
            throw new RuntimeException("获取图片信息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从URL下载图片
     *
     * @param url 图片URL
     * @return 图片字节
     */
    public byte[] downloadImage(String url) {
        return downloadBytes(url);
    }

    // ==================== 文本处理 ====================

    /**
     * 截断文本
     *
     * @param text      原始文本
     * @param maxLength 最大长度
     * @param suffix    截断后缀 (如 "...")
     * @return 截断后的文本
     */
    public String truncateText(String text, int maxLength, String suffix) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        int endIndex = maxLength - (suffix != null ? suffix.length() : 0);
        if (endIndex <= 0) {
            return suffix;
        }
        return text.substring(0, endIndex) + (suffix != null ? suffix : "");
    }

    /**
     * 转换文本编码
     *
     * @param bytes       原始字节
     * @param fromCharset 源编码
     * @param toCharset   目标编码
     * @return 转换后的字节
     * @throws IllegalArgumentException 如果字符集名称无效或不支持
     */
    public byte[] convertEncoding(byte[] bytes, String fromCharset, String toCharset) {
        try {
            Charset sourceCharset = Charset.forName(fromCharset);
            Charset targetCharset = Charset.forName(toCharset);
            String text = new String(bytes, sourceCharset);
            return text.getBytes(targetCharset);
        } catch (java.nio.charset.IllegalCharsetNameException e) {
            log.error("[AssetBinding] Invalid charset name: fromCharset={}, toCharset={}", fromCharset, toCharset, e);
            throw new IllegalArgumentException("无效的字符集名称: " + e.getCharsetName(), e);
        } catch (java.nio.charset.UnsupportedCharsetException e) {
            log.error("[AssetBinding] Unsupported charset: {}", e.getCharsetName(), e);
            throw new IllegalArgumentException("不支持的字符集: " + e.getCharsetName(), e);
        }
    }

    /**
     * 提取文本摘要（简单按句子截取）
     *
     * @param text         原始文本
     * @param maxSentences 最大句子数
     * @return 摘要文本
     */
    public String extractSummary(String text, int maxSentences) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // 简单按句号、问号、感叹号分割
        String[] sentences = text.split("[.。!！?？]+");
        if (sentences.length <= maxSentences) {
            return text;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxSentences && i < sentences.length; i++) {
            if (!sentences[i].trim().isEmpty()) {
                sb.append(sentences[i].trim()).append("。");
            }
        }
        return sb.toString();
    }

    /**
     * 移除HTML标签
     *
     * @param html HTML文本
     * @return 纯文本
     */
    public String stripHtml(String html) {
        if (html == null) {
            return null;
        }
        return html.replaceAll("<[^>]+>", "")
                   .replaceAll("&nbsp;", " ")
                   .replaceAll("&lt;", "<")
                   .replaceAll("&gt;", ">")
                   .replaceAll("&amp;", "&")
                   .replaceAll("\\s+", " ")
                   .trim();
    }

    /**
     * 提取JSON中的文本值
     * 使用 Jackson 解析 JSON，支持嵌套路径访问
     *
     * @param json    JSON字符串
     * @param keyPath 键路径 (支持 a.b.c 格式)
     * @return 提取的值，如果路径不存在或解析失败则返回null
     */
    public String extractJsonValue(String json, String keyPath) {
        if (json == null || json.isBlank() || keyPath == null || keyPath.isBlank()) {
            return null;
        }

        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(json);
            String[] keys = keyPath.split("\\.");

            JsonNode currentNode = rootNode;
            for (String key : keys) {
                if (currentNode == null || currentNode.isNull() || currentNode.isMissingNode()) {
                    return null;
                }

                // 支持数组索引访问（如 items.0.name）
                if (currentNode.isArray()) {
                    try {
                        int index = Integer.parseInt(key);
                        currentNode = currentNode.get(index);
                    } catch (NumberFormatException e) {
                        // 不是数字索引，尝试作为字段名
                        currentNode = currentNode.get(key);
                    }
                } else {
                    currentNode = currentNode.get(key);
                }
            }

            if (currentNode == null || currentNode.isNull() || currentNode.isMissingNode()) {
                return null;
            }

            // 根据节点类型返回适当的值
            if (currentNode.isTextual()) {
                return currentNode.asText();
            } else if (currentNode.isNumber()) {
                return currentNode.asText();
            } else if (currentNode.isBoolean()) {
                return String.valueOf(currentNode.asBoolean());
            } else if (currentNode.isObject() || currentNode.isArray()) {
                return currentNode.toString();
            }

            return currentNode.asText();

        } catch (JsonProcessingException e) {
            log.warn("[AssetBinding] Failed to parse JSON: {}", e.getMessage());
            return null;
        }
    }

    // ==================== 视频处理 ====================

    /**
     * 获取视频信息（基础实现，仅返回文件大小）
     * 完整视频处理需要 FFmpeg 等外部工具
     *
     * @param videoBytes 视频字节
     * @return 视频信息 Map
     */
    public Map<String, Object> getVideoInfo(byte[] videoBytes) {
        Map<String, Object> info = new HashMap<>();
        info.put("sizeBytes", videoBytes.length);
        info.put("sizeMB", String.format("%.2f", videoBytes.length / (1024.0 * 1024.0)));

        // 尝试检测格式（通过魔数）
        String format = detectVideoFormat(videoBytes);
        info.put("format", format);

        return info;
    }

    /**
     * 下载视频
     *
     * @param url 视频URL
     * @return 视频字节
     */
    public byte[] downloadVideo(String url) {
        return downloadBytes(url);
    }

    // ==================== 文档处理 ====================

    /**
     * 读取文本文件内容
     *
     * @param bytes   文件字节
     * @param charset 字符集
     * @return 文本内容
     */
    public String readTextFile(byte[] bytes, String charset) {
        return new String(bytes, Charset.forName(charset != null ? charset : "UTF-8"));
    }

    /**
     * 将文本写入字节
     *
     * @param text    文本内容
     * @param charset 字符集
     * @return 字节数组
     */
    public byte[] writeTextFile(String text, String charset) {
        return text.getBytes(Charset.forName(charset != null ? charset : "UTF-8"));
    }

    /**
     * Base64 编码
     *
     * @param data 原始数据
     * @return Base64 字符串
     */
    public String toBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * Base64 解码
     *
     * @param base64 Base64 字符串
     * @return 原始数据
     */
    public byte[] fromBase64(String base64) {
        // 移除可能的 data URI 前缀
        String cleanBase64 = base64;
        if (base64.contains(",")) {
            cleanBase64 = base64.substring(base64.indexOf(",") + 1);
        }
        return Base64.getDecoder().decode(cleanBase64);
    }

    // ==================== 通用方法 ====================

    /**
     * 下载字节数据（带大小限制）
     * 先尝试 HEAD 请求检查大小，如果服务器不支持则直接下载并在下载后检查
     *
     * @param url URL
     * @return 字节数据
     */
    public byte[] downloadBytes(String url) {
        log.debug("[AssetBinding] Downloading from URL: {}", url);
        try {
            // 尝试发送 HEAD 请求检查文件大小（部分服务器不支持，需要 fallback）
            boolean headCheckPassed = tryHeadRequestSizeCheck(url);

            // 如果 HEAD 请求明确返回文件过大，直接抛出异常
            if (!headCheckPassed) {
                // headCheckPassed 为 false 表示文件过大，已在方法内抛出异常
                // 这里不会执行到
            }

            // 执行实际下载
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(5))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                throw new RuntimeException("下载失败，状态码: " + response.statusCode());
            }

            byte[] body = response.body();

            // 检查实际下载大小（防止服务器未返回 Content-Length 或 HEAD 请求失败）
            if (body.length > MAX_DOWNLOAD_SIZE_BYTES) {
                throw new RuntimeException(String.format(
                    "下载的文件大小超过限制: %.2fMB (最大: %.2fMB)",
                    body.length / (1024.0 * 1024.0),
                    MAX_DOWNLOAD_SIZE_BYTES / (1024.0 * 1024.0)));
            }

            return body;
        } catch (IOException e) {
            log.error("[AssetBinding] Failed to download from URL: {}", url, e);
            throw new RuntimeException("下载失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[AssetBinding] Download interrupted for URL: {}", url, e);
            throw new RuntimeException("下载被中断", e);
        }
    }

    /**
     * 尝试通过 HEAD 请求预检文件大小
     * 如果服务器不支持 HEAD 请求（返回 405 或其他错误），则跳过检查
     *
     * @param url 文件URL
     * @return true 表示可以继续下载，false 不会返回（文件过大时直接抛异常）
     */
    private boolean tryHeadRequestSizeCheck(String url) {
        try {
            HttpRequest headRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<Void> headResponse = httpClient.send(headRequest, HttpResponse.BodyHandlers.discarding());

            int statusCode = headResponse.statusCode();

            // 服务器不支持 HEAD 请求（405 Method Not Allowed）或其他客户端/服务器错误
            if (statusCode == 405 || statusCode >= 400) {
                log.debug("[AssetBinding] HEAD request not supported (status={}), skipping size pre-check for: {}",
                    statusCode, url);
                return true; // 跳过检查，继续下载
            }

            // HEAD 请求成功，检查 Content-Length
            if (statusCode == 200 || statusCode == 204) {
                long contentLength = headResponse.headers()
                        .firstValueAsLong("Content-Length")
                        .orElse(-1);

                if (contentLength > MAX_DOWNLOAD_SIZE_BYTES) {
                    throw new RuntimeException(String.format(
                        "文件大小超过限制: %.2fMB (最大: %.2fMB)",
                        contentLength / (1024.0 * 1024.0),
                        MAX_DOWNLOAD_SIZE_BYTES / (1024.0 * 1024.0)));
                }

                log.debug("[AssetBinding] HEAD check passed, Content-Length: {} bytes", contentLength);
            }

            return true;

        } catch (IOException | InterruptedException e) {
            // HEAD 请求失败（网络问题、超时等），跳过检查，让 GET 请求处理
            log.debug("[AssetBinding] HEAD request failed ({}), proceeding with GET: {}",
                e.getClass().getSimpleName(), url);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return true;
        }
    }

    /**
     * 获取文件扩展名
     *
     * @param filename 文件名
     * @return 扩展名（不含点）
     */
    public String getExtension(String filename) {
        if (filename == null) {
            return null;
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1).toLowerCase();
    }

    /**
     * 检测 MIME 类型
     *
     * @param bytes 文件字节
     * @return MIME 类型
     */
    public String detectMimeType(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return "application/octet-stream";
        }

        // PNG
        if (bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50 && bytes[2] == (byte) 0x4E && bytes[3] == (byte) 0x47) {
            return "image/png";
        }
        // JPEG
        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
            return "image/jpeg";
        }
        // GIF
        if (bytes[0] == (byte) 0x47 && bytes[1] == (byte) 0x49 && bytes[2] == (byte) 0x46) {
            return "image/gif";
        }
        // WebP
        if (bytes.length >= 12 && bytes[0] == (byte) 0x52 && bytes[1] == (byte) 0x49 && bytes[2] == (byte) 0x46 && bytes[3] == (byte) 0x46
            && bytes[8] == (byte) 0x57 && bytes[9] == (byte) 0x45 && bytes[10] == (byte) 0x42 && bytes[11] == (byte) 0x50) {
            return "image/webp";
        }
        // MP4
        if (bytes.length >= 8 && bytes[4] == (byte) 0x66 && bytes[5] == (byte) 0x74 && bytes[6] == (byte) 0x79 && bytes[7] == (byte) 0x70) {
            return "video/mp4";
        }
        // PDF
        if (bytes[0] == (byte) 0x25 && bytes[1] == (byte) 0x50 && bytes[2] == (byte) 0x44 && bytes[3] == (byte) 0x46) {
            return "application/pdf";
        }

        return "application/octet-stream";
    }

    // ==================== 私有方法 ====================

    private byte[] writeImage(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // 对于JPEG格式，需要转换为RGB
        if ("jpg".equalsIgnoreCase(format) || "jpeg".equalsIgnoreCase(format)) {
            BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = rgbImage.createGraphics();
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, image.getWidth(), image.getHeight());
            g2d.drawImage(image, 0, 0, null);
            g2d.dispose();
            image = rgbImage;
        }

        if (!ImageIO.write(image, format, baos)) {
            throw new IOException("不支持的图片格式: " + format);
        }
        return baos.toByteArray();
    }

    private String detectVideoFormat(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return "unknown";
        }

        // MP4 (ftyp box)
        if (bytes.length >= 8 && bytes[4] == 0x66 && bytes[5] == 0x74 && bytes[6] == 0x79 && bytes[7] == 0x70) {
            return "mp4";
        }
        // WebM
        if (bytes[0] == 0x1A && bytes[1] == 0x45 && bytes[2] == (byte) 0xDF && bytes[3] == (byte) 0xA3) {
            return "webm";
        }
        // AVI
        if (bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46
            && bytes[8] == 0x41 && bytes[9] == 0x56 && bytes[10] == 0x49) {
            return "avi";
        }
        // MOV
        if (bytes.length >= 8 && bytes[4] == 0x6D && bytes[5] == 0x6F && bytes[6] == 0x6F && bytes[7] == 0x76) {
            return "mov";
        }

        return "unknown";
    }
}
