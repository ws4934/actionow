package com.actionow.canvas.dto.batch;

import com.actionow.canvas.dto.edge.CreateEdgeRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量创建边请求
 *
 * @author Actionow
 */
@Data
public class BatchEdgeCreateRequest {

    /**
     * 边创建请求列表
     */
    @NotEmpty(message = "边列表不能为空")
    @Size(max = 200, message = "单次批量创建最多200条边")
    @Valid
    private List<CreateEdgeRequest> edges;

    /**
     * 是否忽略错误继续（默认 false，遇到错误立即停止）
     */
    private boolean continueOnError = false;
}
