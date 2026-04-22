package com.actionow.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.actionow.task.entity.CompensationTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 补偿任务 Mapper
 *
 * @author Actionow
 */
@Mapper
public interface CompensationTaskMapper extends BaseMapper<CompensationTask> {

    /**
     * 查询待重试的补偿任务
     * 条件：状态为 PENDING 或 PROCESSING，且重试时间已到
     *
     * @param now   当前时间
     * @param limit 查询数量限制
     * @return 待重试任务列表
     */
    @Select("SELECT * FROM t_compensation_task " +
            "WHERE status IN ('PENDING', 'PROCESSING') " +
            "AND (next_retry_at IS NULL OR next_retry_at <= #{now}) " +
            "ORDER BY next_retry_at ASC NULLS FIRST, created_at ASC " +
            "LIMIT #{limit}")
    List<CompensationTask> selectPendingRetryTasks(@Param("now") LocalDateTime now,
                                                   @Param("limit") int limit);

    /**
     * 尝试锁定任务（乐观锁方式）
     * 将状态从 PENDING 更新为 PROCESSING
     *
     * @param taskId  任务 ID
     * @param version 当前版本号
     * @return 更新行数
     */
    @Update("UPDATE t_compensation_task " +
            "SET status = 'PROCESSING', version = version + 1, updated_at = NOW() " +
            "WHERE id = #{taskId} AND version = #{version} AND status = 'PENDING'")
    int tryLockTask(@Param("taskId") String taskId, @Param("version") int version);

    /**
     * 标记任务成功完成
     *
     * @param taskId 任务 ID
     * @return 更新行数
     */
    @Update("UPDATE t_compensation_task " +
            "SET status = 'COMPLETED', completed_at = NOW(), updated_at = NOW() " +
            "WHERE id = #{taskId}")
    int markCompleted(@Param("taskId") String taskId);

    /**
     * 标记任务重试失败，更新下次重试时间
     *
     * @param taskId      任务 ID
     * @param lastError   错误信息
     * @param nextRetryAt 下次重试时间
     * @return 更新行数
     */
    @Update("UPDATE t_compensation_task " +
            "SET status = 'PENDING', retry_count = retry_count + 1, " +
            "last_error = #{lastError}, next_retry_at = #{nextRetryAt}, updated_at = NOW() " +
            "WHERE id = #{taskId}")
    int markRetryFailed(@Param("taskId") String taskId,
                        @Param("lastError") String lastError,
                        @Param("nextRetryAt") LocalDateTime nextRetryAt);

    /**
     * 标记任务重试耗尽
     *
     * @param taskId    任务 ID
     * @param lastError 最后错误信息
     * @return 更新行数
     */
    @Update("UPDATE t_compensation_task " +
            "SET status = 'EXHAUSTED', last_error = #{lastError}, updated_at = NOW() " +
            "WHERE id = #{taskId}")
    int markExhausted(@Param("taskId") String taskId, @Param("lastError") String lastError);

    /**
     * 统计各状态的补偿任务数
     *
     * @param workspaceId 工作空间 ID（可选，为 null 则统计全部）
     * @param status      状态
     * @return 任务数
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM t_compensation_task WHERE status = #{status} " +
            "<if test='workspaceId != null'> AND workspace_id = #{workspaceId}</if>" +
            "</script>")
    int countByStatus(@Param("workspaceId") String workspaceId, @Param("status") String status);
}
