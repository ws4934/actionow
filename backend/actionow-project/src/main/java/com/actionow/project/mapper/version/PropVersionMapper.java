package com.actionow.project.mapper.version;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.project.entity.version.PropVersion;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 道具版本 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface PropVersionMapper extends BaseMapper<PropVersion> {

    /**
     * 原子插入版本快照（自动计算下一个版本号）
     */
    @Insert("""
        INSERT INTO t_prop_version (
            id, prop_id, workspace_id, version_number, change_summary,
            scope, script_id, name, description, fixed_desc, prop_type,
            appearance_data, cover_asset_id, extra_info,
            created_by, created_at
        ) VALUES (
            #{id}, #{propId}, #{workspaceId},
            COALESCE((SELECT MAX(version_number) FROM t_prop_version WHERE prop_id = #{propId}), 0) + 1,
            #{changeSummary}, #{scope}, #{scriptId}, #{name}, #{description}, #{fixedDesc}, #{propType},
            #{appearanceData, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
            #{coverAssetId},
            #{extraInfo, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
            #{createdBy}, #{createdAt}
        )
        """)
    int insertWithAutoVersionNumber(PropVersion version);

    /**
     * 根据ID查询版本号
     */
    @Select("SELECT version_number FROM t_prop_version WHERE id = #{id}")
    Integer selectVersionNumberById(@Param("id") String id);

    /**
     * 根据道具ID查询所有版本（按版本号降序）
     */
    @Select("SELECT * FROM t_prop_version WHERE prop_id = #{propId} ORDER BY version_number DESC")
    List<PropVersion> selectByPropId(@Param("propId") String propId);

    /**
     * 获取道具的最新版本
     */
    @Select("SELECT * FROM t_prop_version WHERE prop_id = #{propId} ORDER BY version_number DESC LIMIT 1")
    PropVersion selectLatestVersion(@Param("propId") String propId);

    /**
     * 根据道具ID和版本号查询特定版本
     */
    @Select("SELECT * FROM t_prop_version WHERE prop_id = #{propId} AND version_number = #{versionNumber}")
    PropVersion selectByVersionNumber(@Param("propId") String propId, @Param("versionNumber") Integer versionNumber);

    /**
     * 获取道具的最大版本号
     */
    @Select("SELECT COALESCE(MAX(version_number), 0) FROM t_prop_version WHERE prop_id = #{propId}")
    Integer selectMaxVersionNumber(@Param("propId") String propId);

    /**
     * 统计版本数量
     */
    @Select("SELECT COUNT(*) FROM t_prop_version WHERE prop_id = #{propId}")
    int countByPropId(@Param("propId") String propId);

    /**
     * 删除旧版本（保留最近 N 个版本）
     */
    @Delete("""
        DELETE FROM t_prop_version
        WHERE prop_id = #{propId}
        AND version_number NOT IN (
            SELECT version_number FROM (
                SELECT version_number FROM t_prop_version
                WHERE prop_id = #{propId}
                ORDER BY version_number DESC
                LIMIT #{keepCount}
            ) AS keep_versions
        )
        """)
    int deleteOldVersions(@Param("propId") String propId, @Param("keepCount") int keepCount);

    /**
     * 批量获取需要清理的道具ID
     */
    @Select("""
        SELECT prop_id FROM t_prop_version
        GROUP BY prop_id
        HAVING COUNT(*) > #{threshold}
        LIMIT #{batchSize}
        """)
    List<String> selectPropIdsNeedingCleanup(@Param("threshold") int threshold, @Param("batchSize") int batchSize);
}
