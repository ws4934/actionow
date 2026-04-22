package com.actionow.project.service.version;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.actionow.project.dto.version.StyleVersionDetailResponse;
import com.actionow.project.entity.Style;
import com.actionow.project.entity.version.StyleVersion;
import com.actionow.project.mapper.StyleMapper;
import com.actionow.project.mapper.version.StyleVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StyleVersionServiceImpl
        extends AbstractVersionServiceImpl<Style, StyleVersion, StyleVersionDetailResponse> {

    private final StyleMapper styleMapper;
    private final StyleVersionMapper styleVersionMapper;

    private static final List<VersionFieldSpec<Style, StyleVersion>> FIELD_SPECS = List.of(
            VersionFieldSpec.of("scope", "范围",
                    Style::getScope, StyleVersion::getScope, StyleVersion::setScope),
            VersionFieldSpec.of("scriptId", "剧本",
                    Style::getScriptId, StyleVersion::getScriptId, StyleVersion::setScriptId),
            VersionFieldSpec.of("name", "名称",
                    Style::getName, StyleVersion::getName, StyleVersion::setName),
            VersionFieldSpec.of("description", "描述",
                    Style::getDescription, StyleVersion::getDescription, StyleVersion::setDescription),
            VersionFieldSpec.of("fixedDesc", "固定描述词",
                    Style::getFixedDesc, StyleVersion::getFixedDesc, StyleVersion::setFixedDesc),
            VersionFieldSpec.ofMap("styleParams", "风格参数",
                    Style::getStyleParams, StyleVersion::getStyleParams, StyleVersion::setStyleParams),
            VersionFieldSpec.of("coverAssetId", "封面",
                    Style::getCoverAssetId, StyleVersion::getCoverAssetId, StyleVersion::setCoverAssetId),
            VersionFieldSpec.ofMap("extraInfo", "扩展信息",
                    Style::getExtraInfo, StyleVersion::getExtraInfo, StyleVersion::setExtraInfo)
    );

    @Override protected String getEntityType() { return "STYLE"; }
    @Override protected String getEntityTypeName() { return "风格"; }
    @Override protected BaseMapper<Style> getEntityMapper() { return styleMapper; }
    @Override protected String getEntityCurrentVersionId(Style e) { return e.getCurrentVersionId(); }
    @Override protected String getEntityId(Style e) { return e.getId(); }
    @Override protected Integer getEntityVersionNumber(Style e) { return e.getVersionNumber(); }
    @Override protected SFunction<Style, Integer> entityVersionNumberSGetter() { return Style::getVersionNumber; }

    @Override protected List<StyleVersion> findVersionsByEntityId(String id) {
        return styleVersionMapper.selectByStyleId(id);
    }
    @Override protected StyleVersion findVersionByNumber(String id, Integer n) {
        return styleVersionMapper.selectByVersionNumber(id, n);
    }
    @Override protected StyleVersion findVersionById(String id) {
        return styleVersionMapper.selectById(id);
    }
    @Override protected int insertVersionWithAutoNumber(StyleVersion v) {
        return styleVersionMapper.insertWithAutoVersionNumber(v);
    }
    @Override protected Integer findVersionNumberById(String id) {
        return styleVersionMapper.selectVersionNumberById(id);
    }

    @Override protected List<VersionFieldSpec<Style, StyleVersion>> getFieldSpecs() { return FIELD_SPECS; }

    @Override protected StyleVersion newVersion() { return new StyleVersion(); }

    @Override protected void linkVersionToEntity(StyleVersion version, Style entity) {
        version.setStyleId(entity.getId());
        version.setWorkspaceId(entity.getWorkspaceId());
    }

    @Override protected LambdaUpdateWrapper<Style> buildRestoreWrapperBase(
            String entityId, String userId, LocalDateTime now) {
        LambdaUpdateWrapper<Style> w = new LambdaUpdateWrapper<>();
        w.eq(Style::getId, entityId)
                .set(Style::getUpdatedBy, userId)
                .set(Style::getUpdatedAt, now);
        return w;
    }

    @Override protected LambdaUpdateWrapper<Style> buildVersionMetaWrapperBase(
            String entityId, String versionId, Integer versionNumber) {
        LambdaUpdateWrapper<Style> w = new LambdaUpdateWrapper<>();
        w.eq(Style::getId, entityId)
                .set(Style::getCurrentVersionId, versionId)
                .set(Style::getVersionNumber, versionNumber);
        return w;
    }

    @Override
    protected StyleVersionDetailResponse buildDetailResponse(StyleVersion v, String currentVersionId) {
        return StyleVersionDetailResponse.builder()
                .id(v.getId())
                .styleId(v.getStyleId())
                .versionNumber(v.getVersionNumber())
                .changeSummary(v.getChangeSummary())
                .createdBy(v.getCreatedBy())
                .createdAt(v.getCreatedAt())
                .isCurrent(v.getId().equals(currentVersionId))
                .scope(v.getScope())
                .scriptId(v.getScriptId())
                .name(v.getName())
                .description(v.getDescription())
                .fixedDesc(v.getFixedDesc())
                .styleParams(v.getStyleParams())
                .coverAssetId(v.getCoverAssetId())
                .extraInfo(v.getExtraInfo())
                .build();
    }
}
