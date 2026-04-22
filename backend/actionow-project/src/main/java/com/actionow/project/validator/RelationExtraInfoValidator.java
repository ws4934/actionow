package com.actionow.project.validator;

import com.actionow.common.core.exception.BusinessException;
import com.actionow.common.core.result.ResultCode;
import com.actionow.project.constant.ProjectConstants;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 实体关系 extra_info 字段校验器
 * 按 relationType 核对已知字段的类型；未登记的 relationType 或未定义字段放行，
 * 避免对扩展字段形成阻塞，仅拦截"类型错"这一类难以在读取侧兜底的错误。
 */
@Component
public class RelationExtraInfoValidator {

    private static final String T_STRING = "string";
    private static final String T_INTEGER = "integer";
    private static final String T_BOOLEAN = "boolean";

    private final Map<String, Map<String, String>> schemas = buildSchemas();

    public void validate(String relationType, Map<String, Object> extraInfo) {
        if (extraInfo == null || extraInfo.isEmpty() || relationType == null) {
            return;
        }
        Map<String, String> schema = schemas.get(relationType);
        if (schema == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : extraInfo.entrySet()) {
            String expected = schema.get(entry.getKey());
            if (expected == null || entry.getValue() == null) {
                continue;
            }
            if (!matches(expected, entry.getValue())) {
                throw new BusinessException(ResultCode.PARAM_INVALID,
                        "extra_info 字段 '" + entry.getKey() + "' 类型应为 " + expected
                                + "，实际为 " + entry.getValue().getClass().getSimpleName());
            }
        }
    }

    private boolean matches(String expected, Object value) {
        return switch (expected) {
            case T_STRING -> value instanceof String;
            case T_INTEGER -> value instanceof Number n
                    && n.doubleValue() == n.longValue();
            case T_BOOLEAN -> value instanceof Boolean;
            default -> true;
        };
    }

    private static Map<String, Map<String, String>> buildSchemas() {
        Map<String, Map<String, String>> m = new HashMap<>();
        m.put(ProjectConstants.RelationType.APPEARS_IN, Map.of(
                "position", T_STRING,
                "positionDetail", T_STRING,
                "action", T_STRING,
                "expression", T_STRING,
                "outfitOverride", T_STRING,
                "sceneOverride", T_STRING));
        m.put(ProjectConstants.RelationType.USES, Map.of(
                "position", T_STRING,
                "interaction", T_STRING,
                "state", T_STRING));
        m.put(ProjectConstants.RelationType.SPEAKS_IN, Map.of(
                "dialogueIndex", T_INTEGER,
                "text", T_STRING,
                "emotion", T_STRING,
                "voiceStyle", T_STRING,
                "timing", T_STRING));
        m.put(ProjectConstants.RelationType.CHARACTER_RELATIONSHIP, Map.of(
                "relationshipType", T_STRING,
                "bidirectional", T_BOOLEAN,
                "description", T_STRING));
        m.put(ProjectConstants.RelationType.EQUIPPED_WITH, Map.of(
                "description", T_STRING));
        m.put(ProjectConstants.RelationType.OWNS, Map.of(
                "description", T_STRING));
        return m;
    }
}
