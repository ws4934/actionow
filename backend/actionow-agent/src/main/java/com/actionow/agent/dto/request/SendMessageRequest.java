package com.actionow.agent.dto.request;

import com.actionow.agent.config.constant.AgentExecutionMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发送消息请求
 *
 * @author Actionow
 */
@Data
public class SendMessageRequest {

    /**
     * 用户消息内容
     */
    @NotBlank(message = "消息内容不能为空")
    private String message;

    /**
     * 附件 ID 列表（可选）
     */
    private List<String> attachmentIds;

    /**
     * 消息级 Skill 名称列表（可选）
     * 优先级：消息级 > 会话级 > 全部启用 Skill
     * null = 继承会话级配置；[] = 关闭所有 Skill（纯文本模式）
     */
    private List<String> skillNames;

    /**
     * 内联附件列表（可选）
     * 支持 base64 编码的图片、视频、音频、文档
     */
    @Valid
    @Size(max = 10, message = "附件数量不能超过 10 个")
    private List<InlineAttachment> attachments;

    /**
     * 是否使用流式响应（默认 false）
     */
    private boolean stream = false;

    /**
     * 执行模式（内部使用）。
     * 默认 CHAT；Mission 后台执行时显式传 MISSION。
     */
    private String executionMode = AgentExecutionMode.CHAT.name();

    /**
     * 当前 Mission ID（Mission 模式下使用）。
     */
    private String missionId;

    /**
     * 当前 Mission Step ID（Mission 模式下使用）。
     */
    private String missionStepId;

    // ============ 动态作用域 ============
    // 每次发送消息时可以携带当前的数据可见范围
    // 在同一个 Session 中，作用域可以动态变化

    /**
     * 作用域类型（可选，默认继承 Session 的作用域）
     * - global: 可访问当前用户/工作空间下的所有资源
     * - script: 仅可访问指定剧本及其子资源
     */
    private String scope;

    // ============ 上下文锚点 ============

    /**
     * 剧本 ID — 上下文信息（可选）
     */
    private String scriptId;

    /**
     * 章节 ID — 上下文信息（可选）
     */
    private String episodeId;

    /**
     * 分镜 ID — 上下文信息（可选）
     */
    private String storyboardId;

    /**
     * 角色 ID — 上下文信息（可选）
     */
    private String characterId;

    /**
     * 场景 ID — 上下文信息（可选）
     */
    private String sceneId;

    /**
     * 道具 ID — 上下文信息（可选）
     */
    private String propId;

    /**
     * 风格 ID — 上下文信息（可选）
     */
    private String styleId;

    /**
     * 素材 ID — 上下文信息（可选）
     */
    private String assetId;
}
