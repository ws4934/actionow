package com.actionow.project.mapper.version;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.project.entity.version.AssetVersion;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 素材版本 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface AssetVersionMapper extends BaseMapper<AssetVersion> {

    /**
     * 原子插入版本快照（自动计算下一个版本号）
     */
    @Insert("""
        INSERT INTO t_asset_version (
            id, asset_id, workspace_id, version_number, change_summary,
            scope, script_id, name, description, asset_type, source,
            file_key, file_url, thumbnail_url, file_size, mime_type,
            meta_info, extra_info, generation_status, workflow_id, task_id,
            created_by, created_at
        ) VALUES (
            #{id}, #{assetId}, #{workspaceId},
            COALESCE((SELECT MAX(version_number) FROM t_asset_version WHERE asset_id = #{assetId}), 0) + 1,
            #{changeSummary}, #{scope}, #{scriptId}, #{name}, #{description}, #{assetType}, #{source},
            #{fileKey}, #{fileUrl}, #{thumbnailUrl}, #{fileSize}, #{mimeType},
            #{metaInfo, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
            #{extraInfo, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
            #{generationStatus}, #{workflowId}, #{taskId},
            #{createdBy}, #{createdAt}
        )
        """)
    int insertWithAutoVersionNumber(AssetVersion version);

    /**
     * 根据ID查询版本号
     */
    @Select("SELECT version_number FROM t_asset_version WHERE id = #{id}")
    Integer selectVersionNumberById(@Param("id") String id);

    /**
     * 根据素材ID查询所有版本（按版本号降序）
     */
    @Select("SELECT * FROM t_asset_version WHERE asset_id = #{assetId} ORDER BY version_number DESC")
    List<AssetVersion> selectByAssetId(@Param("assetId") String assetId);

    /**
     * 获取素材的最新版本
     */
    @Select("SELECT * FROM t_asset_version WHERE asset_id = #{assetId} ORDER BY version_number DESC LIMIT 1")
    AssetVersion selectLatestVersion(@Param("assetId") String assetId);

    /**
     * 根据素材ID和版本号查询特定版本
     */
    @Select("SELECT * FROM t_asset_version WHERE asset_id = #{assetId} AND version_number = #{versionNumber}")
    AssetVersion selectByVersionNumber(@Param("assetId") String assetId, @Param("versionNumber") Integer versionNumber);

    /**
     * 获取素材的最大版本号
     */
    @Select("SELECT COALESCE(MAX(version_number), 0) FROM t_asset_version WHERE asset_id = #{assetId}")
    Integer selectMaxVersionNumber(@Param("assetId") String assetId);

    /**
     * 统计版本数量
     */
    @Select("SELECT COUNT(*) FROM t_asset_version WHERE asset_id = #{assetId}")
    int countByAssetId(@Param("assetId") String assetId);

    /**
     * 删除旧版本（保留最近 N 个版本）
     */
    @Delete("""
        DELETE FROM t_asset_version
        WHERE asset_id = #{assetId}
        AND version_number NOT IN (
            SELECT version_number FROM (
                SELECT version_number FROM t_asset_version
                WHERE asset_id = #{assetId}
                ORDER BY version_number DESC
                LIMIT #{keepCount}
            ) AS keep_versions
        )
        """)
    int deleteOldVersions(@Param("assetId") String assetId, @Param("keepCount") int keepCount);

    /**
     * 批量获取需要清理的素材ID
     */
    @Select("""
        SELECT asset_id FROM t_asset_version
        GROUP BY asset_id
        HAVING COUNT(*) > #{threshold}
        LIMIT #{batchSize}
        """)
    List<String> selectAssetIdsNeedingCleanup(@Param("threshold") int threshold, @Param("batchSize") int batchSize);
}
