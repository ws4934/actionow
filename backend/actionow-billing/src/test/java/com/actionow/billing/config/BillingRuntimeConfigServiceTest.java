package com.actionow.billing.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * BillingRuntimeConfigService 动态币种配置单元测试
 *
 * 重点验证：
 *  - 默认值落在 {@code USD,CNY,EUR,GBP,JPY} 白名单（ISSUE 4 回归保护）
 *  - 币种大小写不敏感匹配，避免前端/外部接口大小写差异导致误拒
 *  - Redis 空值场景回退到编译期默认值（启动早期或运营尚未下发）
 *
 * @author Actionow
 */
@ExtendWith(MockitoExtension.class)
class BillingRuntimeConfigServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RedisMessageListenerContainer listenerContainer;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private BillingRuntimeConfigService configService;

    @BeforeEach
    void setUp() {
        // Redis 返回 null → 所有配置回退到编译期默认值
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn(null);

        configService = new BillingRuntimeConfigService(redisTemplate, listenerContainer);
        // 触发 @PostConstruct 逻辑（单测环境下需手动调用）
        configService.init();
    }

    @Test
    @DisplayName("默认币种白名单包含 USD/CNY/EUR/GBP/JPY")
    void defaultSupportedCurrencies() {
        Set<String> currencies = configService.getSupportedCurrencies();
        assertTrue(currencies.contains("USD"));
        assertTrue(currencies.contains("CNY"));
        assertTrue(currencies.contains("EUR"));
        assertTrue(currencies.contains("GBP"));
        assertTrue(currencies.contains("JPY"));
        assertEquals(5, currencies.size());
    }

    @Test
    @DisplayName("isCurrencySupported 大小写不敏感")
    void isCurrencySupportedCaseInsensitive() {
        assertTrue(configService.isCurrencySupported("USD"));
        assertTrue(configService.isCurrencySupported("usd"));
        assertTrue(configService.isCurrencySupported("Usd"));
        assertTrue(configService.isCurrencySupported("cny"));
    }

    @Test
    @DisplayName("isCurrencySupported 对未知币种返回 false")
    void isCurrencySupportedRejectsUnknown() {
        assertFalse(configService.isCurrencySupported("XYZ"));
        assertFalse(configService.isCurrencySupported("KRW"));
        assertFalse(configService.isCurrencySupported("HKD"));
    }

    @Test
    @DisplayName("null/空字符串视为不支持，避免 NPE")
    void nullAndBlankRejected() {
        assertFalse(configService.isCurrencySupported(null));
        assertFalse(configService.isCurrencySupported(""));
        assertFalse(configService.isCurrencySupported("   "));
    }

    @Test
    @DisplayName("带前后空白的币种正常解析")
    void trimsWhitespace() {
        assertTrue(configService.isCurrencySupported("  USD  "));
        assertTrue(configService.isCurrencySupported("\tCNY\n"));
    }
}
