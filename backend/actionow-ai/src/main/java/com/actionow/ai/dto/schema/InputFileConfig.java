package com.actionow.ai.dto.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传配置
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InputFileConfig {

    /**
     * 允许的文件类型 MIME
     * 如: "image/png,image/jpeg,image/webp"
     */
    private String accept;

    /**
     * 最大文件大小（字节）
     */
    private Long maxSize;

    /**
     * 最大文件大小显示文本
     * 如: "10MB"
     */
    private String maxSizeLabel;

    /**
     * 最大文件数量（列表类型）
     */
    private Integer maxCount;

    /**
     * 最小文件数量（列表类型）
     */
    private Integer minCount;

    /**
     * 上传提示文字
     */
    private String uploadTip;

    /**
     * 是否支持拖拽上传
     */
    private Boolean draggable;

    /**
     * 图片最大宽度
     */
    private Integer maxWidth;

    /**
     * 图片最大高度
     */
    private Integer maxHeight;

    /**
     * 图片最小宽度
     */
    private Integer minWidth;

    /**
     * 图片最小高度
     */
    private Integer minHeight;

    /**
     * 视频/音频最大时长（秒）
     */
    private Integer maxDuration;

    /**
     * 视频/音频最小时长（秒）
     */
    private Integer minDuration;

    // ==================== AI 模型输入格式配置 ====================

    /**
     * 输入格式
     * URL - 传递文件URL给模型（默认）
     * BASE64 - 传递Base64编码数据给模型
     * BOTH - 同时支持URL和BASE64，优先使用URL
     */
    private InputFormat inputFormat;

    /**
     * 是否需要预签名URL
     * 当inputFormat为URL时，是否生成带过期时间的预签名URL
     * 默认 false，使用永久URL
     */
    private Boolean requirePresignedUrl;

    /**
     * 预签名URL过期时间（秒）
     * 默认 3600（1小时）
     */
    private Integer presignedUrlExpireSeconds;

    /**
     * Base64编码是否包含Data URI前缀
     * 如: "data:image/png;base64,"
     * 默认 false
     */
    private Boolean includeDataUriPrefix;

    /**
     * 输入格式枚举
     */
    public enum InputFormat {
        /**
         * URL格式 - 传递文件URL给模型
         */
        URL,
        /**
         * Base64格式 - 传递Base64编码数据给模型
         */
        BASE64,
        /**
         * 同时支持 - 优先使用URL，如果模型不支持再转为Base64
         */
        BOTH
    }

    /**
     * 获取输入格式，默认为URL
     */
    public InputFormat getInputFormatOrDefault() {
        return inputFormat != null ? inputFormat : InputFormat.URL;
    }

    /**
     * 是否需要转换为Base64
     */
    public boolean needBase64() {
        return inputFormat == InputFormat.BASE64;
    }

    /**
     * 是否需要URL格式
     */
    public boolean needUrl() {
        return inputFormat == null || inputFormat == InputFormat.URL || inputFormat == InputFormat.BOTH;
    }
}
