package com.actionow.ai.service.schema;

import com.actionow.ai.dto.schema.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Schema验证器
 * 用于验证用户输入是否符合Schema定义
 *
 * @author Actionow
 */
@Slf4j
@Component
public class SchemaValidator {

    /**
     * 验证结果
     */
    public record ValidationResult(boolean valid, List<ValidationError> errors) {
        public static ValidationResult success() {
            return new ValidationResult(true, Collections.emptyList());
        }

        public static ValidationResult failure(List<ValidationError> errors) {
            return new ValidationResult(false, errors);
        }

        public static ValidationResult failure(ValidationError error) {
            return new ValidationResult(false, List.of(error));
        }
    }

    /**
     * 验证错误
     */
    public record ValidationError(String field, String message, String code) {
        public static ValidationError required(String field) {
            return new ValidationError(field, "字段 '" + field + "' 是必填项", "REQUIRED");
        }

        public static ValidationError invalidType(String field, String expectedType) {
            return new ValidationError(field, "字段 '" + field + "' 类型不正确，期望类型: " + expectedType, "INVALID_TYPE");
        }

        public static ValidationError minLength(String field, int minLength) {
            return new ValidationError(field, "字段 '" + field + "' 长度不能小于 " + minLength, "MIN_LENGTH");
        }

        public static ValidationError maxLength(String field, int maxLength) {
            return new ValidationError(field, "字段 '" + field + "' 长度不能大于 " + maxLength, "MAX_LENGTH");
        }

        public static ValidationError minValue(String field, Number min) {
            return new ValidationError(field, "字段 '" + field + "' 值不能小于 " + min, "MIN_VALUE");
        }

        public static ValidationError maxValue(String field, Number max) {
            return new ValidationError(field, "字段 '" + field + "' 值不能大于 " + max, "MAX_VALUE");
        }

        public static ValidationError pattern(String field, String message) {
            return new ValidationError(field, message != null ? message : "字段 '" + field + "' 格式不正确", "PATTERN");
        }

        public static ValidationError minItems(String field, int minItems) {
            return new ValidationError(field, "字段 '" + field + "' 至少需要 " + minItems + " 项", "MIN_ITEMS");
        }

        public static ValidationError maxItems(String field, int maxItems) {
            return new ValidationError(field, "字段 '" + field + "' 最多允许 " + maxItems + " 项", "MAX_ITEMS");
        }

        public static ValidationError exclusiveRequired(String groupName) {
            return new ValidationError(groupName, "互斥组 '" + groupName + "' 必须选择一个选项", "EXCLUSIVE_REQUIRED");
        }

        public static ValidationError custom(String field, String message) {
            return new ValidationError(field, message, "CUSTOM");
        }
    }

    /**
     * 验证输入数据
     *
     * @param input           用户输入
     * @param schema          参数Schema定义
     * @param groups          参数分组定义
     * @param exclusiveGroups 互斥组定义
     * @return 验证结果
     */
    public ValidationResult validate(Map<String, Object> input,
                                     List<InputParamDefinition> schema,
                                     List<InputParamGroup> groups,
                                     List<ExclusiveGroup> exclusiveGroups) {
        if (input == null) {
            input = Collections.emptyMap();
        }

        List<ValidationError> errors = new ArrayList<>();

        // 1. 验证普通参数
        if (!CollectionUtils.isEmpty(schema)) {
            for (InputParamDefinition param : schema) {
                // 检查条件依赖
                if (!isParamActive(param, input, schema)) {
                    continue;
                }

                Object value = input.get(param.getName());
                List<ValidationError> paramErrors = validateParam(param, value);
                errors.addAll(paramErrors);
            }
        }

        // 2. 验证互斥组
        if (!CollectionUtils.isEmpty(exclusiveGroups)) {
            for (ExclusiveGroup group : exclusiveGroups) {
                List<ValidationError> groupErrors = validateExclusiveGroup(group, input);
                errors.addAll(groupErrors);
            }
        }

        return errors.isEmpty()
                ? ValidationResult.success()
                : ValidationResult.failure(errors);
    }

    /**
     * 验证单个参数
     */
    private List<ValidationError> validateParam(InputParamDefinition param, Object value) {
        List<ValidationError> errors = new ArrayList<>();
        String fieldName = param.getName();

        // 检查必填
        if (Boolean.TRUE.equals(param.getRequired()) && isEmptyValue(value)) {
            errors.add(ValidationError.required(fieldName));
            return errors; // 必填字段为空，无需继续验证
        }

        // 如果值为空且非必填，跳过验证
        if (isEmptyValue(value)) {
            return errors;
        }

        // 根据类型验证
        InputParamType type = param.getTypeEnum();
        InputParamValidation validation = param.getValidation();

        switch (type) {
            case TEXT, TEXTAREA -> errors.addAll(validateString(fieldName, value, validation));
            case NUMBER -> errors.addAll(validateNumber(fieldName, value, validation));
            case BOOLEAN -> errors.addAll(validateBoolean(fieldName, value));
            case SELECT -> errors.addAll(validateSelect(fieldName, value, param.getOptions()));
            case IMAGE, VIDEO, AUDIO, DOCUMENT -> errors.addAll(validateFile(fieldName, value, param.getFileConfig()));
            case TEXT_LIST -> errors.addAll(validateStringList(fieldName, value, validation));
            case NUMBER_LIST -> errors.addAll(validateNumberList(fieldName, value, validation));
            case IMAGE_LIST, VIDEO_LIST, AUDIO_LIST, DOCUMENT_LIST ->
                    errors.addAll(validateFileList(fieldName, value, param.getFileConfig(), validation));
        }

        return errors;
    }

    /**
     * 验证字符串类型
     */
    private List<ValidationError> validateString(String fieldName, Object value, InputParamValidation validation) {
        List<ValidationError> errors = new ArrayList<>();

        if (!(value instanceof String strValue)) {
            errors.add(ValidationError.invalidType(fieldName, "TEXT"));
            return errors;
        }

        if (validation != null) {
            // 最小长度
            if (validation.getMinLength() != null && strValue.length() < validation.getMinLength()) {
                errors.add(ValidationError.minLength(fieldName, validation.getMinLength()));
            }

            // 最大长度
            if (validation.getMaxLength() != null && strValue.length() > validation.getMaxLength()) {
                errors.add(ValidationError.maxLength(fieldName, validation.getMaxLength()));
            }

            // 正则匹配
            if (StringUtils.hasText(validation.getPattern())) {
                try {
                    if (!Pattern.matches(validation.getPattern(), strValue)) {
                        errors.add(ValidationError.pattern(fieldName, validation.getPatternMessage()));
                    }
                } catch (Exception e) {
                    log.warn("Invalid regex pattern for field {}: {}", fieldName, validation.getPattern());
                }
            }
        }

        return errors;
    }

    /**
     * 验证数字类型
     */
    private List<ValidationError> validateNumber(String fieldName, Object value, InputParamValidation validation) {
        List<ValidationError> errors = new ArrayList<>();

        Number numValue;
        if (value instanceof Number num) {
            numValue = num;
        } else if (value instanceof String str) {
            try {
                numValue = Double.parseDouble(str);
            } catch (NumberFormatException e) {
                errors.add(ValidationError.invalidType(fieldName, "NUMBER"));
                return errors;
            }
        } else {
            errors.add(ValidationError.invalidType(fieldName, "NUMBER"));
            return errors;
        }

        if (validation != null) {
            // 最小值
            if (validation.getMin() != null && numValue.doubleValue() < validation.getMin().doubleValue()) {
                errors.add(ValidationError.minValue(fieldName, validation.getMin()));
            }

            // 最大值
            if (validation.getMax() != null && numValue.doubleValue() > validation.getMax().doubleValue()) {
                errors.add(ValidationError.maxValue(fieldName, validation.getMax()));
            }
        }

        return errors;
    }

    /**
     * 验证布尔类型
     */
    private List<ValidationError> validateBoolean(String fieldName, Object value) {
        List<ValidationError> errors = new ArrayList<>();

        if (!(value instanceof Boolean) && !(value instanceof String)) {
            errors.add(ValidationError.invalidType(fieldName, "BOOLEAN"));
        } else if (value instanceof String str) {
            if (!str.equalsIgnoreCase("true") && !str.equalsIgnoreCase("false")) {
                errors.add(ValidationError.invalidType(fieldName, "BOOLEAN"));
            }
        }

        return errors;
    }

    /**
     * 验证下拉选择
     */
    private List<ValidationError> validateSelect(String fieldName, Object value, List<SelectOption> options) {
        List<ValidationError> errors = new ArrayList<>();

        if (CollectionUtils.isEmpty(options)) {
            return errors;
        }

        Set<String> validValues = options.stream()
                .map(SelectOption::getValue)
                .collect(Collectors.toSet());

        String strValue = value.toString();
        if (!validValues.contains(strValue)) {
            errors.add(ValidationError.custom(fieldName, "字段 '" + fieldName + "' 的值不在允许的选项范围内"));
        }

        return errors;
    }

    /**
     * 验证文件
     */
    private List<ValidationError> validateFile(String fieldName, Object value, InputFileConfig fileConfig) {
        List<ValidationError> errors = new ArrayList<>();

        // 文件值可以是URL字符串或文件对象
        if (value instanceof String url) {
            if (!StringUtils.hasText(url)) {
                errors.add(ValidationError.required(fieldName));
            }
            // 可以添加URL格式验证
        } else if (value instanceof Map) {
            // 文件对象验证
            @SuppressWarnings("unchecked")
            Map<String, Object> fileObj = (Map<String, Object>) value;
            if (!fileObj.containsKey("url") && !fileObj.containsKey("path")) {
                errors.add(ValidationError.custom(fieldName, "字段 '" + fieldName + "' 缺少文件路径"));
            }
        }

        return errors;
    }

    /**
     * 验证字符串列表
     */
    private List<ValidationError> validateStringList(String fieldName, Object value, InputParamValidation validation) {
        List<ValidationError> errors = new ArrayList<>();

        if (!(value instanceof List)) {
            errors.add(ValidationError.invalidType(fieldName, "TEXT_LIST"));
            return errors;
        }

        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) value;

        if (validation != null) {
            if (validation.getMinItems() != null && list.size() < validation.getMinItems()) {
                errors.add(ValidationError.minItems(fieldName, validation.getMinItems()));
            }
            if (validation.getMaxItems() != null && list.size() > validation.getMaxItems()) {
                errors.add(ValidationError.maxItems(fieldName, validation.getMaxItems()));
            }
        }

        // 验证每个元素
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof String)) {
                errors.add(ValidationError.invalidType(fieldName + "[" + i + "]", "TEXT"));
            }
        }

        return errors;
    }

    /**
     * 验证数字列表
     */
    private List<ValidationError> validateNumberList(String fieldName, Object value, InputParamValidation validation) {
        List<ValidationError> errors = new ArrayList<>();

        if (!(value instanceof List)) {
            errors.add(ValidationError.invalidType(fieldName, "NUMBER_LIST"));
            return errors;
        }

        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) value;

        if (validation != null) {
            if (validation.getMinItems() != null && list.size() < validation.getMinItems()) {
                errors.add(ValidationError.minItems(fieldName, validation.getMinItems()));
            }
            if (validation.getMaxItems() != null && list.size() > validation.getMaxItems()) {
                errors.add(ValidationError.maxItems(fieldName, validation.getMaxItems()));
            }
        }

        return errors;
    }

    /**
     * 验证文件列表
     */
    private List<ValidationError> validateFileList(String fieldName, Object value,
                                                    InputFileConfig fileConfig,
                                                    InputParamValidation validation) {
        List<ValidationError> errors = new ArrayList<>();

        if (!(value instanceof List)) {
            errors.add(ValidationError.invalidType(fieldName, "FILE_LIST"));
            return errors;
        }

        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) value;

        if (validation != null) {
            if (validation.getMinItems() != null && list.size() < validation.getMinItems()) {
                errors.add(ValidationError.minItems(fieldName, validation.getMinItems()));
            }
            if (validation.getMaxItems() != null && list.size() > validation.getMaxItems()) {
                errors.add(ValidationError.maxItems(fieldName, validation.getMaxItems()));
            }
        }

        return errors;
    }

    /**
     * 验证互斥组
     */
    private List<ValidationError> validateExclusiveGroup(ExclusiveGroup group, Map<String, Object> input) {
        List<ValidationError> errors = new ArrayList<>();

        if (CollectionUtils.isEmpty(group.getOptions())) {
            return errors;
        }

        // 收集所有选项关联的参数
        List<String> activeParams = new ArrayList<>();
        for (ExclusiveOption option : group.getOptions()) {
            if (!CollectionUtils.isEmpty(option.getParams())) {
                for (String paramName : option.getParams()) {
                    if (input.containsKey(paramName) && !isEmptyValue(input.get(paramName))) {
                        activeParams.add(paramName);
                    }
                }
            }
        }

        // 检查是否有多个选项被选中
        Set<String> selectedOptions = new HashSet<>();
        for (ExclusiveOption option : group.getOptions()) {
            if (!CollectionUtils.isEmpty(option.getParams())) {
                for (String paramName : option.getParams()) {
                    if (input.containsKey(paramName) && !isEmptyValue(input.get(paramName))) {
                        selectedOptions.add(option.getValue());
                        break;
                    }
                }
            }
        }

        if (selectedOptions.size() > 1) {
            errors.add(ValidationError.custom(group.getName(),
                    "互斥组 '" + group.getName() + "' 只能选择一个选项"));
        }

        // 检查必填
        if (Boolean.TRUE.equals(group.getRequired()) && selectedOptions.isEmpty()) {
            errors.add(ValidationError.exclusiveRequired(group.getName()));
        }

        return errors;
    }

    /**
     * 检查参数是否激活（考虑条件依赖）
     */
    private boolean isParamActive(InputParamDefinition param, Map<String, Object> input,
                                  List<InputParamDefinition> allParams) {
        DependsOnCondition dependsOn = param.getDependsOn();
        if (dependsOn == null || !StringUtils.hasText(dependsOn.getField())) {
            return true;
        }

        Object dependValue = input.get(dependsOn.getField());
        ConditionOperator operator = dependsOn.getOperatorEnum();
        Object expectedValue = dependsOn.getValue();

        boolean conditionMet = evaluateCondition(dependValue, operator, expectedValue);

        // 根据作用类型判断
        EffectType effectType = param.getEffectTypeEnum();
        if (effectType == null) {
            effectType = EffectType.VISIBILITY;
        }

        return switch (effectType) {
            case VISIBILITY -> conditionMet;
            case HIDDEN -> !conditionMet;
            case DISABLED, REQUIRED -> true; // 这些情况参数仍然存在
        };
    }

    /**
     * 评估条件
     */
    private boolean evaluateCondition(Object actualValue, ConditionOperator operator, Object expectedValue) {
        if (operator == null) {
            operator = ConditionOperator.EQ;
        }

        return switch (operator) {
            case EQ -> Objects.equals(actualValue, expectedValue);
            case NEQ -> !Objects.equals(actualValue, expectedValue);
            case CONTAINS -> actualValue instanceof String str && expectedValue != null
                    && str.contains(expectedValue.toString());
            case STARTS_WITH -> actualValue instanceof String str && expectedValue != null
                    && str.startsWith(expectedValue.toString());
            case ENDS_WITH -> actualValue instanceof String str && expectedValue != null
                    && str.endsWith(expectedValue.toString());
            case IN -> expectedValue instanceof List<?> list && list.contains(actualValue);
            case NOT_IN -> !(expectedValue instanceof List<?> list && list.contains(actualValue));
            case EMPTY -> isEmptyValue(actualValue);
            case NOT_EMPTY -> !isEmptyValue(actualValue);
            case GT -> compareNumbers(actualValue, expectedValue) > 0;
            case GTE -> compareNumbers(actualValue, expectedValue) >= 0;
            case LT -> compareNumbers(actualValue, expectedValue) < 0;
            case LTE -> compareNumbers(actualValue, expectedValue) <= 0;
        };
    }

    /**
     * 比较数字
     */
    private int compareNumbers(Object a, Object b) {
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue());
        }
        return 0;
    }

    /**
     * 判断值是否为空
     */
    private boolean isEmptyValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String str) {
            return str.trim().isEmpty();
        }
        if (value instanceof Collection<?> coll) {
            return coll.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }
}
