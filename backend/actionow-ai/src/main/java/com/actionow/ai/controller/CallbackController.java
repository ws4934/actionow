package com.actionow.ai.controller;

import com.actionow.ai.entity.ModelProvider;
import com.actionow.ai.entity.ModelProviderExecution;
import com.actionow.ai.feign.TaskFeignClient;
import com.actionow.ai.mapper.ModelProviderExecutionMapper;
import com.actionow.ai.plugin.PluginExecutor;
import com.actionow.ai.plugin.model.PluginConfig;
import com.actionow.ai.plugin.model.PluginExecutionResult;
import com.actionow.ai.service.ModelProviderService;
import com.actionow.common.core.result.Result;
import com.actionow.common.security.annotation.IgnoreAuth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

/**
 * AI回调接收控制器
 * 接收第三方AI服务的回调通知
 *
 * @author Actionow
 */
@Slf4j
@RestController
@RequestMapping("/callback")
@RequiredArgsConstructor
@IgnoreAuth
public class CallbackController {

    private final PluginExecutor pluginExecutor;
    private final ModelProviderService providerService;
    private final ModelProviderExecutionMapper executionMapper;
    private final TaskFeignClient taskFeignClient;

    /**
     * 通用回调接收端点
     *
     * @param providerId 提供商ID
     * @param signature 签名（可选）
     * @param body 回调数据
     */
    @PostMapping("/{providerId}")
    public Result<Void> handleCallback(
            @PathVariable String providerId,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody Map<String, Object> body) {

        log.info("Received callback for provider: {}, body: {}", providerId, body);

        try {
            // 获取提供商配置
            ModelProvider provider = providerService.getById(providerId);
            PluginConfig config = providerService.toPluginConfig(provider);

            // 签名验证（必选策略）：
            // 1. 配置了 signatureSecret → 必须通过签名验证
            // 2. 未配置 signatureSecret 但 allowUnauthenticated=true → 明确放行（高风险，需运营审批）
            // 3. 未配置 signatureSecret 且 allowUnauthenticated=false（默认）→ 拒绝，防止未鉴权回调
            if (!checkCallbackAuth(config, signature, body, providerId)) {
                return Result.fail("回调鉴权失败：请配置 signatureSecret 或显式设置 allowUnauthenticated=true");
            }

            // 提取执行ID或外部任务ID
            String executionId = extractField(body, "execution_id", "executionId");
            String externalTaskId = extractField(body, "task_id", "taskId", "id");

            // 查找执行记录
            ModelProviderExecution execution = null;
            if (StringUtils.hasText(executionId)) {
                execution = executionMapper.selectById(executionId);
            } else if (StringUtils.hasText(externalTaskId)) {
                execution = executionMapper.selectByExternalTaskId(externalTaskId);
            }

            if (execution == null) {
                log.warn("Execution not found for callback, providerId: {}, body: {}", providerId, body);
                // 即使找不到记录也返回成功，避免第三方重试
                return Result.success();
            }

            // 处理回调
            PluginExecutionResult result = pluginExecutor.handleCallback(execution.getId(), body, config);

            // 更新执行记录
            updateExecution(execution, result);

            // 转发终态结果给 Task 模块（CALLBACK 模式断链修复）
            notifyTaskIfTerminal(execution, result);

            log.info("Callback processed successfully for execution: {}", execution.getId());
            return Result.success();

        } catch (Exception e) {
            log.error("Callback processing failed for provider {}: {}", providerId, e.getMessage(), e);
            // 返回成功避免第三方重试，但记录日志
            return Result.success();
        }
    }

    /**
     * 带执行ID的回调端点
     */
    @PostMapping("/{providerId}/{executionId}")
    public Result<Void> handleCallbackWithExecution(
            @PathVariable String providerId,
            @PathVariable String executionId,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody Map<String, Object> body) {

        log.info("Received callback for provider: {}, execution: {}", providerId, executionId);

        try {
            ModelProvider provider = providerService.getById(providerId);
            PluginConfig config = providerService.toPluginConfig(provider);

            // 签名验证（与通用端点策略一致）
            if (!checkCallbackAuth(config, signature, body, providerId)) {
                return Result.fail("回调鉴权失败：请配置 signatureSecret 或显式设置 allowUnauthenticated=true");
            }

            ModelProviderExecution execution = executionMapper.selectById(executionId);
            if (execution == null) {
                log.warn("Execution not found: {}", executionId);
                return Result.success();
            }

            PluginExecutionResult result = pluginExecutor.handleCallback(executionId, body, config);
            updateExecution(execution, result);

            // 转发终态结果给 Task 模块（CALLBACK 模式断链修复）
            notifyTaskIfTerminal(execution, result);

            log.info("Callback processed successfully for execution: {}", executionId);
            return Result.success();

        } catch (Exception e) {
            log.error("Callback processing failed: {}", e.getMessage(), e);
            return Result.success();
        }
    }

    /**
     * 回调鉴权入口
     * <p>
     * 策略（优先级由高到低）：
     * 1. 配置了 signatureSecret → 执行 HMAC-SHA256 验签，失败则拒绝
     * 2. 未配置 signatureSecret 且 allowUnauthenticated=true → 放行（需明确配置，高风险）
     * 3. 未配置 signatureSecret 且 allowUnauthenticated=false（默认）→ 拒绝
     *
     * @return true=允许通过，false=拒绝
     */
    private boolean checkCallbackAuth(PluginConfig config, String signature,
                                       Map<String, Object> body, String providerId) {
        PluginConfig.CallbackConfig callbackConfig = config.getCallbackConfig();
        String secret = callbackConfig != null ? callbackConfig.getSignatureSecret() : null;

        if (StringUtils.hasText(secret)) {
            // 有密钥 → 必须验签
            boolean valid = verifySignature(body, signature, secret);
            if (!valid) {
                log.warn("[CallbackController] 签名验证失败: providerId={}", providerId);
            }
            return valid;
        }

        // 无密钥 → 检查是否显式放行
        boolean allowUnauthenticated = callbackConfig != null && callbackConfig.isAllowUnauthenticated();
        if (allowUnauthenticated) {
            log.warn("[CallbackController] 无签名验证（allowUnauthenticated=true）放行回调: providerId={}. " +
                    "生产环境强烈建议配置 signatureSecret！", providerId);
            return true;
        }

        log.error("[CallbackController] 拒绝回调：未配置 signatureSecret 且未显式允许无鉴权: providerId={}. " +
                "请在 ModelProvider 配置中设置 callbackConfig.signatureSecret 或显式设置 allowUnauthenticated=true",
                providerId);
        return false;
    }

    /**
     * 验证签名
     */
    private boolean verifySignature(Map<String, Object> body, String signature, String secret) {
        if (!StringUtils.hasText(signature)) {
            log.warn("[CallbackController] 请求缺少签名头 X-Signature，拒绝回调");
            return false;
        }

        try {
            String payload = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body);

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = Base64.getEncoder().encodeToString(hash);

            // 使用时间常数比较，防止时序攻击
            return java.security.MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            log.error("[CallbackController] 签名计算异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 提取字段
     */
    private String extractField(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof String s && !s.isEmpty()) {
                return s;
            }
        }
        return null;
    }

    /**
     * 将终态结果转发给 Task 模块
     * <p>
     * 仅在 CALLBACK 响应模式下且结果为终态时触发。
     * BLOCKING 模式下 Task 已通过同步返回值获取结果，无需二次通知。
     * 此方法独立 try/catch，通知失败不影响对第三方的 200 响应。
     */
    private void notifyTaskIfTerminal(ModelProviderExecution execution, PluginExecutionResult result) {
        if (!result.getStatus().isTerminal()) {
            return;
        }
        if (!"CALLBACK".equals(execution.getResponseMode())) {
            return;
        }
        if (!StringUtils.hasText(execution.getTaskId())) {
            log.warn("[CallbackController] CALLBACK 模式下 taskId 为空，无法通知 Task 模块: executionId={}",
                    execution.getId());
            return;
        }
        try {
            Map<String, Object> payload = result.toCallbackPayload();
            taskFeignClient.notifyTaskCallback(execution.getTaskId(), payload);
            log.info("[CallbackController] 已通知 Task 模块: taskId={}, status={}",
                    execution.getTaskId(), result.getStatus().getCode());
        } catch (Exception e) {
            log.error("[CallbackController] 通知 Task 模块失败，任务可能滞留 RUNNING: taskId={}, error={}",
                    execution.getTaskId(), e.getMessage(), e);
        }
    }

    /**
     * 更新执行记录
     */
    private void updateExecution(ModelProviderExecution execution, PluginExecutionResult result) {
        execution.setCallbackReceived(true);
        execution.setCallbackReceivedAt(LocalDateTime.now());
        execution.setStatus(result.getStatus().getCode().toUpperCase());

        if (result.getOutputs() != null) {
            execution.setOutputData(result.getOutputs());
        }
        if (result.getErrorCode() != null) {
            execution.setErrorCode(result.getErrorCode());
        }
        if (result.getErrorMessage() != null) {
            execution.setErrorMessage(result.getErrorMessage());
        }
        if (result.getStatus().isTerminal()) {
            execution.setCompletedAt(LocalDateTime.now());
        }

        executionMapper.updateById(execution);
    }
}
