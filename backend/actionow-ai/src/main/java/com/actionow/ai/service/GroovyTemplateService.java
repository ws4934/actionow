package com.actionow.ai.service;

import com.actionow.ai.dto.GroovyTemplateRequest;
import com.actionow.ai.dto.GroovyTemplateResponse;
import com.actionow.ai.plugin.groovy.GroovyScriptValidator;
import com.actionow.common.core.result.PageResult;

import java.util.List;
import java.util.Map;

/**
 * Groovy模板服务接口
 *
 * @author Actionow
 */
public interface GroovyTemplateService {

    /**
     * 获取所有模板（全量）
     *
     * @param templateType   模板类型（可选）
     * @param generationType 生成类型（可选）
     * @param isSystem       是否系统模板（可选）
     * @return 模板列表
     */
    List<GroovyTemplateResponse> listTemplates(String templateType, String generationType, Boolean isSystem);

    /**
     * 分页查询模板
     *
     * @param current        当前页码
     * @param size           每页大小
     * @param templateType   模板类型（可选）
     * @param generationType 生成类型（可选）
     * @param isSystem       是否系统模板（可选）
     * @param name           名称模糊搜索（可选）
     * @return 分页结果
     */
    PageResult<GroovyTemplateResponse> listTemplatesPage(Long current, Long size, String templateType,
                                                          String generationType, Boolean isSystem, String name);

    /**
     * 获取系统模板列表
     *
     * @return 系统模板列表
     */
    List<GroovyTemplateResponse> listSystemTemplates();

    /**
     * 根据ID获取模板
     *
     * @param id 模板ID
     * @return 模板详情
     */
    GroovyTemplateResponse getById(String id);

    /**
     * 根据名称获取模板
     *
     * @param name 模板名称
     * @return 模板详情
     */
    GroovyTemplateResponse getByName(String name);

    /**
     * 创建模板
     *
     * @param request 创建请求
     * @return 创建的模板
     */
    GroovyTemplateResponse create(GroovyTemplateRequest request);

    /**
     * 更新模板
     *
     * @param id      模板ID
     * @param request 更新请求
     * @return 更新后的模板
     */
    GroovyTemplateResponse update(String id, GroovyTemplateRequest request);

    /**
     * 删除模板
     *
     * @param id 模板ID
     */
    void delete(String id);

    /**
     * 验证脚本语法
     *
     * @param scriptContent 脚本内容
     * @return 验证结果
     */
    GroovyScriptValidator.ValidationResult validateScript(String scriptContent);

    /**
     * 测试脚本执行
     *
     * @param scriptContent 脚本内容
     * @param templateType  模板类型 (REQUEST_BUILDER, RESPONSE_MAPPER, CUSTOM_LOGIC)
     * @param inputs        测试输入
     * @param config        测试配置
     * @param response      测试响应（用于 RESPONSE_MAPPER 和 CUSTOM_LOGIC）
     * @return 执行结果
     */
    Map<String, Object> testScript(String scriptContent, String templateType,
                                   Map<String, Object> inputs, Map<String, Object> config,
                                   Object response);

    /**
     * 获取模板脚本内容
     * 用于PluginConfig解析模板引用
     *
     * @param templateId 模板ID
     * @return 脚本内容
     */
    String getScriptContent(String templateId);
}
