package com.actionow.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.project.entity.EntityAssetRelation;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实体-素材关联 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface EntityAssetRelationMapper extends BaseMapper<EntityAssetRelation> {

    /**
     * 根据实体查询关联的素材关系
     */
    @Select("SELECT * FROM t_entity_asset_relation " +
            "WHERE entity_type = #{entityType} AND entity_id = #{entityId} AND deleted = 0 " +
            "ORDER BY sequence, created_at")
    List<EntityAssetRelation> selectByEntity(
            @Param("entityType") String entityType,
            @Param("entityId") String entityId);

    /**
     * 根据实体和关联类型查询
     */
    @Select("SELECT * FROM t_entity_asset_relation " +
            "WHERE entity_type = #{entityType} AND entity_id = #{entityId} " +
            "AND relation_type = #{relationType} AND deleted = 0 " +
            "ORDER BY sequence, created_at")
    List<EntityAssetRelation> selectByEntityAndRelationType(
            @Param("entityType") String entityType,
            @Param("entityId") String entityId,
            @Param("relationType") String relationType);

    /**
     * 根据素材ID查询关联的实体
     */
    @Select("SELECT * FROM t_entity_asset_relation " +
            "WHERE asset_id = #{assetId} AND deleted = 0 " +
            "ORDER BY created_at DESC")
    List<EntityAssetRelation> selectByAssetId(@Param("assetId") String assetId);

    /**
     * 检查关联是否存在
     */
    @Select("SELECT COUNT(*) FROM t_entity_asset_relation " +
            "WHERE entity_type = #{entityType} AND entity_id = #{entityId} " +
            "AND asset_id = #{assetId} AND relation_type = #{relationType} AND deleted = 0")
    int existsRelation(
            @Param("entityType") String entityType,
            @Param("entityId") String entityId,
            @Param("assetId") String assetId,
            @Param("relationType") String relationType);

    /**
     * 统计实体关联的素材数量
     */
    @Select("SELECT COUNT(*) FROM t_entity_asset_relation " +
            "WHERE entity_type = #{entityType} AND entity_id = #{entityId} AND deleted = 0")
    int countByEntity(
            @Param("entityType") String entityType,
            @Param("entityId") String entityId);

    /**
     * 删除实体的所有关联（软删除）
     */
    @Delete("UPDATE t_entity_asset_relation SET deleted = 1 " +
            "WHERE entity_type = #{entityType} AND entity_id = #{entityId}")
    int deleteByEntity(
            @Param("entityType") String entityType,
            @Param("entityId") String entityId);

    /**
     * 删除素材的所有关联（软删除）
     */
    @Delete("UPDATE t_entity_asset_relation SET deleted = 1 WHERE asset_id = #{assetId}")
    int deleteByAssetId(@Param("assetId") String assetId);

    /**
     * 批量查询实体的 VOICE 关联（每个实体取 sequence 最小的一条）
     */
    @Select("<script>" +
            "SELECT DISTINCT ON (entity_id) * FROM t_entity_asset_relation " +
            "WHERE entity_type = #{entityType} " +
            "AND entity_id IN <foreach collection='entityIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "AND relation_type = 'VOICE' AND deleted = 0 " +
            "ORDER BY entity_id, sequence, created_at" +
            "</script>")
    List<EntityAssetRelation> selectVoiceRelationsByEntities(
            @Param("entityType") String entityType,
            @Param("entityIds") List<String> entityIds);
}
