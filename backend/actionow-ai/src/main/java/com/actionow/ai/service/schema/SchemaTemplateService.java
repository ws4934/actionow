package com.actionow.ai.service.schema;

import com.actionow.ai.dto.schema.*;
import com.actionow.ai.entity.ModelProvider;
import com.actionow.ai.service.ModelProviderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Schema模板服务
 * 提供预置的参数Schema模板，用于快速创建模型提供商配置
 *
 * @author Actionow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaTemplateService {

    private final ModelProviderService modelProviderService;
    private final ObjectMapper objectMapper;

    /**
     * 内置模板缓存
     */
    private final Map<String, SchemaTemplate> builtInTemplates = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 初始化内置模板
        registerBuiltInTemplates();
        log.info("Registered {} built-in schema templates", builtInTemplates.size());
    }

    /**
     * 获取所有模板
     *
     * @param providerType 生成类型过滤（可选）
     * @return 模板列表
     */
    public List<SchemaTemplate> getTemplates(String providerType) {
        return builtInTemplates.values().stream()
                .filter(t -> providerType == null || providerType.equalsIgnoreCase(t.getProviderType()))
                .sorted(Comparator.comparingInt(SchemaTemplate::getOrder))
                .collect(Collectors.toList());
    }

    /**
     * 获取模板详情
     *
     * @param templateId 模板ID
     * @return 模板
     */
    public SchemaTemplate getTemplate(String templateId) {
        return builtInTemplates.get(templateId);
    }

    /**
     * 应用模板到模型提供商
     *
     * @param templateId 模板ID
     * @param providerId 模型提供商ID
     * @return 是否成功
     */
    public boolean applyTemplate(String templateId, String providerId) {
        SchemaTemplate template = builtInTemplates.get(templateId);
        if (template == null) {
            return false;
        }

        ModelProvider provider = modelProviderService.getById(providerId);
        if (provider == null) {
            return false;
        }

        // 转换并应用
        try {
            provider.setInputSchema(convertToMapList(template.getParams()));
            provider.setInputGroups(convertToMapList(template.getGroups()));
            provider.setExclusiveGroups(convertToMapList(template.getExclusiveGroups()));
            modelProviderService.update(provider);
            return true;
        } catch (Exception e) {
            log.error("Failed to apply template {} to provider {}", templateId, providerId, e);
            return false;
        }
    }

    /**
     * 注册内置模板
     */
    private void registerBuiltInTemplates() {
        // 图片生成基础模板
        registerTemplate(createImageGenerationBasicTemplate());

        // 图片生成高级模板
        registerTemplate(createImageGenerationAdvancedTemplate());

        // 视频生成模板
        registerTemplate(createVideoGenerationTemplate());

        // 音频生成模板
        registerTemplate(createAudioGenerationTemplate());

        // 文本解析模板
        registerTemplate(createTextParsingTemplate());

        // 图生图模板
        registerTemplate(createImage2ImageTemplate());
    }

    private void registerTemplate(SchemaTemplate template) {
        builtInTemplates.put(template.getId(), template);
    }

    // ========== 预置模板定义 ==========

    /**
     * 图片生成基础模板
     */
    private SchemaTemplate createImageGenerationBasicTemplate() {
        List<InputParamDefinition> params = Arrays.asList(
                InputParamDefinition.builder()
                        .name("prompt")
                        .type("TEXTAREA")
                        .label("提示词")
                        .labelEn("Prompt")
                        .description("描述你想要生成的图片内容")
                        .required(true)
                        .placeholder("请输入图片描述，例如：一只可爱的橘猫躺在阳光下")
                        .order(1)
                        .validation(InputParamValidation.builder()
                                .minLength(1)
                                .maxLength(2000)
                                .build())
                        .build(),

                InputParamDefinition.builder()
                        .name("negative_prompt")
                        .type("TEXTAREA")
                        .label("负面提示词")
                        .labelEn("Negative Prompt")
                        .description("描述你不想在图片中出现的内容")
                        .required(false)
                        .placeholder("low quality, blurry, distorted")
                        .order(2)
                        .build(),

                InputParamDefinition.builder()
                        .name("size")
                        .type("SELECT")
                        .label("图片尺寸")
                        .labelEn("Size")
                        .required(true)
                        .defaultValue("1024x1024")
                        .order(3)
                        .options(Arrays.asList(
                                SelectOption.builder().value("512x512").label("512x512").build(),
                                SelectOption.builder().value("768x768").label("768x768").build(),
                                SelectOption.builder().value("1024x1024").label("1024x1024 (推荐)").build(),
                                SelectOption.builder().value("1024x576").label("1024x576 (16:9)").build(),
                                SelectOption.builder().value("576x1024").label("576x1024 (9:16)").build()
                        ))
                        .build(),

                InputParamDefinition.builder()
                        .name("num_images")
                        .type("NUMBER")
                        .label("生成数量")
                        .labelEn("Number of Images")
                        .required(false)
                        .defaultValue(1)
                        .order(4)
                        .validation(InputParamValidation.builder()
                                .min(1)
                                .max(4)
                                .step(1)
                                .build())
                        .build()
        );

        return SchemaTemplate.builder()
                .id("image-generation-basic")
                .name("图片生成基础模板")
                .nameEn("Basic Image Generation")
                .description("包含提示词、负面提示词、尺寸、数量等基本参数")
                .providerType("IMAGE")
                .order(1)
                .params(params)
                .groups(Collections.emptyList())
                .exclusiveGroups(Collections.emptyList())
                .build();
    }

    /**
     * 图片生成高级模板
     */
    private SchemaTemplate createImageGenerationAdvancedTemplate() {
        List<InputParamDefinition> params = Arrays.asList(
                // 基础参数
                InputParamDefinition.builder()
                        .name("prompt")
                        .type("TEXTAREA")
                        .label("提示词")
                        .labelEn("Prompt")
                        .required(true)
                        .group("basic")
                        .order(1)
                        .validation(InputParamValidation.builder().minLength(1).maxLength(2000).build())
                        .build(),

                InputParamDefinition.builder()
                        .name("negative_prompt")
                        .type("TEXTAREA")
                        .label("负面提示词")
                        .labelEn("Negative Prompt")
                        .group("basic")
                        .order(2)
                        .build(),

                InputParamDefinition.builder()
                        .name("size")
                        .type("SELECT")
                        .label("图片尺寸")
                        .labelEn("Size")
                        .required(true)
                        .defaultValue("1024x1024")
                        .group("basic")
                        .order(3)
                        .options(Arrays.asList(
                                SelectOption.builder().value("512x512").label("512x512").build(),
                                SelectOption.builder().value("768x768").label("768x768").build(),
                                SelectOption.builder().value("1024x1024").label("1024x1024").build(),
                                SelectOption.builder().value("1536x1536").label("1536x1536").build()
                        ))
                        .build(),

                // 高级参数
                InputParamDefinition.builder()
                        .name("steps")
                        .type("NUMBER")
                        .label("采样步数")
                        .labelEn("Steps")
                        .defaultValue(30)
                        .group("advanced")
                        .order(1)
                        .validation(InputParamValidation.builder().min(1).max(100).step(1).build())
                        .helpTip("更多步数通常意味着更高质量，但生成时间更长")
                        .build(),

                InputParamDefinition.builder()
                        .name("cfg_scale")
                        .type("NUMBER")
                        .label("引导系数")
                        .labelEn("CFG Scale")
                        .defaultValue(7.0)
                        .group("advanced")
                        .order(2)
                        .validation(InputParamValidation.builder().min(1.0).max(20.0).step(0.5).build())
                        .helpTip("控制图片与提示词的匹配程度，值越高越严格")
                        .build(),

                InputParamDefinition.builder()
                        .name("seed")
                        .type("NUMBER")
                        .label("随机种子")
                        .labelEn("Seed")
                        .defaultValue(-1)
                        .group("advanced")
                        .order(3)
                        .validation(InputParamValidation.builder().min(-1).max(2147483647).build())
                        .helpTip("-1 表示随机种子")
                        .build(),

                InputParamDefinition.builder()
                        .name("sampler")
                        .type("SELECT")
                        .label("采样器")
                        .labelEn("Sampler")
                        .defaultValue("euler_a")
                        .group("advanced")
                        .order(4)
                        .options(Arrays.asList(
                                SelectOption.builder().value("euler").label("Euler").build(),
                                SelectOption.builder().value("euler_a").label("Euler A").build(),
                                SelectOption.builder().value("dpm++_2m").label("DPM++ 2M").build(),
                                SelectOption.builder().value("dpm++_sde").label("DPM++ SDE").build()
                        ))
                        .build(),

                // 风格参数
                InputParamDefinition.builder()
                        .name("style")
                        .type("SELECT")
                        .label("风格")
                        .labelEn("Style")
                        .group("style")
                        .order(1)
                        .options(Arrays.asList(
                                SelectOption.builder().value("realistic").label("写实").build(),
                                SelectOption.builder().value("anime").label("动漫").build(),
                                SelectOption.builder().value("illustration").label("插画").build(),
                                SelectOption.builder().value("3d").label("3D渲染").build(),
                                SelectOption.builder().value("pixel").label("像素艺术").build()
                        ))
                        .build()
        );

        List<InputParamGroup> groups = Arrays.asList(
                InputParamGroup.builder()
                        .name("basic")
                        .label("基础参数")
                        .labelEn("Basic")
                        .order(1)
                        .collapsed(false)
                        .build(),
                InputParamGroup.builder()
                        .name("advanced")
                        .label("高级参数")
                        .labelEn("Advanced")
                        .order(2)
                        .collapsed(true)
                        .build(),
                InputParamGroup.builder()
                        .name("style")
                        .label("风格设置")
                        .labelEn("Style")
                        .order(3)
                        .collapsed(true)
                        .build()
        );

        return SchemaTemplate.builder()
                .id("image-generation-advanced")
                .name("图片生成高级模板")
                .nameEn("Advanced Image Generation")
                .description("包含采样步数、引导系数、种子、采样器等高级参数")
                .providerType("IMAGE")
                .order(2)
                .params(params)
                .groups(groups)
                .exclusiveGroups(Collections.emptyList())
                .build();
    }

    /**
     * 视频生成模板
     */
    private SchemaTemplate createVideoGenerationTemplate() {
        List<InputParamDefinition> params = Arrays.asList(
                InputParamDefinition.builder()
                        .name("prompt")
                        .type("TEXTAREA")
                        .label("视频描述")
                        .labelEn("Prompt")
                        .required(true)
                        .order(1)
                        .build(),

                InputParamDefinition.builder()
                        .name("duration")
                        .type("SELECT")
                        .label("视频时长")
                        .labelEn("Duration")
                        .required(true)
                        .defaultValue("5")
                        .order(2)
                        .options(Arrays.asList(
                                SelectOption.builder().value("3").label("3秒").build(),
                                SelectOption.builder().value("5").label("5秒").build(),
                                SelectOption.builder().value("10").label("10秒").build()
                        ))
                        .build(),

                InputParamDefinition.builder()
                        .name("aspect_ratio")
                        .type("SELECT")
                        .label("宽高比")
                        .labelEn("Aspect Ratio")
                        .defaultValue("16:9")
                        .order(3)
                        .options(Arrays.asList(
                                SelectOption.builder().value("16:9").label("16:9 横屏").build(),
                                SelectOption.builder().value("9:16").label("9:16 竖屏").build(),
                                SelectOption.builder().value("1:1").label("1:1 正方形").build()
                        ))
                        .build(),

                InputParamDefinition.builder()
                        .name("first_frame")
                        .type("IMAGE")
                        .label("首帧图片")
                        .labelEn("First Frame")
                        .description("可选，上传首帧图片控制视频开始画面")
                        .order(4)
                        .build()
        );

        return SchemaTemplate.builder()
                .id("video-generation")
                .name("视频生成模板")
                .nameEn("Video Generation")
                .description("适用于文生视频、图生视频场景")
                .providerType("VIDEO")
                .order(10)
                .params(params)
                .groups(Collections.emptyList())
                .exclusiveGroups(Collections.emptyList())
                .build();
    }

    /**
     * 音频生成模板
     */
    private SchemaTemplate createAudioGenerationTemplate() {
        List<InputParamDefinition> params = Arrays.asList(
                InputParamDefinition.builder()
                        .name("text")
                        .type("TEXTAREA")
                        .label("文本内容")
                        .labelEn("Text")
                        .required(true)
                        .description("需要转换为语音的文本")
                        .order(1)
                        .build(),

                InputParamDefinition.builder()
                        .name("voice")
                        .type("SELECT")
                        .label("音色")
                        .labelEn("Voice")
                        .required(true)
                        .order(2)
                        .options(Arrays.asList(
                                SelectOption.builder().value("female_1").label("女声1").build(),
                                SelectOption.builder().value("female_2").label("女声2").build(),
                                SelectOption.builder().value("male_1").label("男声1").build(),
                                SelectOption.builder().value("male_2").label("男声2").build()
                        ))
                        .build(),

                InputParamDefinition.builder()
                        .name("speed")
                        .type("NUMBER")
                        .label("语速")
                        .labelEn("Speed")
                        .defaultValue(1.0)
                        .order(3)
                        .validation(InputParamValidation.builder()
                                .min(0.5).max(2.0).step(0.1)
                                .build())
                        .build(),

                InputParamDefinition.builder()
                        .name("pitch")
                        .type("NUMBER")
                        .label("音调")
                        .labelEn("Pitch")
                        .defaultValue(1.0)
                        .order(4)
                        .validation(InputParamValidation.builder()
                                .min(0.5).max(2.0).step(0.1)
                                .build())
                        .build()
        );

        return SchemaTemplate.builder()
                .id("audio-generation")
                .name("音频生成模板")
                .nameEn("Audio Generation")
                .description("适用于文字转语音(TTS)场景")
                .providerType("AUDIO")
                .order(20)
                .params(params)
                .groups(Collections.emptyList())
                .exclusiveGroups(Collections.emptyList())
                .build();
    }

    /**
     * 文本解析模板
     */
    private SchemaTemplate createTextParsingTemplate() {
        List<InputParamDefinition> params = Arrays.asList(
                InputParamDefinition.builder()
                        .name("content")
                        .type("TEXTAREA")
                        .label("剧本内容")
                        .labelEn("Script Content")
                        .required(true)
                        .description("粘贴或输入需要解析的剧本文本")
                        .order(1)
                        .validation(InputParamValidation.builder()
                                .minLength(100)
                                .maxLength(50000)
                                .build())
                        .build(),

                InputParamDefinition.builder()
                        .name("parse_characters")
                        .type("BOOLEAN")
                        .label("提取角色")
                        .labelEn("Parse Characters")
                        .defaultValue(true)
                        .order(2)
                        .build(),

                InputParamDefinition.builder()
                        .name("parse_scenes")
                        .type("BOOLEAN")
                        .label("提取场景")
                        .labelEn("Parse Scenes")
                        .defaultValue(true)
                        .order(3)
                        .build(),

                InputParamDefinition.builder()
                        .name("parse_props")
                        .type("BOOLEAN")
                        .label("提取道具")
                        .labelEn("Parse Props")
                        .defaultValue(true)
                        .order(4)
                        .build(),

                InputParamDefinition.builder()
                        .name("script_id")
                        .type("TEXT")
                        .label("关联剧本ID")
                        .labelEn("Script ID")
                        .required(true)
                        .description("解析结果将关联到此剧本")
                        .order(5)
                        .build()
        );

        return SchemaTemplate.builder()
                .id("text-parsing")
                .name("剧本解析模板")
                .nameEn("Script Parsing")
                .description("用于解析剧本文本，自动提取角色、场景、道具")
                .providerType("TEXT")
                .order(30)
                .params(params)
                .groups(Collections.emptyList())
                .exclusiveGroups(Collections.emptyList())
                .build();
    }

    /**
     * 图生图模板
     */
    private SchemaTemplate createImage2ImageTemplate() {
        List<InputParamDefinition> params = Arrays.asList(
                InputParamDefinition.builder()
                        .name("image")
                        .type("IMAGE")
                        .label("原始图片")
                        .labelEn("Source Image")
                        .required(true)
                        .description("上传需要转换的原始图片")
                        .order(1)
                        .build(),

                InputParamDefinition.builder()
                        .name("prompt")
                        .type("TEXTAREA")
                        .label("转换提示词")
                        .labelEn("Prompt")
                        .required(true)
                        .description("描述你想要的转换效果")
                        .order(2)
                        .build(),

                InputParamDefinition.builder()
                        .name("strength")
                        .type("NUMBER")
                        .label("转换强度")
                        .labelEn("Strength")
                        .defaultValue(0.7)
                        .order(3)
                        .validation(InputParamValidation.builder()
                                .min(0.0).max(1.0).step(0.1)
                                .build())
                        .helpTip("值越高，转换效果越明显，但可能偏离原图")
                        .build(),

                InputParamDefinition.builder()
                        .name("preserve_structure")
                        .type("BOOLEAN")
                        .label("保持结构")
                        .labelEn("Preserve Structure")
                        .defaultValue(true)
                        .order(4)
                        .helpTip("开启后将尽量保持原图的构图和结构")
                        .build()
        );

        return SchemaTemplate.builder()
                .id("image-to-image")
                .name("图生图模板")
                .nameEn("Image to Image")
                .description("基于原图进行风格转换或内容修改")
                .providerType("IMAGE")
                .order(3)
                .params(params)
                .groups(Collections.emptyList())
                .exclusiveGroups(Collections.emptyList())
                .build();
    }

    // ========== 工具方法 ==========

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> convertToMapList(List<?> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object obj : list) {
            Map<String, Object> map = objectMapper.convertValue(obj, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            result.add(map);
        }
        return result;
    }

    /**
     * Schema模板
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SchemaTemplate {
        private String id;
        private String name;
        private String nameEn;
        private String description;
        private String providerType;
        private int order;
        private List<InputParamDefinition> params;
        private List<InputParamGroup> groups;
        private List<ExclusiveGroup> exclusiveGroups;
    }
}
