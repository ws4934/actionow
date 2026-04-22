package com.actionow.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.project.dto.CharacterQueryRequest;
import com.actionow.project.dto.CreateCharacterRequest;
import com.actionow.project.dto.UpdateCharacterRequest;
import com.actionow.project.dto.CharacterDetailResponse;
import com.actionow.project.dto.CharacterListResponse;
import com.actionow.project.entity.Character;

import java.util.List;
import java.util.Optional;

/**
 * 角色服务接口
 *
 * @author Actionow
 */
public interface CharacterService {

    /**
     * 创建角色
     */
    CharacterDetailResponse create(CreateCharacterRequest request, String workspaceId, String userId);

    /**
     * 创建角色（可控制是否跳过 Canvas 同步）
     * 用于 Canvas 发起的创建，避免循环调用
     */
    CharacterDetailResponse create(CreateCharacterRequest request, String workspaceId, String userId, boolean skipCanvasSync);

    /**
     * 批量创建角色
     */
    List<CharacterDetailResponse> batchCreate(List<CreateCharacterRequest> requests, String workspaceId, String userId);

    /**
     * 更新角色
     */
    CharacterDetailResponse update(String characterId, UpdateCharacterRequest request, String userId);

    /**
     * 删除角色
     */
    void delete(String characterId, String userId);

    /**
     * 获取角色详情
     */
    CharacterDetailResponse getById(String characterId);

    /**
     * 根据ID查找角色实体（内部使用）
     * @return Optional，不存在或已删除返回 empty
     */
    Optional<Character> findById(String characterId);

    /**
     * 获取工作空间级角色
     */
    List<CharacterListResponse> listWorkspaceCharacters(String workspaceId);

    /**
     * 获取剧本级角色
     */
    List<CharacterListResponse> listScriptCharacters(String scriptId);

    /**
     * 获取剧本可用的所有角色
     */
    List<CharacterListResponse> listAvailableCharacters(String workspaceId, String scriptId);

    /**
     * 获取剧本可用的所有角色（支持模糊搜索）
     *
     * @param workspaceId 工作空间ID
     * @param scriptId 剧本ID
     * @param keyword 搜索关键词（匹配名称、描述）
     * @param limit 返回数量限制
     */
    List<CharacterListResponse> listAvailableCharacters(String workspaceId, String scriptId, String keyword, Integer limit);

    /**
     * 分页查询角色
     */
    Page<CharacterListResponse> queryCharacters(CharacterQueryRequest request, String workspaceId);

    /**
     * 设置角色封面
     */
    void setCover(String characterId, String assetId, String userId);

    /**
     * 设置语音种子
     */
    void setVoiceSeed(String characterId, String voiceSeedId, String userId);
}
