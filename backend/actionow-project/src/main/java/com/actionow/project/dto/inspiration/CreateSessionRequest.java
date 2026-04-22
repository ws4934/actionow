package com.actionow.project.dto.inspiration;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建灵感会话请求
 *
 * @author Actionow
 */
@Data
public class CreateSessionRequest {

    @Size(max = 200, message = "标题不能超过200个字符")
    private String title;
}
