package com.actionow.canvas.dto.batch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量操作结果
 * 用于跟踪批量操作中每个项目的成功/失败状态
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchOperationResult<T> {

    /**
     * 成功的项目列表
     */
    @Builder.Default
    private List<T> succeeded = new ArrayList<>();

    /**
     * 失败的项目详情列表
     */
    @Builder.Default
    private List<FailedItem> failed = new ArrayList<>();

    /**
     * 总数
     */
    private int totalCount;

    /**
     * 成功数
     */
    private int successCount;

    /**
     * 失败数
     */
    private int failCount;

    /**
     * 操作是否全部成功
     */
    public boolean isAllSucceeded() {
        return failed.isEmpty();
    }

    /**
     * 操作是否全部失败
     */
    public boolean isAllFailed() {
        return succeeded.isEmpty();
    }

    /**
     * 操作是否部分成功
     */
    public boolean isPartialSuccess() {
        return !succeeded.isEmpty() && !failed.isEmpty();
    }

    /**
     * 添加成功项
     */
    public void addSucceeded(T item) {
        succeeded.add(item);
        successCount++;
    }

    /**
     * 添加失败项
     */
    public void addFailed(int index, String identifier, String errorCode, String errorMessage) {
        failed.add(new FailedItem(index, identifier, errorCode, errorMessage));
        failCount++;
    }

    /**
     * 静态工厂方法 - 创建空结果
     */
    public static <T> BatchOperationResult<T> empty() {
        return BatchOperationResult.<T>builder()
                .totalCount(0)
                .successCount(0)
                .failCount(0)
                .build();
    }

    /**
     * 静态工厂方法 - 从结果列表创建
     */
    public static <T> BatchOperationResult<T> of(List<T> items) {
        BatchOperationResult<T> result = new BatchOperationResult<>();
        result.setTotalCount(items.size());
        result.setSucceeded(new ArrayList<>(items));
        result.setSuccessCount(items.size());
        result.setFailCount(0);
        return result;
    }

    /**
     * 失败项详情
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedItem {
        /**
         * 在原始请求列表中的索引
         */
        private int index;

        /**
         * 标识符（如实体ID、名称等）
         */
        private String identifier;

        /**
         * 错误代码
         */
        private String errorCode;

        /**
         * 错误消息
         */
        private String errorMessage;
    }
}
