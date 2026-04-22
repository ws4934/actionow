package com.actionow.task.feign;

import com.actionow.common.core.result.Result;
import com.actionow.task.dto.AssetInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 素材服务 Feign 客户端降级工厂
 *
 * @author Actionow
 */
@Slf4j
@Component
public class AssetFeignClientFallbackFactory implements FallbackFactory<AssetFeignClient> {

    @Override
    public AssetFeignClient create(Throwable cause) {
        log.error("调用素材服务失败: {}", cause.getMessage());
        return new AssetFeignClient() {
            @Override
            public Result<Map<String, Object>> createAsset(String workspaceId, String userId,
                                                            Map<String, Object> request) {
                log.warn("创建素材降级: workspaceId={}", workspaceId);
                return Result.fail("50002", "素材服务暂时不可用，无法创建素材");
            }

            @Override
            public Result<Map<String, Object>> createEntityAssetRelation(String workspaceId, String userId,
                                                                          Map<String, Object> request) {
                log.warn("创建实体素材关联降级: workspaceId={}", workspaceId);
                return Result.fail("50002", "素材服务暂时不可用，无法创建关联");
            }

            @Override
            public Result<Void> updateGenerationStatus(String assetId, String status) {
                log.warn("更新素材生成状态降级: assetId={}, status={}", assetId, status);
                return Result.fail("50002", "素材服务暂时不可用，状态更新将稍后重试");
            }

            @Override
            public Result<Void> updateFileInfo(String assetId, Map<String, Object> fileInfo) {
                log.warn("更新素材文件信息降级: assetId={}", assetId);
                return Result.fail("50002", "素材服务暂时不可用，文件信息更新将稍后重试");
            }

            @Override
            public Result<Void> updateAssetExtraInfo(String workspaceId, String assetId,
                                                      Map<String, Object> extraInfo) {
                log.warn("更新素材扩展信息降级: assetId={}", assetId);
                return Result.fail("50002", "素材服务暂时不可用，扩展信息更新将稍后重试");
            }

            @Override
            public Result<AssetInfoResponse> getAssetInfo(String assetId) {
                log.warn("获取素材信息降级: assetId={}", assetId);
                return Result.fail("50002", "素材服务暂时不可用");
            }

            @Override
            public Result<Map<String, Object>> getAsset(String workspaceId, String assetId) {
                log.warn("获取素材详情降级: assetId={}", assetId);
                return Result.fail("50002", "素材服务暂时不可用");
            }

            @Override
            public Result<List<AssetInfoResponse>> batchGetAssets(List<String> assetIds) {
                log.warn("批量获取素材信息降级: count={}", assetIds != null ? assetIds.size() : 0);
                return Result.fail("50002", "素材服务暂时不可用");
            }

            @Override
            public Result<AssetInfoResponse> getByTaskId(String taskId) {
                log.warn("根据任务ID获取素材信息降级: taskId={}", taskId);
                return Result.fail("50002", "素材服务暂时不可用");
            }

            @Override
            public Result<List<Map<String, Object>>> getEntityAssets(String workspaceId,
                                                                      String entityType, String entityId) {
                log.warn("查询实体素材降级: entityType={}, entityId={}", entityType, entityId);
                return Result.success(Collections.emptyList());
            }

            @Override
            public Result<Map<String, Boolean>> batchCheckEntityAssets(String workspaceId,
                                                                        List<Map<String, String>> queries) {
                log.warn("批量检查实体素材降级: count={}", queries != null ? queries.size() : 0);
                return Result.success(Collections.emptyMap());
            }
        };
    }
}
