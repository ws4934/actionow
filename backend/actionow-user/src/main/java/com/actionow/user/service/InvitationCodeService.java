package com.actionow.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.actionow.common.core.result.PageResult;
import com.actionow.user.dto.request.BatchCreateInvitationCodeRequest;
import com.actionow.user.dto.request.CreateInvitationCodeRequest;
import com.actionow.user.dto.request.UpdateInvitationCodeRequest;
import com.actionow.user.dto.response.*;
import com.actionow.user.entity.InvitationCode;
import com.actionow.user.entity.User;

/**
 * 邀请码服务接口
 *
 * @author Actionow
 */
public interface InvitationCodeService extends IService<InvitationCode> {

    // ==================== 管理员接口 ====================

    /**
     * 创建单个邀请码
     */
    InvitationCodeResponse create(CreateInvitationCodeRequest request, String operatorId);

    /**
     * 批量创建邀请码
     */
    BatchCreateInvitationCodeResult batchCreate(BatchCreateInvitationCodeRequest request, String operatorId);

    /**
     * 更新邀请码
     */
    InvitationCodeResponse update(String id, UpdateInvitationCodeRequest request, String operatorId);

    /**
     * 更新邀请码状态
     */
    void updateStatus(String id, String status, String operatorId);

    /**
     * 删除邀请码
     */
    void delete(String id, String operatorId);

    /**
     * 获取邀请码详情
     */
    InvitationCodeResponse getById(String id);

    /**
     * 分页查询邀请码列表
     */
    PageResult<InvitationCodeResponse> listPage(int page, int size, String type, String status, String keyword);

    /**
     * 获取邀请码使用记录
     */
    PageResult<InvitationCodeUsageResponse> getUsages(String id, int page, int size);

    /**
     * 获取统计信息
     */
    InvitationCodeStatisticsResponse getStatistics();

    // ==================== 用户接口 ====================

    /**
     * 获取用户当前有效的邀请码
     */
    InvitationCode getUserActiveCode(String userId);

    /**
     * 为用户生成专属邀请码
     */
    InvitationCode generateUserCode(String userId);

    /**
     * 刷新用户邀请码（旧码失效）
     */
    InvitationCode refreshUserCode(String userId);

    /**
     * 获取用户邀请码响应
     */
    UserInvitationCodeResponse getUserCodeResponse(String userId);

    /**
     * 获取用户邀请的人列表
     */
    PageResult<InviteeResponse> getInvitees(String userId, int page, int size);

    /**
     * 获取用户的邀请人信息
     */
    InviterResponse getInviter(String userId);

    // ==================== 公共接口 ====================

    /**
     * 根据邀请码查询
     */
    InvitationCode findByCode(String code);

    /**
     * 验证邀请码
     */
    InvitationCodeValidateResponse validateCode(String code);

    /**
     * 使用邀请码
     */
    void useCode(String code, User invitee, String ipAddress, String userAgent);

    /**
     * 检查邀请码是否存在
     */
    boolean existsByCode(String code);

    /**
     * 获取注册配置
     */
    RegistrationConfigResponse getRegistrationConfig();

    /**
     * 检查是否需要邀请码注册
     */
    boolean isInvitationCodeRequired();

    /**
     * 检查是否允许使用用户邀请码
     */
    boolean isUserCodeAllowed();

    /**
     * 检查是否为用户生成邀请码
     */
    boolean isUserCodeEnabled();
}
