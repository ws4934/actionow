package com.actionow.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新成员角色请求
 *
 * @author Actionow
 */
@Data
public class UpdateMemberRoleRequest {

    /**
     * 新角色
     */
    @NotBlank(message = "角色不能为空")
    private String role;
}
