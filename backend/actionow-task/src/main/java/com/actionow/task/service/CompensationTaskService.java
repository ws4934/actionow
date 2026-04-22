package com.actionow.task.service;

import com.actionow.task.constant.CompensationType;

/**
 * 补偿任务创建服务
 * <p>
 * 独立 Bean 的目的：使 {@link org.springframework.transaction.annotation.Transactional} 的
 * {@code REQUIRES_NEW} 传播语义生效。若将此方法留在
 * {@link com.actionow.task.service.impl.PointsTransactionManagerImpl} 内部，
 * 同类方法互调会绕过 Spring AOP 代理，导致 {@code @Transactional} 注解失效，
 * 调用方（例如 {@code retryCompensation} 的外层事务回滚时）所创建的补偿任务记录将一并丢失，
 * 远程已提交的钱包侧操作将失去兜底。
 *
 * @author Actionow
 */
public interface CompensationTaskService {

    /**
     * 创建补偿任务（独立事务，不受调用方事务回滚影响）。
     *
     * @param type         补偿类型
     * @param workspaceId  工作空间 ID
     * @param userId       操作人 ID
     * @param businessId   业务 ID
     * @param businessType 业务类型
     * @param amount       相关金额（可为空）
     * @param remark       备注
     * @param errorMessage 触发补偿的错误描述
     */
    void createCompensationTask(CompensationType type, String workspaceId, String userId,
                                String businessId, String businessType,
                                Long amount, String remark, String errorMessage);
}
