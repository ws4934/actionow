package com.actionow.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.common.core.constant.CommonConstants;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.id.UuidGenerator;
import com.actionow.common.core.result.ResultCode;
import com.actionow.project.constant.ProjectConstants;
import com.actionow.project.constant.EntityDefaults;
import com.actionow.project.dto.CharacterQueryRequest;
import com.actionow.project.dto.CharacterDetailResponse;
import com.actionow.project.dto.CharacterListResponse;
import com.actionow.project.dto.CreateCharacterRequest;
import com.actionow.project.dto.UpdateCharacterRequest;
import com.actionow.project.dto.asset.AssetResponse;
import com.actionow.project.entity.Character;
import com.actionow.project.entity.EntityAssetRelation;
import com.actionow.project.mapper.CharacterMapper;
import com.actionow.project.mapper.EntityAssetRelationMapper;
import com.actionow.project.service.AssetService;
import com.actionow.project.service.CharacterService;
import com.actionow.project.service.UserInfoHelper;
import com.actionow.project.service.version.VersionService;
import com.actionow.project.dto.version.CharacterVersionDetailResponse;
import com.actionow.project.feign.UserBasicInfo;
import com.actionow.common.mq.publisher.CanvasMessagePublisher;
import com.actionow.common.mq.message.CollabEntityChangeEvent;
import com.actionow.project.publisher.EntityChangeEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterServiceImpl implements CharacterService {

    private final CharacterMapper characterMapper;
    private final UserInfoHelper userInfoHelper;
    private final CanvasMessagePublisher canvasMessagePublisher;
    private final EntityChangeEventPublisher entityChangeEventPublisher;
    private final VersionService<Character, CharacterVersionDetailResponse> characterVersionService;
    private final AssetService assetService;
    private final EntityAssetRelationMapper entityAssetRelationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CharacterDetailResponse create(CreateCharacterRequest request, String workspaceId, String userId) {
        return create(request, workspaceId, userId, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CharacterDetailResponse create(CreateCharacterRequest request, String workspaceId, String userId, boolean skipCanvasSync) {
        Character character = new Character();
        character.setId(UuidGenerator.generateUuidV7());
        character.setWorkspaceId(workspaceId);
        character.setScope(request.getScope());
        character.setScriptId(request.getScriptId());
        character.setName(request.getName());
        character.setDescription(request.getDescription());
        character.setFixedDesc(request.getFixedDesc());
        character.setAge(request.getAge());
        character.setGender(request.getGender());
        character.setCharacterType(request.getCharacterType());
        // 合并 appearanceData，确保包含完整的默认结构
        character.setAppearanceData(EntityDefaults.mergeCharacterAppearanceData(request.getAppearanceData()));
        character.setVersion(1);
        character.setCreatedBy(userId);

        characterMapper.insert(character);

        // 创建初始版本快照 (V1)
        characterVersionService.createVersionSnapshot(character, "创建角色", userId);

        log.info("角色创建成功: characterId={}, name={}, scope={}, skipCanvasSync={}",
                character.getId(), character.getName(), character.getScope(), skipCanvasSync);

        // 发布Canvas同步消息（仅剧本级角色，且未跳过同步）
        if (!skipCanvasSync && ProjectConstants.Scope.SCRIPT.equals(character.getScope()) && character.getScriptId() != null) {
            canvasMessagePublisher.publishEntityChange(
                    ProjectConstants.EntityType.CHARACTER,
                    character.getId(),
                    character.getScriptId(),
                    ProjectConstants.EntityType.SCRIPT,
                    character.getScriptId(),
                    workspaceId,
                    "CREATED",
                    toEntityDataMap(character)
            );
        }

        // 发布协作事件（仅剧本级角色）
        if (ProjectConstants.Scope.SCRIPT.equals(character.getScope()) && character.getScriptId() != null) {
            entityChangeEventPublisher.publishEntityCreated(
                    CollabEntityChangeEvent.EntityType.CHARACTER,
                    character.getId(),
                    character.getScriptId(),
                    toEntityDataMap(character)
            );
        }

        return CharacterDetailResponse.fromEntity(character);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<CharacterDetailResponse> batchCreate(List<CreateCharacterRequest> requests, String workspaceId, String userId) {
        List<CharacterDetailResponse> responses = new java.util.ArrayList<>();
        for (CreateCharacterRequest request : requests) {
            responses.add(create(request, workspaceId, userId, true));
        }
        log.info("批量创建角色成功: workspaceId={}, count={}", workspaceId, requests.size());
        return responses;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CharacterDetailResponse update(String characterId, UpdateCharacterRequest request, String userId) {
        Character character = getCharacterOrThrow(characterId);

        // 获取保存模式，默认为 NEW_VERSION
        String saveMode = request.getSaveMode();
        if (saveMode == null) {
            saveMode = ProjectConstants.SaveMode.NEW_VERSION;
        }

        // NEW_ENTITY 模式：另存为新实体
        if (ProjectConstants.SaveMode.NEW_ENTITY.equals(saveMode)) {
            CreateCharacterRequest newRequest = new CreateCharacterRequest();
            newRequest.setScope(character.getScope());
            newRequest.setScriptId(character.getScriptId());
            newRequest.setName(request.getName() != null ? request.getName() : character.getName());
            newRequest.setDescription(request.getDescription() != null ? request.getDescription() : character.getDescription());
            newRequest.setFixedDesc(request.getFixedDesc() != null ? request.getFixedDesc() : character.getFixedDesc());
            newRequest.setAge(request.getAge() != null ? request.getAge() : character.getAge());
            newRequest.setGender(request.getGender() != null ? request.getGender() : character.getGender());
            newRequest.setCharacterType(request.getCharacterType() != null ? request.getCharacterType() : character.getCharacterType());
            newRequest.setVoiceSeedId(request.getVoiceSeedId() != null ? request.getVoiceSeedId() : character.getVoiceSeedId());
            newRequest.setAppearanceData(request.getAppearanceData() != null ? request.getAppearanceData() : character.getAppearanceData());
            newRequest.setCoverAssetId(request.getCoverAssetId() != null ? request.getCoverAssetId() : character.getCoverAssetId());
            newRequest.setExtraInfo(request.getExtraInfo() != null ? request.getExtraInfo() : character.getExtraInfo());
            return create(newRequest, character.getWorkspaceId(), userId);
        }

        // 检测实际变更，生成变更摘要
        StringBuilder changes = new StringBuilder();
        if (request.getName() != null && !request.getName().equals(character.getName())) {
            changes.append("名称");
        }
        if (request.getDescription() != null && !request.getDescription().equals(character.getDescription())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("描述");
        }
        if (request.getFixedDesc() != null && !request.getFixedDesc().equals(character.getFixedDesc())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("固定描述");
        }
        if (request.getAge() != null && !request.getAge().equals(character.getAge())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("年龄");
        }
        if (request.getGender() != null && !request.getGender().equals(character.getGender())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("性别");
        }
        if (request.getCharacterType() != null && !request.getCharacterType().equals(character.getCharacterType())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("角色类型");
        }
        if (request.getVoiceSeedId() != null && !request.getVoiceSeedId().equals(character.getVoiceSeedId())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("语音种子");
        }
        if (request.getAppearanceData() != null && !request.getAppearanceData().equals(character.getAppearanceData())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("外观数据");
        }
        if (request.getCoverAssetId() != null && !request.getCoverAssetId().equals(character.getCoverAssetId())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("封面");
        }
        if (request.getExtraInfo() != null && !request.getExtraInfo().equals(character.getExtraInfo())) {
            if (!changes.isEmpty()) changes.append("、");
            changes.append("扩展信息");
        }

        // 1. 先更新数据
        if (request.getName() != null) {
            character.setName(request.getName());
        }
        if (request.getDescription() != null) {
            character.setDescription(request.getDescription());
        }
        if (request.getFixedDesc() != null) {
            character.setFixedDesc(request.getFixedDesc());
        }
        if (request.getAge() != null) {
            character.setAge(request.getAge());
        }
        if (request.getGender() != null) {
            character.setGender(request.getGender());
        }
        if (request.getCharacterType() != null) {
            character.setCharacterType(request.getCharacterType());
        }
        if (request.getVoiceSeedId() != null) {
            character.setVoiceSeedId(request.getVoiceSeedId());
        }
        if (request.getAppearanceData() != null) {
            character.setAppearanceData(request.getAppearanceData());
        }
        // appearanceDataPatch: merge 语义（优先级高于 appearanceData 整块替换）
        if (request.getAppearanceDataPatch() != null && !request.getAppearanceDataPatch().isEmpty()) {
            Map<String, Object> merged = character.getAppearanceData() != null
                    ? new HashMap<>(character.getAppearanceData()) : new HashMap<>();
            merged.putAll(request.getAppearanceDataPatch());
            character.setAppearanceData(merged);
        }
        if (request.getCoverAssetId() != null) {
            character.setCoverAssetId(request.getCoverAssetId());
        }
        if (request.getExtraInfo() != null) {
            character.setExtraInfo(request.getExtraInfo());
        }
        // extraInfoPatch: merge 语义
        if (request.getExtraInfoPatch() != null && !request.getExtraInfoPatch().isEmpty()) {
            Map<String, Object> merged = character.getExtraInfo() != null
                    ? new HashMap<>(character.getExtraInfo()) : new HashMap<>();
            merged.putAll(request.getExtraInfoPatch());
            character.setExtraInfo(merged);
        }
        character.setUpdatedBy(userId);
        // 不要手动设置 version，MyBatis Plus @Version 会自动处理

        int rows = characterMapper.updateById(character);
        if (rows == 0) {
            log.warn("角色更新失败（并发冲突）: characterId={}, version={}", characterId, character.getVersion());
            throw new BusinessException(ResultCode.CONCURRENT_OPERATION);
        }

        // 2. NEW_VERSION 模式：仅在有实际变更时创建版本快照（保存更新后的数据）
        // OVERWRITE 模式：跳过版本快照创建
        if (ProjectConstants.SaveMode.NEW_VERSION.equals(saveMode) && !changes.isEmpty()) {
            String changeSummary = "更新" + changes;
            character = getCharacterOrThrow(characterId);
            characterVersionService.createVersionSnapshot(character, changeSummary, userId);
        }

        log.info("角色更新成功: characterId={}, versionNumber={}, saveMode={}", characterId, character.getVersionNumber(), saveMode);

        // 发布Canvas同步消息（仅剧本级角色）
        if (ProjectConstants.Scope.SCRIPT.equals(character.getScope()) && character.getScriptId() != null) {
            canvasMessagePublisher.publishEntityChange(
                    ProjectConstants.EntityType.CHARACTER,
                    character.getId(),
                    character.getScriptId(),
                    ProjectConstants.EntityType.SCRIPT,
                    character.getScriptId(),
                    character.getWorkspaceId(),
                    "UPDATED",
                    toEntityDataMap(character)
            );

            // 发布协作事件
            entityChangeEventPublisher.publishEntityUpdated(
                    CollabEntityChangeEvent.EntityType.CHARACTER,
                    character.getId(),
                    character.getScriptId(),
                    null,
                    toEntityDataMap(character)
            );
        }

        return CharacterDetailResponse.fromEntity(character);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String characterId, String userId) {
        // 先验证角色存在
        Character character = getCharacterOrThrow(characterId);
        // 使用 MyBatis-Plus 的 deleteById，会自动转换为逻辑删除（UPDATE SET deleted=1）
        characterMapper.deleteById(characterId);

        log.info("角色删除成功: characterId={}", characterId);

        // 发布Canvas同步消息（仅剧本级角色）
        if (ProjectConstants.Scope.SCRIPT.equals(character.getScope()) && character.getScriptId() != null) {
            canvasMessagePublisher.publishEntityChange(
                    ProjectConstants.EntityType.CHARACTER,
                    character.getId(),
                    character.getScriptId(),
                    ProjectConstants.EntityType.SCRIPT,
                    character.getScriptId(),
                    character.getWorkspaceId(),
                    "DELETED",
                    null
            );

            // 发布协作事件
            entityChangeEventPublisher.publishEntityDeleted(
                    CollabEntityChangeEvent.EntityType.CHARACTER,
                    character.getId(),
                    character.getScriptId()
            );
        }
    }

    @Override
    public CharacterDetailResponse getById(String characterId) {
        Character character = getCharacterOrThrow(characterId);
        CharacterDetailResponse response = CharacterDetailResponse.fromEntity(character);
        // 填充创建者信息
        if (character.getCreatedBy() != null) {
            UserBasicInfo userInfo = userInfoHelper.getUserInfo(character.getCreatedBy());
            if (userInfo != null) {
                response.setCreatedByUsername(userInfo.getUsername());
                response.setCreatedByNickname(userInfo.getNickname());
            }
        }
        // 填充封面URL
        if (character.getCoverAssetId() != null) {
            try {
                var asset = assetService.getById(character.getCoverAssetId());
                response.setCoverUrl(asset.getThumbnailUrl() != null ? asset.getThumbnailUrl() : asset.getFileUrl());
            } catch (Exception e) {
                log.warn("获取角色封面素材失败: characterId={}, coverAssetId={}", characterId, character.getCoverAssetId());
            }
        }
        // 填充音色URL
        try {
            List<EntityAssetRelation> voiceRelations = entityAssetRelationMapper.selectVoiceRelationsByEntities(
                    ProjectConstants.EntityType.CHARACTER, List.of(characterId));
            if (!voiceRelations.isEmpty()) {
                EntityAssetRelation voiceRelation = voiceRelations.get(0);
                AssetResponse voiceAsset = assetService.getById(voiceRelation.getAssetId());
                response.setVoiceAssetId(voiceRelation.getAssetId());
                response.setVoiceUrl(voiceAsset.getFileUrl());
            }
        } catch (Exception e) {
            log.warn("获取角色音色素材失败: characterId={}", characterId);
        }
        return response;
    }

    @Override
    public Optional<Character> findById(String characterId) {
        Character character = characterMapper.selectById(characterId);
        if (character == null || character.getDeleted() == CommonConstants.DELETED) {
            return Optional.empty();
        }
        return Optional.of(character);
    }

    @Override
    public List<CharacterListResponse> listWorkspaceCharacters(String workspaceId) {
        List<Character> characters = characterMapper.selectWorkspaceCharacters(workspaceId);
        return convertToListResponses(characters);
    }

    @Override
    public List<CharacterListResponse> listScriptCharacters(String scriptId) {
        List<Character> characters = characterMapper.selectScriptCharacters(scriptId);
        return convertToListResponses(characters);
    }

    @Override
    public List<CharacterListResponse> listAvailableCharacters(String workspaceId, String scriptId) {
        List<Character> characters = characterMapper.selectAvailableCharacters(workspaceId, scriptId);
        return convertToListResponses(characters);
    }

    @Override
    public List<CharacterListResponse> listAvailableCharacters(String workspaceId, String scriptId, String keyword, Integer limit) {
        List<Character> characters = characterMapper.selectAvailableCharactersFiltered(
                workspaceId, scriptId, keyword, limit);
        return convertToListResponses(characters);
    }

    @Override
    public Page<CharacterListResponse> queryCharacters(CharacterQueryRequest request, String workspaceId) {
        // SYSTEM 作用域：从 tenant_system schema 查询，内存分页
        if ("SYSTEM".equalsIgnoreCase(request.getScope())) {
            return querySystemCharacters(request);
        }

        // 跨 schema 可用角色查询：scriptId 非空且未指定具体 scope 时，合并 SYSTEM+WORKSPACE+SCRIPT
        if (StringUtils.hasText(request.getScriptId())
                && !StringUtils.hasText(request.getScope())) {
            int offset = (request.getPageNum() - 1) * request.getPageSize();
            List<Character> records = characterMapper.selectAvailableCharactersPaginated(
                    workspaceId, request.getScriptId(), request.getKeyword(),
                    request.getCharacterType(), request.getGender(),
                    request.getPageSize(), offset);
            long total = characterMapper.countAvailableCharacters(
                    workspaceId, request.getScriptId(), request.getKeyword(),
                    request.getCharacterType(), request.getGender());
            Page<CharacterListResponse> responsePage = new Page<>(request.getPageNum(), request.getPageSize(), total);
            responsePage.setRecords(convertToListResponses(records));
            return responsePage;
        }

        Page<Character> page = new Page<>(request.getPageNum(), request.getPageSize());

        LambdaQueryWrapper<Character> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Character::getWorkspaceId, workspaceId)
                .eq(Character::getDeleted, CommonConstants.NOT_DELETED);

        // 作用域过滤
        if (StringUtils.hasText(request.getScope())) {
            wrapper.eq(Character::getScope, request.getScope());
        }
        // 剧本ID过滤
        if (StringUtils.hasText(request.getScriptId())) {
            wrapper.eq(Character::getScriptId, request.getScriptId());
        }
        // 角色类型过滤
        if (StringUtils.hasText(request.getCharacterType())) {
            wrapper.eq(Character::getCharacterType, request.getCharacterType());
        }
        // 性别过滤
        if (StringUtils.hasText(request.getGender())) {
            wrapper.eq(Character::getGender, request.getGender());
        }
        // 关键词搜索（名称、描述、固定描述、外观数据、附加信息）
        if (StringUtils.hasText(request.getKeyword())) {
            String kw = "%" + request.getKeyword() + "%";
            wrapper.and(w -> w.like(Character::getName, request.getKeyword())
                    .or()
                    .like(Character::getDescription, request.getKeyword())
                    .or()
                    .like(Character::getFixedDesc, request.getKeyword())
                    .or()
                    .apply("appearance_data::text LIKE {0}", kw)
                    .or()
                    .apply("extra_info::text LIKE {0}", kw));
        }

        // 排序
        String orderBy = request.getOrderBy();
        boolean isAsc = "asc".equalsIgnoreCase(request.getOrderDir());
        switch (orderBy) {
            case "name" -> wrapper.orderBy(true, isAsc, Character::getName);
            case "updated_at" -> wrapper.orderBy(true, isAsc, Character::getUpdatedAt);
            default -> wrapper.orderBy(true, isAsc, Character::getCreatedAt);
        }

        Page<Character> resultPage = characterMapper.selectPage(page, wrapper);

        // 转换为响应对象
        Page<CharacterListResponse> responsePage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        responsePage.setRecords(convertToListResponses(resultPage.getRecords()));

        return responsePage;
    }

    private Page<CharacterListResponse> querySystemCharacters(CharacterQueryRequest request) {
        List<Character> all = new ArrayList<>(characterMapper.selectSystemCharacters());

        if (StringUtils.hasText(request.getKeyword())) {
            String kw = request.getKeyword().toLowerCase();
            all = all.stream()
                    .filter(c -> (c.getName() != null && c.getName().toLowerCase().contains(kw))
                            || (c.getDescription() != null && c.getDescription().toLowerCase().contains(kw)))
                    .collect(Collectors.toList());
        }
        if (StringUtils.hasText(request.getCharacterType())) {
            all = all.stream()
                    .filter(c -> request.getCharacterType().equalsIgnoreCase(c.getCharacterType()))
                    .collect(Collectors.toList());
        }
        if (StringUtils.hasText(request.getGender())) {
            all = all.stream()
                    .filter(c -> request.getGender().equalsIgnoreCase(c.getGender()))
                    .collect(Collectors.toList());
        }

        boolean isAsc = !"desc".equalsIgnoreCase(request.getOrderDir());
        Comparator<Character> comparator = switch (request.getOrderBy()) {
            case "name" -> Comparator.comparing((Character c) -> c.getName() != null ? c.getName() : "");
            case "updated_at" -> Comparator.comparing((Character c) ->
                    c.getUpdatedAt() != null ? c.getUpdatedAt() : java.time.LocalDateTime.MIN);
            default -> Comparator.comparing((Character c) ->
                    c.getCreatedAt() != null ? c.getCreatedAt() : java.time.LocalDateTime.MIN);
        };
        all.sort(isAsc ? comparator : comparator.reversed());

        long total = all.size();
        int from = (request.getPageNum() - 1) * request.getPageSize();
        int to = Math.min(from + request.getPageSize(), (int) total);
        List<Character> pageData = from >= total ? List.of() : all.subList(from, to);

        Page<CharacterListResponse> responsePage = new Page<>(request.getPageNum(), request.getPageSize(), total);
        responsePage.setRecords(convertToListResponses(pageData));
        return responsePage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setCover(String characterId, String assetId, String userId) {
        Character character = getCharacterOrThrow(characterId);
        character.setCoverAssetId(assetId);
        characterMapper.updateById(character);

        log.info("角色封面设置成功: characterId={}, assetId={}", characterId, assetId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setVoiceSeed(String characterId, String voiceSeedId, String userId) {
        Character character = getCharacterOrThrow(characterId);
        character.setVoiceSeedId(voiceSeedId);
        characterMapper.updateById(character);

        log.info("角色语音种子设置成功: characterId={}, voiceSeedId={}", characterId, voiceSeedId);
    }

    private Character getCharacterOrThrow(String characterId) {
        Character character = characterMapper.selectById(characterId);
        if (character == null || character.getDeleted() == CommonConstants.DELETED) {
            throw new BusinessException(ResultCode.CHARACTER_NOT_FOUND);
        }
        return character;
    }

    /**
     * 转换为列表响应并批量填充用户信息和封面URL
     */
    private List<CharacterListResponse> convertToListResponses(List<Character> characters) {
        if (characters == null || characters.isEmpty()) {
            return List.of();
        }

        List<CharacterListResponse> responses = characters.stream()
                .map(CharacterListResponse::fromEntity)
                .collect(Collectors.toList());

        // 批量填充用户信息
        populateUserInfo(responses);
        // 批量填充封面URL
        populateCoverUrl(characters, responses);
        // 批量填充音色URL
        populateVoiceUrl(characters, responses);
        return responses;
    }

    private void populateUserInfo(List<CharacterListResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return;
        }
        Set<String> userIds = responses.stream()
                .map(CharacterListResponse::getCreatedBy)
                .filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return;
        }
        Map<String, UserBasicInfo> userInfoMap = userInfoHelper.batchGetUserInfo(userIds);
        responses.forEach(response -> {
            UserBasicInfo userInfo = userInfoMap.get(response.getCreatedBy());
            if (userInfo != null) {
                response.setCreatedByUsername(userInfo.getUsername());
                response.setCreatedByNickname(userInfo.getNickname());
            }
        });
    }

    private void populateCoverUrl(List<Character> characters, List<CharacterListResponse> responses) {
        // 收集所有有效的封面素材ID
        Set<String> coverAssetIds = characters.stream()
                .map(Character::getCoverAssetId)
                .filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toSet());
        if (coverAssetIds.isEmpty()) {
            return;
        }
        // 批量获取素材信息
        try {
            var assets = assetService.batchGet(new java.util.ArrayList<>(coverAssetIds));
            Map<String, String> assetUrlMap = assets.stream()
                    .filter(a -> a.getThumbnailUrl() != null || a.getFileUrl() != null)
                    .collect(Collectors.toMap(
                            a -> a.getId(),
                            a -> a.getThumbnailUrl() != null ? a.getThumbnailUrl() : a.getFileUrl(),
                            (a, b) -> a
                    ));
            // 填充到响应中
            for (int i = 0; i < characters.size(); i++) {
                String coverAssetId = characters.get(i).getCoverAssetId();
                if (coverAssetId != null && assetUrlMap.containsKey(coverAssetId)) {
                    responses.get(i).setCoverUrl(assetUrlMap.get(coverAssetId));
                }
            }
        } catch (Exception e) {
            log.warn("批量获取角色封面素材失败", e);
        }
    }

    private void populateVoiceUrl(List<Character> characters, List<CharacterListResponse> responses) {
        List<String> entityIds = characters.stream()
                .map(Character::getId)
                .collect(Collectors.toList());
        if (entityIds.isEmpty()) {
            return;
        }
        try {
            List<EntityAssetRelation> voiceRelations = entityAssetRelationMapper.selectVoiceRelationsByEntities(
                    ProjectConstants.EntityType.CHARACTER, entityIds);
            if (voiceRelations.isEmpty()) {
                return;
            }
            // 批量获取音色素材
            List<String> voiceAssetIds = voiceRelations.stream()
                    .map(EntityAssetRelation::getAssetId)
                    .distinct()
                    .collect(Collectors.toList());
            List<AssetResponse> voiceAssets = assetService.batchGet(voiceAssetIds);
            Map<String, String> voiceUrlMap = voiceAssets.stream()
                    .filter(a -> a.getFileUrl() != null)
                    .collect(Collectors.toMap(AssetResponse::getId, AssetResponse::getFileUrl, (a, b) -> a));
            // 构建 entityId -> voiceRelation 映射
            Map<String, EntityAssetRelation> entityVoiceMap = voiceRelations.stream()
                    .collect(Collectors.toMap(EntityAssetRelation::getEntityId, r -> r, (a, b) -> a));
            // 填充到响应中
            for (int i = 0; i < characters.size(); i++) {
                EntityAssetRelation voiceRelation = entityVoiceMap.get(characters.get(i).getId());
                if (voiceRelation != null) {
                    responses.get(i).setVoiceAssetId(voiceRelation.getAssetId());
                    String voiceUrl = voiceUrlMap.get(voiceRelation.getAssetId());
                    if (voiceUrl != null) {
                        responses.get(i).setVoiceUrl(voiceUrl);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("批量获取角色音色素材失败", e);
        }
    }

    /**
     * 将角色实体转换为 Canvas 缓存数据
     */
    private Map<String, Object> toEntityDataMap(Character character) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("name", character.getName());
        data.put("description", character.getDescription());
        data.put("version", character.getVersion());
        data.put("coverAssetId", character.getCoverAssetId());
        return data;
    }
}
