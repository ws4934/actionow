package com.actionow.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.project.entity.ScriptPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 剧本权限 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface ScriptPermissionMapper extends BaseMapper<ScriptPermission> {

    /**
     * 查询用户在指定剧本上的有效权限记录（排除已过期和已软删除）
     */
    @Select("SELECT * FROM t_script_permission " +
            "WHERE script_id = #{scriptId} AND user_id = #{userId} AND deleted = 0 " +
            "AND (expires_at IS NULL OR expires_at > NOW()) LIMIT 1")
    ScriptPermission selectByScriptAndUser(@Param("scriptId") String scriptId, @Param("userId") String userId);

    /**
     * 查询指定剧本的所有有效权限列表（含已过期权限，供管理员审视）
     */
    @Select("SELECT * FROM t_script_permission WHERE script_id = #{scriptId} AND deleted = 0 ORDER BY granted_at ASC")
    List<ScriptPermission> selectByScriptId(@Param("scriptId") String scriptId);

    /**
     * 查询用户在指定剧本上的有效权限类型（排除已过期，用于快速鉴权）
     */
    @Select("SELECT permission_type FROM t_script_permission " +
            "WHERE script_id = #{scriptId} AND user_id = #{userId} AND deleted = 0 " +
            "AND (expires_at IS NULL OR expires_at > NOW()) LIMIT 1")
    String selectPermissionType(@Param("scriptId") String scriptId, @Param("userId") String userId);

    /**
     * 检查用户是否有任意有效权限（用于 EXISTS 检查）
     */
    @Select("SELECT COUNT(*) FROM t_script_permission " +
            "WHERE script_id = #{scriptId} AND user_id = #{userId} AND deleted = 0 " +
            "AND (expires_at IS NULL OR expires_at > NOW())")
    int countByScriptAndUser(@Param("scriptId") String scriptId, @Param("userId") String userId);
}
