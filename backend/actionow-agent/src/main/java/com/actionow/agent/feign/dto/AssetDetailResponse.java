package com.actionow.agent.feign.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 素材详情响应 DTO。
 * <p>对应 {@code actionow-project} 的 AssetResponse；仅保留 agent 层用到的字段，
 * 未声明字段通过 {@code @JsonIgnoreProperties(ignoreUnknown = true)} 兼容新增字段。
 *
 * @author Actionow
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssetDetailResponse {

    /** 素材 ID */
    private String id;

    /** 素材名 */
    private String name;

    /** 文件地址（一般为预签名 URL） */
    private String fileUrl;

    /** 缩略图地址 */
    private String thumbnailUrl;

    /** MIME 类型 */
    private String mimeType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 素材类型：IMAGE / VIDEO / AUDIO / DOCUMENT 等 */
    private String assetType;
}
