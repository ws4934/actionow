package com.actionow.agent.interaction;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 用户对 ask_user 事件的回答载荷。
 *
 * <p>字段语义按 inputType 区分：
 * <ul>
 *   <li>{@code single_choice} / {@code confirm} — {@link #answer} 是选中的 choice id（或 "yes"/"no"）</li>
 *   <li>{@code multi_choice} — {@link #multiAnswer} 是选中的 choice id 列表</li>
 *   <li>{@code text} / {@code number} — {@link #answer} 是自由文本（number 下为数字字符串）</li>
 *   <li>所有类型 — {@link #rejected} 标识用户显式拒绝 / 关闭弹窗，agent 应视为取消</li>
 * </ul>
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserAnswer {

    /** 单选 / 确认 / 文本场景下的答案 */
    private String answer;

    /** 多选场景下的答案列表 */
    private List<String> multiAnswer;

    /** 用户是否拒绝回答 / 关闭弹窗（agent 应把后续任务标记为用户取消） */
    private Boolean rejected;

    /** 可选：用户提交时附带的额外字段（预留） */
    private Map<String, Object> extras;
}
