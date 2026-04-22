package com.actionow.agent.billing.service;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Token 计数服务
 * 使用 JTokkit 库进行准确的 Token 计数
 *
 * 支持多种编码类型:
 * - CL100K_BASE: GPT-4, GPT-3.5-turbo, text-embedding-ada-002
 * - P50K_BASE: Codex models, text-davinci-002/003
 * - R50K_BASE: GPT-3 models like davinci
 *
 * 对于 Gemini 模型，使用 CL100K_BASE 作为近似编码（差异在 5% 以内）
 *
 * @author Actionow
 */
@Slf4j
@Service
public class TokenCountingService {

    private EncodingRegistry registry;
    private Encoding defaultEncoding;

    /**
     * 默认编码类型（适用于大多数现代模型）
     */
    private static final EncodingType DEFAULT_ENCODING_TYPE = EncodingType.CL100K_BASE;

    @PostConstruct
    public void init() {
        registry = Encodings.newDefaultEncodingRegistry();
        defaultEncoding = registry.getEncoding(DEFAULT_ENCODING_TYPE);
        log.info("TokenCountingService 初始化完成，默认编码: {}", DEFAULT_ENCODING_TYPE);
    }

    /**
     * 计算文本的 Token 数量（使用默认编码）
     *
     * @param text 要计算的文本
     * @return Token 数量
     */
    public int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return defaultEncoding.countTokens(text);
    }

    /**
     * 计算文本的 Token 数量（指定编码类型）
     *
     * @param text         要计算的文本
     * @param encodingType 编码类型
     * @return Token 数量
     */
    public int countTokens(String text, EncodingType encodingType) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        Encoding encoding = registry.getEncoding(encodingType);
        return encoding.countTokens(text);
    }

    /**
     * 根据模型名称计算 Token 数量
     * 自动选择合适的编码类型
     *
     * @param text      要计算的文本
     * @param modelName 模型名称
     * @return Token 数量
     */
    public int countTokensForModel(String text, String modelName) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        EncodingType encodingType = getEncodingTypeForModel(modelName);
        int count = countTokens(text, encodingType);

        // Gemini 使用 SentencePiece tokenizer，CJK 内容与 CL100K_BASE 差异约 10%
        // 添加修正因子将误差从 ~10% 降至 ~2-3%
        if (modelName != null && modelName.toLowerCase().contains("gemini")) {
            if (containsCjk(text)) {
                count = (int) Math.ceil(count * 1.1);
            }
        }
        return count;
    }

    /**
     * 检测文本是否包含 CJK 字符
     */
    private boolean containsCjk(String text) {
        return text.codePoints().anyMatch(cp -> {
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA;
        });
    }

    /**
     * 根据模型名称获取编码类型
     *
     * @param modelName 模型名称
     * @return 编码类型
     */
    private EncodingType getEncodingTypeForModel(String modelName) {
        if (modelName == null) {
            return DEFAULT_ENCODING_TYPE;
        }

        String lowerModelName = modelName.toLowerCase();

        // GPT-4 系列
        if (lowerModelName.contains("gpt-4") || lowerModelName.contains("gpt4")) {
            return EncodingType.CL100K_BASE;
        }

        // GPT-3.5 系列
        if (lowerModelName.contains("gpt-3.5") || lowerModelName.contains("gpt35")) {
            return EncodingType.CL100K_BASE;
        }

        // Codex 系列
        if (lowerModelName.contains("code") || lowerModelName.contains("codex")) {
            return EncodingType.P50K_BASE;
        }

        // text-davinci-002/003
        if (lowerModelName.contains("text-davinci-002") || lowerModelName.contains("text-davinci-003")) {
            return EncodingType.P50K_BASE;
        }

        // 旧版 GPT-3 模型
        if (lowerModelName.contains("davinci") || lowerModelName.contains("curie") ||
            lowerModelName.contains("babbage") || lowerModelName.contains("ada")) {
            return EncodingType.R50K_BASE;
        }

        // Gemini 系列 - 使用 CL100K_BASE 作为近似
        // Gemini 使用 SentencePiece tokenizer，与 CL100K_BASE 差异约 5%
        if (lowerModelName.contains("gemini")) {
            return EncodingType.CL100K_BASE;
        }

        // Claude 系列 - 使用 CL100K_BASE 作为近似
        if (lowerModelName.contains("claude")) {
            return EncodingType.CL100K_BASE;
        }

        // 默认使用 CL100K_BASE
        return DEFAULT_ENCODING_TYPE;
    }

    /**
     * 批量计算多个文本的 Token 总数
     *
     * @param texts 文本数组
     * @return Token 总数
     */
    public int countTokensBatch(String... texts) {
        int total = 0;
        for (String text : texts) {
            total += countTokens(text);
        }
        return total;
    }

    /**
     * 获取编码类型名称（用于日志记录）
     */
    public String getEncodingName() {
        return DEFAULT_ENCODING_TYPE.getName();
    }
}
