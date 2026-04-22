package com.actionow.ai.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.ai.entity.ModelProviderExecution;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 模型提供商执行记录Mapper
 * 使用 LambdaQueryWrapper 确保 JacksonTypeHandler 正确处理 JSONB 字段
 * （inputData, outputData）。
 *
 * @author Actionow
 */
@Mapper
public interface ModelProviderExecutionMapper extends BaseMapper<ModelProviderExecution> {

    /**
     * 根据外部任务ID查询
     */
    default ModelProviderExecution selectByExternalTaskId(String externalTaskId) {
        return selectOne(new LambdaQueryWrapper<ModelProviderExecution>()
                .eq(ModelProviderExecution::getExternalTaskId, externalTaskId));
    }

    /**
     * 根据外部运行ID查询
     */
    default ModelProviderExecution selectByExternalRunId(String externalRunId) {
        return selectOne(new LambdaQueryWrapper<ModelProviderExecution>()
                .eq(ModelProviderExecution::getExternalRunId, externalRunId));
    }

    /**
     * 根据任务ID查询
     */
    default List<ModelProviderExecution> selectByTaskId(String taskId) {
        return selectList(new LambdaQueryWrapper<ModelProviderExecution>()
                .eq(ModelProviderExecution::getTaskId, taskId));
    }

    /**
     * 查询待处理的执行记录（用于轮询）
     */
    default List<ModelProviderExecution> selectPendingPollingExecutions() {
        return selectList(new LambdaQueryWrapper<ModelProviderExecution>()
                .in(ModelProviderExecution::getStatus, List.of("PENDING", "RUNNING"))
                .eq(ModelProviderExecution::getResponseMode, "POLLING"));
    }

    /**
     * 查询用户的执行记录
     */
    default List<ModelProviderExecution> selectByUserId(String userId, int limit) {
        return selectList(new LambdaQueryWrapper<ModelProviderExecution>()
                .eq(ModelProviderExecution::getUserId, userId)
                .orderByDesc(ModelProviderExecution::getCreatedAt)
                .last("LIMIT " + limit));
    }

    /**
     * 查询工作空间的执行记录
     */
    default List<ModelProviderExecution> selectByWorkspaceId(String workspaceId, int limit) {
        return selectList(new LambdaQueryWrapper<ModelProviderExecution>()
                .eq(ModelProviderExecution::getWorkspaceId, workspaceId)
                .orderByDesc(ModelProviderExecution::getCreatedAt)
                .last("LIMIT " + limit));
    }
}
