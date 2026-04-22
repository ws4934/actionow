package com.actionow.project.mapper.version;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.project.entity.version.StoryboardVersion;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 分镜版本 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface StoryboardVersionMapper extends BaseMapper<StoryboardVersion> {

    /**
     * 原子插入版本快照（自动计算下一个版本号）
     */
    @Insert("""
        INSERT INTO t_storyboard_version (
            id, storyboard_id, workspace_id, version_number, change_summary,
            script_id, episode_id, title, sequence, status, synopsis, duration,
            visual_desc, audio_desc, extra_info, created_by, created_at
        ) VALUES (
            #{id}, #{storyboardId}, #{workspaceId},
            COALESCE((SELECT MAX(version_number) FROM t_storyboard_version WHERE storyboard_id = #{storyboardId}), 0) + 1,
            #{changeSummary}, #{scriptId}, #{episodeId}, #{title}, #{sequence}, #{status}, #{synopsis}, #{duration},
            #{visualDesc, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
            #{audioDesc, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
            #{extraInfo, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
            #{createdBy}, #{createdAt}
        )
        """)
    int insertWithAutoVersionNumber(StoryboardVersion version);

    /**
     * 根据ID查询版本号
     */
    @Select("SELECT version_number FROM t_storyboard_version WHERE id = #{id}")
    Integer selectVersionNumberById(@Param("id") String id);

    /**
     * 根据分镜ID查询所有版本（按版本号降序）
     */
    @Select("SELECT * FROM t_storyboard_version WHERE storyboard_id = #{storyboardId} ORDER BY version_number DESC")
    List<StoryboardVersion> selectByStoryboardId(@Param("storyboardId") String storyboardId);

    /**
     * 获取分镜的最新版本
     */
    @Select("SELECT * FROM t_storyboard_version WHERE storyboard_id = #{storyboardId} ORDER BY version_number DESC LIMIT 1")
    StoryboardVersion selectLatestVersion(@Param("storyboardId") String storyboardId);

    /**
     * 根据分镜ID和版本号查询特定版本
     */
    @Select("SELECT * FROM t_storyboard_version WHERE storyboard_id = #{storyboardId} AND version_number = #{versionNumber}")
    StoryboardVersion selectByVersionNumber(@Param("storyboardId") String storyboardId, @Param("versionNumber") Integer versionNumber);

    /**
     * 获取分镜的最大版本号
     */
    @Select("SELECT COALESCE(MAX(version_number), 0) FROM t_storyboard_version WHERE storyboard_id = #{storyboardId}")
    Integer selectMaxVersionNumber(@Param("storyboardId") String storyboardId);

    /**
     * 统计版本数量
     */
    @Select("SELECT COUNT(*) FROM t_storyboard_version WHERE storyboard_id = #{storyboardId}")
    int countByStoryboardId(@Param("storyboardId") String storyboardId);

    /**
     * 删除旧版本（保留最近 N 个版本）
     */
    @Delete("""
        DELETE FROM t_storyboard_version
        WHERE storyboard_id = #{storyboardId}
        AND version_number NOT IN (
            SELECT version_number FROM (
                SELECT version_number FROM t_storyboard_version
                WHERE storyboard_id = #{storyboardId}
                ORDER BY version_number DESC
                LIMIT #{keepCount}
            ) AS keep_versions
        )
        """)
    int deleteOldVersions(@Param("storyboardId") String storyboardId, @Param("keepCount") int keepCount);

    /**
     * 批量获取需要清理的分镜ID
     */
    @Select("""
        SELECT storyboard_id FROM t_storyboard_version
        GROUP BY storyboard_id
        HAVING COUNT(*) > #{threshold}
        LIMIT #{batchSize}
        """)
    List<String> selectStoryboardIdsNeedingCleanup(@Param("threshold") int threshold, @Param("batchSize") int batchSize);
}
