package com.actionow.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.actionow.project.dto.asset.*;
import com.actionow.project.entity.Asset;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 素材服务接口
 * 负责素材实体的CRUD操作，文件操作委托给 actionow-asset 服务
 *
 * @author Actionow
 */
public interface AssetService {

    /**
     * 创建素材（元数据）
     * 实际文件上传由前端直接调用 actionow-asset 的预签名接口
     */
    AssetResponse create(CreateAssetRequest request, String workspaceId, String userId);

    /**
     * 创建素材（元数据）- 支持跳过Canvas同步
     * @param skipCanvasSync 是否跳过Canvas同步（从Canvas创建时传true防止循环）
     */
    AssetResponse create(CreateAssetRequest request, String workspaceId, String userId, boolean skipCanvasSync);

    /**
     * 更新素材信息
     */
    AssetResponse update(String assetId, UpdateAssetRequest request, String userId);

    /**
     * 删除素材
     */
    void delete(String assetId, String userId);

    /**
     * 获取素材详情
     */
    AssetResponse getById(String assetId);

    /**
     * 根据ID获取实体
     */
    Optional<Asset> findById(String assetId);

    /**
     * 获取工作空间的素材列表
     */
    List<AssetResponse> listByWorkspace(String workspaceId);

    /**
     * 根据剧本ID获取素材列表
     */
    List<AssetResponse> listByScript(String scriptId);

    /**
     * 分页查询素材
     */
    Page<AssetResponse> queryAssets(AssetQueryRequest request, String workspaceId);

    /**
     * 根据素材类型获取列表
     */
    List<AssetResponse> listByType(String workspaceId, String assetType);

    /**
     * 更新生成状态（AI生成回调）
     */
    void updateGenerationStatus(String assetId, String status, String userId);

    /**
     * 更新文件信息（上传完成后回调）
     */
    void updateFileInfo(String assetId, String fileKey, String fileUrl, String thumbnailUrl,
                        Long fileSize, String mimeType, Map<String, Object> metaInfo);

    /**
     * 更新文件信息（AI生成完成后回调）
     * @param generateThumbnail 是否自动生成缩略图（当 thumbnailUrl 为空且是图片类型时）
     */
    void updateFileInfo(String assetId, String fileKey, String fileUrl, String thumbnailUrl,
                        Long fileSize, String mimeType, Map<String, Object> metaInfo, Boolean generateThumbnail);

    /**
     * 更新素材扩展信息
     * 用于存储生成参数（供重试使用）
     */
    void updateExtraInfo(String assetId, Map<String, Object> extraInfo);

    /**
     * 根据任务ID查找素材
     */
    Optional<Asset> findByTaskId(String taskId);

    /**
     * 批量获取素材
     */
    List<AssetResponse> batchGet(List<String> assetIds);

    /**
     * 为灵感模式创建素材记录
     * 直接在 t_asset 中创建，scope=WORKSPACE, source=AI_GENERATED, generationStatus=COMPLETED
     * 不触发 Canvas/Collab 事件（灵感资产不关联剧本）。
     *
     * <p><b>已 deprecated</b>：灵感子系统被 Asset+EntityRelation 统一流程取代后，
     * 此桥接方法将随 Inspiration 一同下线。新的 AI 生成入口请直接走常规 Asset 创建路径。
     */
    @Deprecated(since = "3.0", forRemoval = true)
    Asset createForInspiration(String url, String thumbnailUrl, String assetType,
                               String mimeType, Long fileSize,
                               Map<String, Object> metaInfo, String workspaceId, String userId);

    // ==================== 游离素材管理 ====================

    /**
     * 查询剧本下的游离素材（未挂载到任何实体的素材）
     *
     * @param scriptId    剧本ID
     * @param workspaceId 工作空间ID
     * @param assetType   素材类型（可选）
     * @param page        页码
     * @param size        每页大小
     * @return 分页的游离素材列表
     */
    Page<AssetResponse> listUnattachedByScript(String scriptId, String workspaceId, String assetType, int page, int size);

    /**
     * 复制素材
     * 基于源素材创建新 Asset 记录（共享同一 fileKey/fileUrl）
     *
     * @param assetId     源素材ID
     * @param request     复制请求
     * @param workspaceId 工作空间ID
     * @param userId      用户ID
     * @return 新素材信息
     */
    AssetResponse copyAsset(String assetId, CopyAssetRequest request, String workspaceId, String userId);

    // ==================== 上传相关 ====================

    /**
     * 初始化素材上传
     * 一次请求完成：创建素材记录（状态为 PENDING） + 获取预签名上传 URL
     *
     * @param request     上传初始化请求
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID
     * @return 包含素材信息和预签名 URL 的响应
     */
    AssetUploadInitResponse initUpload(AssetUploadInitRequest request, String workspaceId, String userId);

    /**
     * 确认素材上传完成
     * 验证文件已上传到 OSS，更新素材状态和文件信息
     *
     * @param assetId 素材 ID
     * @param request 确认请求
     * @param userId  用户 ID
     * @return 更新后的素材信息
     */
    AssetResponse confirmUpload(String assetId, AssetUploadConfirmRequest request, String userId);

    // ==================== 回收站相关 ====================

    /**
     * 获取回收站中的素材列表
     *
     * @param workspaceId 工作空间 ID
     * @param page        页码
     * @param size        每页大小
     * @return 分页的已删除素材列表
     */
    Page<AssetResponse> listTrash(String workspaceId, int page, int size);

    /**
     * 从回收站恢复素材
     *
     * @param assetId 素材 ID
     * @param userId  用户 ID
     * @return 恢复后的素材信息
     */
    AssetResponse restoreFromTrash(String assetId, String userId);

    /**
     * 永久删除素材（从回收站彻底删除）
     *
     * @param assetId 素材 ID
     * @param userId  用户 ID
     */
    void permanentDelete(String assetId, String userId);

    /**
     * 清空回收站
     *
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID
     * @return 删除的素材数量
     */
    int emptyTrash(String workspaceId, String userId);

    /**
     * 清理过期的回收站素材（定时任务调用）
     *
     * @param retentionDays 保留天数
     * @return 清理的素材数量
     */
    int cleanupExpiredTrash(int retentionDays);
}
