package com.actionow.project.mapper.version;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.project.entity.version.StyleVersion;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 风格版本 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface StyleVersionMapper extends BaseMapper<StyleVersion> {

    /**
     * 原子插入版本快照（自动计算下一个版本号）
     */
    @Insert("""
        INSERT INTO t_style_version (
            id, style_id, workspace_id, version_number, change_summary,
            scope, script_id, name, description, fixed_desc,
            style_params, cover_asset_id, extra_info,
            created_by, created_at
        ) VALUES (
            #{id}, #{styleId}, #{workspaceId},
            COALESCE((SELECT MAX(version_number) FROM t_style_version WHERE style_id = #{styleId}), 0) + 1,
            #{changeSummary}, #{scope}, #{scriptId}, #{name}, #{description}, #{fixedDesc},
            #{styleParams, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
            #{coverAssetId},
            #{extraInfo, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
            #{createdBy}, #{createdAt}
        )
        """)
    int insertWithAutoVersionNumber(StyleVersion version);

    /**
     * 根据ID查询版本号
     */
    @Select("SELECT version_number FROM t_style_version WHERE id = #{id}")
    Integer selectVersionNumberById(@Param("id") String id);

    /**
     * 根据风格ID查询所有版本（按版本号降序）
     */
    @Select("SELECT * FROM t_style_version WHERE style_id = #{styleId} ORDER BY version_number DESC")
    List<StyleVersion> selectByStyleId(@Param("styleId") String styleId);

    /**
     * 获取风格的最新版本
     */
    @Select("SELECT * FROM t_style_version WHERE style_id = #{styleId} ORDER BY version_number DESC LIMIT 1")
    StyleVersion selectLatestVersion(@Param("styleId") String styleId);

    /**
     * 根据风格ID和版本号查询特定版本
     */
    @Select("SELECT * FROM t_style_version WHERE style_id = #{styleId} AND version_number = #{versionNumber}")
    StyleVersion selectByVersionNumber(@Param("styleId") String styleId, @Param("versionNumber") Integer versionNumber);

    /**
     * 获取风格的最大版本号
     */
    @Select("SELECT COALESCE(MAX(version_number), 0) FROM t_style_version WHERE style_id = #{styleId}")
    Integer selectMaxVersionNumber(@Param("styleId") String styleId);

    /**
     * 统计版本数量
     */
    @Select("SELECT COUNT(*) FROM t_style_version WHERE style_id = #{styleId}")
    int countByStyleId(@Param("styleId") String styleId);

    /**
     * 删除旧版本（保留最近 N 个版本）
     */
    @Delete("""
        DELETE FROM t_style_version
        WHERE style_id = #{styleId}
        AND version_number NOT IN (
            SELECT version_number FROM (
                SELECT version_number FROM t_style_version
                WHERE style_id = #{styleId}
                ORDER BY version_number DESC
                LIMIT #{keepCount}
            ) AS keep_versions
        )
        """)
    int deleteOldVersions(@Param("styleId") String styleId, @Param("keepCount") int keepCount);

    /**
     * 批量获取需要清理的风格ID
     */
    @Select("""
        SELECT style_id FROM t_style_version
        GROUP BY style_id
        HAVING COUNT(*) > #{threshold}
        LIMIT #{batchSize}
        """)
    List<String> selectStyleIdsNeedingCleanup(@Param("threshold") int threshold, @Param("batchSize") int batchSize);
}
