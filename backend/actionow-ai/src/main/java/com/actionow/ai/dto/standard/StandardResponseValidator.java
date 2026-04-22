package com.actionow.ai.dto.standard;

import com.actionow.ai.plugin.model.PluginExecutionResult.ExecutionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 标准响应校验器
 * 用于验证 Groovy 脚本返回的响应是否符合规范
 *
 * @author Actionow
 */
@Slf4j
public class StandardResponseValidator {

    /**
     * 校验响应是否有效
     *
     * @param response 标准响应
     * @return 校验错误列表，空列表表示校验通过
     */
    public static List<String> validate(StandardResponse response) {
        List<String> errors = new ArrayList<>();

        if (response == null) {
            errors.add("响应不能为空");
            return errors;
        }

        // 1. 校验 outputType
        if (response.getOutputType() == null) {
            errors.add("outputType 不能为空");
        }

        // 2. 校验 status
        if (response.getStatus() == null) {
            errors.add("status 不能为空");
        }

        // 3. 失败时必须有 error 信息
        if (response.getStatus() == ExecutionStatus.FAILED) {
            validateError(response.getError(), errors);
        }

        // 4. 成功时根据 outputType 校验对应数据
        if (response.getStatus() == ExecutionStatus.SUCCEEDED) {
            validateSuccessData(response, errors);
        }

        return errors;
    }

    /**
     * 校验并返回是否有效
     */
    public static boolean isValid(StandardResponse response) {
        return validate(response).isEmpty();
    }

    /**
     * 校验并在无效时抛出异常
     */
    public static void validateOrThrow(StandardResponse response) {
        List<String> errors = validate(response);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("响应格式不符合规范: " + String.join("; ", errors));
        }
    }

    /**
     * 校验错误信息
     */
    private static void validateError(ErrorInfo error, List<String> errors) {
        if (error == null) {
            errors.add("失败状态必须包含 error 信息");
            return;
        }
        if (!StringUtils.hasText(error.getCode())) {
            errors.add("error.code 不能为空");
        }
        if (!StringUtils.hasText(error.getMessage())) {
            errors.add("error.message 不能为空");
        }
    }

    /**
     * 校验成功数据
     */
    private static void validateSuccessData(StandardResponse response, List<String> errors) {
        OutputType outputType = response.getOutputType();
        if (outputType == null) {
            return; // 已在上面校验
        }

        if (outputType.isMediaType()) {
            validateMediaOutput(response.getMedia(), errors);
        } else if (outputType.isEntityType()) {
            validateEntityOutput(response, errors);
        } else if (outputType == OutputType.TEXT_CONTENT) {
            if (!StringUtils.hasText(response.getTextContent())) {
                errors.add("TEXT_CONTENT 类型必须包含 textContent");
            }
        }
    }

    /**
     * 校验媒体输出
     */
    private static void validateMediaOutput(MediaOutput media, List<String> errors) {
        if (media == null) {
            errors.add("媒体类型必须包含 media 数据");
            return;
        }
        if (media.getMediaType() == null) {
            errors.add("media.mediaType 不能为空");
        }
        if (media.getItems() == null || media.getItems().isEmpty()) {
            errors.add("media.items 不能为空");
            return;
        }

        // 校验每个媒体项
        for (int i = 0; i < media.getItems().size(); i++) {
            MediaItem item = media.getItems().get(i);
            String prefix = "media.items[" + i + "].";

            if (!StringUtils.hasText(item.getFileUrl())) {
                errors.add(prefix + "fileUrl 不能为空");
            }
            if (!StringUtils.hasText(item.getMimeType())) {
                errors.add(prefix + "mimeType 不能为空");
            }
        }
    }

    /**
     * 校验实体输出
     */
    private static void validateEntityOutput(StandardResponse response, List<String> errors) {
        OutputType outputType = response.getOutputType();
        List<EntityOutput> entities = response.getEntities();

        if (entities == null || entities.isEmpty()) {
            errors.add("实体类型必须包含 entities 数据");
            return;
        }

        // 校验每个实体
        for (int i = 0; i < entities.size(); i++) {
            EntityOutput entity = entities.get(i);
            String prefix = "entities[" + i + "].";

            if (entity.getEntityType() == null) {
                errors.add(prefix + "entityType 不能为空");
                continue;
            }
            if (entity.getData() == null) {
                errors.add(prefix + "data 不能为空");
                continue;
            }

            // 根据实体类型校验必填字段
            validateEntityData(entity, prefix, errors);
        }
    }

    /**
     * 校验实体数据
     */
    private static void validateEntityData(EntityOutput entity, String prefix, List<String> errors) {
        Object data = entity.getData();
        EntityType entityType = entity.getEntityType();

        // 如果 data 是 Map，进行字段校验
        if (data instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) data;

            switch (entityType) {
                case CHARACTER, SCENE, PROP, STYLE -> {
                    // 这些类型必须有 name
                    if (!StringUtils.hasText((String) dataMap.get("name"))) {
                        errors.add(prefix + "data.name 不能为空");
                    }
                }
                case SCRIPT, EPISODE -> {
                    // 剧本和剧集必须有 title
                    if (!StringUtils.hasText((String) dataMap.get("title"))) {
                        errors.add(prefix + "data.title 不能为空");
                    }
                }
                case STORYBOARD -> {
                    // 分镜推荐有 sequence 或 synopsis
                    // 不做强制校验
                }
            }
        }
    }

    /**
     * 获取校验结果的简短摘要
     */
    public static String getSummary(List<String> errors) {
        if (errors.isEmpty()) {
            return "校验通过";
        }
        return String.format("发现 %d 个错误: %s", errors.size(), errors.get(0));
    }
}
