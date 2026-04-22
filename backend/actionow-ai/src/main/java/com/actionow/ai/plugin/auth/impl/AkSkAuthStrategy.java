package com.actionow.ai.plugin.auth.impl;

import com.actionow.ai.plugin.auth.AuthConfig;
import com.actionow.ai.plugin.auth.AuthenticationStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

/**
 * AK/SK签名认证策略
 * 使用AccessKey和SecretKey生成签名
 *
 * @author Actionow
 */
@Slf4j
public class AkSkAuthStrategy implements AuthenticationStrategy {

    @Override
    public String getType() {
        return AuthConfig.AuthType.AK_SK;
    }

    @Override
    public String getDisplayName() {
        return "AK/SK签名认证";
    }

    @Override
    public void applyAuth(HttpHeaders headers, AuthConfig config) {
        if (!StringUtils.hasText(config.getAccessKey())) {
            throw new IllegalArgumentException("Access Key is required");
        }
        if (!StringUtils.hasText(config.getSecretKey())) {
            throw new IllegalArgumentException("Secret Key is required");
        }

        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signatureVersion = StringUtils.hasText(config.getSignatureVersion())
            ? config.getSignatureVersion()
            : "1";

        // 构建待签名字符串
        String stringToSign = buildStringToSign(config.getAccessKey(), timestamp, signatureVersion);

        // 生成签名
        String signature = generateSignature(
            stringToSign,
            config.getSecretKey(),
            config.getSignatureAlgorithm()
        );

        // 设置请求头
        String accessKeyHeader = StringUtils.hasText(config.getApiKeyHeader())
            ? config.getApiKeyHeader()
            : "X-Access-Key";
        String timestampHeader = StringUtils.hasText(config.getTimestampHeader())
            ? config.getTimestampHeader()
            : "X-Timestamp";
        String signatureHeader = StringUtils.hasText(config.getSignatureHeader())
            ? config.getSignatureHeader()
            : "X-Signature";

        headers.set(accessKeyHeader, config.getAccessKey());
        headers.set(timestampHeader, timestamp);
        headers.set(signatureHeader, signature);
        headers.set("X-Signature-Version", signatureVersion);
    }

    @Override
    public ValidationResult validate(AuthConfig config) {
        if (!StringUtils.hasText(config.getAccessKey())) {
            return ValidationResult.failure("Access Key不能为空");
        }
        if (!StringUtils.hasText(config.getSecretKey())) {
            return ValidationResult.failure("Secret Key不能为空");
        }
        return ValidationResult.success();
    }

    @Override
    public String[] getSensitiveFields() {
        return new String[]{"accessKey", "secretKey"};
    }

    /**
     * 构建待签名字符串
     */
    private String buildStringToSign(String accessKey, String timestamp, String version) {
        return accessKey + "\n" + timestamp + "\n" + version;
    }

    /**
     * 生成签名
     */
    private String generateSignature(String stringToSign, String secretKey, String algorithm) {
        try {
            String algo = StringUtils.hasText(algorithm) ? algorithm : "HmacSHA256";
            Mac mac = Mac.getInstance(algo);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                secretKey.getBytes(StandardCharsets.UTF_8),
                algo
            );
            mac.init(secretKeySpec);
            byte[] signatureBytes = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signatureBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to generate signature: {}", e.getMessage(), e);
            throw new RuntimeException("Signature generation failed", e);
        }
    }
}
