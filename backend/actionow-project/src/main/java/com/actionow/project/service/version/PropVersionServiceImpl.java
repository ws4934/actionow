package com.actionow.project.service.version;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.actionow.project.dto.version.PropVersionDetailResponse;
import com.actionow.project.entity.Prop;
import com.actionow.project.entity.version.PropVersion;
import com.actionow.project.mapper.PropMapper;
import com.actionow.project.mapper.version.PropVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PropVersionServiceImpl
        extends AbstractVersionServiceImpl<Prop, PropVersion, PropVersionDetailResponse> {

    private final PropMapper propMapper;
    private final PropVersionMapper propVersionMapper;

    private static final List<VersionFieldSpec<Prop, PropVersion>> FIELD_SPECS = List.of(
            VersionFieldSpec.of("scope", "范围",
                    Prop::getScope, PropVersion::getScope, PropVersion::setScope),
            VersionFieldSpec.of("scriptId", "剧本",
                    Prop::getScriptId, PropVersion::getScriptId, PropVersion::setScriptId),
            VersionFieldSpec.of("name", "名称",
                    Prop::getName, PropVersion::getName, PropVersion::setName),
            VersionFieldSpec.of("description", "描述",
                    Prop::getDescription, PropVersion::getDescription, PropVersion::setDescription),
            VersionFieldSpec.of("fixedDesc", "固定描述词",
                    Prop::getFixedDesc, PropVersion::getFixedDesc, PropVersion::setFixedDesc),
            VersionFieldSpec.of("propType", "道具类型",
                    Prop::getPropType, PropVersion::getPropType, PropVersion::setPropType),
            VersionFieldSpec.ofMap("appearanceData", "外观数据",
                    Prop::getAppearanceData, PropVersion::getAppearanceData, PropVersion::setAppearanceData),
            VersionFieldSpec.of("coverAssetId", "封面",
                    Prop::getCoverAssetId, PropVersion::getCoverAssetId, PropVersion::setCoverAssetId),
            VersionFieldSpec.ofMap("extraInfo", "扩展信息",
                    Prop::getExtraInfo, PropVersion::getExtraInfo, PropVersion::setExtraInfo)
    );

    @Override protected String getEntityType() { return "PROP"; }
    @Override protected String getEntityTypeName() { return "道具"; }
    @Override protected BaseMapper<Prop> getEntityMapper() { return propMapper; }
    @Override protected String getEntityCurrentVersionId(Prop e) { return e.getCurrentVersionId(); }
    @Override protected String getEntityId(Prop e) { return e.getId(); }
    @Override protected Integer getEntityVersionNumber(Prop e) { return e.getVersionNumber(); }
    @Override protected SFunction<Prop, Integer> entityVersionNumberSGetter() { return Prop::getVersionNumber; }

    @Override protected List<PropVersion> findVersionsByEntityId(String id) {
        return propVersionMapper.selectByPropId(id);
    }
    @Override protected PropVersion findVersionByNumber(String id, Integer n) {
        return propVersionMapper.selectByVersionNumber(id, n);
    }
    @Override protected PropVersion findVersionById(String id) {
        return propVersionMapper.selectById(id);
    }
    @Override protected int insertVersionWithAutoNumber(PropVersion v) {
        return propVersionMapper.insertWithAutoVersionNumber(v);
    }
    @Override protected Integer findVersionNumberById(String id) {
        return propVersionMapper.selectVersionNumberById(id);
    }

    @Override protected List<VersionFieldSpec<Prop, PropVersion>> getFieldSpecs() { return FIELD_SPECS; }

    @Override protected PropVersion newVersion() { return new PropVersion(); }

    @Override protected void linkVersionToEntity(PropVersion version, Prop entity) {
        version.setPropId(entity.getId());
        version.setWorkspaceId(entity.getWorkspaceId());
    }

    @Override protected LambdaUpdateWrapper<Prop> buildRestoreWrapperBase(
            String entityId, String userId, LocalDateTime now) {
        LambdaUpdateWrapper<Prop> w = new LambdaUpdateWrapper<>();
        w.eq(Prop::getId, entityId)
                .set(Prop::getUpdatedBy, userId)
                .set(Prop::getUpdatedAt, now);
        return w;
    }

    @Override protected LambdaUpdateWrapper<Prop> buildVersionMetaWrapperBase(
            String entityId, String versionId, Integer versionNumber) {
        LambdaUpdateWrapper<Prop> w = new LambdaUpdateWrapper<>();
        w.eq(Prop::getId, entityId)
                .set(Prop::getCurrentVersionId, versionId)
                .set(Prop::getVersionNumber, versionNumber);
        return w;
    }

    @Override
    protected PropVersionDetailResponse buildDetailResponse(PropVersion v, String currentVersionId) {
        return PropVersionDetailResponse.builder()
                .id(v.getId())
                .propId(v.getPropId())
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
                .propType(v.getPropType())
                .appearanceData(v.getAppearanceData())
                .coverAssetId(v.getCoverAssetId())
                .extraInfo(v.getExtraInfo())
                .build();
    }
}
