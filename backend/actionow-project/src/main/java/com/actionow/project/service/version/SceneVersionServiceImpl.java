package com.actionow.project.service.version;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.actionow.project.dto.version.SceneVersionDetailResponse;
import com.actionow.project.entity.Scene;
import com.actionow.project.entity.version.SceneVersion;
import com.actionow.project.mapper.SceneMapper;
import com.actionow.project.mapper.version.SceneVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SceneVersionServiceImpl
        extends AbstractVersionServiceImpl<Scene, SceneVersion, SceneVersionDetailResponse> {

    private final SceneMapper sceneMapper;
    private final SceneVersionMapper sceneVersionMapper;

    private static final List<VersionFieldSpec<Scene, SceneVersion>> FIELD_SPECS = List.of(
            VersionFieldSpec.of("scope", "范围",
                    Scene::getScope, SceneVersion::getScope, SceneVersion::setScope),
            VersionFieldSpec.of("sceneType", "场景类型",
                    Scene::getSceneType, SceneVersion::getSceneType, SceneVersion::setSceneType),
            VersionFieldSpec.of("scriptId", "剧本",
                    Scene::getScriptId, SceneVersion::getScriptId, SceneVersion::setScriptId),
            VersionFieldSpec.of("name", "名称",
                    Scene::getName, SceneVersion::getName, SceneVersion::setName),
            VersionFieldSpec.of("description", "描述",
                    Scene::getDescription, SceneVersion::getDescription, SceneVersion::setDescription),
            VersionFieldSpec.of("fixedDesc", "固定描述词",
                    Scene::getFixedDesc, SceneVersion::getFixedDesc, SceneVersion::setFixedDesc),
            VersionFieldSpec.ofMap("appearanceData", "外观数据",
                    Scene::getAppearanceData, SceneVersion::getAppearanceData, SceneVersion::setAppearanceData),
            VersionFieldSpec.of("coverAssetId", "封面",
                    Scene::getCoverAssetId, SceneVersion::getCoverAssetId, SceneVersion::setCoverAssetId),
            VersionFieldSpec.ofMap("extraInfo", "扩展信息",
                    Scene::getExtraInfo, SceneVersion::getExtraInfo, SceneVersion::setExtraInfo)
    );

    @Override protected String getEntityType() { return "SCENE"; }
    @Override protected String getEntityTypeName() { return "场景"; }
    @Override protected BaseMapper<Scene> getEntityMapper() { return sceneMapper; }
    @Override protected String getEntityCurrentVersionId(Scene e) { return e.getCurrentVersionId(); }
    @Override protected String getEntityId(Scene e) { return e.getId(); }
    @Override protected Integer getEntityVersionNumber(Scene e) { return e.getVersionNumber(); }
    @Override protected SFunction<Scene, Integer> entityVersionNumberSGetter() { return Scene::getVersionNumber; }

    @Override protected List<SceneVersion> findVersionsByEntityId(String id) {
        return sceneVersionMapper.selectBySceneId(id);
    }
    @Override protected SceneVersion findVersionByNumber(String id, Integer n) {
        return sceneVersionMapper.selectByVersionNumber(id, n);
    }
    @Override protected SceneVersion findVersionById(String id) {
        return sceneVersionMapper.selectById(id);
    }
    @Override protected int insertVersionWithAutoNumber(SceneVersion v) {
        return sceneVersionMapper.insertWithAutoVersionNumber(v);
    }
    @Override protected Integer findVersionNumberById(String id) {
        return sceneVersionMapper.selectVersionNumberById(id);
    }

    @Override protected List<VersionFieldSpec<Scene, SceneVersion>> getFieldSpecs() { return FIELD_SPECS; }

    @Override protected SceneVersion newVersion() { return new SceneVersion(); }

    @Override protected void linkVersionToEntity(SceneVersion version, Scene entity) {
        version.setSceneId(entity.getId());
        version.setWorkspaceId(entity.getWorkspaceId());
    }

    @Override protected LambdaUpdateWrapper<Scene> buildRestoreWrapperBase(
            String entityId, String userId, LocalDateTime now) {
        LambdaUpdateWrapper<Scene> w = new LambdaUpdateWrapper<>();
        w.eq(Scene::getId, entityId)
                .set(Scene::getUpdatedBy, userId)
                .set(Scene::getUpdatedAt, now);
        return w;
    }

    @Override protected LambdaUpdateWrapper<Scene> buildVersionMetaWrapperBase(
            String entityId, String versionId, Integer versionNumber) {
        LambdaUpdateWrapper<Scene> w = new LambdaUpdateWrapper<>();
        w.eq(Scene::getId, entityId)
                .set(Scene::getCurrentVersionId, versionId)
                .set(Scene::getVersionNumber, versionNumber);
        return w;
    }

    @Override
    protected SceneVersionDetailResponse buildDetailResponse(SceneVersion v, String currentVersionId) {
        return SceneVersionDetailResponse.builder()
                .id(v.getId())
                .sceneId(v.getSceneId())
                .versionNumber(v.getVersionNumber())
                .changeSummary(v.getChangeSummary())
                .createdBy(v.getCreatedBy())
                .createdAt(v.getCreatedAt())
                .isCurrent(v.getId().equals(currentVersionId))
                .scope(v.getScope())
                .sceneType(v.getSceneType())
                .scriptId(v.getScriptId())
                .name(v.getName())
                .description(v.getDescription())
                .fixedDesc(v.getFixedDesc())
                .appearanceData(v.getAppearanceData())
                .coverAssetId(v.getCoverAssetId())
                .extraInfo(v.getExtraInfo())
                .build();
    }
}
