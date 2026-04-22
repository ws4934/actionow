package com.actionow.canvas.dto.view;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 视图数据请求
 * 用于获取特定视图下的节点和边数据
 *
 * 支持两种过滤模式：
 * 1. 类型过滤：通过 viewKey 按实体类型过滤（如 CHARACTER 视图显示所有角色和素材）
 * 2. 实体聚焦：通过 focusEntityType + focusEntityId 聚焦某个具体实体及其关联节点
 *
 * @author Actionow
 */
@Data
public class ViewDataRequest {

    /**
     * 画布ID
     */
    @NotBlank(message = "画布ID不能为空")
    private String canvasId;

    /**
     * 视图键（可选，不传则返回所有数据）
     * SCRIPT, EPISODE, STORYBOARD, CHARACTER, SCENE, PROP, ASSET
     */
    private String viewKey;

    /**
     * 聚焦实体类型（可选）
     * 与 focusEntityId 配合使用，用于显示某个特定实体及其关联节点
     * 支持: CHARACTER, SCENE, PROP, ASSET
     */
    private String focusEntityType;

    /**
     * 聚焦实体ID（可选）
     * 与 focusEntityType 配合使用
     * 例如：focusEntityType=CHARACTER, focusEntityId=xxx 表示只显示角色xxx及其关联素材
     */
    private String focusEntityId;

    /**
     * 关联深度（可选，默认1）
     * 1: 只显示直接关联的节点
     * 2: 显示二级关联（如角色->素材->素材的素材）
     */
    private Integer depth = 1;

    /**
     * 是否包含实体详情
     */
    private Boolean includeEntityDetail = true;

    /**
     * 额外筛选条件（预留扩展）
     */
    private Map<String, Object> filters;

    /**
     * 分页 - 偏移量
     */
    private Integer offset;

    /**
     * 分页 - 限制数量
     */
    private Integer limit;

    /**
     * 排序字段
     */
    private String sortBy;

    /**
     * 排序方向 ASC/DESC
     */
    private String sortDirection;
}
