package com.actionow.agent.service;

import com.actionow.agent.dto.request.SkillCreateRequest;
import com.actionow.agent.dto.request.SkillUpdateRequest;
import com.actionow.agent.dto.response.SkillImportResult;
import com.actionow.agent.dto.response.SkillResponse;
import com.actionow.common.core.result.PageResult;

/**
 * Agent Skill 管理服务接口
 *
 * @author Actionow
 */
public interface AgentSkillService {

    /**
     * 创建 Skill
     */
    SkillResponse create(SkillCreateRequest request);

    /**
     * 更新 Skill
     */
    SkillResponse update(String name, SkillUpdateRequest request);

    /**
     * 软删除 Skill
     */
    void delete(String name);

    /**
     * 切换 Skill 启用状态
     */
    SkillResponse toggle(String name);

    /**
     * 根据名称获取 Skill（含 content 字段）
     */
    SkillResponse getByName(String name);

    /**
     * 以指定 workspace 视角获取 Skill（优先 WORKSPACE 级，回退 SYSTEM 级）。
     * 用于防止跨 workspace 读取他人私有 Skill 内容。
     */
    SkillResponse getByNameForWorkspace(String name, String workspaceId);

    /**
     * 分页查询（list 接口省略 content）
     */
    PageResult<SkillResponse> findPage(int page, int size, String keyword);

    /**
     * 重载本地缓存并广播 Redis Pub/Sub 通知。
     * 若配置了 SKILL_PACKAGE_URL，先从 OSS 下载 ZIP 包并 upsert 到数据库，再重载缓存。
     *
     * @return 重载后的 Skill 数量
     */
    int reload();

    // ==================== Workspace 级 Skill 管理 ====================

    /**
     * 创建 WORKSPACE 级 Skill（自动设置 scope/workspaceId/creatorId）
     */
    SkillResponse createForWorkspace(SkillCreateRequest request, String workspaceId, String userId);

    /**
     * 更新 WORKSPACE 级 Skill（校验 workspace 归属）
     */
    SkillResponse updateForWorkspace(String name, SkillUpdateRequest request, String workspaceId);

    /**
     * 软删除 WORKSPACE 级 Skill（校验 workspace 归属）
     */
    void deleteForWorkspace(String name, String workspaceId);

    /**
     * 切换 WORKSPACE 级 Skill 启用状态
     */
    SkillResponse toggleForWorkspace(String name, String workspaceId);

    /**
     * 分页查询 workspace 可见的 Skill（SYSTEM + 本 WORKSPACE）
     */
    PageResult<SkillResponse> findPageForWorkspace(int page, int size, String keyword, String workspaceId);

    // ==================== Skill 包导入 ====================

    /**
     * 导入 Skill ZIP 包（SYSTEM scope）
     *
     * @param zipBytes ZIP 文件字节数组
     * @return 导入结果
     */
    SkillImportResult importPackage(byte[] zipBytes);

    /**
     * 导入 Skill ZIP 包（WORKSPACE scope）
     *
     * @param zipBytes    ZIP 文件字节数组
     * @param workspaceId 目标工作空间 ID
     * @param userId      操作者 ID
     * @return 导入结果
     */
    SkillImportResult importPackageForWorkspace(byte[] zipBytes, String workspaceId, String userId);
}
