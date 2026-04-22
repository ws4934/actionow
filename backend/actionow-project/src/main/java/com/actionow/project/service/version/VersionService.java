package com.actionow.project.service.version;

import com.actionow.project.dto.version.RestoreVersionRequest;
import com.actionow.project.dto.version.VersionInfoResponse;

import java.util.List;

/**
 * 版本服务接口
 * 定义通用的版本控制操作
 *
 * @param <T> 主实体类型
 * @param <D> 版本详情响应类型
 * @author Actionow
 */
public interface VersionService<T, D> {

    /**
     * 获取实体的所有版本列表
     *
     * @param entityId 实体ID
     * @return 版本列表（按版本号降序）
     */
    List<VersionInfoResponse> listVersions(String entityId);

    /**
     * 获取指定版本的详情
     *
     * @param entityId      实体ID
     * @param versionNumber 版本号
     * @return 版本详情
     */
    D getVersion(String entityId, Integer versionNumber);

    /**
     * 获取实体的当前版本详情
     *
     * @param entityId 实体ID
     * @return 当前版本详情
     */
    D getCurrentVersion(String entityId);

    /**
     * 恢复到指定版本
     * 会创建一个新版本，内容为恢复目标版本的快照
     *
     * @param entityId 实体ID
     * @param request  恢复请求
     * @param userId   操作用户ID
     * @return 新创建的版本号
     */
    Integer restoreVersion(String entityId, RestoreVersionRequest request, String userId);

    /**
     * 比较两个版本的差异
     *
     * @param entityId       实体ID
     * @param versionNumber1 版本号1
     * @param versionNumber2 版本号2
     * @return 差异信息
     */
    VersionDiffResponse compareVersions(String entityId, Integer versionNumber1, Integer versionNumber2);

    /**
     * 为实体创建新版本快照
     * 在实体更新前调用，保存当前状态作为历史版本
     *
     * @param entity        当前实体状态
     * @param changeSummary 变更摘要
     * @param userId        操作用户ID
     * @return 新版本的ID
     */
    String createVersionSnapshot(T entity, String changeSummary, String userId);
}
