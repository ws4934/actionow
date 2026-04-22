package com.actionow.project.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 更新分镜请求
 * 所有字段均为可选，只更新传入的非空字段
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStoryboardRequest {

    /**
     * 分镜标题
     */
    @Size(max = 200, message = "标题不能超过200个字符")
    private String title;

    /**
     * 分镜描述/台词
     */
    @Size(max = 5000, message = "描述不能超过5000个字符")
    private String synopsis;

    /**
     * 时长（秒）
     */
    private Integer duration;

    /**
     * 视觉描述（仅镜头属性，不包含实体引用）
     */
    private Map<String, Object> visualDesc;

    /**
     * 音频描述（仅非实体属性）
     */
    private Map<String, Object> audioDesc;

    /**
     * 扩展信息 (JSON)
     */
    private Map<String, Object> extraInfo;

    /**
     * 视觉描述增量补丁 (JSON)
     * 与现有 visualDesc 做 merge（putAll），不会覆盖未传字段
     */
    private Map<String, Object> visualDescPatch;

    /**
     * 音频描述增量补丁 (JSON)
     * 与现有 audioDesc 做 merge（putAll），不会覆盖未传字段
     */
    private Map<String, Object> audioDescPatch;

    /**
     * 扩展信息增量补丁 (JSON)
     * 与现有 extraInfo 做 merge（putAll），不会覆盖未传字段
     */
    private Map<String, Object> extraInfoPatch;

    /**
     * 场景属性覆盖增量补丁 (JSON)
     * 与现有 sceneOverride 做 merge（putAll），不会覆盖未传字段
     */
    private Map<String, Object> sceneOverridePatch;

    /**
     * 封面素材ID
     */
    private String coverAssetId;

    /**
     * 状态: DRAFT, GENERATING, COMPLETED, FAILED
     */
    private String status;

    /**
     * 序号
     */
    private Integer sequence;

    /**
     * 保存模式
     * OVERWRITE - 覆盖当前版本
     * NEW_VERSION - 存为新版本（默认）
     * NEW_ENTITY - 另存为新实体
     */
    private String saveMode;

    // ==================== 实体关系操作 ====================

    /**
     * 设置场景ID（替换现有场景关系）
     */
    private String sceneId;

    /**
     * 场景属性覆盖
     */
    private Map<String, Object> sceneOverride;

    /**
     * 设置风格ID（替换现有风格关系）
     */
    private String styleId;

    /**
     * 替换角色列表（完全替换现有角色关系）
     * 如果为 null，不修改；如果为空列表，删除所有角色关系
     */
    private List<CreateStoryboardRequest.CharacterAppearance> characters;

    /**
     * 添加角色列表
     */
    private List<CreateStoryboardRequest.CharacterAppearance> addCharacters;

    /**
     * 移除角色ID列表
     */
    private List<String> removeCharacterIds;

    /**
     * 替换道具列表（完全替换现有道具关系）
     */
    private List<CreateStoryboardRequest.PropUsage> props;

    /**
     * 添加道具列表
     */
    private List<CreateStoryboardRequest.PropUsage> addProps;

    /**
     * 移除道具ID列表
     */
    private List<String> removePropIds;

    /**
     * 替换对白列表（完全替换现有对白关系）
     */
    private List<CreateStoryboardRequest.DialogueLine> dialogues;

    /**
     * 添加对白列表
     */
    private List<CreateStoryboardRequest.DialogueLine> addDialogues;

    /**
     * 移除对白（按角色ID移除该角色的所有对白）
     */
    private List<String> removeDialogueCharacterIds;
}
