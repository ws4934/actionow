package com.actionow.project.mapper.version;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.project.entity.version.EpisodeVersion;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 剧集版本 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface EpisodeVersionMapper extends BaseMapper<EpisodeVersion> {

    /**
     * 原子插入版本快照（自动计算下一个版本号）
     */
    @Insert("""
        INSERT INTO t_episode_version (
            id, episode_id, workspace_id, version_number, change_summary,
            script_id, title, sequence, status, synopsis, content,
            cover_asset_id, doc_asset_id, extra_info, created_by, created_at
        ) VALUES (
            #{id}, #{episodeId}, #{workspaceId},
            COALESCE((SELECT MAX(version_number) FROM t_episode_version WHERE episode_id = #{episodeId}), 0) + 1,
            #{changeSummary}, #{scriptId}, #{title}, #{sequence}, #{status}, #{synopsis}, #{content},
            #{coverAssetId}, #{docAssetId},
            #{extraInfo, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
            #{createdBy}, #{createdAt}
        )
        """)
    int insertWithAutoVersionNumber(EpisodeVersion version);

    /**
     * 根据ID查询版本号
     */
    @Select("SELECT version_number FROM t_episode_version WHERE id = #{id}")
    Integer selectVersionNumberById(@Param("id") String id);

    /**
     * 根据剧集ID查询所有版本（按版本号降序）
     */
    @Select("SELECT * FROM t_episode_version WHERE episode_id = #{episodeId} ORDER BY version_number DESC")
    List<EpisodeVersion> selectByEpisodeId(@Param("episodeId") String episodeId);

    /**
     * 获取剧集的最新版本
     */
    @Select("SELECT * FROM t_episode_version WHERE episode_id = #{episodeId} ORDER BY version_number DESC LIMIT 1")
    EpisodeVersion selectLatestVersion(@Param("episodeId") String episodeId);

    /**
     * 根据剧集ID和版本号查询特定版本
     */
    @Select("SELECT * FROM t_episode_version WHERE episode_id = #{episodeId} AND version_number = #{versionNumber}")
    EpisodeVersion selectByVersionNumber(@Param("episodeId") String episodeId, @Param("versionNumber") Integer versionNumber);

    /**
     * 获取剧集的最大版本号
     */
    @Select("SELECT COALESCE(MAX(version_number), 0) FROM t_episode_version WHERE episode_id = #{episodeId}")
    Integer selectMaxVersionNumber(@Param("episodeId") String episodeId);

    /**
     * 统计版本数量
     */
    @Select("SELECT COUNT(*) FROM t_episode_version WHERE episode_id = #{episodeId}")
    int countByEpisodeId(@Param("episodeId") String episodeId);

    /**
     * 删除旧版本（保留最近 N 个版本）
     */
    @Delete("""
        DELETE FROM t_episode_version
        WHERE episode_id = #{episodeId}
        AND version_number NOT IN (
            SELECT version_number FROM (
                SELECT version_number FROM t_episode_version
                WHERE episode_id = #{episodeId}
                ORDER BY version_number DESC
                LIMIT #{keepCount}
            ) AS keep_versions
        )
        """)
    int deleteOldVersions(@Param("episodeId") String episodeId, @Param("keepCount") int keepCount);

    /**
     * 批量获取需要清理的剧集ID
     */
    @Select("""
        SELECT episode_id FROM t_episode_version
        GROUP BY episode_id
        HAVING COUNT(*) > #{threshold}
        LIMIT #{batchSize}
        """)
    List<String> selectEpisodeIdsNeedingCleanup(@Param("threshold") int threshold, @Param("batchSize") int batchSize);
}
