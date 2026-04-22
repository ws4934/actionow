package com.actionow.user.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户 Mapper
 * 使用 LambdaQueryWrapper 确保 JacksonTypeHandler 正确处理 JSONB 字段（extraInfo）。
 *
 * @author Actionow
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据用户名查询
     */
    default User selectByUsername(String username) {
        return selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
    }

    /**
     * 根据邮箱查询
     */
    default User selectByEmail(String email) {
        return selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email));
    }

    /**
     * 根据手机号查询
     */
    default User selectByPhone(String phone) {
        return selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone));
    }

    /**
     * 检查用户名是否存在
     */
    @Select("SELECT COUNT(*) FROM t_user WHERE username = #{username} AND deleted = 0")
    int countByUsername(@Param("username") String username);

    /**
     * 检查邮箱是否存在
     */
    @Select("SELECT COUNT(*) FROM t_user WHERE email = #{email} AND deleted = 0")
    int countByEmail(@Param("email") String email);

    /**
     * 检查手机号是否存在
     */
    @Select("SELECT COUNT(*) FROM t_user WHERE phone = #{phone} AND deleted = 0")
    int countByPhone(@Param("phone") String phone);

    /**
     * 查询用户在指定工作空间的角色（t_workspace_member 位于 public schema，所有服务均可访问）
     */
    @Select("SELECT role FROM t_workspace_member WHERE workspace_id = #{workspaceId} AND user_id = #{userId} AND deleted = 0 LIMIT 1")
    String selectWorkspaceMemberRole(@Param("workspaceId") String workspaceId, @Param("userId") String userId);

    /**
     * 原子递增登录失败次数
     */
    @Update("UPDATE t_user SET login_fail_count = login_fail_count + 1, updated_at = NOW() WHERE id = #{userId} AND deleted = 0")
    int incrementLoginFailCount(@Param("userId") String userId);
}
