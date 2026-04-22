package com.actionow.project.mapper.version;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.project.entity.version.ScriptVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

/**
 * 剧本版本 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface ScriptVersionMapper extends BaseMapper<ScriptVersion> {

    /**
     * 原子插入版本快照（自动计算下一个版本号）
     * 使用子查询保证原子性，即使是第一个版本也能正确插入
     *
     * @param version 版本对象（versionNumber 会被自动计算）
     * @return 插入行数
     */
    @Insert("""
        INSERT INTO t_script_version (
            id, script_id, workspace_id, version_number, change_summary,
            title, status, synopsis, content, cover_asset_id, doc_asset_id, extra_info,
            created_by, created_at
        ) VALUES (
            #{id}, #{scriptId}, #{workspaceId},
            COALESCE((SELECT MAX(version_number) FROM t_script_version WHERE script_id = #{scriptId}), 0) + 1,
            #{changeSummary},
            #{title}, #{status}, #{synopsis}, #{content}, #{coverAssetId}, #{docAssetId},
            #{extraInfo, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
            #{createdBy}, #{createdAt}
        )
        """)
    int insertWithAutoVersionNumber(ScriptVersion version);

    /**
     * 根据ID查询刚插入记录的版本号
     */
    @Select("SELECT version_number FROM t_script_version WHERE id = #{id}")
    Integer selectVersionNumberById(@Param("id") String id);

    /**
     * 根据剧本ID查询所有版本（按版本号降序）
     */
    @Select("SELECT * FROM t_script_version WHERE script_id = #{scriptId} ORDER BY version_number DESC")
    List<ScriptVersion> selectByScriptId(@Param("scriptId") String scriptId);

    /**
     * 获取剧本的最新版本
     */
    @Select("SELECT * FROM t_script_version WHERE script_id = #{scriptId} ORDER BY version_number DESC LIMIT 1")
    ScriptVersion selectLatestVersion(@Param("scriptId") String scriptId);

    /**
     * 根据剧本ID和版本号查询特定版本
     */
    @Select("SELECT * FROM t_script_version WHERE script_id = #{scriptId} AND version_number = #{versionNumber}")
    ScriptVersion selectByVersionNumber(@Param("scriptId") String scriptId, @Param("versionNumber") Integer versionNumber);

    /**
     * 获取剧本的最大版本号
     */
    @Select("SELECT COALESCE(MAX(version_number), 0) FROM t_script_version WHERE script_id = #{scriptId}")
    Integer selectMaxVersionNumber(@Param("scriptId") String scriptId);

    /**
     * 统计剧本的版本数量
     */
    @Select("SELECT COUNT(*) FROM t_script_version WHERE script_id = #{scriptId}")
    int countByScriptId(@Param("scriptId") String scriptId);

    /**
     * 删除旧版本（保留最近 N 个版本）
     * 用于版本清理任务
     *
     * @param scriptId 剧本ID
     * @param keepCount 保留的版本数量
     * @return 删除的行数
     */
    @Delete("""
        DELETE FROM t_script_version
        WHERE script_id = #{scriptId}
        AND version_number NOT IN (
            SELECT version_number FROM (
                SELECT version_number FROM t_script_version
                WHERE script_id = #{scriptId}
                ORDER BY version_number DESC
                LIMIT #{keepCount}
            ) AS keep_versions
        )
        """)
    int deleteOldVersions(@Param("scriptId") String scriptId, @Param("keepCount") int keepCount);

    /**
     * 批量获取需要清理的剧本ID（版本数超过阈值）
     */
    @Select("""
        SELECT script_id FROM t_script_version
        GROUP BY script_id
        HAVING COUNT(*) > #{threshold}
        LIMIT #{batchSize}
        """)
    List<String> selectScriptIdsNeedingCleanup(@Param("threshold") int threshold, @Param("batchSize") int batchSize);
}
