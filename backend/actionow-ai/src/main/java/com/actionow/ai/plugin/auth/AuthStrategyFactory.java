package com.actionow.ai.plugin.auth;

import com.actionow.ai.plugin.auth.impl.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 认证策略工厂
 * 根据认证类型创建对应的策略实例
 *
 * @author Actionow
 */
@Slf4j
@Component
public class AuthStrategyFactory {

    private final Map<String, AuthenticationStrategy> strategyMap = new HashMap<>();

    public AuthStrategyFactory() {
        // 注册内置策略
        register(new ApiKeyAuthStrategy());
        register(new AkSkAuthStrategy());
        register(new BearerTokenAuthStrategy());
        register(new CustomHeaderAuthStrategy());
        register(new NoAuthStrategy());
    }

    /**
     * 注册认证策略
     *
     * @param strategy 策略实例
     */
    public void register(AuthenticationStrategy strategy) {
        strategyMap.put(strategy.getType().toUpperCase(), strategy);
        log.debug("Registered auth strategy: {}", strategy.getType());
    }

    /**
     * 获取认证策略
     *
     * @param authType 认证类型
     * @return 策略实例
     */
    public AuthenticationStrategy getStrategy(String authType) {
        if (authType == null || authType.isEmpty()) {
            return strategyMap.get(AuthConfig.AuthType.NONE);
        }

        AuthenticationStrategy strategy = strategyMap.get(authType.toUpperCase());
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown auth type: " + authType);
        }
        return strategy;
    }

    /**
     * 获取认证策略（可选）
     *
     * @param authType 认证类型
     * @return 策略实例（可选）
     */
    public Optional<AuthenticationStrategy> findStrategy(String authType) {
        if (authType == null || authType.isEmpty()) {
            return Optional.of(strategyMap.get(AuthConfig.AuthType.NONE));
        }
        return Optional.ofNullable(strategyMap.get(authType.toUpperCase()));
    }

    /**
     * 检查是否支持指定的认证类型
     *
     * @param authType 认证类型
     * @return 是否支持
     */
    public boolean supports(String authType) {
        return authType != null && strategyMap.containsKey(authType.toUpperCase());
    }

    /**
     * 获取所有支持的认证类型
     *
     * @return 类型列表
     */
    public Map<String, String> getSupportedTypes() {
        Map<String, String> types = new HashMap<>();
        strategyMap.forEach((key, strategy) ->
            types.put(key, strategy.getDisplayName())
        );
        return types;
    }

    /**
     * 无认证策略（内部类）
     */
    private static class NoAuthStrategy implements AuthenticationStrategy {
        @Override
        public String getType() {
            return AuthConfig.AuthType.NONE;
        }

        @Override
        public String getDisplayName() {
            return "无认证";
        }

        @Override
        public void applyAuth(org.springframework.http.HttpHeaders headers, AuthConfig config) {
            // 不添加任何认证信息
        }

        @Override
        public String[] getSensitiveFields() {
            return new String[0];
        }
    }
}
