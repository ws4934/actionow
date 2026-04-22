package com.actionow.ai.service;

import com.actionow.ai.dto.PromptTemplateRequest;
import com.actionow.ai.dto.PromptTemplateResponse;

import java.util.List;
import java.util.Map;

/**
 * 提示词模板服务
 *
 * @author Actionow
 */
public interface PromptTemplateService {

    /**
     * 创建模板
     */
    PromptTemplateResponse create(String workspaceId, PromptTemplateRequest request, String creatorId);

    /**
     * 更新模板
     */
    PromptTemplateResponse update(String workspaceId, String templateId, PromptTemplateRequest request);

    /**
     * 删除模板
     */
    void delete(String workspaceId, String templateId);

    /**
     * 获取模板详情
     */
    PromptTemplateResponse getById(String templateId);

    /**
     * 获取工作空间可用模板列表
     */
    List<PromptTemplateResponse> listAvailable(String workspaceId, String type);

    /**
     * 获取系统模板列表
     */
    List<PromptTemplateResponse> listSystemTemplates(String type);

    /**
     * 渲染模板（替换变量）
     */
    String renderTemplate(String templateId, Map<String, Object> variables);

    /**
     * 增加使用次数
     */
    void incrementUseCount(String templateId);
}
