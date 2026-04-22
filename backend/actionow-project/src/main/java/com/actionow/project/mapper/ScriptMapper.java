package com.actionow.project.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.project.entity.Script;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 剧本 Mapper
 * 使用 LambdaQueryWrapper 确保 JacksonTypeHandler 正确处理 JSONB 字段（extraInfo）。
 *
 * @author Actionow
 */
@Mapper
public interface ScriptMapper extends BaseMapper<Script> {

    /**
     * 根据工作空间ID查询剧本列表
     */
    default List<Script> selectByWorkspaceId(String workspaceId) {
        return selectList(new LambdaQueryWrapper<Script>()
                .eq(Script::getWorkspaceId, workspaceId)
                .orderByDesc(Script::getUpdatedAt));
    }

    /**
     * 根据状态查询剧本
     */
    default List<Script> selectByStatus(String workspaceId, String status) {
        return selectList(new LambdaQueryWrapper<Script>()
                .eq(Script::getWorkspaceId, workspaceId)
                .eq(Script::getStatus, status)
                .orderByDesc(Script::getUpdatedAt));
    }

    /**
     * 统计工作空间剧本数量
     */
    @Select("SELECT COUNT(*) FROM t_script WHERE workspace_id = #{workspaceId} AND deleted = 0")
    int countByWorkspaceId(@Param("workspaceId") String workspaceId);

    /**
     * 按用户可访问范围查询剧本列表（权限过滤）
     * <p>
     * Creator/Admin 可见所有剧本；其他角色只能看到：
     * - 自己创建的剧本
     * - 在 t_script_permission 表中有任意权限记录的剧本
     * </p>
     */
    @Select("SELECT s.* FROM t_script s " +
            "WHERE s.workspace_id = #{workspaceId} AND s.deleted = 0 " +
            "AND (" +
            "  #{role} IN ('CREATOR', 'ADMIN') " +
            "  OR s.created_by = #{userId} " +
            "  OR EXISTS (" +
            "    SELECT 1 FROM t_script_permission sp " +
            "    WHERE sp.script_id = s.id AND sp.user_id = #{userId} AND sp.deleted = 0 " +
            "    AND (sp.expires_at IS NULL OR sp.expires_at > NOW())" +
            "  )" +
            ") " +
            "ORDER BY s.updated_at DESC")
    List<Script> selectAccessibleByUser(@Param("workspaceId") String workspaceId,
                                        @Param("userId") String userId,
                                        @Param("role") String role);
}
