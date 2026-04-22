package com.actionow.task.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.task.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 任务 Mapper
 * 使用 LambdaQueryWrapper 确保 JacksonTypeHandler 正确处理 JSONB 字段
 * （inputParams, outputResult, errorDetail）。
 * 注意: @Select 原生 SQL 会绕过 autoResultMap，导致 JSONB 列返回 null。
 *
 * @author Actionow
 */
@Mapper
public interface TaskMapper extends BaseMapper<Task> {

    /**
     * 根据工作空间ID查询任务列表
     */
    default List<Task> selectByWorkspaceId(String workspaceId) {
        return selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getWorkspaceId, workspaceId)
                .orderByDesc(Task::getCreatedAt)
                .last("LIMIT 500"));
    }

    /**
     * 根据状态查询任务列表
     */
    default List<Task> selectByWorkspaceIdAndStatus(String workspaceId, String status) {
        return selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getWorkspaceId, workspaceId)
                .eq(Task::getStatus, status)
                .orderByDesc(Task::getPriority)
                .orderByAsc(Task::getCreatedAt)
                .last("LIMIT 500"));
    }

    /**
     * 根据创建者ID查询任务
     */
    default List<Task> selectByCreatorId(String creatorId) {
        return selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getCreatorId, creatorId)
                .orderByDesc(Task::getCreatedAt)
                .last("LIMIT 500"));
    }

    /**
     * 获取待执行的任务列表（按优先级和创建时间排序）
     */
    default List<Task> selectPendingTasks(int limit) {
        return selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getStatus, "PENDING")
                .orderByDesc(Task::getPriority)
                .orderByAsc(Task::getCreatedAt)
                .last("LIMIT " + limit));
    }

    /**
     * 根据剧本ID查询任务
     */
    default List<Task> selectByScriptId(String scriptId) {
        return selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getScriptId, scriptId)
                .orderByDesc(Task::getCreatedAt)
                .last("LIMIT 5000"));
    }

    /**
     * 根据实体ID和类型查询任务
     */
    default List<Task> selectByEntity(String entityId, String entityType) {
        return selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getEntityId, entityId)
                .eq(Task::getEntityType, entityType)
                .orderByDesc(Task::getCreatedAt)
                .last("LIMIT 5000"));
    }

    /**
     * 更新任务进度
     */
    @Update("UPDATE t_task SET progress = #{progress}, updated_at = NOW() WHERE id = #{taskId} AND deleted = 0")
    int updateProgress(@Param("taskId") String taskId, @Param("progress") int progress);

    /**
     * 更新批量任务关联字段（不触碰 version，避免与 executeTask 乐观锁竞争）
     */
    @Update("UPDATE t_task SET batch_job_id = #{batchJobId}, batch_item_id = #{batchItemId}, " +
            "source = #{source}, updated_at = NOW() WHERE id = #{taskId} AND deleted = 0")
    int updateBatchFields(@Param("taskId") String taskId,
                          @Param("batchJobId") String batchJobId,
                          @Param("batchItemId") String batchItemId,
                          @Param("source") String source);

    /**
     * 查询 POLLING 模式下 RUNNING 状态的任务（跨租户扫描）
     * <p>
     * 使用 LambdaQueryWrapper.apply() 注入 PostgreSQL JSONB 条件，
     * 确保 autoResultMap 生效，inputParams 等 JSONB 字段正常反序列化。
     * 按 started_at 升序，优先处理最早挂起的任务。
     */
    default List<Task> selectPollingRunningTasks(int limit) {
        return selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getStatus, "RUNNING")
                .apply("input_params->>'actualResponseMode' = 'POLLING'")
                .orderByAsc(Task::getStartedAt)
                .last("LIMIT " + limit));
    }

    /**
     * 查询 CALLBACK 模式下已超时的 RUNNING 任务（跨租户扫描）
     * <p>
     * 第三方回调未到达时，任务将永久卡在 RUNNING 状态。
     * 本查询扫描 timeout_at 已过期的 CALLBACK 任务，供超时补偿处理使用。
     * 按 timeout_at 升序，优先处理最早超时的任务。
     */
    default List<Task> selectCallbackTimeoutTasks(int limit) {
        return selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getStatus, "RUNNING")
                .apply("input_params->>'actualResponseMode' = 'CALLBACK'")
                .apply("((timeout_at IS NOT NULL AND timeout_at < NOW()) " +
                        "OR (timeout_at IS NULL AND started_at IS NOT NULL " +
                        "AND timeout_seconds IS NOT NULL " +
                        "AND started_at + (timeout_seconds * INTERVAL '1 second') < NOW()))")
                .orderByAsc(Task::getTimeoutAt)
                .last("LIMIT " + limit));
    }

    /**
     * 查询卡在 PENDING 状态的超时任务（跨租户扫描）
     * <p>
     * 用于处理 MQ 消息丢失场景：任务已创建、积分已冻结，但 MQ 消息未被消费导致任务永久挂起。
     * 超时判断优先级：timeout_at > created_at + 1 小时（兜底默认超时）。
     * 按 created_at 升序，优先处理最早卡住的任务。
     */
    default List<Task> selectStuckPendingTasks(int limit) {
        return selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getStatus, "PENDING")
                .apply("((timeout_at IS NOT NULL AND timeout_at < NOW()) " +
                        "OR (timeout_at IS NULL AND timeout_seconds IS NOT NULL " +
                        "AND created_at + (timeout_seconds * INTERVAL '1 second') < NOW()) " +
                        "OR (timeout_at IS NULL AND timeout_seconds IS NULL " +
                        "AND created_at < NOW() - INTERVAL '1 hour'))")
                .orderByAsc(Task::getCreatedAt)
                .last("LIMIT " + limit));
    }

    /**
     * 局部更新 inputParams JSONB（绕过 version 列，避免与 executeTask 乐观锁竞争）
     */
    @Update("UPDATE t_task SET input_params = COALESCE(input_params, '{}'::jsonb) || #{patch}::jsonb, " +
            "updated_at = NOW() WHERE id = #{taskId} AND deleted = 0")
    int patchInputParams(@Param("taskId") String taskId, @Param("patch") String patch);

    /**
     * 统计工作空间的运行中任务数
     */
    @Select("SELECT COUNT(*) FROM t_task WHERE workspace_id = #{workspaceId} AND status = 'RUNNING' AND deleted = 0")
    int countRunningTasks(@Param("workspaceId") String workspaceId);

    /**
     * 分页查询工作空间任务
     */
    default IPage<Task> selectPageByWorkspaceId(Page<Task> page, String workspaceId,
                                                 String status, String type,
                                                 String scriptId, String entityType) {
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<Task>()
                .eq(Task::getWorkspaceId, workspaceId)
                .eq(status != null && !status.isEmpty(), Task::getStatus, status)
                .eq(type != null && !type.isEmpty(), Task::getType, type)
                .eq(scriptId != null && !scriptId.isEmpty(), Task::getScriptId, scriptId)
                .eq(entityType != null && !entityType.isEmpty(), Task::getEntityType, entityType)
                .orderByDesc(Task::getCreatedAt);
        return selectPage(page, wrapper);
    }

    /**
     * 分页查询用户创建的任务
     */
    default IPage<Task> selectPageByCreatorId(Page<Task> page, String creatorId, String status) {
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<Task>()
                .eq(Task::getCreatorId, creatorId)
                .eq(status != null && !status.isEmpty(), Task::getStatus, status)
                .orderByDesc(Task::getCreatedAt);
        return selectPage(page, wrapper);
    }

}
