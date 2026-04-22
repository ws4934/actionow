package com.actionow.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.actionow.ai.dto.GroovyTemplateRequest;
import com.actionow.ai.dto.GroovyTemplateResponse;
import com.actionow.ai.entity.GroovyTemplate;
import com.actionow.ai.mapper.GroovyTemplateMapper;
import com.actionow.ai.plugin.groovy.GroovyScriptCache;
import com.actionow.ai.plugin.groovy.GroovyScriptContext;
import com.actionow.ai.plugin.groovy.GroovyScriptEngine;
import com.actionow.ai.plugin.groovy.GroovyScriptValidator;
import com.actionow.ai.service.GroovyTemplateService;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Groovy模板服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroovyTemplateServiceImpl extends ServiceImpl<GroovyTemplateMapper, GroovyTemplate>
        implements GroovyTemplateService {

    private final GroovyScriptValidator scriptValidator;
    private final GroovyScriptEngine scriptEngine;
    private final GroovyScriptCache scriptCache;

    @Override
    public List<GroovyTemplateResponse> listTemplates(String templateType, String generationType, Boolean isSystem) {
        LambdaQueryWrapper<GroovyTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroovyTemplate::getDeleted, 0);

        if (StringUtils.hasText(templateType)) {
            wrapper.eq(GroovyTemplate::getTemplateType, templateType);
        }
        if (StringUtils.hasText(generationType)) {
            wrapper.and(w -> w.eq(GroovyTemplate::getGenerationType, generationType)
                    .or().eq(GroovyTemplate::getGenerationType, "ALL"));
        }
        if (isSystem != null) {
            wrapper.eq(GroovyTemplate::getIsSystem, isSystem);
        }

        wrapper.orderByDesc(GroovyTemplate::getIsSystem);
        wrapper.orderByAsc(GroovyTemplate::getName);

        return list(wrapper).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<GroovyTemplateResponse> listSystemTemplates() {
        return baseMapper.selectSystemTemplates().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<GroovyTemplateResponse> listTemplatesPage(Long current, Long size, String templateType,
                                                                 String generationType, Boolean isSystem, String name) {
        // 参数校验
        if (current == null || current < 1) {
            current = 1L;
        }
        if (size == null || size < 1) {
            size = 20L;
        }
        if (size > 100) {
            size = 100L;
        }

        // 分页查询
        Page<GroovyTemplate> page = new Page<>(current, size);
        IPage<GroovyTemplate> templatePage = baseMapper.selectPage(page, templateType, generationType, isSystem, name);

        if (templatePage.getRecords().isEmpty()) {
            return PageResult.empty(current, size);
        }

        List<GroovyTemplateResponse> records = templatePage.getRecords().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PageResult.of(templatePage.getCurrent(), templatePage.getSize(),
                templatePage.getTotal(), records);
    }

    @Override
    public GroovyTemplateResponse getById(String id) {
        GroovyTemplate template = getById((java.io.Serializable) id);
        if (template == null || template.getDeleted() != 0) {
            throw new BusinessException("模板不存在: " + id);
        }
        return toResponse(template);
    }

    @Override
    public GroovyTemplateResponse getByName(String name) {
        GroovyTemplate template = baseMapper.selectByName(name);
        if (template == null) {
            throw new BusinessException("模板不存在: " + name);
        }
        return toResponse(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroovyTemplateResponse create(GroovyTemplateRequest request) {
        // 验证脚本语法
        GroovyScriptValidator.ValidationResult validation = scriptValidator.validate(request.getScriptContent());
        if (!validation.isValid()) {
            throw new BusinessException("脚本验证失败: " + String.join(", ", validation.getErrors()));
        }

        // 检查名称是否重复
        GroovyTemplate existing = baseMapper.selectByName(request.getName());
        if (existing != null) {
            throw new BusinessException("模板名称已存在: " + request.getName());
        }

        GroovyTemplate template = new GroovyTemplate();
        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setTemplateType(request.getTemplateType());
        template.setGenerationType(request.getGenerationType() != null ? request.getGenerationType() : "ALL");
        template.setScriptContent(request.getScriptContent());
        template.setScriptVersion(request.getScriptVersion() != null ? request.getScriptVersion() : "1.0.0");
        template.setIsSystem(false); // 用户创建的模板不能是系统模板
        template.setExampleInput(request.getExampleInput());
        template.setExampleOutput(request.getExampleOutput());
        template.setDocumentation(request.getDocumentation());
        template.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);

        save(template);

        log.info("Created Groovy template: {} ({})", template.getName(), template.getId());
        return toResponse(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroovyTemplateResponse update(String id, GroovyTemplateRequest request) {
        GroovyTemplate template = getById((java.io.Serializable) id);
        if (template == null || template.getDeleted() != 0) {
            throw new BusinessException("模板不存在: " + id);
        }

        // 系统模板不允许修改
        if (Boolean.TRUE.equals(template.getIsSystem())) {
            throw new BusinessException("系统模板不允许修改");
        }

        // 验证脚本语法并更新
        if (StringUtils.hasText(request.getScriptContent())) {
            GroovyScriptValidator.ValidationResult validation = scriptValidator.validate(request.getScriptContent());
            if (!validation.isValid()) {
                throw new BusinessException("脚本验证失败: " + String.join(", ", validation.getErrors()));
            }
            // 从缓存中移除旧的脚本
            if (StringUtils.hasText(template.getScriptContent())) {
                scriptCache.invalidate(template.getScriptContent());
            }
            template.setScriptContent(request.getScriptContent());
        }

        // 检查名称是否重复
        if (StringUtils.hasText(request.getName()) && !request.getName().equals(template.getName())) {
            GroovyTemplate existing = baseMapper.selectByName(request.getName());
            if (existing != null) {
                throw new BusinessException("模板名称已存在: " + request.getName());
            }
            template.setName(request.getName());
        }

        if (request.getDescription() != null) {
            template.setDescription(request.getDescription());
        }
        if (request.getTemplateType() != null) {
            template.setTemplateType(request.getTemplateType());
        }
        if (request.getGenerationType() != null) {
            template.setGenerationType(request.getGenerationType());
        }
        if (request.getScriptVersion() != null) {
            template.setScriptVersion(request.getScriptVersion());
        }
        if (request.getExampleInput() != null) {
            template.setExampleInput(request.getExampleInput());
        }
        if (request.getExampleOutput() != null) {
            template.setExampleOutput(request.getExampleOutput());
        }
        if (request.getDocumentation() != null) {
            template.setDocumentation(request.getDocumentation());
        }
        if (request.getEnabled() != null) {
            template.setEnabled(request.getEnabled());
        }

        updateById(template);

        log.info("Updated Groovy template: {} ({})", template.getName(), template.getId());
        return toResponse(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        GroovyTemplate template = getById((java.io.Serializable) id);
        if (template == null || template.getDeleted() != 0) {
            throw new BusinessException("模板不存在: " + id);
        }

        // 系统模板不允许删除
        if (Boolean.TRUE.equals(template.getIsSystem())) {
            throw new BusinessException("系统模板不允许删除");
        }

        // 从缓存中移除已编译的脚本
        if (StringUtils.hasText(template.getScriptContent())) {
            scriptCache.invalidate(template.getScriptContent());
        }

        // 逻辑删除
        template.setDeleted(1);
        updateById(template);

        log.info("Deleted Groovy template: {} ({})", template.getName(), template.getId());
    }

    @Override
    public GroovyScriptValidator.ValidationResult validateScript(String scriptContent) {
        return scriptValidator.validate(scriptContent);
    }

    @Override
    public Map<String, Object> testScript(String scriptContent, String templateType,
                                          Map<String, Object> inputs, Map<String, Object> config,
                                          Object response) {
        // 先验证脚本
        GroovyScriptValidator.ValidationResult validation = scriptValidator.validate(scriptContent);
        if (!validation.isValid()) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("errors", validation.getErrors());
            return result;
        }

        try {
            Object output;

            // 根据模板类型选择执行方式
            if ("RESPONSE_MAPPER".equals(templateType) || "CUSTOM_LOGIC".equals(templateType)) {
                // 响应映射器和自定义逻辑需要 response 参数
                if (response == null) {
                    response = new HashMap<String, Object>(); // 提供空响应作为默认值
                }

                // 创建响应映射上下文
                GroovyScriptContext context = GroovyScriptContext.forResponseMapper(
                        inputs != null ? inputs : new HashMap<>(),
                        config != null ? config : new HashMap<>(),
                        response
                );

                if ("CUSTOM_LOGIC".equals(templateType)) {
                    output = scriptEngine.executeCustomLogic(scriptContent, context);
                } else {
                    output = scriptEngine.executeResponseMapper(scriptContent, response, context);
                }
            } else {
                // 默认使用请求构建器上下文 (REQUEST_BUILDER)
                GroovyScriptContext context = GroovyScriptContext.forRequestBuilder(
                        inputs != null ? inputs : new HashMap<>(),
                        config != null ? config : new HashMap<>()
                );
                output = scriptEngine.executeRequestBuilder(scriptContent, context);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("output", output);
            result.put("warnings", validation.getWarnings());
            return result;

        } catch (Exception e) {
            log.warn("Script test execution failed: {}", e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    @Override
    public String getScriptContent(String templateId) {
        if (!StringUtils.hasText(templateId)) {
            return null;
        }

        GroovyTemplate template = getById((java.io.Serializable) templateId);
        if (template == null || template.getDeleted() != 0 || !Boolean.TRUE.equals(template.getEnabled())) {
            log.warn("Template not found or disabled: {}", templateId);
            return null;
        }

        return template.getScriptContent();
    }

    /**
     * 转换为响应DTO
     */
    private GroovyTemplateResponse toResponse(GroovyTemplate template) {
        return GroovyTemplateResponse.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .templateType(template.getTemplateType())
                .generationType(template.getGenerationType())
                .scriptContent(template.getScriptContent())
                .scriptVersion(template.getScriptVersion())
                .isSystem(template.getIsSystem())
                .exampleInput(template.getExampleInput())
                .exampleOutput(template.getExampleOutput())
                .documentation(template.getDocumentation())
                .enabled(template.getEnabled())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }
}
