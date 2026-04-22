package com.actionow.project.service.version;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.actionow.project.dto.version.AssetVersionDetailResponse;
import com.actionow.project.entity.Asset;
import com.actionow.project.entity.version.AssetVersion;
import com.actionow.project.mapper.AssetMapper;
import com.actionow.project.mapper.version.AssetVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetVersionServiceImpl
        extends AbstractVersionServiceImpl<Asset, AssetVersion, AssetVersionDetailResponse> {

    private final AssetMapper assetMapper;
    private final AssetVersionMapper assetVersionMapper;

    private static final List<VersionFieldSpec<Asset, AssetVersion>> FIELD_SPECS = List.of(
            VersionFieldSpec.of("scope", "范围",
                    Asset::getScope, AssetVersion::getScope, AssetVersion::setScope),
            VersionFieldSpec.of("scriptId", "剧本",
                    Asset::getScriptId, AssetVersion::getScriptId, AssetVersion::setScriptId),
            VersionFieldSpec.of("name", "名称",
                    Asset::getName, AssetVersion::getName, AssetVersion::setName),
            VersionFieldSpec.of("description", "描述",
                    Asset::getDescription, AssetVersion::getDescription, AssetVersion::setDescription),
            VersionFieldSpec.of("assetType", "素材类型",
                    Asset::getAssetType, AssetVersion::getAssetType, AssetVersion::setAssetType),
            VersionFieldSpec.of("source", "来源",
                    Asset::getSource, AssetVersion::getSource, AssetVersion::setSource),
            VersionFieldSpec.of("fileKey", "文件路径",
                    Asset::getFileKey, AssetVersion::getFileKey, AssetVersion::setFileKey),
            VersionFieldSpec.of("fileUrl", "文件URL",
                    Asset::getFileUrl, AssetVersion::getFileUrl, AssetVersion::setFileUrl),
            VersionFieldSpec.of("thumbnailUrl", "缩略图",
                    Asset::getThumbnailUrl, AssetVersion::getThumbnailUrl, AssetVersion::setThumbnailUrl),
            VersionFieldSpec.of("fileSize", "文件大小",
                    Asset::getFileSize, AssetVersion::getFileSize, AssetVersion::setFileSize),
            VersionFieldSpec.of("mimeType", "MIME类型",
                    Asset::getMimeType, AssetVersion::getMimeType, AssetVersion::setMimeType),
            VersionFieldSpec.ofMap("metaInfo", "元数据",
                    Asset::getMetaInfo, AssetVersion::getMetaInfo, AssetVersion::setMetaInfo),
            VersionFieldSpec.ofMap("extraInfo", "扩展信息",
                    Asset::getExtraInfo, AssetVersion::getExtraInfo, AssetVersion::setExtraInfo),
            VersionFieldSpec.of("generationStatus", "生成状态",
                    Asset::getGenerationStatus, AssetVersion::getGenerationStatus, AssetVersion::setGenerationStatus),
            VersionFieldSpec.of("workflowId", "工作流",
                    Asset::getWorkflowId, AssetVersion::getWorkflowId, AssetVersion::setWorkflowId),
            VersionFieldSpec.of("taskId", "任务",
                    Asset::getTaskId, AssetVersion::getTaskId, AssetVersion::setTaskId)
    );

    @Override protected String getEntityType() { return "ASSET"; }
    @Override protected String getEntityTypeName() { return "素材"; }
    @Override protected BaseMapper<Asset> getEntityMapper() { return assetMapper; }
    @Override protected String getEntityCurrentVersionId(Asset e) { return e.getCurrentVersionId(); }
    @Override protected String getEntityId(Asset e) { return e.getId(); }
    @Override protected Integer getEntityVersionNumber(Asset e) { return e.getVersionNumber(); }
    @Override protected SFunction<Asset, Integer> entityVersionNumberSGetter() { return Asset::getVersionNumber; }

    @Override protected List<AssetVersion> findVersionsByEntityId(String id) {
        return assetVersionMapper.selectByAssetId(id);
    }
    @Override protected AssetVersion findVersionByNumber(String id, Integer n) {
        return assetVersionMapper.selectByVersionNumber(id, n);
    }
    @Override protected AssetVersion findVersionById(String id) {
        return assetVersionMapper.selectById(id);
    }
    @Override protected int insertVersionWithAutoNumber(AssetVersion v) {
        return assetVersionMapper.insertWithAutoVersionNumber(v);
    }
    @Override protected Integer findVersionNumberById(String id) {
        return assetVersionMapper.selectVersionNumberById(id);
    }

    @Override protected List<VersionFieldSpec<Asset, AssetVersion>> getFieldSpecs() { return FIELD_SPECS; }

    @Override protected AssetVersion newVersion() { return new AssetVersion(); }

    @Override protected void linkVersionToEntity(AssetVersion version, Asset entity) {
        version.setAssetId(entity.getId());
        version.setWorkspaceId(entity.getWorkspaceId());
    }

    @Override protected LambdaUpdateWrapper<Asset> buildRestoreWrapperBase(
            String entityId, String userId, LocalDateTime now) {
        LambdaUpdateWrapper<Asset> w = new LambdaUpdateWrapper<>();
        w.eq(Asset::getId, entityId)
                .set(Asset::getUpdatedBy, userId)
                .set(Asset::getUpdatedAt, now);
        return w;
    }

    @Override protected LambdaUpdateWrapper<Asset> buildVersionMetaWrapperBase(
            String entityId, String versionId, Integer versionNumber) {
        LambdaUpdateWrapper<Asset> w = new LambdaUpdateWrapper<>();
        w.eq(Asset::getId, entityId)
                .set(Asset::getCurrentVersionId, versionId)
                .set(Asset::getVersionNumber, versionNumber);
        return w;
    }

    @Override
    protected AssetVersionDetailResponse buildDetailResponse(AssetVersion v, String currentVersionId) {
        return AssetVersionDetailResponse.builder()
                .id(v.getId())
                .assetId(v.getAssetId())
                .versionNumber(v.getVersionNumber())
                .changeSummary(v.getChangeSummary())
                .createdBy(v.getCreatedBy())
                .createdAt(v.getCreatedAt())
                .isCurrent(v.getId().equals(currentVersionId))
                .scope(v.getScope())
                .scriptId(v.getScriptId())
                .name(v.getName())
                .description(v.getDescription())
                .assetType(v.getAssetType())
                .source(v.getSource())
                .fileKey(v.getFileKey())
                .fileUrl(v.getFileUrl())
                .thumbnailUrl(v.getThumbnailUrl())
                .fileSize(v.getFileSize())
                .mimeType(v.getMimeType())
                .metaInfo(v.getMetaInfo())
                .extraInfo(v.getExtraInfo())
                .generationStatus(v.getGenerationStatus())
                .workflowId(v.getWorkflowId())
                .taskId(v.getTaskId())
                .build();
    }
}
