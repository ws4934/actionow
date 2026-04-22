package com.actionow.agent.dto.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 更新 Skill 请求 DTO（所有字段可选）
 *
 * @author Actionow
 */
@Data
public class SkillUpdateRequest {

    private String displayName;

    private String description;

    private String content;

    private List<String> groupedToolIds;

    private Map<String, Object> outputSchema;

    private List<String> tags;

    private List<Map<String, Object>> references;

    private List<Map<String, Object>> examples;
}
