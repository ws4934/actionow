package com.actionow.project.dto.asset;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 更新素材请求 DTO
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAssetRequest {

    /**
     * 素材名称
     */
    @Size(max = 500, message = "素材名称不能超过500个字符")
    private String name;

    /**
     * 描述
     */
    @Size(max = 2000, message = "描述不能超过2000个字符")
    private String description;

    /**
     * 扩展信息
     */
    private Map<String, Object> extraInfo;

    /**
     * 变更摘要（用于版本记录）
     */
    private String changeSummary;
}
