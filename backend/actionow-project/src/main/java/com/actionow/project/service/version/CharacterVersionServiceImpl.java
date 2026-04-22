package com.actionow.project.service.version;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.actionow.project.dto.version.CharacterVersionDetailResponse;
import com.actionow.project.entity.Character;
import com.actionow.project.entity.version.CharacterVersion;
import com.actionow.project.mapper.CharacterMapper;
import com.actionow.project.mapper.version.CharacterVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterVersionServiceImpl
        extends AbstractVersionServiceImpl<Character, CharacterVersion, CharacterVersionDetailResponse> {

    private final CharacterMapper characterMapper;
    private final CharacterVersionMapper characterVersionMapper;

    private static final List<VersionFieldSpec<Character, CharacterVersion>> FIELD_SPECS = List.of(
            VersionFieldSpec.of("scope", "范围",
                    Character::getScope, CharacterVersion::getScope, CharacterVersion::setScope),
            VersionFieldSpec.of("scriptId", "剧本",
                    Character::getScriptId, CharacterVersion::getScriptId, CharacterVersion::setScriptId),
            VersionFieldSpec.of("name", "名称",
                    Character::getName, CharacterVersion::getName, CharacterVersion::setName),
            VersionFieldSpec.of("description", "描述",
                    Character::getDescription, CharacterVersion::getDescription, CharacterVersion::setDescription),
            VersionFieldSpec.of("fixedDesc", "固定描述词",
                    Character::getFixedDesc, CharacterVersion::getFixedDesc, CharacterVersion::setFixedDesc),
            VersionFieldSpec.of("age", "年龄",
                    Character::getAge, CharacterVersion::getAge, CharacterVersion::setAge),
            VersionFieldSpec.of("gender", "性别",
                    Character::getGender, CharacterVersion::getGender, CharacterVersion::setGender),
            VersionFieldSpec.of("characterType", "角色类型",
                    Character::getCharacterType, CharacterVersion::getCharacterType, CharacterVersion::setCharacterType),
            VersionFieldSpec.of("voiceSeedId", "语音种子",
                    Character::getVoiceSeedId, CharacterVersion::getVoiceSeedId, CharacterVersion::setVoiceSeedId),
            VersionFieldSpec.ofMap("appearanceData", "外貌数据",
                    Character::getAppearanceData, CharacterVersion::getAppearanceData, CharacterVersion::setAppearanceData),
            VersionFieldSpec.of("coverAssetId", "封面",
                    Character::getCoverAssetId, CharacterVersion::getCoverAssetId, CharacterVersion::setCoverAssetId),
            VersionFieldSpec.ofMap("extraInfo", "扩展信息",
                    Character::getExtraInfo, CharacterVersion::getExtraInfo, CharacterVersion::setExtraInfo)
    );

    @Override protected String getEntityType() { return "CHARACTER"; }
    @Override protected String getEntityTypeName() { return "角色"; }
    @Override protected BaseMapper<Character> getEntityMapper() { return characterMapper; }
    @Override protected String getEntityCurrentVersionId(Character e) { return e.getCurrentVersionId(); }
    @Override protected String getEntityId(Character e) { return e.getId(); }
    @Override protected Integer getEntityVersionNumber(Character e) { return e.getVersionNumber(); }
    @Override protected SFunction<Character, Integer> entityVersionNumberSGetter() { return Character::getVersionNumber; }

    @Override protected List<CharacterVersion> findVersionsByEntityId(String id) {
        return characterVersionMapper.selectByCharacterId(id);
    }
    @Override protected CharacterVersion findVersionByNumber(String id, Integer n) {
        return characterVersionMapper.selectByVersionNumber(id, n);
    }
    @Override protected CharacterVersion findVersionById(String id) {
        return characterVersionMapper.selectById(id);
    }
    @Override protected int insertVersionWithAutoNumber(CharacterVersion v) {
        return characterVersionMapper.insertWithAutoVersionNumber(v);
    }
    @Override protected Integer findVersionNumberById(String id) {
        return characterVersionMapper.selectVersionNumberById(id);
    }

    @Override protected List<VersionFieldSpec<Character, CharacterVersion>> getFieldSpecs() { return FIELD_SPECS; }

    @Override protected CharacterVersion newVersion() { return new CharacterVersion(); }

    @Override protected void linkVersionToEntity(CharacterVersion version, Character entity) {
        version.setCharacterId(entity.getId());
        version.setWorkspaceId(entity.getWorkspaceId());
    }

    @Override protected LambdaUpdateWrapper<Character> buildRestoreWrapperBase(
            String entityId, String userId, LocalDateTime now) {
        LambdaUpdateWrapper<Character> w = new LambdaUpdateWrapper<>();
        w.eq(Character::getId, entityId)
                .set(Character::getUpdatedBy, userId)
                .set(Character::getUpdatedAt, now);
        return w;
    }

    @Override protected LambdaUpdateWrapper<Character> buildVersionMetaWrapperBase(
            String entityId, String versionId, Integer versionNumber) {
        LambdaUpdateWrapper<Character> w = new LambdaUpdateWrapper<>();
        w.eq(Character::getId, entityId)
                .set(Character::getCurrentVersionId, versionId)
                .set(Character::getVersionNumber, versionNumber);
        return w;
    }

    @Override
    protected CharacterVersionDetailResponse buildDetailResponse(CharacterVersion v, String currentVersionId) {
        return CharacterVersionDetailResponse.builder()
                .id(v.getId())
                .characterId(v.getCharacterId())
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
                .age(v.getAge())
                .gender(v.getGender())
                .characterType(v.getCharacterType())
                .voiceSeedId(v.getVoiceSeedId())
                .appearanceData(v.getAppearanceData())
                .coverAssetId(v.getCoverAssetId())
                .extraInfo(v.getExtraInfo())
                .build();
    }
}
