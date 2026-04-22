package com.actionow.wallet.service;

import com.actionow.wallet.dto.QuotaRequest;
import com.actionow.wallet.dto.QuotaResponse;

import java.util.List;

/**
 * 成员配额服务接口
 *
 * @author Actionow
 */
public interface QuotaService {

    /**
     * 设置成员配额
     */
    QuotaResponse setQuota(String workspaceId, QuotaRequest request, String operatorId);

    /**
     * 获取成员配额
     */
    QuotaResponse getQuota(String workspaceId, String userId);

    /**
     * 获取或创建成员配额
     */
    QuotaResponse getOrCreateQuota(String workspaceId, String userId);

    /**
     * 获取工作空间所有成员配额
     */
    List<QuotaResponse> listQuotas(String workspaceId);

    /**
     * 检查配额是否足够
     */
    boolean hasEnoughQuota(String workspaceId, String userId, long amount);

    /**
     * 使用配额
     */
    boolean useQuota(String workspaceId, String userId, long amount);

    /**
     * 退还配额
     */
    boolean refundQuota(String workspaceId, String userId, long amount);

    /**
     * 删除配额
     */
    void deleteQuota(String workspaceId, String userId, String operatorId);

    /**
     * 重置单个用户配额
     */
    QuotaResponse resetQuota(String workspaceId, String userId, String operatorId);

    /**
     * 重置工作空间配额（新周期）
     */
    void resetWorkspaceQuotas(String workspaceId);

    /**
     * 按计划类型批量调整工作空间成员配额上限
     * 在计划升级/降级时调用，确保配额限制与当前计划一致
     *
     * @param workspaceId 工作空间 ID
     * @param planType    新的计划类型（Free/Basic/Pro/Enterprise）
     */
    void adjustQuotasForPlan(String workspaceId, String planType);
}
