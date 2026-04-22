package com.actionow.workspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.workspace.entity.WorkspaceInvitation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 工作空间邀请 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface WorkspaceInvitationMapper extends BaseMapper<WorkspaceInvitation> {

    /**
     * 根据邀请码查询有效邀请
     */
    @Select("SELECT * FROM t_workspace_invitation WHERE code = #{code} AND status = 'ACTIVE' AND deleted = 0 AND expires_at > NOW()")
    WorkspaceInvitation selectByCode(@Param("code") String code);

    /**
     * 根据工作空间ID查询所有邀请
     */
    @Select("SELECT * FROM t_workspace_invitation WHERE workspace_id = #{workspaceId} AND deleted = 0 ORDER BY created_at DESC")
    List<WorkspaceInvitation> selectByWorkspaceId(@Param("workspaceId") String workspaceId);

    /**
     * 根据工作空间ID分页查询邀请
     */
    @Select("SELECT * FROM t_workspace_invitation WHERE workspace_id = #{workspaceId} AND deleted = 0 ORDER BY created_at DESC")
    IPage<WorkspaceInvitation> selectPageByWorkspaceId(Page<WorkspaceInvitation> page, @Param("workspaceId") String workspaceId);

    /**
     * 增加邀请使用次数
     */
    @Update("UPDATE t_workspace_invitation SET used_count = used_count + 1 WHERE id = #{id}")
    int incrementUsedCount(@Param("id") String id);

    /**
     * 禁用邀请
     */
    @Update("UPDATE t_workspace_invitation SET status = 'DISABLED' WHERE id = #{id}")
    int disableInvitation(@Param("id") String id);

    /**
     * 检查邀请码是否已存在（包括已过期/已禁用的）
     */
    @Select("SELECT COUNT(*) FROM t_workspace_invitation WHERE code = #{code}")
    int countByCode(@Param("code") String code);

    /**
     * 按工作空间批量禁用所有活跃邀请（工作空间删除时级联使用）
     */
    @Update("UPDATE t_workspace_invitation SET status = 'DISABLED' " +
            "WHERE workspace_id = #{workspaceId} AND status = 'ACTIVE'")
    int disableByWorkspaceId(@Param("workspaceId") String workspaceId);
}
