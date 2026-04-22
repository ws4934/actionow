package com.actionow.ai.plugin.model;

import com.actionow.ai.dto.standard.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 插件执行结果
 * 统一的执行结果格式，包含状态、输出和元信息
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginExecutionResult {

    /**
     * 执行ID
     */
    private String executionId;

    /**
     * 提供商ID
     */
    private String providerId;

    /**
     * 外部任务ID（第三方返回的ID）
     */
    private String externalTaskId;

    /**
     * 外部运行ID（用于查询状态）
     */
    private String externalRunId;

    /**
     * 执行状态
     */
    private ExecutionStatus status;

    /**
     * 响应模式
     */
    private ResponseMode responseMode;

    /**
     * 输出类型（新字段，用于区分媒体/实体/文本输出）
     */
    private OutputType outputType;

    /**
     * 标准响应（新格式，包含结构化的媒体/实体/文本输出）
     */
    private StandardResponse standardResponse;

    /**
     * 输出数据（统一格式，保留向后兼容）
     */
    private Map<String, Object> outputs;

    /**
     * 生成的资产列表
     */
    private List<GeneratedAsset> assets;

    /**
     * 错误码
     */
    private String errorCode;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 积分消耗
     */
    @Builder.Default
    private Long creditCost = 0L;

    /**
     * 执行耗时（毫秒）
     */
    private Long elapsedTimeMs;

    /**
     * Token消耗
     */
    private Integer totalTokens;

    /**
     * 提交时间
     */
    private LocalDateTime submittedAt;

    /**
     * 开始时间
     */
    private LocalDateTime startedAt;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;

    /**
     * 原始响应（调试用）
     */
    private Object rawResponse;

    /**
     * 执行状态枚举
     */
    public enum ExecutionStatus {
        PENDING("pending", "等待中"),
        RUNNING("running", "执行中"),
        SUCCEEDED("succeeded", "成功"),
        FAILED("failed", "失败"),
        CANCELLED("cancelled", "已取消"),
        TIMEOUT("timeout", "超时");

        private final String code;
        private final String description;

        ExecutionStatus(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }

        public static ExecutionStatus fromCode(String code) {
            for (ExecutionStatus status : values()) {
                if (status.code.equalsIgnoreCase(code)) {
                    return status;
                }
            }
            return FAILED;
        }

        public boolean isTerminal() {
            return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == TIMEOUT;
        }
    }

    /**
     * 生成的资产
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeneratedAsset {
        /**
         * 资产类型（IMAGE/VIDEO/AUDIO/TEXT）
         */
        private String assetType;

        /**
         * 资产URL（外部URL或base64）
         */
        private String url;

        /**
         * 是否为Base64数据
         */
        @Builder.Default
        private boolean base64 = false;

        /**
         * MIME类型
         */
        private String mimeType;

        /**
         * 文件名
         */
        private String fileName;

        /**
         * 文件大小（字节）
         */
        private Long fileSize;

        /**
         * 宽度（图片/视频）
         */
        private Integer width;

        /**
         * 高度（图片/视频）
         */
        private Integer height;

        /**
         * 时长（视频/音频，秒）
         */
        private Integer duration;

        /**
         * 额外元数据
         */
        private Map<String, Object> metadata;
    }

    /**
     * 创建成功结果
     */
    public static PluginExecutionResult success(String executionId, Map<String, Object> outputs) {
        return PluginExecutionResult.builder()
                .executionId(executionId)
                .status(ExecutionStatus.SUCCEEDED)
                .outputs(outputs)
                .completedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 创建失败结果
     */
    public static PluginExecutionResult failure(String executionId, String errorCode, String errorMessage) {
        return PluginExecutionResult.builder()
                .executionId(executionId)
                .status(ExecutionStatus.FAILED)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .completedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 创建待处理结果（用于异步模式）
     */
    public static PluginExecutionResult pending(String executionId, String externalTaskId) {
        return PluginExecutionResult.builder()
                .executionId(executionId)
                .externalTaskId(externalTaskId)
                .status(ExecutionStatus.PENDING)
                .submittedAt(LocalDateTime.now())
                .build();
    }

    // ==================== 新增：从 StandardResponse 构建 ====================

    /**
     * 从 StandardResponse 创建执行结果
     *
     * @param executionId      执行ID
     * @param standardResponse 标准响应
     * @return 执行结果
     */
    public static PluginExecutionResult fromStandardResponse(String executionId, StandardResponse standardResponse) {
        if (standardResponse == null) {
            return failure(executionId, "NULL_RESPONSE", "标准响应为空");
        }

        PluginExecutionResultBuilder builder = PluginExecutionResult.builder()
                .executionId(executionId)
                .outputType(standardResponse.getOutputType())
                .standardResponse(standardResponse)
                .status(standardResponse.getStatus())
                .completedAt(LocalDateTime.now());

        // 设置错误信息
        if (standardResponse.getError() != null) {
            builder.errorCode(standardResponse.getError().getCode());
            builder.errorMessage(standardResponse.getError().getMessage());
        }

        // 设置元数据
        ResponseMetadata metadata = standardResponse.getMetadata();
        if (metadata != null) {
            builder.externalTaskId(metadata.getExternalTaskId());
            builder.externalRunId(metadata.getExternalRunId());
            builder.elapsedTimeMs(metadata.getElapsedMs());
            builder.totalTokens(metadata.getTotalTokens());
            builder.rawResponse(metadata.getRaw());
        }

        // 从媒体输出构建 assets
        if (standardResponse.hasMedia()) {
            List<GeneratedAsset> assets = new ArrayList<>();
            MediaOutput media = standardResponse.getMedia();
            String assetType = media.getMediaType() != null ? media.getMediaType().getCode() : "IMAGE";

            for (MediaItem item : media.getItems()) {
                assets.add(GeneratedAsset.builder()
                        .assetType(assetType)
                        .url(item.getFileUrl())
                        .mimeType(item.getMimeType())
                        .fileSize(item.getFileSize())
                        .width(item.getWidth())
                        .height(item.getHeight())
                        .duration(item.getDuration())
                        .base64(Boolean.TRUE.equals(item.getBase64()))
                        .metadata(item.getParams())
                        .build());
            }
            builder.assets(assets);
        }

        return builder.build();
    }

    /**
     * 检查是否为媒体输出
     */
    public boolean isMediaOutput() {
        return outputType != null && outputType.isMediaType();
    }

    /**
     * 检查是否为实体输出
     */
    public boolean isEntityOutput() {
        return outputType != null && outputType.isEntityType();
    }

    /**
     * 检查是否为文本输出
     */
    public boolean isTextOutput() {
        return outputType == OutputType.TEXT_CONTENT;
    }

    /**
     * 获取第一个媒体项
     */
    public MediaItem getFirstMediaItem() {
        if (standardResponse != null && standardResponse.hasMedia()) {
            return standardResponse.getFirstMediaItem();
        }
        return null;
    }

    /**
     * 获取所有媒体项
     */
    public List<MediaItem> getMediaItems() {
        if (standardResponse != null && standardResponse.hasMedia()) {
            return standardResponse.getMedia().getItems();
        }
        return new ArrayList<>();
    }

    /**
     * 获取实体输出列表
     */
    public List<EntityOutput> getEntities() {
        if (standardResponse != null && standardResponse.hasEntities()) {
            return standardResponse.getEntities();
        }
        return new ArrayList<>();
    }

    /**
     * 获取文本内容
     */
    public String getTextContent() {
        if (standardResponse != null) {
            return standardResponse.getTextContent();
        }
        return null;
    }

    /**
     * 转换为回调通知的 payload 格式
     * 符合 Task 服务的 ProviderExecutionResult 结构
     */
    public Map<String, Object> toCallbackPayload() {
        Map<String, Object> payload = new java.util.HashMap<>();

        // 基础信息
        payload.put("success", status == ExecutionStatus.SUCCEEDED);
        payload.put("executionId", executionId);
        payload.put("externalTaskId", externalTaskId);
        payload.put("externalRunId", externalRunId);
        payload.put("status", status != null ? status.getCode() : "failed");
        payload.put("responseMode", responseMode != null ? responseMode.name() : "POLLING");

        // 错误信息
        if (errorCode != null) {
            payload.put("errorCode", errorCode);
        }
        if (errorMessage != null) {
            payload.put("errorMessage", errorMessage);
        }

        // 积分和耗时
        payload.put("creditCost", creditCost != null ? creditCost.intValue() : 0);
        payload.put("elapsedTime", elapsedTimeMs);

        // 输出数据
        if (outputs != null) {
            payload.put("outputs", outputs);
        }

        // 从 StandardResponse 提取媒体信息
        if (standardResponse != null && standardResponse.hasMedia()) {
            MediaItem firstItem = standardResponse.getFirstMediaItem();
            if (firstItem != null) {
                payload.put("fileUrl", firstItem.getFileUrl());
                payload.put("fileKey", firstItem.getFileKey());
                payload.put("mimeType", firstItem.getMimeType());
                payload.put("fileSize", firstItem.getFileSize());

                // 构建元数据
                Map<String, Object> metaInfo = new java.util.HashMap<>();
                if (firstItem.getWidth() != null) {
                    metaInfo.put("width", firstItem.getWidth());
                }
                if (firstItem.getHeight() != null) {
                    metaInfo.put("height", firstItem.getHeight());
                }
                if (firstItem.getDuration() != null) {
                    metaInfo.put("duration", firstItem.getDuration());
                }
                if (firstItem.getParams() != null) {
                    metaInfo.putAll(firstItem.getParams());
                }
                if (!metaInfo.isEmpty()) {
                    payload.put("metaInfo", metaInfo);
                }
            }
        }

        // 从 assets 中兜底获取媒体信息（向后兼容）
        if (!payload.containsKey("fileUrl") && assets != null && !assets.isEmpty()) {
            GeneratedAsset firstAsset = assets.get(0);
            payload.put("fileUrl", firstAsset.getUrl());
            payload.put("mimeType", firstAsset.getMimeType());
            payload.put("fileSize", firstAsset.getFileSize());

            Map<String, Object> metaInfo = new java.util.HashMap<>();
            if (firstAsset.getWidth() != null) {
                metaInfo.put("width", firstAsset.getWidth());
            }
            if (firstAsset.getHeight() != null) {
                metaInfo.put("height", firstAsset.getHeight());
            }
            if (firstAsset.getDuration() != null) {
                metaInfo.put("duration", firstAsset.getDuration());
            }
            if (!metaInfo.isEmpty()) {
                payload.put("metaInfo", metaInfo);
            }
        }

        return payload;
    }
}
