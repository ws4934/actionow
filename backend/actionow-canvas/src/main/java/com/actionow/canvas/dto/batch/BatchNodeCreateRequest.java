package com.actionow.canvas.dto.batch;

import com.actionow.canvas.dto.node.CreateNodeRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量创建节点请求
 *
 * @author Actionow
 */
@Data
public class BatchNodeCreateRequest {

    /**
     * 节点创建请求列表
     */
    @NotEmpty(message = "节点列表不能为空")
    @Size(max = 100, message = "单次批量创建最多100个节点")
    @Valid
    private List<CreateNodeRequest> nodes;

    /**
     * 是否忽略错误继续（默认 false，遇到错误立即停止）
     */
    private boolean continueOnError = false;
}
