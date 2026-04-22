package com.actionow.task.service;

import com.actionow.task.entity.Task;

import java.util.List;

/**
 * 任务优先级队列服务
 * 提供基于优先级的任务调度能力
 *
 * @author Actionow
 */
public interface TaskPriorityQueueService {

    /**
     * 优先级常量
     */
    int PRIORITY_HIGHEST = 1;   // 最高优先级（VIP用户/紧急任务）
    int PRIORITY_HIGH = 2;      // 高优先级
    int PRIORITY_NORMAL = 3;    // 普通优先级（默认）
    int PRIORITY_LOW = 4;       // 低优先级
    int PRIORITY_LOWEST = 5;    // 最低优先级（批量任务）

    /**
     * 将任务入队
     *
     * @param task 任务实体
     */
    void enqueue(Task task);

    /**
     * 将任务入队（指定优先级）
     *
     * @param task     任务实体
     * @param priority 优先级
     */
    void enqueue(Task task, int priority);

    /**
     * 获取下一个待执行任务
     * 按优先级排序，相同优先级按提交时间排序
     *
     * @return 下一个任务，无任务返回null
     */
    Task dequeue();

    /**
     * 批量获取待执行任务
     *
     * @param limit 数量限制
     * @return 任务列表
     */
    List<Task> dequeue(int limit);

    /**
     * 查看队列头部任务（不出队）
     *
     * @return 队首任务
     */
    Task peek();

    /**
     * 获取队列长度
     *
     * @return 队列长度
     */
    int size();

    /**
     * 获取指定优先级的队列长度
     *
     * @param priority 优先级
     * @return 该优先级任务数量
     */
    int size(int priority);

    /**
     * 调整任务优先级
     *
     * @param taskId      任务ID
     * @param newPriority 新优先级
     * @return 是否成功
     */
    boolean adjustPriority(String taskId, int newPriority);

    /**
     * 从队列移除任务
     *
     * @param taskId 任务ID
     * @return 是否成功
     */
    boolean remove(String taskId);

    /**
     * 检查任务是否在队列中
     *
     * @param taskId 任务ID
     * @return 是否在队列中
     */
    boolean contains(String taskId);

    /**
     * 获取任务在队列中的位置
     *
     * @param taskId 任务ID
     * @return 位置（从0开始），不存在返回-1
     */
    int getPosition(String taskId);

    /**
     * 清空队列
     */
    void clear();
}
