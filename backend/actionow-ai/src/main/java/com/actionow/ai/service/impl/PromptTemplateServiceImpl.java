package com.actionow.ai.service.impl;

import com.actionow.ai.constant.AiConstants;
import com.actionow.ai.dto.PromptTemplateRequest;
import com.actionow.ai.dto.PromptTemplateResponse;
import com.actionow.ai.entity.PromptTemplate;
import com.actionow.ai.mapper.PromptTemplateMapper;
import com.actionow.ai.service.PromptTemplateService;
import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 提示词模板服务实现
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateServiceImpl implements PromptTemplateService {

    private final PromptTemplateMapper promptTemplateMapper;

    /**
     * 模板变量正则表达式：{{variable}}
     */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromptTemplateResponse create(String workspaceId, PromptTemplateRequest request, String creatorId) {
        PromptTemplate template = new PromptTemplate();
        BeanUtils.copyProperties(request, template);
        template.setWorkspaceId(workspaceId);
        template.setScope(AiConstants.TemplateScope.WORKSPACE);
        template.setStatus(AiConstants.TemplateStatus.ACTIVE);
        template.setUseCount(0L);
        template.setCreatorId(creatorId);

        promptTemplateMapper.insert(template);
        log.info("Created prompt template: {} for workspace: {}", template.getId(), workspaceId);

        return toResponse(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromptTemplateResponse update(String workspaceId, String templateId, PromptTemplateRequest request) {
        PromptTemplate template = getTemplateOrThrow(templateId);

        // 检查权限：只能修改自己工作空间的模板，系统模板不可修改
        if (AiConstants.TemplateScope.SYSTEM.equals(template.getScope())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "系统模板不可修改");
        }
        if (!workspaceId.equals(template.getWorkspaceId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权修改此模板");
        }

        if (StringUtils.hasText(request.getName())) {
            template.setName(request.getName());
        }
        if (request.getDescription() != null) {
            template.setDescription(request.getDescription());
        }
        if (StringUtils.hasText(request.getContent())) {
            template.setContent(request.getContent());
        }
        if (request.getNegativePrompt() != null) {
            template.setNegativePrompt(request.getNegativePrompt());
        }
        if (request.getVariables() != null) {
            template.setVariables(request.getVariables());
        }
        if (request.getDefaultParams() != null) {
            template.setDefaultParams(request.getDefaultParams());
        }

        promptTemplateMapper.updateById(template);
        log.info("Updated prompt template: {}", templateId);

        return toResponse(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String workspaceId, String templateId) {
        PromptTemplate template = getTemplateOrThrow(templateId);

        // 检查权限
        if (AiConstants.TemplateScope.SYSTEM.equals(template.getScope())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "系统模板不可删除");
        }
        if (!workspaceId.equals(template.getWorkspaceId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权删除此模板");
        }

        promptTemplateMapper.deleteById(templateId);
        log.info("Deleted prompt template: {}", templateId);
    }

    @Override
    public PromptTemplateResponse getById(String templateId) {
        PromptTemplate template = getTemplateOrThrow(templateId);
        return toResponse(template);
    }

    @Override
    public List<PromptTemplateResponse> listAvailable(String workspaceId, String type) {
        List<PromptTemplate> templates = promptTemplateMapper.selectAvailableTemplates(workspaceId, type);
        return templates.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<PromptTemplateResponse> listSystemTemplates(String type) {
        List<PromptTemplate> templates = promptTemplateMapper.selectSystemTemplates(type);
        return templates.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public String renderTemplate(String templateId, Map<String, Object> variables) {
        PromptTemplate template = getTemplateOrThrow(templateId);
        String content = template.getContent();

        if (variables == null || variables.isEmpty()) {
            return content;
        }

        // 替换变量
        Matcher matcher = VARIABLE_PATTERN.matcher(content);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = variables.get(varName);
            String replacement = value != null ? value.toString() : matcher.group(0);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    @Override
    public void incrementUseCount(String templateId) {
        promptTemplateMapper.incrementUseCount(templateId);
    }

    private PromptTemplate getTemplateOrThrow(String templateId) {
        PromptTemplate template = promptTemplateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "模板不存在");
        }
        return template;
    }

    private PromptTemplateResponse toResponse(PromptTemplate template) {
        PromptTemplateResponse response = new PromptTemplateResponse();
        BeanUtils.copyProperties(template, response);
        return response;
    }
}
