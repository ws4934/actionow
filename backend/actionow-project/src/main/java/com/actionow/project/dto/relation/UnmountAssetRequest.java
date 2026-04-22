package com.actionow.project.dto.relation;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 解挂素材请求
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnmountAssetRequest {

    /**
     * 实体类型
     */
    @NotBlank(message = "实体类型不能为空")
    private String entityType;

    /**
     * 实体ID
     */
    @NotBlank(message = "实体ID不能为空")
    private String entityId;

    /**
     * 素材ID
     */
    @NotBlank(message = "素材ID不能为空")
    private String assetId;

    /**
     * 关联类型（可选，不指定则删除该组合下所有关联）
     */
    private String relationType;
}
