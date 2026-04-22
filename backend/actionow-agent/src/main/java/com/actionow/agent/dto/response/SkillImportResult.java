package com.actionow.agent.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Skill 包导入结果
 *
 * @author Actionow
 */
@Data
@Builder
public class SkillImportResult {

    /** 解析到的 Skill 总数 */
    private int total;

    /** 成功导入数 */
    private int success;

    /** 失败数 */
    private int failed;

    /** 失败详情列表（skillName: 错误信息） */
    private List<String> errors;
}
