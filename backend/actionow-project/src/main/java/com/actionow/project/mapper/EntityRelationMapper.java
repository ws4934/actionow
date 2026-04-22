package com.actionow.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.project.entity.EntityRelation;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实体关系 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface EntityRelationMapper extends BaseMapper<EntityRelation> {

    /**
     * 查询源实体的所有关系（出向）
     */
    @Select("SELECT * FROM t_entity_relation " +
            "WHERE source_type = #{sourceType} AND source_id = #{sourceId} AND deleted = 0 " +
            "ORDER BY relation_type, sequence, created_at")
    List<EntityRelation> selectBySource(
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId);

    /**
     * 查询源实体指定类型的关系
     */
    @Select("SELECT * FROM t_entity_relation " +
            "WHERE source_type = #{sourceType} AND source_id = #{sourceId} " +
            "AND relation_type = #{relationType} AND deleted = 0 " +
            "ORDER BY sequence, created_at")
    List<EntityRelation> selectBySourceAndRelationType(
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId,
            @Param("relationType") String relationType);

    /**
     * 查询源实体指定目标类型的关系
     */
    @Select("SELECT * FROM t_entity_relation " +
            "WHERE source_type = #{sourceType} AND source_id = #{sourceId} " +
            "AND target_type = #{targetType} AND deleted = 0 " +
            "ORDER BY sequence, created_at")
    List<EntityRelation> selectBySourceAndTargetType(
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId,
            @Param("targetType") String targetType);

    /**
     * 查询目标实体的所有关系（入向）
     */
    @Select("SELECT * FROM t_entity_relation " +
            "WHERE target_type = #{targetType} AND target_id = #{targetId} AND deleted = 0 " +
            "ORDER BY relation_type, sequence, created_at")
    List<EntityRelation> selectByTarget(
            @Param("targetType") String targetType,
            @Param("targetId") String targetId);

    /**
     * 查询目标实体指定类型的关系
     */
    @Select("SELECT * FROM t_entity_relation " +
            "WHERE target_type = #{targetType} AND target_id = #{targetId} " +
            "AND relation_type = #{relationType} AND deleted = 0 " +
            "ORDER BY sequence, created_at")
    List<EntityRelation> selectByTargetAndRelationType(
            @Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("relationType") String relationType);

    /**
     * 查询两个实体之间的关系
     */
    @Select("SELECT * FROM t_entity_relation " +
            "WHERE source_type = #{sourceType} AND source_id = #{sourceId} " +
            "AND target_type = #{targetType} AND target_id = #{targetId} AND deleted = 0 " +
            "ORDER BY relation_type, sequence")
    List<EntityRelation> selectBetweenEntities(
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId);

    /**
     * 查询两个实体之间指定类型的关系
     */
    @Select("SELECT * FROM t_entity_relation " +
            "WHERE source_type = #{sourceType} AND source_id = #{sourceId} " +
            "AND target_type = #{targetType} AND target_id = #{targetId} " +
            "AND relation_type = #{relationType} AND deleted = 0")
    EntityRelation selectBetweenEntitiesWithType(
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("relationType") String relationType);

    /**
     * 检查关系是否存在
     */
    @Select("SELECT COUNT(*) FROM t_entity_relation " +
            "WHERE source_type = #{sourceType} AND source_id = #{sourceId} " +
            "AND target_type = #{targetType} AND target_id = #{targetId} " +
            "AND relation_type = #{relationType} AND deleted = 0")
    int existsRelation(
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("relationType") String relationType);

    /**
     * 统计源实体的关系数量
     */
    @Select("SELECT COUNT(*) FROM t_entity_relation " +
            "WHERE source_type = #{sourceType} AND source_id = #{sourceId} AND deleted = 0")
    int countBySource(
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId);

    /**
     * 统计源实体指定类型的关系数量
     */
    @Select("SELECT COUNT(*) FROM t_entity_relation " +
            "WHERE source_type = #{sourceType} AND source_id = #{sourceId} " +
            "AND relation_type = #{relationType} AND deleted = 0")
    int countBySourceAndRelationType(
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId,
            @Param("relationType") String relationType);

    /**
     * 删除源实体的所有关系（硬删除，因为唯一约束不含deleted字段）
     */
    @Delete("DELETE FROM t_entity_relation " +
            "WHERE source_type = #{sourceType} AND source_id = #{sourceId}")
    int deleteBySource(
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId);

    /**
     * 删除源实体指定类型的关系（硬删除）
     */
    @Delete("DELETE FROM t_entity_relation " +
            "WHERE source_type = #{sourceType} AND source_id = #{sourceId} " +
            "AND relation_type = #{relationType}")
    int deleteBySourceAndRelationType(
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId,
            @Param("relationType") String relationType);

    /**
     * 删除目标实体的所有关系（硬删除）
     */
    @Delete("DELETE FROM t_entity_relation " +
            "WHERE target_type = #{targetType} AND target_id = #{targetId}")
    int deleteByTarget(
            @Param("targetType") String targetType,
            @Param("targetId") String targetId);

    /**
     * 删除两个实体之间的关系（硬删除）
     */
    @Delete("DELETE FROM t_entity_relation " +
            "WHERE source_type = #{sourceType} AND source_id = #{sourceId} " +
            "AND target_type = #{targetType} AND target_id = #{targetId}")
    int deleteBetweenEntities(
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId);

    /**
     * 删除两个实体之间指定类型的关系（硬删除）
     */
    @Delete("DELETE FROM t_entity_relation " +
            "WHERE source_type = #{sourceType} AND source_id = #{sourceId} " +
            "AND target_type = #{targetType} AND target_id = #{targetId} " +
            "AND relation_type = #{relationType}")
    int deleteBetweenEntitiesWithType(
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("relationType") String relationType);

    /**
     * 批量查询多个源实体的关系
     */
    @Select("<script>" +
            "SELECT * FROM t_entity_relation " +
            "WHERE source_type = #{sourceType} " +
            "AND source_id IN <foreach collection='sourceIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "AND deleted = 0 " +
            "ORDER BY source_id, relation_type, sequence" +
            "</script>")
    List<EntityRelation> selectBySourceIds(
            @Param("sourceType") String sourceType,
            @Param("sourceIds") List<String> sourceIds);

    /**
     * 获取关系的最大序号
     */
    @Select("SELECT COALESCE(MAX(sequence), 0) FROM t_entity_relation " +
            "WHERE source_type = #{sourceType} AND source_id = #{sourceId} " +
            "AND relation_type = #{relationType} AND deleted = 0")
    int getMaxSequence(
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId,
            @Param("relationType") String relationType);

    /**
     * 根据ID硬删除关系（绕过 @TableLogic 软删除）
     */
    @Delete("DELETE FROM t_entity_relation WHERE id = #{id}")
    int hardDeleteById(@Param("id") String id);
}
