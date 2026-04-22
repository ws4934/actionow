package com.actionow.project.controller;

import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.Result;
import com.actionow.common.core.result.ResultCode;
import com.actionow.common.security.annotation.IgnoreAuth;
import com.actionow.project.constant.ProjectConstants;
import com.actionow.project.dto.*;
import com.actionow.project.entity.*;
import com.actionow.project.entity.Character;
import com.actionow.project.mapper.*;
import com.actionow.project.dto.relation.CreateEntityAssetRelationRequest;
import com.actionow.project.dto.relation.EntityAssetRelationResponse;
import com.actionow.project.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Canvas 同步内部接口控制器
 * 供 Canvas 服务 Feign 调用，处理实体创建和更新
 *
 * @author Actionow
 */
@Slf4j
@RestController
@RequestMapping("/internal/project")
@RequiredArgsConstructor
@IgnoreAuth
public class CanvasInternalController {

    private final CharacterMapper characterMapper;
    private final SceneMapper sceneMapper;
    private final PropMapper propMapper;
    private final StyleMapper styleMapper;
    private final EpisodeMapper episodeMapper;
    private final StoryboardMapper storyboardMapper;
    private final ScriptMapper scriptMapper;
    private final AssetMapper assetMapper;

    private final CharacterService characterService;
    private final SceneService sceneService;
    private final PropService propService;
    private final StyleService styleService;
    private final EpisodeService episodeService;
    private final StoryboardService storyboardService;
    private final ScriptService scriptService;
    private final AssetService assetService;
    private final EntityAssetRelationService entityAssetRelationService;

    // ==================== Canvas 实体创建接口 ====================

    /**
     * 从 Canvas 创建实体
     */
    @PostMapping("/entities/create")
    public Result<CanvasEntityCreateResponse> createEntityFromCanvas(@RequestBody CanvasEntityCreateRequest request) {
        log.info("Canvas 创建实体请求: entityType={}, name={}, workspaceId={}",
                request.getEntityType(), request.getName(), request.getWorkspaceId());

        validateCreateRequest(request);

        CanvasEntityCreateResponse response = switch (request.getEntityType().toUpperCase()) {
            case ProjectConstants.EntityType.CHARACTER -> createCharacter(request);
            case ProjectConstants.EntityType.SCENE -> createScene(request);
            case ProjectConstants.EntityType.PROP -> createProp(request);
            case ProjectConstants.EntityType.STYLE -> createStyle(request);
            case ProjectConstants.EntityType.EPISODE -> createEpisode(request);
            case ProjectConstants.EntityType.STORYBOARD -> createStoryboard(request);
            case ProjectConstants.EntityType.SCRIPT -> createScript(request);
            case ProjectConstants.EntityType.ASSET -> createAsset(request);
            default -> throw new BusinessException(ResultCode.PARAM_INVALID.getCode(),
                    "不支持的实体类型: " + request.getEntityType());
        };

        log.info("Canvas 创建实体成功: entityType={}, entityId={}",
                response.getEntityType(), response.getEntityId());

        return Result.success(response);
    }

    /**
     * 批量创建实体
     */
    @PostMapping("/entities/batch-create")
    public Result<BatchEntityCreateResponse> batchCreateEntities(@RequestBody BatchEntityCreateRequest request) {
        log.info("Canvas 批量创建实体请求: count={}, transactional={}",
                request.getRequests() != null ? request.getRequests().size() : 0,
                request.getTransactional());

        if (request.getRequests() == null || request.getRequests().isEmpty()) {
            return Result.success(BatchEntityCreateResponse.builder()
                    .results(Collections.emptyList())
                    .successCount(0)
                    .failedCount(0)
                    .totalCount(0)
                    .build());
        }

        List<BatchEntityCreateResponse.EntityCreateResult> results = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;

        for (int i = 0; i < request.getRequests().size(); i++) {
            CanvasEntityCreateRequest createRequest = request.getRequests().get(i);
            try {
                String validationError = validateCreateRequestForBatch(createRequest);
                if (validationError != null) {
                    results.add(BatchEntityCreateResponse.EntityCreateResult.failed(i, createRequest.getEntityType(), validationError));
                    failedCount++;
                    continue;
                }

                CanvasEntityCreateResponse response = switch (createRequest.getEntityType().toUpperCase()) {
                    case ProjectConstants.EntityType.CHARACTER -> createCharacter(createRequest);
                    case ProjectConstants.EntityType.SCENE -> createScene(createRequest);
                    case ProjectConstants.EntityType.PROP -> createProp(createRequest);
                    case ProjectConstants.EntityType.STYLE -> createStyle(createRequest);
                    case ProjectConstants.EntityType.EPISODE -> createEpisode(createRequest);
                    case ProjectConstants.EntityType.STORYBOARD -> createStoryboard(createRequest);
                    case ProjectConstants.EntityType.SCRIPT -> createScript(createRequest);
                    case ProjectConstants.EntityType.ASSET -> createAsset(createRequest);
                    default -> throw new BusinessException(ResultCode.PARAM_INVALID.getCode(),
                            "不支持的实体类型: " + createRequest.getEntityType());
                };

                results.add(BatchEntityCreateResponse.EntityCreateResult.success(i, response));
                successCount++;
            } catch (Exception e) {
                log.warn("批量创建实体失败: index={}, entityType={}, error={}",
                        i, createRequest.getEntityType(), e.getMessage());
                results.add(BatchEntityCreateResponse.EntityCreateResult.failed(
                        i, createRequest.getEntityType(), e.getMessage()));
                failedCount++;

                if (Boolean.TRUE.equals(request.getTransactional())) {
                    throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(),
                            "批量创建失败，索引 " + i + ": " + e.getMessage());
                }
            }
        }

        log.info("Canvas 批量创建实体完成: total={}, success={}, failed={}",
                request.getRequests().size(), successCount, failedCount);

        return Result.success(BatchEntityCreateResponse.builder()
                .results(results)
                .successCount(successCount)
                .failedCount(failedCount)
                .totalCount(request.getRequests().size())
                .build());
    }

    /**
     * 从 Canvas 更新实体
     */
    @PutMapping("/entities/update")
    public Result<CanvasEntityUpdateResponse> updateEntityFromCanvas(@RequestBody CanvasEntityUpdateRequest request) {
        log.info("Canvas 更新实体请求: entityType={}, entityId={}, workspaceId={}",
                request.getEntityType(), request.getEntityId(), request.getWorkspaceId());

        validateUpdateRequest(request);

        CanvasEntityUpdateResponse response = switch (request.getEntityType().toUpperCase()) {
            case ProjectConstants.EntityType.CHARACTER -> updateCharacter(request);
            case ProjectConstants.EntityType.SCENE -> updateScene(request);
            case ProjectConstants.EntityType.PROP -> updateProp(request);
            case ProjectConstants.EntityType.STYLE -> updateStyle(request);
            case ProjectConstants.EntityType.EPISODE -> updateEpisode(request);
            case ProjectConstants.EntityType.STORYBOARD -> updateStoryboard(request);
            case ProjectConstants.EntityType.SCRIPT -> updateScript(request);
            case ProjectConstants.EntityType.ASSET -> updateAsset(request);
            default -> throw new BusinessException(ResultCode.PARAM_INVALID.getCode(),
                    "不支持的实体类型: " + request.getEntityType());
        };

        log.info("Canvas 更新实体成功: entityType={}, entityId={}",
                response.getEntityType(), response.getEntityId());

        return Result.success(response);
    }

    /**
     * 批量更新实体
     */
    @PostMapping("/entities/batch-update")
    public Result<BatchEntityUpdateResponse> batchUpdateEntities(@RequestBody BatchEntityUpdateRequest request) {
        log.info("Canvas 批量更新实体请求: count={}, transactional={}",
                request.getRequests() != null ? request.getRequests().size() : 0,
                request.getTransactional());

        if (request.getRequests() == null || request.getRequests().isEmpty()) {
            return Result.success(BatchEntityUpdateResponse.builder()
                    .results(Collections.emptyList())
                    .successCount(0)
                    .failedCount(0)
                    .totalCount(0)
                    .build());
        }

        List<BatchEntityUpdateResponse.EntityUpdateResult> results = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;

        for (int i = 0; i < request.getRequests().size(); i++) {
            CanvasEntityUpdateRequest updateRequest = request.getRequests().get(i);
            try {
                String validationError = validateUpdateRequestForBatch(updateRequest);
                if (validationError != null) {
                    results.add(BatchEntityUpdateResponse.EntityUpdateResult.failed(
                            i, updateRequest.getEntityId(), updateRequest.getEntityType(), validationError));
                    failedCount++;
                    continue;
                }

                CanvasEntityUpdateResponse response = switch (updateRequest.getEntityType().toUpperCase()) {
                    case ProjectConstants.EntityType.CHARACTER -> updateCharacter(updateRequest);
                    case ProjectConstants.EntityType.SCENE -> updateScene(updateRequest);
                    case ProjectConstants.EntityType.PROP -> updateProp(updateRequest);
                    case ProjectConstants.EntityType.STYLE -> updateStyle(updateRequest);
                    case ProjectConstants.EntityType.EPISODE -> updateEpisode(updateRequest);
                    case ProjectConstants.EntityType.STORYBOARD -> updateStoryboard(updateRequest);
                    case ProjectConstants.EntityType.SCRIPT -> updateScript(updateRequest);
                    case ProjectConstants.EntityType.ASSET -> updateAsset(updateRequest);
                    default -> throw new BusinessException(ResultCode.PARAM_INVALID.getCode(),
                            "不支持的实体类型: " + updateRequest.getEntityType());
                };

                if (response.isSuccess()) {
                    results.add(BatchEntityUpdateResponse.EntityUpdateResult.success(i, response));
                    successCount++;
                } else {
                    results.add(BatchEntityUpdateResponse.EntityUpdateResult.failed(
                            i, response.getEntityId(), response.getEntityType(), response.getErrorMessage()));
                    failedCount++;
                }
            } catch (Exception e) {
                log.warn("批量更新实体失败: index={}, entityType={}, entityId={}, error={}",
                        i, updateRequest.getEntityType(), updateRequest.getEntityId(), e.getMessage());
                results.add(BatchEntityUpdateResponse.EntityUpdateResult.failed(
                        i, updateRequest.getEntityId(), updateRequest.getEntityType(), e.getMessage()));
                failedCount++;

                if (Boolean.TRUE.equals(request.getTransactional())) {
                    throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(),
                            "批量更新失败，索引 " + i + ": " + e.getMessage());
                }
            }
        }

        log.info("Canvas 批量更新实体完成: total={}, success={}, failed={}",
                request.getRequests().size(), successCount, failedCount);

        return Result.success(BatchEntityUpdateResponse.builder()
                .results(results)
                .successCount(successCount)
                .failedCount(failedCount)
                .totalCount(request.getRequests().size())
                .build());
    }

    // ==================== 验证方法 ====================

    private void validateCreateRequest(CanvasEntityCreateRequest request) {
        if (!StringUtils.hasText(request.getEntityType())) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "entityType 不能为空");
        }
        if (!StringUtils.hasText(request.getName())) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "name 不能为空");
        }
        if (!StringUtils.hasText(request.getWorkspaceId())) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "workspaceId 不能为空");
        }
    }

    private String validateCreateRequestForBatch(CanvasEntityCreateRequest request) {
        if (!StringUtils.hasText(request.getEntityType())) {
            return "entityType 不能为空";
        }
        if (!StringUtils.hasText(request.getName())) {
            return "name 不能为空";
        }
        if (!StringUtils.hasText(request.getWorkspaceId())) {
            return "workspaceId 不能为空";
        }
        return null;
    }

    private void validateUpdateRequest(CanvasEntityUpdateRequest request) {
        if (!StringUtils.hasText(request.getEntityType())) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "entityType 不能为空");
        }
        if (!StringUtils.hasText(request.getEntityId())) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "entityId 不能为空");
        }
    }

    private String validateUpdateRequestForBatch(CanvasEntityUpdateRequest request) {
        if (!StringUtils.hasText(request.getEntityType())) {
            return "entityType 不能为空";
        }
        if (!StringUtils.hasText(request.getEntityId())) {
            return "entityId 不能为空";
        }
        return null;
    }

    // ==================== 创建方法 ====================

    private CanvasEntityCreateResponse createCharacter(CanvasEntityCreateRequest request) {
        CreateCharacterRequest createRequest = new CreateCharacterRequest();
        createRequest.setName(request.getName());
        createRequest.setDescription(request.getDescription());
        createRequest.setScope(request.getScope() != null ? request.getScope() : ProjectConstants.Scope.SCRIPT);
        createRequest.setScriptId(request.getScriptId());

        if (request.getExtraData() != null) {
            if (request.getExtraData().get("age") != null) {
                createRequest.setAge((Integer) request.getExtraData().get("age"));
            }
            if (request.getExtraData().get("gender") != null) {
                createRequest.setGender((String) request.getExtraData().get("gender"));
            }
            if (request.getExtraData().get("characterType") != null) {
                createRequest.setCharacterType((String) request.getExtraData().get("characterType"));
            }
            if (request.getExtraData().get("fixedDesc") != null) {
                createRequest.setFixedDesc((String) request.getExtraData().get("fixedDesc"));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> appearanceData = (Map<String, Object>) request.getExtraData().get("appearanceData");
            if (appearanceData != null) {
                createRequest.setAppearanceData(appearanceData);
            }
        }

        CharacterDetailResponse detail = characterService.create(
                createRequest, request.getWorkspaceId(), null, true);

        return CanvasEntityCreateResponse.builder()
                .entityId(detail.getId())
                .entityType(ProjectConstants.EntityType.CHARACTER)
                .name(detail.getName())
                .thumbnailUrl(detail.getCoverUrl())
                .build();
    }

    private CanvasEntityCreateResponse createScene(CanvasEntityCreateRequest request) {
        CreateSceneRequest createRequest = new CreateSceneRequest();
        createRequest.setName(request.getName());
        createRequest.setDescription(request.getDescription());
        createRequest.setScope(request.getScope() != null ? request.getScope() : ProjectConstants.Scope.SCRIPT);
        createRequest.setScriptId(request.getScriptId());

        if (request.getExtraData() != null) {
            if (request.getExtraData().get("fixedDesc") != null) {
                createRequest.setFixedDesc((String) request.getExtraData().get("fixedDesc"));
            }
            if (request.getExtraData().get("sceneType") != null) {
                createRequest.setSceneType((String) request.getExtraData().get("sceneType"));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> appearanceData = (Map<String, Object>) request.getExtraData().get("appearanceData");
            if (appearanceData != null) {
                createRequest.setAppearanceData(appearanceData);
            }
        }

        SceneDetailResponse detail = sceneService.create(
                createRequest, request.getWorkspaceId(), null, true);

        return CanvasEntityCreateResponse.builder()
                .entityId(detail.getId())
                .entityType(ProjectConstants.EntityType.SCENE)
                .name(detail.getName())
                .thumbnailUrl(null)
                .build();
    }

    private CanvasEntityCreateResponse createProp(CanvasEntityCreateRequest request) {
        CreatePropRequest createRequest = new CreatePropRequest();
        createRequest.setName(request.getName());
        createRequest.setDescription(request.getDescription());
        createRequest.setScope(request.getScope() != null ? request.getScope() : ProjectConstants.Scope.SCRIPT);
        createRequest.setScriptId(request.getScriptId());

        if (request.getExtraData() != null) {
            if (request.getExtraData().get("fixedDesc") != null) {
                createRequest.setFixedDesc((String) request.getExtraData().get("fixedDesc"));
            }
            if (request.getExtraData().get("propType") != null) {
                createRequest.setPropType((String) request.getExtraData().get("propType"));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> appearanceData = (Map<String, Object>) request.getExtraData().get("appearanceData");
            if (appearanceData != null) {
                createRequest.setAppearanceData(appearanceData);
            }
        }

        PropDetailResponse detail = propService.create(
                createRequest, request.getWorkspaceId(), null, true);

        return CanvasEntityCreateResponse.builder()
                .entityId(detail.getId())
                .entityType(ProjectConstants.EntityType.PROP)
                .name(detail.getName())
                .thumbnailUrl(null)
                .build();
    }

    private CanvasEntityCreateResponse createStyle(CanvasEntityCreateRequest request) {
        CreateStyleRequest createRequest = new CreateStyleRequest();
        createRequest.setName(request.getName());
        createRequest.setDescription(request.getDescription());
        createRequest.setScope(request.getScope() != null ? request.getScope() : ProjectConstants.Scope.SCRIPT);
        createRequest.setScriptId(request.getScriptId());

        if (request.getExtraData() != null) {
            if (request.getExtraData().get("fixedDesc") != null) {
                createRequest.setFixedDesc((String) request.getExtraData().get("fixedDesc"));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> styleParams = (Map<String, Object>) request.getExtraData().get("styleParams");
            if (styleParams != null) {
                createRequest.setStyleParams(styleParams);
            }
        }

        StyleDetailResponse detail = styleService.create(
                createRequest, request.getWorkspaceId(), null, true);

        return CanvasEntityCreateResponse.builder()
                .entityId(detail.getId())
                .entityType(ProjectConstants.EntityType.STYLE)
                .name(detail.getName())
                .thumbnailUrl(null)
                .build();
    }

    private CanvasEntityCreateResponse createEpisode(CanvasEntityCreateRequest request) {
        if (!StringUtils.hasText(request.getScriptId())) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "创建剧集需要 scriptId");
        }

        CreateEpisodeRequest createRequest = new CreateEpisodeRequest();
        createRequest.setTitle(request.getName());
        createRequest.setSynopsis(request.getDescription());

        if (request.getExtraData() != null) {
            if (request.getExtraData().get("sequence") != null) {
                createRequest.setSequence((Integer) request.getExtraData().get("sequence"));
            }
        }

        EpisodeDetailResponse detail = episodeService.create(
                request.getScriptId(), createRequest, null, true);

        return CanvasEntityCreateResponse.builder()
                .entityId(detail.getId())
                .entityType(ProjectConstants.EntityType.EPISODE)
                .name(detail.getTitle())
                .thumbnailUrl(null)
                .build();
    }

    private CanvasEntityCreateResponse createStoryboard(CanvasEntityCreateRequest request) {
        if (!StringUtils.hasText(request.getEpisodeId())) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "创建分镜需要 episodeId");
        }

        CreateStoryboardRequest createRequest = new CreateStoryboardRequest();
        createRequest.setTitle(request.getName());
        createRequest.setSynopsis(request.getDescription());

        if (request.getExtraData() != null) {
            if (request.getExtraData().get("sequence") != null) {
                createRequest.setSequence((Integer) request.getExtraData().get("sequence"));
            }
            if (request.getExtraData().get("duration") != null) {
                createRequest.setDuration((Integer) request.getExtraData().get("duration"));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> visualDesc = (Map<String, Object>) request.getExtraData().get("visualDesc");
            if (visualDesc != null) {
                createRequest.setVisualDesc(visualDesc);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> audioDesc = (Map<String, Object>) request.getExtraData().get("audioDesc");
            if (audioDesc != null) {
                createRequest.setAudioDesc(audioDesc);
            }
        }

        StoryboardDetailResponse detail = storyboardService.create(
                request.getEpisodeId(), createRequest, null, true);

        return CanvasEntityCreateResponse.builder()
                .entityId(detail.getId())
                .entityType(ProjectConstants.EntityType.STORYBOARD)
                .name(detail.getTitle())
                .thumbnailUrl(null)
                .build();
    }

    private CanvasEntityCreateResponse createScript(CanvasEntityCreateRequest request) {
        CreateScriptRequest createRequest = new CreateScriptRequest();
        createRequest.setTitle(request.getName());
        createRequest.setSynopsis(request.getDescription());

        if (request.getExtraData() != null) {
            if (request.getExtraData().get("coverAssetId") != null) {
                createRequest.setCoverAssetId((String) request.getExtraData().get("coverAssetId"));
            }
        }

        ScriptDetailResponse detail = scriptService.create(
                createRequest, request.getWorkspaceId(), null, true);

        return CanvasEntityCreateResponse.builder()
                .entityId(detail.getId())
                .entityType(ProjectConstants.EntityType.SCRIPT)
                .name(detail.getTitle())
                .thumbnailUrl(null)
                .build();
    }

    private CanvasEntityCreateResponse createAsset(CanvasEntityCreateRequest request) {
        com.actionow.project.dto.asset.CreateAssetRequest createRequest =
                com.actionow.project.dto.asset.CreateAssetRequest.builder()
                        .name(request.getName())
                        .description(request.getDescription())
                        .scope(request.getScope() != null ? request.getScope() : ProjectConstants.Scope.WORKSPACE)
                        .scriptId(request.getScriptId())
                        .build();

        if (request.getExtraData() != null) {
            if (request.getExtraData().get("assetType") != null) {
                createRequest.setAssetType((String) request.getExtraData().get("assetType"));
            }
            if (request.getExtraData().get("source") != null) {
                createRequest.setSource((String) request.getExtraData().get("source"));
            }
            if (request.getExtraData().get("fileName") != null) {
                createRequest.setFileName((String) request.getExtraData().get("fileName"));
            }
            if (request.getExtraData().get("fileSize") != null) {
                Object fileSizeObj = request.getExtraData().get("fileSize");
                if (fileSizeObj instanceof Number) {
                    createRequest.setFileSize(((Number) fileSizeObj).longValue());
                }
            }
            if (request.getExtraData().get("mimeType") != null) {
                createRequest.setMimeType((String) request.getExtraData().get("mimeType"));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> extraInfo = (Map<String, Object>) request.getExtraData().get("extraInfo");
            if (extraInfo != null) {
                createRequest.setExtraInfo(extraInfo);
            }
        }

        if (createRequest.getAssetType() == null) {
            createRequest.setAssetType("IMAGE");
        }

        com.actionow.project.dto.asset.AssetResponse detail = assetService.create(
                createRequest, request.getWorkspaceId(), null, true);

        return CanvasEntityCreateResponse.builder()
                .entityId(detail.getId())
                .entityType(ProjectConstants.EntityType.ASSET)
                .name(detail.getName())
                .thumbnailUrl(detail.getThumbnailUrl())
                .build();
    }

    // ==================== 更新方法 ====================

    private CanvasEntityUpdateResponse updateCharacter(CanvasEntityUpdateRequest request) {
        Character character = characterMapper.selectById(request.getEntityId());
        if (character == null) {
            return CanvasEntityUpdateResponse.builder()
                    .entityId(request.getEntityId())
                    .entityType(ProjectConstants.EntityType.CHARACTER)
                    .success(false)
                    .errorMessage("角色不存在")
                    .build();
        }

        if (StringUtils.hasText(request.getName())) {
            character.setName(request.getName());
        }
        if (request.getDescription() != null) {
            character.setDescription(request.getDescription());
        }

        Map<String, Object> extra = request.getExtraData();
        if (extra != null) {
            if (extra.containsKey("fixedDesc")) {
                character.setFixedDesc((String) extra.get("fixedDesc"));
            }
            if (extra.containsKey("age")) {
                Object ageObj = extra.get("age");
                if (ageObj instanceof Number) {
                    character.setAge(((Number) ageObj).intValue());
                }
            }
            if (extra.containsKey("gender")) {
                character.setGender((String) extra.get("gender"));
            }
            if (extra.containsKey("characterType")) {
                character.setCharacterType((String) extra.get("characterType"));
            }
            if (extra.containsKey("voiceSeedId")) {
                character.setVoiceSeedId((String) extra.get("voiceSeedId"));
            }
            if (extra.containsKey("coverAssetId")) {
                character.setCoverAssetId((String) extra.get("coverAssetId"));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> appearanceData = (Map<String, Object>) extra.get("appearanceData");
            if (appearanceData != null) {
                character.setAppearanceData(appearanceData);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> extraInfo = (Map<String, Object>) extra.get("extraInfo");
            if (extraInfo != null) {
                character.setExtraInfo(extraInfo);
            }
        }

        characterMapper.updateById(character);

        return CanvasEntityUpdateResponse.builder()
                .entityId(character.getId())
                .entityType(ProjectConstants.EntityType.CHARACTER)
                .name(character.getName())
                .thumbnailUrl(null)
                .success(true)
                .build();
    }

    private CanvasEntityUpdateResponse updateScene(CanvasEntityUpdateRequest request) {
        Scene scene = sceneMapper.selectById(request.getEntityId());
        if (scene == null) {
            return CanvasEntityUpdateResponse.builder()
                    .entityId(request.getEntityId())
                    .entityType(ProjectConstants.EntityType.SCENE)
                    .success(false)
                    .errorMessage("场景不存在")
                    .build();
        }

        if (StringUtils.hasText(request.getName())) {
            scene.setName(request.getName());
        }
        if (request.getDescription() != null) {
            scene.setDescription(request.getDescription());
        }

        Map<String, Object> extra = request.getExtraData();
        if (extra != null) {
            if (extra.containsKey("fixedDesc")) {
                scene.setFixedDesc((String) extra.get("fixedDesc"));
            }
            if (extra.containsKey("sceneType")) {
                scene.setSceneType((String) extra.get("sceneType"));
            }
            if (extra.containsKey("coverAssetId")) {
                scene.setCoverAssetId((String) extra.get("coverAssetId"));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> appearanceData = (Map<String, Object>) extra.get("appearanceData");
            if (appearanceData != null) {
                scene.setAppearanceData(appearanceData);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> extraInfo = (Map<String, Object>) extra.get("extraInfo");
            if (extraInfo != null) {
                scene.setExtraInfo(extraInfo);
            }
        }

        sceneMapper.updateById(scene);

        return CanvasEntityUpdateResponse.builder()
                .entityId(scene.getId())
                .entityType(ProjectConstants.EntityType.SCENE)
                .name(scene.getName())
                .success(true)
                .build();
    }

    private CanvasEntityUpdateResponse updateProp(CanvasEntityUpdateRequest request) {
        Prop prop = propMapper.selectById(request.getEntityId());
        if (prop == null) {
            return CanvasEntityUpdateResponse.builder()
                    .entityId(request.getEntityId())
                    .entityType(ProjectConstants.EntityType.PROP)
                    .success(false)
                    .errorMessage("道具不存在")
                    .build();
        }

        if (StringUtils.hasText(request.getName())) {
            prop.setName(request.getName());
        }
        if (request.getDescription() != null) {
            prop.setDescription(request.getDescription());
        }

        Map<String, Object> extra = request.getExtraData();
        if (extra != null) {
            if (extra.containsKey("fixedDesc")) {
                prop.setFixedDesc((String) extra.get("fixedDesc"));
            }
            if (extra.containsKey("propType")) {
                prop.setPropType((String) extra.get("propType"));
            }
            if (extra.containsKey("coverAssetId")) {
                prop.setCoverAssetId((String) extra.get("coverAssetId"));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> appearanceData = (Map<String, Object>) extra.get("appearanceData");
            if (appearanceData != null) {
                prop.setAppearanceData(appearanceData);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> extraInfo = (Map<String, Object>) extra.get("extraInfo");
            if (extraInfo != null) {
                prop.setExtraInfo(extraInfo);
            }
        }

        propMapper.updateById(prop);

        return CanvasEntityUpdateResponse.builder()
                .entityId(prop.getId())
                .entityType(ProjectConstants.EntityType.PROP)
                .name(prop.getName())
                .success(true)
                .build();
    }

    private CanvasEntityUpdateResponse updateStyle(CanvasEntityUpdateRequest request) {
        Style style = styleMapper.selectById(request.getEntityId());
        if (style == null) {
            return CanvasEntityUpdateResponse.builder()
                    .entityId(request.getEntityId())
                    .entityType(ProjectConstants.EntityType.STYLE)
                    .success(false)
                    .errorMessage("风格不存在")
                    .build();
        }

        if (StringUtils.hasText(request.getName())) {
            style.setName(request.getName());
        }
        if (request.getDescription() != null) {
            style.setDescription(request.getDescription());
        }

        Map<String, Object> extra = request.getExtraData();
        if (extra != null) {
            if (extra.containsKey("fixedDesc")) {
                style.setFixedDesc((String) extra.get("fixedDesc"));
            }
            if (extra.containsKey("coverAssetId")) {
                style.setCoverAssetId((String) extra.get("coverAssetId"));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> styleParams = (Map<String, Object>) extra.get("styleParams");
            if (styleParams != null) {
                style.setStyleParams(styleParams);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> extraInfo = (Map<String, Object>) extra.get("extraInfo");
            if (extraInfo != null) {
                style.setExtraInfo(extraInfo);
            }
        }

        styleMapper.updateById(style);

        return CanvasEntityUpdateResponse.builder()
                .entityId(style.getId())
                .entityType(ProjectConstants.EntityType.STYLE)
                .name(style.getName())
                .success(true)
                .build();
    }

    private CanvasEntityUpdateResponse updateEpisode(CanvasEntityUpdateRequest request) {
        Episode episode = episodeMapper.selectById(request.getEntityId());
        if (episode == null) {
            return CanvasEntityUpdateResponse.builder()
                    .entityId(request.getEntityId())
                    .entityType(ProjectConstants.EntityType.EPISODE)
                    .success(false)
                    .errorMessage("剧集不存在")
                    .build();
        }

        if (StringUtils.hasText(request.getName())) {
            episode.setTitle(request.getName());
        }
        if (request.getDescription() != null) {
            episode.setSynopsis(request.getDescription());
        }

        Map<String, Object> extra = request.getExtraData();
        if (extra != null) {
            if (extra.containsKey("sequence")) {
                Object seqObj = extra.get("sequence");
                if (seqObj instanceof Number) {
                    episode.setSequence(((Number) seqObj).intValue());
                }
            }
            if (extra.containsKey("status")) {
                episode.setStatus((String) extra.get("status"));
            }
            if (extra.containsKey("content")) {
                episode.setContent((String) extra.get("content"));
            }
            if (extra.containsKey("coverAssetId")) {
                episode.setCoverAssetId((String) extra.get("coverAssetId"));
            }
            if (extra.containsKey("docAssetId")) {
                episode.setDocAssetId((String) extra.get("docAssetId"));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> extraInfo = (Map<String, Object>) extra.get("extraInfo");
            if (extraInfo != null) {
                episode.setExtraInfo(extraInfo);
            }
        }

        episodeMapper.updateById(episode);

        return CanvasEntityUpdateResponse.builder()
                .entityId(episode.getId())
                .entityType(ProjectConstants.EntityType.EPISODE)
                .name(episode.getTitle())
                .success(true)
                .build();
    }

    private CanvasEntityUpdateResponse updateStoryboard(CanvasEntityUpdateRequest request) {
        Storyboard storyboard = storyboardMapper.selectById(request.getEntityId());
        if (storyboard == null) {
            return CanvasEntityUpdateResponse.builder()
                    .entityId(request.getEntityId())
                    .entityType(ProjectConstants.EntityType.STORYBOARD)
                    .success(false)
                    .errorMessage("分镜不存在")
                    .build();
        }

        if (StringUtils.hasText(request.getName())) {
            storyboard.setTitle(request.getName());
        }
        if (request.getDescription() != null) {
            storyboard.setSynopsis(request.getDescription());
        }

        Map<String, Object> extra = request.getExtraData();
        if (extra != null) {
            if (extra.containsKey("sequence")) {
                Object seqObj = extra.get("sequence");
                if (seqObj instanceof Number) {
                    storyboard.setSequence(((Number) seqObj).intValue());
                }
            }
            if (extra.containsKey("status")) {
                storyboard.setStatus((String) extra.get("status"));
            }
            if (extra.containsKey("duration")) {
                Object durObj = extra.get("duration");
                if (durObj instanceof Number) {
                    storyboard.setDuration(((Number) durObj).intValue());
                }
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> visualDesc = (Map<String, Object>) extra.get("visualDesc");
            if (visualDesc != null) {
                storyboard.setVisualDesc(visualDesc);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> audioDesc = (Map<String, Object>) extra.get("audioDesc");
            if (audioDesc != null) {
                storyboard.setAudioDesc(audioDesc);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> extraInfo = (Map<String, Object>) extra.get("extraInfo");
            if (extraInfo != null) {
                storyboard.setExtraInfo(extraInfo);
            }
        }

        storyboardMapper.updateById(storyboard);

        return CanvasEntityUpdateResponse.builder()
                .entityId(storyboard.getId())
                .entityType(ProjectConstants.EntityType.STORYBOARD)
                .name(storyboard.getTitle())
                .success(true)
                .build();
    }

    private CanvasEntityUpdateResponse updateScript(CanvasEntityUpdateRequest request) {
        Script script = scriptMapper.selectById(request.getEntityId());
        if (script == null) {
            return CanvasEntityUpdateResponse.builder()
                    .entityId(request.getEntityId())
                    .entityType(ProjectConstants.EntityType.SCRIPT)
                    .success(false)
                    .errorMessage("剧本不存在")
                    .build();
        }

        if (StringUtils.hasText(request.getName())) {
            script.setTitle(request.getName());
        }
        if (request.getDescription() != null) {
            script.setSynopsis(request.getDescription());
        }

        Map<String, Object> extra = request.getExtraData();
        if (extra != null) {
            if (extra.containsKey("status")) {
                script.setStatus((String) extra.get("status"));
            }
            if (extra.containsKey("content")) {
                script.setContent((String) extra.get("content"));
            }
            if (extra.containsKey("coverAssetId")) {
                script.setCoverAssetId((String) extra.get("coverAssetId"));
            }
            if (extra.containsKey("docAssetId")) {
                script.setDocAssetId((String) extra.get("docAssetId"));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> extraInfo = (Map<String, Object>) extra.get("extraInfo");
            if (extraInfo != null) {
                script.setExtraInfo(extraInfo);
            }
        }

        scriptMapper.updateById(script);

        return CanvasEntityUpdateResponse.builder()
                .entityId(script.getId())
                .entityType(ProjectConstants.EntityType.SCRIPT)
                .name(script.getTitle())
                .success(true)
                .build();
    }

    private CanvasEntityUpdateResponse updateAsset(CanvasEntityUpdateRequest request) {
        Asset asset = assetMapper.selectById(request.getEntityId());
        if (asset == null) {
            return CanvasEntityUpdateResponse.builder()
                    .entityId(request.getEntityId())
                    .entityType(ProjectConstants.EntityType.ASSET)
                    .success(false)
                    .errorMessage("素材不存在")
                    .build();
        }

        if (StringUtils.hasText(request.getName())) {
            asset.setName(request.getName());
        }
        if (request.getDescription() != null) {
            asset.setDescription(request.getDescription());
        }
        if (request.getThumbnailUrl() != null) {
            asset.setThumbnailUrl(request.getThumbnailUrl());
        }

        Map<String, Object> extra = request.getExtraData();
        if (extra != null) {
            if (extra.containsKey("assetType")) {
                asset.setAssetType((String) extra.get("assetType"));
            }
            if (extra.containsKey("source")) {
                asset.setSource((String) extra.get("source"));
            }
            if (extra.containsKey("generationStatus")) {
                asset.setGenerationStatus((String) extra.get("generationStatus"));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> metaInfo = (Map<String, Object>) extra.get("metaInfo");
            if (metaInfo != null) {
                asset.setMetaInfo(metaInfo);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> extraInfo = (Map<String, Object>) extra.get("extraInfo");
            if (extraInfo != null) {
                asset.setExtraInfo(extraInfo);
            }
        }

        assetMapper.updateById(asset);

        return CanvasEntityUpdateResponse.builder()
                .entityId(asset.getId())
                .entityType(ProjectConstants.EntityType.ASSET)
                .name(asset.getName())
                .thumbnailUrl(asset.getThumbnailUrl())
                .success(true)
                .build();
    }

    // ==================== 删除方法 ====================

    /**
     * 从 Canvas 删除实体
     */
    @DeleteMapping("/entities/{entityType}/{entityId}")
    public Result<Void> deleteEntityFromCanvas(
            @PathVariable String entityType,
            @PathVariable String entityId) {
        log.info("Canvas 删除实体请求: entityType={}, entityId={}", entityType, entityId);

        if (!StringUtils.hasText(entityType)) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "entityType 不能为空");
        }
        if (!StringUtils.hasText(entityId)) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "entityId 不能为空");
        }

        switch (entityType.toUpperCase()) {
            case ProjectConstants.EntityType.CHARACTER -> characterService.delete(entityId, null);
            case ProjectConstants.EntityType.SCENE -> sceneService.delete(entityId, null);
            case ProjectConstants.EntityType.PROP -> propService.delete(entityId, null);
            case ProjectConstants.EntityType.STYLE -> styleService.delete(entityId, null);
            case ProjectConstants.EntityType.EPISODE -> episodeService.delete(entityId, null);
            case ProjectConstants.EntityType.STORYBOARD -> storyboardService.delete(entityId, null);
            case ProjectConstants.EntityType.SCRIPT -> scriptService.delete(entityId, null);
            case ProjectConstants.EntityType.ASSET -> assetService.delete(entityId, null);
            default -> throw new BusinessException(ResultCode.PARAM_INVALID.getCode(),
                    "不支持的实体类型: " + entityType);
        }

        log.info("Canvas 删除实体成功: entityType={}, entityId={}", entityType, entityId);

        return Result.success();
    }

    // ==================== EntityAssetRelation 管理接口 ====================

    /**
     * 从 Canvas 创建实体-素材关联
     * 当在 Canvas 中创建 ASSET 类型边（实体 -> 素材）时，调用此接口同步创建关联记录
     *
     * @param request 创建关联请求
     * @return 创建的关联信息
     */
    @PostMapping("/entity-asset-relations/create")
    public Result<EntityAssetRelationResponse> createEntityAssetRelation(
            @RequestBody CreateEntityAssetRelationRequest request) {
        log.info("Canvas 创建实体素材关联请求: entityType={}, entityId={}, assetId={}, relationType={}",
                request.getEntityType(), request.getEntityId(), request.getAssetId(), request.getRelationType());

        // 参数校验
        if (!StringUtils.hasText(request.getEntityType())) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "entityType 不能为空");
        }
        if (!StringUtils.hasText(request.getEntityId())) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "entityId 不能为空");
        }
        if (!StringUtils.hasText(request.getAssetId())) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "assetId 不能为空");
        }
        if (!StringUtils.hasText(request.getRelationType())) {
            // 默认关联类型为 DRAFT
            request.setRelationType("DRAFT");
        }

        // 检查关联是否已存在
        boolean exists = entityAssetRelationService.existsRelation(
                request.getEntityType(), request.getEntityId(),
                request.getAssetId(), request.getRelationType());
        if (exists) {
            log.info("Canvas 实体素材关联已存在，跳过创建: entityType={}, entityId={}, assetId={}",
                    request.getEntityType(), request.getEntityId(), request.getAssetId());
            return Result.success(null);
        }

        // 创建关联（workspaceId 和 userId 由服务层从上下文获取或设为 null）
        // 使用 skipCanvasSync=true 避免循环调用（Canvas -> Project -> Canvas）
        EntityAssetRelationResponse response = entityAssetRelationService.createRelation(
                request, null, null, true);

        log.info("Canvas 创建实体素材关联成功: relationId={}, entityType={}, entityId={}, assetId={}",
                response.getId(), request.getEntityType(), request.getEntityId(), request.getAssetId());

        return Result.success(response);
    }
}
