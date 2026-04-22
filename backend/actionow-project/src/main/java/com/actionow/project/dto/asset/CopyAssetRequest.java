package com.actionow.project.dto.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 复制素材请求
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopyAssetRequest {

    /**
     * 目标剧本ID（可选，不指定则使用源素材的 scriptId）
     */
    private String targetScriptId;

    /**
     * 目标实体类型（可选，指定后会同时挂载到目标实体）
     */
    private String targetEntityType;

    /**
     * 目标实体ID（可选，与 targetEntityType 配合使用）
     */
    private String targetEntityId;

    /**
     * 关联类型（可选，挂载时使用，默认 DRAFT）
     */
    private String relationType;
}
