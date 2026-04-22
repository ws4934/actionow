package com.actionow.common.file.util;

import com.actionow.common.file.constant.FileConstants;
import com.actionow.common.core.id.UuidGenerator;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * OSS 存储路径构建器
 * 提供流式 API 构建符合规范的文件存储路径
 *
 * <p>路径格式：
 * <ul>
 *   <li>租户文件: tenant_{workspaceId}/{mediaDir}/{source}/{date}/{filename}</li>
 *   <li>公共文件: public/{subDir}/{filename}</li>
 *   <li>回收站: tenant_{workspaceId}/trash/{date}/{originalPath}</li>
 *   <li>缩略图: tenant_{workspaceId}/{mediaDir}/thumbnails/{date}/{filename}_thumb.jpg</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 用户上传图片
 * String path = StoragePathBuilder.tenant("workspace-123")
 *     .images()
 *     .uploads()
 *     .withDatePath()
 *     .filename("image.jpg")
 *     .build();
 * // 结果: tenant_workspace-123/images/uploads/2024/02/10/019abc...def.jpg
 *
 * // AI生成视频
 * String path = StoragePathBuilder.tenant("workspace-123")
 *     .videos()
 *     .aiGenerated()
 *     .withDatePath()
 *     .filename("video.mp4")
 *     .build();
 * // 结果: tenant_workspace-123/videos/ai-generated/2024/02/10/019abc...def.mp4
 *
 * // 缩略图
 * String thumbPath = StoragePathBuilder.thumbnailFor(originalPath);
 *
 * // 移动到回收站
 * String trashPath = StoragePathBuilder.trashPath("workspace-123", originalPath);
 * }</pre>
 *
 * @author Actionow
 */
public class StoragePathBuilder {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private String namespace;
    private String mediaDir;
    private String source;
    private String subDir;
    private String datePath;
    private String filename;
    private String extension;
    private boolean generateUuid = true;

    private StoragePathBuilder() {
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 创建租户空间路径构建器
     *
     * @param workspaceId 工作空间ID
     * @return 路径构建器
     */
    public static StoragePathBuilder tenant(String workspaceId) {
        StoragePathBuilder builder = new StoragePathBuilder();
        builder.namespace = FileConstants.Namespace.tenant(workspaceId);
        return builder;
    }

    /**
     * 创建公共空间路径构建器
     *
     * @return 路径构建器
     */
    public static StoragePathBuilder publicSpace() {
        StoragePathBuilder builder = new StoragePathBuilder();
        builder.namespace = FileConstants.Namespace.PUBLIC;
        return builder;
    }

    /**
     * 创建临时空间路径构建器
     *
     * @return 路径构建器
     */
    public static StoragePathBuilder temp() {
        StoragePathBuilder builder = new StoragePathBuilder();
        builder.namespace = FileConstants.Namespace.TEMP;
        return builder;
    }

    /**
     * 生成缩略图路径
     *
     * @param originalPath 原始文件路径
     * @return 缩略图路径
     */
    public static String thumbnailFor(String originalPath) {
        if (!StringUtils.hasText(originalPath)) {
            return null;
        }

        // 获取不含扩展名的路径
        int lastDotIndex = originalPath.lastIndexOf('.');
        String basePath = lastDotIndex > 0 ? originalPath.substring(0, lastDotIndex) : originalPath;

        // 将媒体目录替换为 thumbnails 子目录
        // tenant_xxx/images/uploads/2024/02/10/uuid.jpg -> tenant_xxx/images/thumbnails/2024/02/10/uuid_thumb.jpg
        for (String mediaDir : new String[]{
                FileConstants.MediaDir.IMAGES,
                FileConstants.MediaDir.VIDEOS
        }) {
            String uploadPattern = "/" + mediaDir + "/" + FileConstants.Source.UPLOADS + "/";
            String aiPattern = "/" + mediaDir + "/" + FileConstants.Source.AI_GENERATED + "/";
            String transferPattern = "/" + mediaDir + "/" + FileConstants.Source.TRANSFERRED + "/";

            if (basePath.contains(uploadPattern)) {
                basePath = basePath.replace(uploadPattern, "/" + mediaDir + "/thumbnails/");
                break;
            }
            if (basePath.contains(aiPattern)) {
                basePath = basePath.replace(aiPattern, "/" + mediaDir + "/thumbnails/");
                break;
            }
            if (basePath.contains(transferPattern)) {
                basePath = basePath.replace(transferPattern, "/" + mediaDir + "/thumbnails/");
                break;
            }
        }

        return basePath + "_thumb.jpg";
    }

    /**
     * 生成回收站路径
     *
     * @param workspaceId  工作空间ID
     * @param originalPath 原始文件路径
     * @return 回收站路径
     */
    public static String trashPath(String workspaceId, String originalPath) {
        if (!StringUtils.hasText(originalPath)) {
            return null;
        }

        String tenantPrefix = FileConstants.Namespace.tenant(workspaceId);
        String datePath = LocalDateTime.now().format(DATE_FORMATTER);

        // 移除租户前缀，保留相对路径
        String relativePath = originalPath;
        if (originalPath.startsWith(tenantPrefix + "/")) {
            relativePath = originalPath.substring(tenantPrefix.length() + 1);
        }

        return String.format("%s/%s/%s/%s",
                tenantPrefix,
                FileConstants.SpecialDir.TRASH,
                datePath,
                relativePath);
    }

    /**
     * 从回收站路径还原原始路径
     *
     * @param trashPath 回收站路径
     * @return 原始路径
     */
    public static String restoreFromTrash(String trashPath) {
        if (!StringUtils.hasText(trashPath)) {
            return null;
        }

        // tenant_xxx/trash/2024/02/10/images/uploads/2024/02/10/uuid.jpg
        // -> tenant_xxx/images/uploads/2024/02/10/uuid.jpg
        String trashMarker = "/" + FileConstants.SpecialDir.TRASH + "/";
        int trashIndex = trashPath.indexOf(trashMarker);
        if (trashIndex < 0) {
            return trashPath;
        }

        String tenantPrefix = trashPath.substring(0, trashIndex);
        String afterTrash = trashPath.substring(trashIndex + trashMarker.length());

        // 跳过日期路径 (yyyy/MM/dd/)
        int dateEndIndex = 0;
        int slashCount = 0;
        for (int i = 0; i < afterTrash.length() && slashCount < 3; i++) {
            if (afterTrash.charAt(i) == '/') {
                slashCount++;
            }
            dateEndIndex = i + 1;
        }

        String relativePath = afterTrash.substring(dateEndIndex);
        return tenantPrefix + "/" + relativePath;
    }

    // ==================== 媒体类型设置 ====================

    /**
     * 设置为图片目录
     */
    public StoragePathBuilder images() {
        this.mediaDir = FileConstants.MediaDir.IMAGES;
        return this;
    }

    /**
     * 设置为视频目录
     */
    public StoragePathBuilder videos() {
        this.mediaDir = FileConstants.MediaDir.VIDEOS;
        return this;
    }

    /**
     * 设置为音频目录
     */
    public StoragePathBuilder audios() {
        this.mediaDir = FileConstants.MediaDir.AUDIOS;
        return this;
    }

    /**
     * 设置为文档目录
     */
    public StoragePathBuilder documents() {
        this.mediaDir = FileConstants.MediaDir.DOCUMENTS;
        return this;
    }

    /**
     * 设置为模型目录
     */
    public StoragePathBuilder models() {
        this.mediaDir = FileConstants.MediaDir.MODELS;
        return this;
    }

    /**
     * 根据文件类型自动设置媒体目录
     *
     * @param fileType 文件类型（IMAGE, VIDEO, AUDIO, DOCUMENT, MODEL）
     */
    public StoragePathBuilder mediaType(String fileType) {
        this.mediaDir = FileConstants.MediaDir.fromFileType(fileType);
        return this;
    }

    // ==================== 来源设置 ====================

    /**
     * 设置为用户上传
     */
    public StoragePathBuilder uploads() {
        this.source = FileConstants.Source.UPLOADS;
        return this;
    }

    /**
     * 设置为AI生成
     */
    public StoragePathBuilder aiGenerated() {
        this.source = FileConstants.Source.AI_GENERATED;
        return this;
    }

    /**
     * 设置为外部转存
     */
    public StoragePathBuilder transferred() {
        this.source = FileConstants.Source.TRANSFERRED;
        return this;
    }

    /**
     * 设置为缩略图
     */
    public StoragePathBuilder thumbnails() {
        this.source = FileConstants.MediaDir.THUMBNAILS;
        return this;
    }

    // ==================== 特殊目录设置 ====================

    /**
     * 设置子目录
     */
    public StoragePathBuilder subDir(String subDir) {
        this.subDir = subDir;
        return this;
    }

    /**
     * 设置为创作元素目录
     */
    public StoragePathBuilder elements() {
        this.subDir = FileConstants.SpecialDir.ELEMENTS;
        return this;
    }

    /**
     * 设置为剧本目录
     *
     * @param scriptId 剧本ID
     */
    public StoragePathBuilder script(String scriptId) {
        this.subDir = FileConstants.SpecialDir.SCRIPTS + "/" + scriptId;
        return this;
    }

    /**
     * 设置为分镜目录
     *
     * @param scriptId 剧本ID
     */
    public StoragePathBuilder storyboards(String scriptId) {
        this.subDir = FileConstants.SpecialDir.SCRIPTS + "/" + scriptId + "/" + FileConstants.SpecialDir.STORYBOARDS;
        return this;
    }

    /**
     * 设置为导出目录
     *
     * @param scriptId 剧本ID
     */
    public StoragePathBuilder exports(String scriptId) {
        this.subDir = FileConstants.SpecialDir.SCRIPTS + "/" + scriptId + "/" + FileConstants.SpecialDir.EXPORTS;
        return this;
    }

    /**
     * 设置为临时目录
     */
    public StoragePathBuilder tempDir() {
        this.subDir = FileConstants.SpecialDir.TEMP;
        return this;
    }

    // ==================== 日期路径设置 ====================

    /**
     * 添加当前日期路径 (yyyy/MM/dd)
     */
    public StoragePathBuilder withDatePath() {
        this.datePath = LocalDateTime.now().format(DATE_FORMATTER);
        return this;
    }

    /**
     * 添加指定日期路径
     *
     * @param dateTime 日期时间
     */
    public StoragePathBuilder withDatePath(LocalDateTime dateTime) {
        this.datePath = dateTime.format(DATE_FORMATTER);
        return this;
    }

    // ==================== 文件名设置 ====================

    /**
     * 设置文件名（自动生成UUID前缀）
     *
     * @param originalName 原始文件名
     */
    public StoragePathBuilder filename(String originalName) {
        this.filename = originalName;
        this.generateUuid = true;
        return this;
    }

    /**
     * 设置完整文件名（不生成UUID）
     *
     * @param fullFilename 完整文件名
     */
    public StoragePathBuilder fullFilename(String fullFilename) {
        this.filename = fullFilename;
        this.generateUuid = false;
        return this;
    }

    /**
     * 设置文件扩展名（与UUID一起生成文件名）
     *
     * @param extension 扩展名（如 "jpg", "png"）
     */
    public StoragePathBuilder extension(String extension) {
        this.extension = extension.startsWith(".") ? extension : "." + extension;
        this.generateUuid = true;
        return this;
    }

    // ==================== 构建方法 ====================

    /**
     * 构建最终路径
     *
     * @return OSS 文件路径
     */
    public String build() {
        StringBuilder path = new StringBuilder();

        // 1. 命名空间
        if (StringUtils.hasText(namespace)) {
            path.append(namespace);
        }

        // 2. 媒体目录
        if (StringUtils.hasText(mediaDir)) {
            appendSegment(path, mediaDir);
        }

        // 3. 子目录（如 elements、scripts/xxx）
        if (StringUtils.hasText(subDir)) {
            appendSegment(path, subDir);
        }

        // 4. 来源（uploads、ai-generated、transferred、thumbnails）
        if (StringUtils.hasText(source)) {
            appendSegment(path, source);
        }

        // 5. 日期路径
        if (StringUtils.hasText(datePath)) {
            appendSegment(path, datePath);
        }

        // 6. 文件名
        String finalFilename = buildFilename();
        if (StringUtils.hasText(finalFilename)) {
            appendSegment(path, finalFilename);
        }

        return path.toString();
    }

    /**
     * 构建目录路径（不含文件名）
     *
     * @return 目录路径
     */
    public String buildDir() {
        String originalFilename = this.filename;
        String originalExtension = this.extension;
        this.filename = null;
        this.extension = null;

        String result = build();

        this.filename = originalFilename;
        this.extension = originalExtension;

        return result;
    }

    private String buildFilename() {
        if (generateUuid) {
            String uuid = UuidGenerator.generateUuidV7();
            if (StringUtils.hasText(extension)) {
                return uuid + extension;
            }
            if (StringUtils.hasText(filename)) {
                String ext = getExtension(filename);
                return uuid + ext;
            }
            return uuid;
        }
        return filename;
    }

    private void appendSegment(StringBuilder path, String segment) {
        if (path.length() > 0 && !path.toString().endsWith("/")) {
            path.append("/");
        }
        path.append(segment);
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }

    // ==================== 便捷静态方法 ====================

    /**
     * 快速生成租户上传文件路径
     *
     * @param workspaceId  工作空间ID
     * @param fileType     文件类型
     * @param originalName 原始文件名
     * @return 文件路径
     */
    public static String forUpload(String workspaceId, String fileType, String originalName) {
        return tenant(workspaceId)
                .mediaType(fileType)
                .uploads()
                .withDatePath()
                .filename(originalName)
                .build();
    }

    /**
     * 快速生成AI生成文件路径
     *
     * @param workspaceId 工作空间ID
     * @param fileType    文件类型
     * @param extension   文件扩展名
     * @return 文件路径
     */
    public static String forAiGenerated(String workspaceId, String fileType, String extension) {
        return tenant(workspaceId)
                .mediaType(fileType)
                .aiGenerated()
                .withDatePath()
                .extension(extension)
                .build();
    }

    /**
     * 快速生成公共资源路径
     *
     * @param subDir   子目录（如 PublicDir.AVATARS）
     * @param filename 文件名
     * @return 文件路径
     */
    public static String forPublic(String subDir, String filename) {
        return publicSpace()
                .subDir(subDir)
                .fullFilename(filename)
                .build();
    }
}
