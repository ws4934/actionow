package com.actionow.agent.tool.annotation;

/**
 * 工具动作类型
 *
 * <p>用于标记工具的主要行为语义，便于 Tool Catalog 查询和前端展示。
 */
public enum ToolActionType {
    READ,
    SEARCH,
    WRITE,
    GENERATE,
    CONTROL,
    UNKNOWN
}
