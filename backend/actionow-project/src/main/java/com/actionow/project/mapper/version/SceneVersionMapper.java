package com.actionow.project.mapper.version;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.project.entity.version.SceneVersion;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 场景版本 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface SceneVersionMapper extends BaseMapper<SceneVersion> {

    /**
     * 原子插入版本快照（自动计算下一个版本号）
     */
    @Insert("""
        INSERT INTO t_scene_version (
            id, scene_id, workspace_id, version_number, change_summary,
            scope, scene_type, script_id, name, description, fixed_desc,
            appearance_data, cover_asset_id, extra_info,
            created_by, created_at
        ) VALUES (
            #{id}, #{sceneId}, #{workspaceId},
            COALESCE((SELECT MAX(version_number) FROM t_scene_version WHERE scene_id = #{sceneId}), 0) + 1,
            #{changeSummary}, #{scope}, #{sceneType}, #{scriptId}, #{name}, #{description}, #{fixedDesc},
            #{appearanceData, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
            #{coverAssetId},
            #{extraInfo, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
            #{createdBy}, #{createdAt}
        )
        """)
    int insertWithAutoVersionNumber(SceneVersion version);

    /**
     * 根据ID查询版本号
     */
    @Select("SELECT version_number FROM t_scene_version WHERE id = #{id}")
    Integer selectVersionNumberById(@Param("id") String id);

    /**
     * 根据场景ID查询所有版本（按版本号降序）
     */
    @Select("SELECT * FROM t_scene_version WHERE scene_id = #{sceneId} ORDER BY version_number DESC")
    List<SceneVersion> selectBySceneId(@Param("sceneId") String sceneId);

    /**
     * 获取场景的最新版本
     */
    @Select("SELECT * FROM t_scene_version WHERE scene_id = #{sceneId} ORDER BY version_number DESC LIMIT 1")
    SceneVersion selectLatestVersion(@Param("sceneId") String sceneId);

    /**
     * 根据场景ID和版本号查询特定版本
     */
    @Select("SELECT * FROM t_scene_version WHERE scene_id = #{sceneId} AND version_number = #{versionNumber}")
    SceneVersion selectByVersionNumber(@Param("sceneId") String sceneId, @Param("versionNumber") Integer versionNumber);

    /**
     * 获取场景的最大版本号
     */
    @Select("SELECT COALESCE(MAX(version_number), 0) FROM t_scene_version WHERE scene_id = #{sceneId}")
    Integer selectMaxVersionNumber(@Param("sceneId") String sceneId);

    /**
     * 统计版本数量
     */
    @Select("SELECT COUNT(*) FROM t_scene_version WHERE scene_id = #{sceneId}")
    int countBySceneId(@Param("sceneId") String sceneId);

    /**
     * 删除旧版本（保留最近 N 个版本）
     */
    @Delete("""
        DELETE FROM t_scene_version
        WHERE scene_id = #{sceneId}
        AND version_number NOT IN (
            SELECT version_number FROM (
                SELECT version_number FROM t_scene_version
                WHERE scene_id = #{sceneId}
                ORDER BY version_number DESC
                LIMIT #{keepCount}
            ) AS keep_versions
        )
        """)
    int deleteOldVersions(@Param("sceneId") String sceneId, @Param("keepCount") int keepCount);

    /**
     * 批量获取需要清理的场景ID
     */
    @Select("""
        SELECT scene_id FROM t_scene_version
        GROUP BY scene_id
        HAVING COUNT(*) > #{threshold}
        LIMIT #{batchSize}
        """)
    List<String> selectSceneIdsNeedingCleanup(@Param("threshold") int threshold, @Param("batchSize") int batchSize);
}
