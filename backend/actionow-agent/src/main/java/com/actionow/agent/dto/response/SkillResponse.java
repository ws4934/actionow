package com.actionow.agent.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Skill 响应 DTO
 * list 接口省略 content 字段（避免大量文本传输），get 接口返回完整内容
 *
 * @author Actionow
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SkillResponse {

    private String id;

    private String name;

    private String displayName;

    private String description;

    /** 完整指令内容，仅 getSkill 接口返回 */
    private String content;

    private List<String> groupedToolIds;

    private Map<String, Object> outputSchema;

    private List<String> tags;

    private List<Map<String, Object>> references;

    private List<Map<String, Object>> examples;

    private Boolean enabled;

    private Integer version;

    private String scope;

    private String workspaceId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
