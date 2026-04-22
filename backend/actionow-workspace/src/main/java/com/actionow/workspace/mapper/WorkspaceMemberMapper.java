package com.actionow.workspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.workspace.entity.WorkspaceMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作空间成员 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface WorkspaceMemberMapper extends BaseMapper<WorkspaceMember> {

    /**
     * 根据工作空间ID查询所有成员
     */
    @Select("SELECT * FROM t_workspace_member WHERE workspace_id = #{workspaceId} AND deleted = 0")
    List<WorkspaceMember> selectByWorkspaceId(@Param("workspaceId") String workspaceId);

    /**
     * 分页查询工作空间成员
     */
    @Select("<script>" +
            "SELECT * FROM t_workspace_member WHERE workspace_id = #{workspaceId} AND deleted = 0" +
            "<if test='role != null'> AND role = #{role}</if>" +
            " ORDER BY joined_at DESC" +
            "</script>")
    IPage<WorkspaceMember> selectPageByWorkspaceId(Page<WorkspaceMember> page,
                                                    @Param("workspaceId") String workspaceId,
                                                    @Param("role") String role);

    /**
     * 统计工作空间成员数量（带角色筛选）
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM t_workspace_member WHERE workspace_id = #{workspaceId} AND deleted = 0" +
            "<if test='role != null'> AND role = #{role}</if>" +
            "</script>")
    long countByWorkspaceIdAndRole(@Param("workspaceId") String workspaceId, @Param("role") String role);

    /**
     * 根据用户ID查询所有加入的工作空间
     */
    @Select("SELECT * FROM t_workspace_member WHERE user_id = #{userId} AND deleted = 0")
    List<WorkspaceMember> selectByUserId(@Param("userId") String userId);

    /**
     * 查询用户在工作空间的成员信息
     */
    @Select("SELECT * FROM t_workspace_member WHERE workspace_id = #{workspaceId} AND user_id = #{userId} AND deleted = 0")
    WorkspaceMember selectByWorkspaceAndUser(@Param("workspaceId") String workspaceId, @Param("userId") String userId);

    /**
     * 查询用户在工作空间的成员信息（包含已删除记录）
     */
    @Select("SELECT * FROM t_workspace_member WHERE workspace_id = #{workspaceId} AND user_id = #{userId} LIMIT 1")
    WorkspaceMember selectAnyByWorkspaceAndUser(@Param("workspaceId") String workspaceId, @Param("userId") String userId);

    /**
     * 根据成员记录ID查询工作空间成员信息
     */
    @Select("SELECT * FROM t_workspace_member WHERE id = #{memberId} AND workspace_id = #{workspaceId} AND deleted = 0")
    WorkspaceMember selectByIdAndWorkspace(@Param("memberId") String memberId, @Param("workspaceId") String workspaceId);

    /**
     * 逻辑删除成员记录
     */
    @Update("UPDATE t_workspace_member " +
            "SET deleted = 1, deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1 " +
            "WHERE id = #{memberId} AND deleted = 0")
    int softDeleteById(@Param("memberId") String memberId);

    /**
     * 恢复已删除的成员记录
     */
    @Update("UPDATE t_workspace_member " +
            "SET deleted = 0, deleted_at = NULL, role = #{role}, status = #{status}, nickname = #{nickname}, " +
            "invited_by = #{invitedBy}, joined_at = #{joinedAt}, updated_at = CURRENT_TIMESTAMP, version = version + 1 " +
            "WHERE id = #{memberId} AND deleted = 1")
    int restoreDeletedMember(@Param("memberId") String memberId,
                             @Param("role") String role,
                             @Param("status") String status,
                             @Param("nickname") String nickname,
                             @Param("invitedBy") String invitedBy,
                             @Param("joinedAt") LocalDateTime joinedAt);

    /**
     * 统计工作空间成员数量
     */
    @Select("SELECT COUNT(*) FROM t_workspace_member WHERE workspace_id = #{workspaceId} AND deleted = 0")
    int countByWorkspaceId(@Param("workspaceId") String workspaceId);

    /**
     * 按工作空间批量软删除所有成员（工作空间删除时级联使用）
     */
    @Update("UPDATE t_workspace_member " +
            "SET deleted = 1, deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, version = version + 1 " +
            "WHERE workspace_id = #{workspaceId} AND deleted = 0")
    int softDeleteByWorkspaceId(@Param("workspaceId") String workspaceId);
}
