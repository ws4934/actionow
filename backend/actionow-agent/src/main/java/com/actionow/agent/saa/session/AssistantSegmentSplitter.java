package com.actionow.agent.saa.session;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 历史 assistant 占位消息的段落切分器 —— Step 1 Spike 只读适配器。
 *
 * <h2>背景</h2>
 * P1~P2 期间 {@code finalizePlaceholderMessage} 把一次 turn 内 ReAct 多步的 LLM 文本
 * 合并成一整段写回 {@code content}，造成前端按顺序回放历史时读到
 * "好的，我们来立项……首先，我需要加载…… Rolling…… 正在为你立项…… Rolling…… 已创建……"
 * 这样"穿插工具调用叙事却展示成一串连续独白"的奇怪观感。
 *
 * <p>Step 2 起每条 message event 会独立落一行 {@code role=assistant} 段落；
 * 但历史数据仍是整段 blob。本切分器提供"只读兜底"：把旧 blob 按空行启发式切回多段，
 * 供前端用与新数据相同的"每段一气泡"渲染路径。
 *
 * <h2>启发规则</h2>
 * <ol>
 *   <li>按 {@code \n\s*\n} 切分（一个或多个空行分隔）；</li>
 *   <li>trim 每段两侧空白，丢弃空段；</li>
 *   <li>保持原始顺序；</li>
 *   <li>若切不出多段，直接返回单元素列表（调用方可判断是否跳过扩展）。</li>
 * </ol>
 *
 * <h2>非目标</h2>
 * <ul>
 *   <li>不解析 markdown 块（```code``` / 列表 / 表格）—— 简单启发够用，spike 优先；</li>
 *   <li>不尝试重排与 tool row 的 interleave —— 段落仍保留同一 placeholder 的 sequence，
 *       前端在时间线上显示为"一坨连续的 assistant 气泡"；真正与 tool 交错由 Step 2
 *       写入侧改造实现（新写入的段落有各自递增 sequence）。</li>
 * </ul>
 *
 * @author Actionow
 */
public final class AssistantSegmentSplitter {

    /** 两个或更多连续的换行（中间可包含空白）。 */
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n\\s*\\n+");

    private AssistantSegmentSplitter() {}

    /**
     * 把整段 assistant content 拆成段落列表。
     *
     * @param content 原始字符串，允许 null / blank
     * @return 非空段落列表（至少一个元素），null / blank 输入返回空列表
     */
    public static List<String> split(String content) {
        if (content == null) return List.of();
        String trimmed = content.trim();
        if (trimmed.isEmpty()) return List.of();
        String[] parts = PARAGRAPH_BREAK.split(trimmed);
        List<String> result = new ArrayList<>(parts.length);
        for (String part : parts) {
            String p = part.trim();
            if (!p.isEmpty()) result.add(p);
        }
        if (result.isEmpty()) result.add(trimmed);
        return result;
    }
}
