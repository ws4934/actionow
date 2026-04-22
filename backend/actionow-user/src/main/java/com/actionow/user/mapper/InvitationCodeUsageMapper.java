package com.actionow.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.user.entity.InvitationCodeUsage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 邀请码使用记录 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface InvitationCodeUsageMapper extends BaseMapper<InvitationCodeUsage> {

    /**
     * 根据邀请码ID分页查询使用记录
     */
    @Select("SELECT * FROM t_invitation_code_usage WHERE invitation_code_id = #{codeId} ORDER BY used_at DESC")
    IPage<InvitationCodeUsage> selectByCodeId(Page<InvitationCodeUsage> page, @Param("codeId") String codeId);

    /**
     * 根据邀请人ID分页查询使用记录
     */
    @Select("SELECT * FROM t_invitation_code_usage WHERE inviter_id = #{inviterId} ORDER BY used_at DESC")
    IPage<InvitationCodeUsage> selectByInviterId(Page<InvitationCodeUsage> page, @Param("inviterId") String inviterId);

    /**
     * 根据被邀请人ID查询
     */
    @Select("SELECT * FROM t_invitation_code_usage WHERE invitee_id = #{inviteeId} LIMIT 1")
    InvitationCodeUsage selectByInviteeId(@Param("inviteeId") String inviteeId);

    /**
     * 统计邀请人的邀请数量
     */
    @Select("SELECT COUNT(*) FROM t_invitation_code_usage WHERE inviter_id = #{inviterId}")
    int countByInviterId(@Param("inviterId") String inviterId);

    /**
     * 统计邀请码的使用次数
     */
    @Select("SELECT COUNT(*) FROM t_invitation_code_usage WHERE invitation_code_id = #{codeId}")
    int countByCodeId(@Param("codeId") String codeId);
}
