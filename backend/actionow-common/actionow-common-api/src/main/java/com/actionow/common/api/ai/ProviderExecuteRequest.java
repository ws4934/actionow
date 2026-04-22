package com.actionow.common.api.ai;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

import java.util.HashMap;
import java.util.Map;

/**
 * 模型提供商执行请求（统一版本）
 * <p>
 * 用于跨服务调用 AI 服务执行生成任务。
 * 所有业务参数统一通过 params Map 传递，不对具体参数做特殊处理。
 * </p>
 *
 * <h3>协议层字段</h3>
 * <ul>
 *     <li>providerId - 模型提供商 ID（必填）</li>
 *     <li>taskId - 关联任务 ID（必填）</li>
 *     <li>callbackUrl - 回调 URL（CALLBACK/POLLING 模式使用）</li>
 *     <li>responseMode - 响应模式（BLOCKING/STREAMING/CALLBACK/POLLING）</li>
 * </ul>
 *
 * <h3>业务层参数（params）</h3>
 * <p>
 * params 是一个通用的 Map，可包含任意业务参数，具体参数由模型提供商决定。
 * 常见参数如 prompt, negative_prompt, width, height, seed 等，
 * 但不同模型可能有不同的参数需求，因此不做强制校验。
 * </p>
 *
 * @author Actionow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderExecuteRequest {

    /**
     * 提供商 ID
     */
    @NotBlank(message = "提供商ID不能为空")
    @Size(max = 64, message = "提供商ID长度不能超过64字符")
    private String providerId;

    /**
     * 任务 ID（用于关联和回调）
     */
    @NotBlank(message = "任务ID不能为空")
    @Size(max = 64, message = "任务ID长度不能超过64字符")
    private String taskId;

    /**
     * 业务参数 Map
     * 包含所有生成参数，具体参数由模型提供商决定
     */
    @NotNull(message = "参数不能为空")
    @Builder.Default
    private Map<String, Object> params = new HashMap<>();

    /**
     * 回调 URL（用于异步模式）
     * 当 responseMode 为 CALLBACK 或 POLLING 时使用
     */
    @URL(message = "回调URL格式不正确")
    @Size(max = 2048, message = "回调URL长度不能超过2048字符")
    private String callbackUrl;

    /**
     * 响应模式
     * BLOCKING - 阻塞模式，同步等待结果
     * STREAMING - 流式模式，SSE 实时返回
     * CALLBACK - 回调模式，任务完成后回调
     * POLLING - 轮询模式，客户端主动查询状态
     * 默认为 null，会根据提供商支持的模式自动选择
     */
    @Pattern(regexp = "^(BLOCKING|STREAMING|CALLBACK|POLLING)?$",
            message = "响应模式必须是 BLOCKING、STREAMING、CALLBACK 或 POLLING")
    private String responseMode;

    // ==================== 验证方法 ====================

    /**
     * 校验：当响应模式为 CALLBACK 时，回调 URL 必须提供
     */
    @JsonIgnore
    @AssertTrue(message = "回调模式下必须提供回调URL")
    private boolean isCallbackUrlValid() {
        if ("CALLBACK".equalsIgnoreCase(responseMode)) {
            return callbackUrl != null && !callbackUrl.isBlank();
        }
        return true;
    }
}
