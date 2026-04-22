package com.actionow.project.mapper.version;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.project.entity.version.CharacterVersion;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 角色版本 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface CharacterVersionMapper extends BaseMapper<CharacterVersion> {

    /**
     * 原子插入版本快照（自动计算下一个版本号）
     */
    @Insert("""
        INSERT INTO t_character_version (
            id, character_id, workspace_id, version_number, change_summary,
            scope, script_id, name, description, fixed_desc, age, gender,
            character_type, voice_seed_id, appearance_data, cover_asset_id, extra_info,
            created_by, created_at
        ) VALUES (
            #{id}, #{characterId}, #{workspaceId},
            COALESCE((SELECT MAX(version_number) FROM t_character_version WHERE character_id = #{characterId}), 0) + 1,
            #{changeSummary}, #{scope}, #{scriptId}, #{name}, #{description}, #{fixedDesc}, #{age}, #{gender},
            #{characterType}, #{voiceSeedId},
            #{appearanceData, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
            #{coverAssetId},
            #{extraInfo, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
            #{createdBy}, #{createdAt}
        )
        """)
    int insertWithAutoVersionNumber(CharacterVersion version);

    /**
     * 根据ID查询版本号
     */
    @Select("SELECT version_number FROM t_character_version WHERE id = #{id}")
    Integer selectVersionNumberById(@Param("id") String id);

    /**
     * 根据角色ID查询所有版本（按版本号降序）
     */
    @Select("SELECT * FROM t_character_version WHERE character_id = #{characterId} ORDER BY version_number DESC")
    List<CharacterVersion> selectByCharacterId(@Param("characterId") String characterId);

    /**
     * 获取角色的最新版本
     */
    @Select("SELECT * FROM t_character_version WHERE character_id = #{characterId} ORDER BY version_number DESC LIMIT 1")
    CharacterVersion selectLatestVersion(@Param("characterId") String characterId);

    /**
     * 根据角色ID和版本号查询特定版本
     */
    @Select("SELECT * FROM t_character_version WHERE character_id = #{characterId} AND version_number = #{versionNumber}")
    CharacterVersion selectByVersionNumber(@Param("characterId") String characterId, @Param("versionNumber") Integer versionNumber);

    /**
     * 获取角色的最大版本号
     */
    @Select("SELECT COALESCE(MAX(version_number), 0) FROM t_character_version WHERE character_id = #{characterId}")
    Integer selectMaxVersionNumber(@Param("characterId") String characterId);

    /**
     * 统计版本数量
     */
    @Select("SELECT COUNT(*) FROM t_character_version WHERE character_id = #{characterId}")
    int countByCharacterId(@Param("characterId") String characterId);

    /**
     * 删除旧版本（保留最近 N 个版本）
     */
    @Delete("""
        DELETE FROM t_character_version
        WHERE character_id = #{characterId}
        AND version_number NOT IN (
            SELECT version_number FROM (
                SELECT version_number FROM t_character_version
                WHERE character_id = #{characterId}
                ORDER BY version_number DESC
                LIMIT #{keepCount}
            ) AS keep_versions
        )
        """)
    int deleteOldVersions(@Param("characterId") String characterId, @Param("keepCount") int keepCount);

    /**
     * 批量获取需要清理的角色ID
     */
    @Select("""
        SELECT character_id FROM t_character_version
        GROUP BY character_id
        HAVING COUNT(*) > #{threshold}
        LIMIT #{batchSize}
        """)
    List<String> selectCharacterIdsNeedingCleanup(@Param("threshold") int threshold, @Param("batchSize") int batchSize);
}
