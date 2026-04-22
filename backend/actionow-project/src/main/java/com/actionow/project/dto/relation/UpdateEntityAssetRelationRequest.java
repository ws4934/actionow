package com.actionow.project.dto.relation;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新实体-素材关联请求
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEntityAssetRelationRequest {

    /**
     * 关联类型: REFERENCE(参考素材), OFFICIAL(正式素材), DRAFT(草稿素材)
     */
    @NotBlank(message = "关联类型不能为空")
    private String relationType;
}
