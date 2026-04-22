package com.actionow.project.service.version;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.actionow.project.dto.version.StoryboardVersionDetailResponse;
import com.actionow.project.entity.Storyboard;
import com.actionow.project.entity.version.StoryboardVersion;
import com.actionow.project.mapper.StoryboardMapper;
import com.actionow.project.mapper.version.StoryboardVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoryboardVersionServiceImpl
        extends AbstractVersionServiceImpl<Storyboard, StoryboardVersion, StoryboardVersionDetailResponse> {

    private final StoryboardMapper storyboardMapper;
    private final StoryboardVersionMapper storyboardVersionMapper;

    private static final List<VersionFieldSpec<Storyboard, StoryboardVersion>> FIELD_SPECS = List.of(
            VersionFieldSpec.of("scriptId", "剧本",
                    Storyboard::getScriptId, StoryboardVersion::getScriptId, StoryboardVersion::setScriptId),
            VersionFieldSpec.of("episodeId", "剧集",
                    Storyboard::getEpisodeId, StoryboardVersion::getEpisodeId, StoryboardVersion::setEpisodeId),
            VersionFieldSpec.of("title", "标题",
                    Storyboard::getTitle, StoryboardVersion::getTitle, StoryboardVersion::setTitle),
            VersionFieldSpec.of("sequence", "序号",
                    Storyboard::getSequence, StoryboardVersion::getSequence, StoryboardVersion::setSequence),
            VersionFieldSpec.of("status", "状态",
                    Storyboard::getStatus, StoryboardVersion::getStatus, StoryboardVersion::setStatus),
            VersionFieldSpec.of("synopsis", "描述",
                    Storyboard::getSynopsis, StoryboardVersion::getSynopsis, StoryboardVersion::setSynopsis),
            VersionFieldSpec.of("duration", "时长",
                    Storyboard::getDuration, StoryboardVersion::getDuration, StoryboardVersion::setDuration),
            VersionFieldSpec.ofMap("visualDesc", "视觉描述",
                    Storyboard::getVisualDesc, StoryboardVersion::getVisualDesc, StoryboardVersion::setVisualDesc),
            VersionFieldSpec.ofMap("audioDesc", "音频描述",
                    Storyboard::getAudioDesc, StoryboardVersion::getAudioDesc, StoryboardVersion::setAudioDesc),
            VersionFieldSpec.of("coverAssetId", "封面",
                    Storyboard::getCoverAssetId, StoryboardVersion::getCoverAssetId, StoryboardVersion::setCoverAssetId),
            VersionFieldSpec.ofMap("extraInfo", "扩展信息",
                    Storyboard::getExtraInfo, StoryboardVersion::getExtraInfo, StoryboardVersion::setExtraInfo)
    );

    @Override protected String getEntityType() { return "STORYBOARD"; }
    @Override protected String getEntityTypeName() { return "分镜"; }
    @Override protected BaseMapper<Storyboard> getEntityMapper() { return storyboardMapper; }
    @Override protected String getEntityCurrentVersionId(Storyboard e) { return e.getCurrentVersionId(); }
    @Override protected String getEntityId(Storyboard e) { return e.getId(); }
    @Override protected Integer getEntityVersionNumber(Storyboard e) { return e.getVersionNumber(); }
    @Override protected SFunction<Storyboard, Integer> entityVersionNumberSGetter() { return Storyboard::getVersionNumber; }

    @Override protected List<StoryboardVersion> findVersionsByEntityId(String id) {
        return storyboardVersionMapper.selectByStoryboardId(id);
    }
    @Override protected StoryboardVersion findVersionByNumber(String id, Integer n) {
        return storyboardVersionMapper.selectByVersionNumber(id, n);
    }
    @Override protected StoryboardVersion findVersionById(String id) {
        return storyboardVersionMapper.selectById(id);
    }
    @Override protected int insertVersionWithAutoNumber(StoryboardVersion v) {
        return storyboardVersionMapper.insertWithAutoVersionNumber(v);
    }
    @Override protected Integer findVersionNumberById(String id) {
        return storyboardVersionMapper.selectVersionNumberById(id);
    }

    @Override protected List<VersionFieldSpec<Storyboard, StoryboardVersion>> getFieldSpecs() { return FIELD_SPECS; }

    @Override protected StoryboardVersion newVersion() { return new StoryboardVersion(); }

    @Override protected void linkVersionToEntity(StoryboardVersion version, Storyboard entity) {
        version.setStoryboardId(entity.getId());
        version.setWorkspaceId(entity.getWorkspaceId());
    }

    @Override protected LambdaUpdateWrapper<Storyboard> buildRestoreWrapperBase(
            String entityId, String userId, LocalDateTime now) {
        LambdaUpdateWrapper<Storyboard> w = new LambdaUpdateWrapper<>();
        w.eq(Storyboard::getId, entityId)
                .set(Storyboard::getUpdatedBy, userId)
                .set(Storyboard::getUpdatedAt, now);
        return w;
    }

    @Override protected LambdaUpdateWrapper<Storyboard> buildVersionMetaWrapperBase(
            String entityId, String versionId, Integer versionNumber) {
        LambdaUpdateWrapper<Storyboard> w = new LambdaUpdateWrapper<>();
        w.eq(Storyboard::getId, entityId)
                .set(Storyboard::getCurrentVersionId, versionId)
                .set(Storyboard::getVersionNumber, versionNumber);
        return w;
    }

    @Override
    protected StoryboardVersionDetailResponse buildDetailResponse(StoryboardVersion v, String currentVersionId) {
        return StoryboardVersionDetailResponse.builder()
                .id(v.getId())
                .storyboardId(v.getStoryboardId())
                .versionNumber(v.getVersionNumber())
                .changeSummary(v.getChangeSummary())
                .createdBy(v.getCreatedBy())
                .createdAt(v.getCreatedAt())
                .isCurrent(v.getId().equals(currentVersionId))
                .scriptId(v.getScriptId())
                .episodeId(v.getEpisodeId())
                .title(v.getTitle())
                .sequence(v.getSequence())
                .status(v.getStatus())
                .synopsis(v.getSynopsis())
                .duration(v.getDuration())
                .visualDesc(v.getVisualDesc())
                .audioDesc(v.getAudioDesc())
                .coverAssetId(v.getCoverAssetId())
                .extraInfo(v.getExtraInfo())
                .build();
    }
}
