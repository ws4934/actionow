package com.actionow.project.dto.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 素材上传初始化请求
 * 一次请求完成：创建素材记录 + 获取预签名上传 URL
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetUploadInitRequest {

    /**
     * 素材名称
     */
    @NotBlank(message = "素材名称不能为空")
    @Size(max = 500, message = "素材名称不能超过500个字符")
    private String name;

    /**
     * 原始文件名
     */
    @NotBlank(message = "文件名不能为空")
    private String fileName;

    /**
     * MIME 类型
     */
    @NotBlank(message = "MIME类型不能为空")
    private String mimeType;

    /**
     * 文件大小（字节）
     */
    @NotNull(message = "文件大小不能为空")
    @Positive(message = "文件大小必须为正数")
    private Long fileSize;

    /**
     * 素材类型: IMAGE, VIDEO, AUDIO, DOCUMENT, MODEL, OTHER
     * 可选，如果不传则根据 mimeType 自动判断
     */
    private String assetType;

    /**
     * 描述
     */
    @Size(max = 2000, message = "描述不能超过2000个字符")
    private String description;

    /**
     * 作用域: WORKSPACE(默认), SCRIPT
     */
    private String scope;

    /**
     * 剧本ID（scope为SCRIPT时必填）
     */
    private String scriptId;

    /**
     * 扩展信息
     */
    private Map<String, Object> extraInfo;

    /**
     * 关联实体列表（可选）
     */
    private List<CreateAssetRequest.RelatedEntityInfo> relatedEntities;

    /**
     * 是否需要生成缩略图（默认 true）
     */
    private Boolean needThumbnail;
}
