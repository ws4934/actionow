package com.actionow.task.service;

import com.actionow.task.constant.TaskConstants;
import com.actionow.task.entity.BatchJobItem;
import com.actionow.task.entity.PipelineStep;
import com.actionow.task.mapper.BatchJobItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pipeline 步骤参数解析器
 * <p>
 * 负责解析 params_template 中的 {@code {{steps[N].output.xxx}}} 占位符，
 * 将其替换为前序步骤完成后的实际输出值。
 *
 * <p>支持的占位符格式:
 * <pre>
 * {{steps[1].output.text}}          → 步骤1输出的 text 字段
 * {{steps[1].output.file_url}}      → 步骤1输出的 file_url 字段
 * {{steps[1].output.all_file_url}}  → 步骤1所有 items 的 file_url 聚合为 List
 * {{input.prompt}}                  → 原始输入中的 prompt 字段
 * </pre>
 *
 * <p><b>安全性说明（防注入）：</b>
 * 本解析器使用严格的正则模式（{@link #STEP_OUTPUT_PATTERN} 等），仅匹配固定格式占位符，
 * <b>不支持任意表达式求值</b>（不使用 SpEL/Groovy/MVEL）。
 * 占位符替换的目标值来源于数据库（已完成的 BatchJobItem 输出），不受用户直接控制。
 * paramsTemplate 由系统管理员通过 PipelineEngine 模板或 API 配置，不接受用户输入作为模板内容。
 * 因此，本模块不存在模板注入（Template Injection）风险。
 *
 * <p>安全边界清单:
 * <ul>
 *   <li>MAX_TEMPLATE_LENGTH = 10,000：防止超大模板字符串（ReDoS 缓解）</li>
 *   <li>MAX_PATH_DEPTH = 5：防止深度嵌套路径遍历</li>
 *   <li>MAX_STEP_NUMBER = 100：防止超大步骤编号</li>
 *   <li>parseStepNumber 长度限制 &lt; 3：防止超大数字 NumberFormatException</li>
 * </ul>
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineStepResolver {

    private static final int MAX_STEP_NUMBER = 100;
    private static final int MAX_PATH_DEPTH = 5;
    private static final int MAX_TEMPLATE_LENGTH = 10_000;

    private final BatchJobItemMapper batchJobItemMapper;

    /**
     * 步骤输出引用模式: {{steps[N].output.fieldName}}
     */
    private static final Pattern STEP_OUTPUT_PATTERN = Pattern.compile(
            "\\{\\{steps\\[(\\d+)]\\.(output|item)\\.([a-zA-Z0-9_.]+)}}");

    /**
     * 步骤聚合引用模式: {{steps[N].output.all_fieldName}}
     * 收集该步骤所有 items 的同一字段值，返回 List
     */
    private static final Pattern STEP_AGGREGATE_PATTERN = Pattern.compile(
            "\\{\\{steps\\[(\\d+)]\\.(output|item)\\.all_([a-zA-Z0-9_.]+)}}");

    /**
     * 输入引用模式: {{input.fieldName}}
     */
    private static final Pattern INPUT_PATTERN = Pattern.compile(
            "\\{\\{input\\.([a-zA-Z0-9_.]+)}}");

    /**
     * 解析步骤参数模板，替换占位符为实际值
     *
     * @param step           当前步骤定义
     * @param batchJobId     批量作业ID
     * @param stepOutputs    各步骤编号 → 已完成 items 列表的映射
     * @param originalParams 原始输入参数（来自 BatchJob.sharedParams 或初始 item.params）
     * @return 解析后的参数 Map
     */
    public Map<String, Object> resolveParams(PipelineStep step,
                                              String batchJobId,
                                              Map<Integer, List<BatchJobItem>> stepOutputs,
                                              Map<String, Object> originalParams) {
        Map<String, Object> template = step.getParamsTemplate();
        if (template == null || template.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : template.entrySet()) {
            Object value = resolveValue(entry.getValue(), stepOutputs, originalParams);
            resolved.put(entry.getKey(), value);
        }
        return resolved;
    }

    /**
     * 收集指定 BatchJob 中各 pipeline step 的已完成 items 输出
     *
     * @param batchJobId 批量作业ID
     * @return stepNumber → completed items list
     */
    public Map<Integer, List<BatchJobItem>> collectStepOutputs(String batchJobId) {
        List<BatchJobItem> completedItems = batchJobItemMapper.selectByBatchJobIdAndStatus(
                batchJobId, TaskConstants.BatchItemStatus.COMPLETED);

        Map<Integer, List<BatchJobItem>> stepOutputs = new HashMap<>();
        for (BatchJobItem item : completedItems) {
            if (item.getPipelineStepId() != null) {
                // 按 pipeline_step_id 分组 → 需要查步骤编号
                // 这里我们用 item.params 中预存的 _stepNumber（见 PipelineEngine 写入）
                Object stepNumObj = item.getParams() != null ? item.getParams().get("_stepNumber") : null;
                if (stepNumObj instanceof Number) {
                    int stepNum = ((Number) stepNumObj).intValue();
                    stepOutputs.computeIfAbsent(stepNum, k -> new ArrayList<>()).add(item);
                }
            }
        }
        return stepOutputs;
    }

    /**
     * 递归解析值（支持嵌套 Map 和 List）
     */
    @SuppressWarnings("unchecked")
    private Object resolveValue(Object value, Map<Integer, List<BatchJobItem>> stepOutputs,
                                 Map<String, Object> originalParams) {
        if (value instanceof String strVal) {
            return resolveStringValue(strVal, stepOutputs, originalParams);
        } else if (value instanceof Map<?, ?> mapVal) {
            Map<String, Object> resolved = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapVal.entrySet()) {
                resolved.put(String.valueOf(entry.getKey()),
                        resolveValue(entry.getValue(), stepOutputs, originalParams));
            }
            return resolved;
        } else if (value instanceof List<?> listVal) {
            List<Object> resolved = new ArrayList<>();
            for (Object item : listVal) {
                resolved.add(resolveValue(item, stepOutputs, originalParams));
            }
            return resolved;
        }
        return value;
    }

    /**
     * 解析字符串值中的占位符
     */
    private Object resolveStringValue(String value, Map<Integer, List<BatchJobItem>> stepOutputs,
                                       Map<String, Object> originalParams) {
        if (value.length() > MAX_TEMPLATE_LENGTH) {
            log.warn("模板字符串超出安全长度限制: length={}", value.length());
            return value;
        }

        // 优先检查聚合引用: {{steps[N].output.all_xxx}}
        Matcher aggregateFullMatch = STEP_AGGREGATE_PATTERN.matcher(value.trim());
        if (aggregateFullMatch.matches()) {
            return resolveAggregateReference(aggregateFullMatch, stepOutputs);
        }

        // 如果整个字符串就是一个占位符，直接返回原始类型值（保留非String类型）
        Matcher fullMatch = STEP_OUTPUT_PATTERN.matcher(value.trim());
        if (fullMatch.matches()) {
            return resolveStepReference(fullMatch, stepOutputs);
        }

        Matcher inputFullMatch = INPUT_PATTERN.matcher(value.trim());
        if (inputFullMatch.matches()) {
            return resolveInputReference(inputFullMatch, originalParams);
        }

        // 部分替换：字符串内嵌占位符 → 替换为字符串
        String result = value;
        result = replaceStepReferences(result, stepOutputs);
        result = replaceInputReferences(result, originalParams);
        return result;
    }

    /**
     * 解析步骤输出引用
     */
    private Object resolveStepReference(Matcher matcher, Map<Integer, List<BatchJobItem>> stepOutputs) {
        int stepNumber = parseStepNumber(matcher.group(1));
        if (stepNumber < 0) {
            return null;
        }
        String section = matcher.group(2); // "output" or "item"
        String fieldPath = matcher.group(3);

        List<BatchJobItem> items = stepOutputs.get(stepNumber);
        if (items == null || items.isEmpty()) {
            log.warn("步骤 {} 无输出数据，占位符未解析", stepNumber);
            return null;
        }

        // 取第一个完成的 item（单输出场景）
        BatchJobItem firstItem = items.get(0);

        if ("item".equals(section)) {
            return resolveItemField(firstItem, fieldPath);
        }

        // output → 从 item.params 中的 _output 子 map 读取
        return resolveOutputField(firstItem, fieldPath);
    }

    /**
     * 聚合引用: 遍历指定步骤的所有 items，收集同一字段值返回 List
     * 用于 fan-out 场景: {{steps[1].output.all_file_url}} → List<String>
     */
    private Object resolveAggregateReference(Matcher matcher, Map<Integer, List<BatchJobItem>> stepOutputs) {
        int stepNumber = parseStepNumber(matcher.group(1));
        if (stepNumber < 0) {
            return Collections.emptyList();
        }
        String section = matcher.group(2); // "output" or "item"
        String fieldPath = matcher.group(3);

        List<BatchJobItem> items = stepOutputs.get(stepNumber);
        if (items == null || items.isEmpty()) {
            log.warn("步骤 {} 无输出数据，聚合引用未解析", stepNumber);
            return Collections.emptyList();
        }

        List<Object> aggregated = new ArrayList<>();
        for (BatchJobItem item : items) {
            Object val;
            if ("item".equals(section)) {
                val = resolveItemField(item, fieldPath);
            } else {
                val = resolveOutputField(item, fieldPath);
            }
            if (val != null) {
                aggregated.add(val);
            }
        }
        return aggregated;
    }

    /**
     * 从 item 的 _output 中读取输出字段
     */
    @SuppressWarnings("unchecked")
    private Object resolveOutputField(BatchJobItem item, String fieldPath) {
        if (item.getParams() == null) return null;

        // 优先从 _output 读取（task 完成后回写的输出）
        Object outputMap = item.getParams().get("_output");
        if (outputMap instanceof Map<?, ?> output) {
            Object val = navigatePath((Map<String, Object>) output, fieldPath);
            if (val != null) return val;
        }

        // fallback: 直接从 item 属性读取
        return resolveItemField(item, fieldPath);
    }

    /**
     * 解析 item 直接字段
     */
    private Object resolveItemField(BatchJobItem item, String fieldPath) {
        return switch (fieldPath) {
            case "task_id", "taskId" -> item.getTaskId();
            case "asset_id", "assetId" -> item.getAssetId();
            case "relation_id", "relationId" -> item.getRelationId();
            case "entity_id", "entityId" -> item.getEntityId();
            case "entity_type", "entityType" -> item.getEntityType();
            case "entity_name", "entityName" -> item.getEntityName();
            case "provider_id", "providerId" -> item.getProviderId();
            case "credit_cost", "creditCost" -> item.getCreditCost();
            default -> {
                // 尝试从 params 中读取
                if (item.getParams() != null) {
                    yield item.getParams().get(fieldPath);
                }
                yield null;
            }
        };
    }

    /**
     * 解析输入引用
     */
    private Object resolveInputReference(Matcher matcher, Map<String, Object> originalParams) {
        String fieldPath = matcher.group(1);
        if (originalParams == null) return null;
        return navigatePath(originalParams, fieldPath);
    }

    /**
     * 替换字符串中的步骤引用（部分替换模式）
     */
    private String replaceStepReferences(String value, Map<Integer, List<BatchJobItem>> stepOutputs) {
        Matcher matcher = STEP_OUTPUT_PATTERN.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            Object resolved = resolveStepReference(matcher, stepOutputs);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(
                    resolved != null ? String.valueOf(resolved) : ""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 替换字符串中的输入引用（部分替换模式）
     */
    private String replaceInputReferences(String value, Map<String, Object> originalParams) {
        Matcher matcher = INPUT_PATTERN.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            Object resolved = resolveInputReference(matcher, originalParams);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(
                    resolved != null ? String.valueOf(resolved) : ""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 按点分路径导航 Map
     * 例如 "output.text" 会先取 map["output"] 再取其中的 "text"
     */
    @SuppressWarnings("unchecked")
    private Object navigatePath(Map<String, Object> map, String path) {
        String[] parts = path.split("\\.");
        if (parts.length > MAX_PATH_DEPTH) {
            log.warn("路径深度超出安全范围: {}", path);
            return null;
        }
        Object current = map;
        for (String part : parts) {
            if (current instanceof Map<?, ?> m) {
                current = m.get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    /**
     * 安全解析步骤编号，防止超大数字导致 NumberFormatException
     * @return 有效步骤编号，或 -1 表示无效
     */
    private int parseStepNumber(String raw) {
        if (raw.length() > 3) {
            log.warn("步骤编号过长，拒绝解析: {}", raw);
            return -1;
        }
        int stepNumber = Integer.parseInt(raw);
        if (stepNumber > MAX_STEP_NUMBER) {
            log.warn("步骤编号超出安全范围: {}", stepNumber);
            return -1;
        }
        return stepNumber;
    }
}
