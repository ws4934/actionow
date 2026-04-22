package com.actionow.project.service.version;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.actionow.project.dto.version.ScriptVersionDetailResponse;
import com.actionow.project.entity.Script;
import com.actionow.project.entity.version.ScriptVersion;
import com.actionow.project.mapper.ScriptMapper;
import com.actionow.project.mapper.version.ScriptVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptVersionServiceImpl
        extends AbstractVersionServiceImpl<Script, ScriptVersion, ScriptVersionDetailResponse> {

    private final ScriptMapper scriptMapper;
    private final ScriptVersionMapper scriptVersionMapper;

    private static final List<VersionFieldSpec<Script, ScriptVersion>> FIELD_SPECS = List.of(
            VersionFieldSpec.of("title", "标题",
                    Script::getTitle, ScriptVersion::getTitle, ScriptVersion::setTitle),
            VersionFieldSpec.of("status", "状态",
                    Script::getStatus, ScriptVersion::getStatus, ScriptVersion::setStatus),
            VersionFieldSpec.of("synopsis", "简介",
                    Script::getSynopsis, ScriptVersion::getSynopsis, ScriptVersion::setSynopsis),
            VersionFieldSpec.of("content", "内容",
                    Script::getContent, ScriptVersion::getContent, ScriptVersion::setContent),
            VersionFieldSpec.of("coverAssetId", "封面",
                    Script::getCoverAssetId, ScriptVersion::getCoverAssetId, ScriptVersion::setCoverAssetId),
            VersionFieldSpec.of("docAssetId", "文档",
                    Script::getDocAssetId, ScriptVersion::getDocAssetId, ScriptVersion::setDocAssetId),
            VersionFieldSpec.ofMap("extraInfo", "扩展信息",
                    Script::getExtraInfo, ScriptVersion::getExtraInfo, ScriptVersion::setExtraInfo)
    );

    @Override protected String getEntityType() { return "SCRIPT"; }
    @Override protected String getEntityTypeName() { return "剧本"; }
    @Override protected BaseMapper<Script> getEntityMapper() { return scriptMapper; }
    @Override protected String getEntityCurrentVersionId(Script e) { return e.getCurrentVersionId(); }
    @Override protected String getEntityId(Script e) { return e.getId(); }
    @Override protected Integer getEntityVersionNumber(Script e) { return e.getVersionNumber(); }
    @Override protected SFunction<Script, Integer> entityVersionNumberSGetter() { return Script::getVersionNumber; }

    @Override protected List<ScriptVersion> findVersionsByEntityId(String id) {
        return scriptVersionMapper.selectByScriptId(id);
    }
    @Override protected ScriptVersion findVersionByNumber(String id, Integer n) {
        return scriptVersionMapper.selectByVersionNumber(id, n);
    }
    @Override protected ScriptVersion findVersionById(String id) {
        return scriptVersionMapper.selectById(id);
    }
    @Override protected int insertVersionWithAutoNumber(ScriptVersion v) {
        return scriptVersionMapper.insertWithAutoVersionNumber(v);
    }
    @Override protected Integer findVersionNumberById(String id) {
        return scriptVersionMapper.selectVersionNumberById(id);
    }

    @Override protected List<VersionFieldSpec<Script, ScriptVersion>> getFieldSpecs() { return FIELD_SPECS; }

    @Override protected ScriptVersion newVersion() { return new ScriptVersion(); }

    @Override protected void linkVersionToEntity(ScriptVersion version, Script entity) {
        version.setScriptId(entity.getId());
        version.setWorkspaceId(entity.getWorkspaceId());
    }

    @Override protected LambdaUpdateWrapper<Script> buildRestoreWrapperBase(
            String entityId, String userId, LocalDateTime now) {
        LambdaUpdateWrapper<Script> w = new LambdaUpdateWrapper<>();
        w.eq(Script::getId, entityId)
                .set(Script::getUpdatedBy, userId)
                .set(Script::getUpdatedAt, now);
        return w;
    }

    @Override protected LambdaUpdateWrapper<Script> buildVersionMetaWrapperBase(
            String entityId, String versionId, Integer versionNumber) {
        LambdaUpdateWrapper<Script> w = new LambdaUpdateWrapper<>();
        w.eq(Script::getId, entityId)
                .set(Script::getCurrentVersionId, versionId)
                .set(Script::getVersionNumber, versionNumber);
        return w;
    }

    @Override
    protected ScriptVersionDetailResponse buildDetailResponse(ScriptVersion v, String currentVersionId) {
        return ScriptVersionDetailResponse.builder()
                .id(v.getId())
                .scriptId(v.getScriptId())
                .versionNumber(v.getVersionNumber())
                .changeSummary(v.getChangeSummary())
                .createdBy(v.getCreatedBy())
                .createdAt(v.getCreatedAt())
                .isCurrent(v.getId().equals(currentVersionId))
                .title(v.getTitle())
                .status(v.getStatus())
                .synopsis(v.getSynopsis())
                .content(v.getContent())
                .coverAssetId(v.getCoverAssetId())
                .docAssetId(v.getDocAssetId())
                .extraInfo(v.getExtraInfo())
                .build();
    }
}
