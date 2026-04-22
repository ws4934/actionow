package com.actionow.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工作空间Schema查询 Mapper
 * 用于定时任务跨租户查询
 * 直接查询 public schema 的 t_workspace 表
 *
 * @author Actionow
 */
@Mapper
public interface WorkspaceSchemaMapper {

    /**
     * 查询所有活跃工作空间的Schema名称
     * 只返回状态为 Active 且未删除的工作空间
     */
    @Select("SELECT schema_name FROM public.t_workspace WHERE status = 'ACTIVE' AND deleted = 0 AND schema_name IS NOT NULL")
    List<String> selectAllActiveSchemas();

    /**
     * 查询工作空间是否允许普通成员创建剧本
     * 读取 config->'permissions'->>'memberCanCreateScript'，null 时视为 true（默认允许）
     *
     * @param workspaceId 工作空间ID
     * @return true=允许，false=不允许，null=配置不存在（调用方应视为 true）
     */
    @Select("SELECT (config->'permissions'->>'memberCanCreateScript')::boolean " +
            "FROM public.t_workspace WHERE id = #{workspaceId} AND deleted = 0")
    Boolean selectMemberCanCreateScript(@Param("workspaceId") String workspaceId);
}
