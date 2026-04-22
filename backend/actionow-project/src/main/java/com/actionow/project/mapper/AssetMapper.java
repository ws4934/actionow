package com.actionow.project.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.project.entity.Asset;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 素材 Mapper
 * 使用 LambdaQueryWrapper 确保 JacksonTypeHandler 正确处理 JSONB 字段
 * （metaInfo, extraInfo）。
 * 跨 schema 查询（tenant_system）和回收站查询使用 @ResultMap 引用 autoResultMap。
 *
 * @author Actionow
 */
@Mapper
public interface AssetMapper extends BaseMapper<Asset> {

    /**
     * 根据工作空间ID查询素材列表
     */
    default List<Asset> selectByWorkspaceId(String workspaceId) {
        return selectList(new LambdaQueryWrapper<Asset>()
                .eq(Asset::getWorkspaceId, workspaceId)
                .orderByDesc(Asset::getUpdatedAt));
    }

    /**
     * 根据剧本ID查询素材列表
     */
    default List<Asset> selectByScriptId(String scriptId) {
        return selectList(new LambdaQueryWrapper<Asset>()
                .eq(Asset::getScriptId, scriptId)
                .orderByDesc(Asset::getUpdatedAt));
    }

    /**
     * 根据素材类型查询
     */
    default List<Asset> selectByType(String workspaceId, String assetType) {
        return selectList(new LambdaQueryWrapper<Asset>()
                .eq(Asset::getWorkspaceId, workspaceId)
                .eq(Asset::getAssetType, assetType)
                .orderByDesc(Asset::getUpdatedAt));
    }

    /**
     * 根据生成状态查询
     */
    default List<Asset> selectByGenerationStatus(String workspaceId, String status) {
        return selectList(new LambdaQueryWrapper<Asset>()
                .eq(Asset::getWorkspaceId, workspaceId)
                .eq(Asset::getGenerationStatus, status)
                .orderByDesc(Asset::getUpdatedAt));
    }

    /**
     * 根据来源查询
     */
    default List<Asset> selectBySource(String workspaceId, String source) {
        return selectList(new LambdaQueryWrapper<Asset>()
                .eq(Asset::getWorkspaceId, workspaceId)
                .eq(Asset::getSource, source)
                .orderByDesc(Asset::getUpdatedAt));
    }

    /**
     * 统计工作空间素材数量
     */
    @Select("SELECT COUNT(*) FROM t_asset WHERE workspace_id = #{workspaceId} AND deleted = 0")
    int countByWorkspaceId(@Param("workspaceId") String workspaceId);

    /**
     * 根据任务ID查询素材
     */
    default Asset selectByTaskId(String taskId) {
        return selectOne(new LambdaQueryWrapper<Asset>()
                .eq(Asset::getTaskId, taskId));
    }

    // ==================== 回收站相关（绕过 @TableLogic，使用 @ResultMap） ====================

    /**
     * 查询已删除的素材（绕过 @TableLogic）
     */
    @ResultMap("mybatis-plus_Asset")
    @Select("SELECT * FROM t_asset WHERE id = #{id} AND deleted = 1")
    Asset selectDeletedById(@Param("id") String id);

    /**
     * 查询工作空间回收站中的素材（绕过 @TableLogic）
     */
    @ResultMap("mybatis-plus_Asset")
    @Select("SELECT * FROM t_asset WHERE workspace_id = #{workspaceId} AND deleted = 1 ORDER BY deleted_at DESC")
    List<Asset> selectTrashByWorkspaceId(@Param("workspaceId") String workspaceId);

    /**
     * 统计工作空间回收站中的素材数量
     */
    @Select("SELECT COUNT(*) FROM t_asset WHERE workspace_id = #{workspaceId} AND deleted = 1")
    int countTrashByWorkspaceId(@Param("workspaceId") String workspaceId);

    /**
     * 软删除素材（绕过 @TableLogic，显式设置 deleted = 1）
     */
    @Update("UPDATE t_asset SET deleted = 1, deleted_at = #{deletedAt}, trash_path = #{trashPath}, version = version + 1 " +
            "WHERE id = #{id} AND deleted = 0 AND version = #{version}")
    int softDelete(@Param("id") String id, @Param("deletedAt") LocalDateTime deletedAt,
                   @Param("trashPath") String trashPath, @Param("version") Integer version);

    /**
     * 从回收站恢复素材（绕过 @TableLogic，显式设置 deleted = 0）
     */
    @Update("UPDATE t_asset SET deleted = 0, deleted_at = NULL, trash_path = NULL, version = version + 1 " +
            "WHERE id = #{id} AND deleted = 1 AND version = #{version}")
    int restoreFromTrash(@Param("id") String id, @Param("version") Integer version);

    /**
     * 物理删除素材记录
     */
    @Delete("DELETE FROM t_asset WHERE id = #{id}")
    int hardDeleteById(@Param("id") String id);

    /**
     * 查询回收站中过期的素材
     */
    @ResultMap("mybatis-plus_Asset")
    @Select("SELECT * FROM t_asset WHERE deleted = 1 AND deleted_at IS NOT NULL AND deleted_at < #{expireTime}")
    List<Asset> selectExpiredTrash(@Param("expireTime") LocalDateTime expireTime);

    /**
     * 统计共享同一 file_key 的其他素材数量（排除自身）
     * 用于孤儿校验：仅当没有其他记录引用该存储对象时，才允许物理删除存储文件
     */
    @Select("SELECT COUNT(*) FROM t_asset WHERE file_key = #{fileKey} AND id <> #{excludeId}")
    int countOtherByFileKey(@Param("fileKey") String fileKey, @Param("excludeId") String excludeId);

    /**
     * 统计共享同一 trash_path 的其他素材数量（排除自身）
     */
    @Select("SELECT COUNT(*) FROM t_asset WHERE trash_path = #{trashPath} AND id <> #{excludeId}")
    int countOtherByTrashPath(@Param("trashPath") String trashPath, @Param("excludeId") String excludeId);

    // ==================== 公共库（系统租户）相关 ====================

    /**
     * 查询系统租户已发布素材（scope = SYSTEM）
     */
    @ResultMap("mybatis-plus_Asset")
    @Select("SELECT * FROM tenant_system.t_asset WHERE scope = 'SYSTEM' AND deleted = 0 ORDER BY published_at DESC")
    List<Asset> selectSystemAssets();

    /**
     * 查询系统租户全部素材草稿（含 WORKSPACE 和 SYSTEM）
     */
    @ResultMap("mybatis-plus_Asset")
    @Select("SELECT * FROM tenant_system.t_asset WHERE deleted = 0 ORDER BY created_at DESC")
    List<Asset> selectSystemAssetDrafts();

    /**
     * 根据ID查询系统租户素材
     */
    @ResultMap("mybatis-plus_Asset")
    @Select("SELECT * FROM tenant_system.t_asset WHERE id = #{id} AND deleted = 0")
    Asset selectSystemAssetById(@Param("id") String id);

    /**
     * 批量查询系统租户素材（用于批量填充封面URL，避免 N+1）
     * 注意：仅查询 id, file_url, thumbnail_url 列，其他字段为 null
     */
    @Select("<script>SELECT id, file_url, thumbnail_url FROM tenant_system.t_asset " +
            "WHERE id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "AND deleted = 0</script>")
    List<Asset> selectSystemAssetsByIds(@Param("ids") List<String> ids);

    /**
     * 发布素材到公共库（WORKSPACE → SYSTEM）
     */
    @Update("UPDATE tenant_system.t_asset SET scope = 'SYSTEM', published_at = #{publishedAt}, " +
            "published_by = #{operatorId}, publish_note = #{publishNote}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND deleted = 0")
    int publishAsset(@Param("id") String id, @Param("publishedAt") LocalDateTime publishedAt,
                     @Param("operatorId") String operatorId, @Param("publishNote") String publishNote);

    /**
     * 下架素材（SYSTEM → WORKSPACE）
     */
    @Update("UPDATE tenant_system.t_asset SET scope = 'WORKSPACE', published_at = NULL, " +
            "published_by = NULL, publish_note = NULL, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND deleted = 0")
    int unpublishAsset(@Param("id") String id, @Param("operatorId") String operatorId);
}
