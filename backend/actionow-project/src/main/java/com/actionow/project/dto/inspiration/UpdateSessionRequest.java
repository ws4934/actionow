package com.actionow.project.dto.inspiration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新灵感会话请求
 *
 * @author Actionow
 */
@Data
public class UpdateSessionRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过200个字符")
    private String title;
}
