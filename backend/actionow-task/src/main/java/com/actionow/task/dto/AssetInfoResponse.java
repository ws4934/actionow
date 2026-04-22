package com.actionow.task.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 素材信息响应
 * 从 Asset 服务获取
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetInfoResponse {

    /**
     * 素材 ID
     */
    private String id;

    /**
     * 工作空间 ID
     */
    private String workspaceId;

    /**
     * 素材名称
     */
    private String name;

    /**
     * 素材类型: IMAGE/VIDEO/AUDIO
     */
    private String assetType;

    /**
     * 生成状态
     */
    private String generationStatus;

    /**
     * 工作流 ID
     */
    private String workflowId;

    /**
     * 任务 ID
     */
    private String taskId;
}
