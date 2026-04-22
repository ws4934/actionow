package com.actionow.agent.tool.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记此 @Tool 方法为 Mission 模式专用的直接工具。
 *
 * <p>被标记的工具：
 * <ul>
 *   <li>在 MISSION 模式下作为 direct callback 注入（不属于任何 Skill）</li>
 *   <li>在 CHAT 模式下被过滤掉（LLM 不可见）</li>
 * </ul>
 *
 * <p>替代 DefaultToolAccessPolicy 中的硬编码 MISSION_DIRECT_TOOLS 常量，
 * 新增工具只需加此注解 + INSERT DB，无需修改 Policy 代码。
 *
 * @author Actionow
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MissionDirectTool {
}
