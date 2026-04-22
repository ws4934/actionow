package com.actionow.project.service.version;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.actionow.project.dto.version.EpisodeVersionDetailResponse;
import com.actionow.project.entity.Episode;
import com.actionow.project.entity.version.EpisodeVersion;
import com.actionow.project.mapper.EpisodeMapper;
import com.actionow.project.mapper.version.EpisodeVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EpisodeVersionServiceImpl
        extends AbstractVersionServiceImpl<Episode, EpisodeVersion, EpisodeVersionDetailResponse> {

    private final EpisodeMapper episodeMapper;
    private final EpisodeVersionMapper episodeVersionMapper;

    private static final List<VersionFieldSpec<Episode, EpisodeVersion>> FIELD_SPECS = List.of(
            VersionFieldSpec.of("scriptId", "剧本",
                    Episode::getScriptId, EpisodeVersion::getScriptId, EpisodeVersion::setScriptId),
            VersionFieldSpec.of("title", "标题",
                    Episode::getTitle, EpisodeVersion::getTitle, EpisodeVersion::setTitle),
            VersionFieldSpec.of("sequence", "序号",
                    Episode::getSequence, EpisodeVersion::getSequence, EpisodeVersion::setSequence),
            VersionFieldSpec.of("status", "状态",
                    Episode::getStatus, EpisodeVersion::getStatus, EpisodeVersion::setStatus),
            VersionFieldSpec.of("synopsis", "简介",
                    Episode::getSynopsis, EpisodeVersion::getSynopsis, EpisodeVersion::setSynopsis),
            VersionFieldSpec.of("content", "内容",
                    Episode::getContent, EpisodeVersion::getContent, EpisodeVersion::setContent),
            VersionFieldSpec.of("coverAssetId", "封面",
                    Episode::getCoverAssetId, EpisodeVersion::getCoverAssetId, EpisodeVersion::setCoverAssetId),
            VersionFieldSpec.of("docAssetId", "文档",
                    Episode::getDocAssetId, EpisodeVersion::getDocAssetId, EpisodeVersion::setDocAssetId),
            VersionFieldSpec.ofMap("extraInfo", "扩展信息",
                    Episode::getExtraInfo, EpisodeVersion::getExtraInfo, EpisodeVersion::setExtraInfo)
    );

    @Override protected String getEntityType() { return "EPISODE"; }
    @Override protected String getEntityTypeName() { return "剧集"; }
    @Override protected BaseMapper<Episode> getEntityMapper() { return episodeMapper; }
    @Override protected String getEntityCurrentVersionId(Episode e) { return e.getCurrentVersionId(); }
    @Override protected String getEntityId(Episode e) { return e.getId(); }
    @Override protected Integer getEntityVersionNumber(Episode e) { return e.getVersionNumber(); }
    @Override protected SFunction<Episode, Integer> entityVersionNumberSGetter() { return Episode::getVersionNumber; }

    @Override protected List<EpisodeVersion> findVersionsByEntityId(String id) {
        return episodeVersionMapper.selectByEpisodeId(id);
    }
    @Override protected EpisodeVersion findVersionByNumber(String id, Integer n) {
        return episodeVersionMapper.selectByVersionNumber(id, n);
    }
    @Override protected EpisodeVersion findVersionById(String id) {
        return episodeVersionMapper.selectById(id);
    }
    @Override protected int insertVersionWithAutoNumber(EpisodeVersion v) {
        return episodeVersionMapper.insertWithAutoVersionNumber(v);
    }
    @Override protected Integer findVersionNumberById(String id) {
        return episodeVersionMapper.selectVersionNumberById(id);
    }

    @Override protected List<VersionFieldSpec<Episode, EpisodeVersion>> getFieldSpecs() { return FIELD_SPECS; }

    @Override protected EpisodeVersion newVersion() { return new EpisodeVersion(); }

    @Override protected void linkVersionToEntity(EpisodeVersion version, Episode entity) {
        version.setEpisodeId(entity.getId());
        version.setWorkspaceId(entity.getWorkspaceId());
    }

    @Override protected LambdaUpdateWrapper<Episode> buildRestoreWrapperBase(
            String entityId, String userId, LocalDateTime now) {
        LambdaUpdateWrapper<Episode> w = new LambdaUpdateWrapper<>();
        w.eq(Episode::getId, entityId)
                .set(Episode::getUpdatedBy, userId)
                .set(Episode::getUpdatedAt, now);
        return w;
    }

    @Override protected LambdaUpdateWrapper<Episode> buildVersionMetaWrapperBase(
            String entityId, String versionId, Integer versionNumber) {
        LambdaUpdateWrapper<Episode> w = new LambdaUpdateWrapper<>();
        w.eq(Episode::getId, entityId)
                .set(Episode::getCurrentVersionId, versionId)
                .set(Episode::getVersionNumber, versionNumber);
        return w;
    }

    @Override
    protected EpisodeVersionDetailResponse buildDetailResponse(EpisodeVersion v, String currentVersionId) {
        return EpisodeVersionDetailResponse.builder()
                .id(v.getId())
                .episodeId(v.getEpisodeId())
                .versionNumber(v.getVersionNumber())
                .changeSummary(v.getChangeSummary())
                .createdBy(v.getCreatedBy())
                .createdAt(v.getCreatedAt())
                .isCurrent(v.getId().equals(currentVersionId))
                .scriptId(v.getScriptId())
                .title(v.getTitle())
                .sequence(v.getSequence())
                .status(v.getStatus())
                .synopsis(v.getSynopsis())
                .content(v.getContent())
                .coverAssetId(v.getCoverAssetId())
                .docAssetId(v.getDocAssetId())
                .extraInfo(v.getExtraInfo())
                .build();
    }
}
