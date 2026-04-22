package com.actionow.project.feign;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 素材基本信息（供其他服务获取）
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetBasicInfo {

    /**
     * 素材ID
     */
    private String id;

    /**
     * 素材名称
     */
    private String name;

    /**
     * 素材类型
     */
    private String assetType;

    /**
     * 文件URL
     */
    private String fileUrl;

    /**
     * 缩略图URL
     */
    private String thumbnailUrl;

    /**
     * MIME类型
     */
    private String mimeType;
}
