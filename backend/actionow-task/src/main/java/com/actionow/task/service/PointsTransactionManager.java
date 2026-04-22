package com.actionow.task.service;

/**
 * 积分事务管理器
 * 提供冻结、解冻、确认消费操作，并自动创建补偿任务处理失败情况
 *
 * @author Actionow
 */
public interface PointsTransactionManager {

    /**
     * 冻结积分（同步操作，失败直接抛异常）
     *
     * @param workspaceId  工作空间 ID
     * @param userId       用户 ID
     * @param amount       冻结金额
     * @param businessType 业务类型
     * @param businessId   业务 ID
     * @param remark       备注
     * @return 冻结事务 ID
     * @throws RuntimeException 冻结失败时抛出异常
     */
    String freezePoints(String workspaceId, String userId, Long amount,
                        String businessType, String businessId, String remark);

    /**
     * 解冻积分（异步操作，失败自动创建补偿任务）
     *
     * @param workspaceId   工作空间 ID
     * @param userId        操作人 ID
     * @param businessId    业务 ID
     * @param businessType  业务类型
     * @param remark        备注
     * @param frozenAmount  原始冻结金额（用于退还成员配额，null 表示无需退还配额）
     */
    void unfreezePointsAsync(String workspaceId, String userId,
                             String businessId, String businessType, String remark,
                             Long frozenAmount);

    /**
     * 确认消费（异步操作，失败自动创建补偿任务）
     *
     * @param workspaceId   工作空间 ID
     * @param userId        操作人 ID
     * @param businessId    业务 ID
     * @param businessType  业务类型
     * @param actualAmount  实际消费金额
     * @param remark        备注
     */
    void confirmConsumeAsync(String workspaceId, String userId,
                             String businessId, String businessType, Long actualAmount, String remark);

    /**
     * 执行补偿任务重试
     *
     * @param compensationTaskId 补偿任务 ID
     */
    void retryCompensation(String compensationTaskId);
}
