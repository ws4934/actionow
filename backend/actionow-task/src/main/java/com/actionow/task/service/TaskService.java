package com.actionow.task.service;

import com.actionow.common.core.result.PageResult;
import com.actionow.task.dto.*;
import com.actionow.task.entity.Task;

import java.util.List;
import java.util.Map;

/**
 * 任务服务接口
 * 提供通用任务管理功能（CRUD、状态流转）
 * AI 生成相关功能请使用 {@link AiGenerationFacade}
 *
 * @author Actionow
 */
public interface TaskService {

    /**
     * 创建任务
     */
    TaskResponse create(CreateTaskRequest request, String workspaceId, String userId);

    /**
     * 取消任务
     */
    void cancel(String taskId, String userId);

    /**
     * 重试任务
     */
    TaskResponse retry(String taskId, String userId);

    /**
     * 获取任务详情
     */
    TaskResponse getById(String taskId);

    /**
     * 获取任务实体（内部使用）
     */
    Task getEntityById(String taskId);

    /**
     * 获取工作空间的任务列表（全量）
     */
    List<TaskResponse> listByWorkspace(String workspaceId);

    /**
     * 分页查询工作空间任务
     *
     * @param workspaceId 工作空间ID
     * @param current     当前页码
     * @param size        每页大小
     * @param status      状态（可选）
     * @param type        类型（可选）
     * @param scriptId    剧本ID（可选）
     * @param entityType  实体类型（可选）
     * @return 分页结果
     */
    PageResult<TaskResponse> listByWorkspacePage(String workspaceId, Long current, Long size,
                                                  String status, String type,
                                                  String scriptId, String entityType);

    /**
     * 按状态获取任务列表
     */
    List<TaskResponse> listByWorkspaceAndStatus(String workspaceId, String status);

    /**
     * 获取用户创建的任务列表（全量）
     */
    List<TaskResponse> listByCreator(String creatorId);

    /**
     * 分页查询用户创建的任务
     *
     * @param creatorId 创建者ID
     * @param current   当前页码
     * @param size      每页大小
     * @param status    状态（可选）
     * @return 分页结果
     */
    PageResult<TaskResponse> listByCreatorPage(String creatorId, Long current, Long size, String status);

    /**
     * 更新任务进度
     */
    void updateProgress(String taskId, int progress);

    /**
     * 开始任务
     */
    void startTask(String taskId);

    /**
     * 完成任务
     */
    void completeTask(String taskId, Map<String, Object> result);

    /**
     * 任务失败
     */
    void failTask(String taskId, String errorMessage, Map<String, Object> errorDetail);

    /**
     * 获取待执行的任务
     */
    List<TaskResponse> getPendingTasks(int limit);

    /**
     * 获取运行中任务数
     */
    int getRunningTaskCount(String workspaceId);
}
