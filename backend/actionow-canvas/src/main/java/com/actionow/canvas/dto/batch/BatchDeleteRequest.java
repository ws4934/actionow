package com.actionow.canvas.dto.batch;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量删除请求
 *
 * @author Actionow
 */
@Data
public class BatchDeleteRequest {

    /**
     * 要删除的 ID 列表
     */
    @NotEmpty(message = "ID列表不能为空")
    @Size(max = 100, message = "单次批量删除最多100条")
    private List<String> ids;

    /**
     * 是否忽略错误继续（默认 true，跳过不存在的项）
     */
    private boolean continueOnError = true;
}
