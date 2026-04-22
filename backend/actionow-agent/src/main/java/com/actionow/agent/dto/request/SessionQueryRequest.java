package com.actionow.agent.dto.request;

import lombok.Data;

/**
 * 会话列表查询请求
 *
 * @author Actionow
 */
@Data
public class SessionQueryRequest {

    /**
     * 页码（从1开始）
     */
    private Integer page = 1;

    /**
     * 每页大小
     */
    private Integer size = 20;

    /**
     * 是否独立 Agent 会话
     * true: 只查询独立 Agent 会话（如提示词生成器）
     * false: 只查询非独立 Agent 会话（协调者会话）
     * null: 不筛选，返回所有会话
     */
    private Boolean standalone;

    /**
     * 剧本 ID（可选）
     * 非 null: 只查询绑定到该剧本的会话
     * null: 不按剧本筛选，返回所有会话
     */
    private String scriptId;

    /**
     * 获取偏移量
     */
    public int getOffset() {
        return (page - 1) * size;
    }
}
