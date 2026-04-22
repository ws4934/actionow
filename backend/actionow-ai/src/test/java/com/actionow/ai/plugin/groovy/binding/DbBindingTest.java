package com.actionow.ai.plugin.groovy.binding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DbBinding 租户隔离与结果集限制测试
 */
class DbBindingTest {

    private DbBinding binding;

    @BeforeEach
    void setUp() {
        // 传入 null feignClient — 只测试 validateContext / limitResults 逻辑
        binding = new DbBinding(null);
    }

    @Nested
    @DisplayName("上下文验证 (validateContext)")
    class ValidateContextTests {

        @Test
        @DisplayName("workspaceId 为 null 时抛出异常")
        void shouldRejectNullWorkspaceId() {
            binding.setContext(null, "user-1", "tenant");

            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    binding.createScript(Map.of("title", "test")));
            assertTrue(ex.getMessage().contains("workspaceId"));
        }

        @Test
        @DisplayName("workspaceId 为空字符串时抛出异常")
        void shouldRejectBlankWorkspaceId() {
            binding.setContext("  ", "user-1", "tenant");

            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    binding.createScript(Map.of("title", "test")));
            assertTrue(ex.getMessage().contains("workspaceId"));
        }

        @Test
        @DisplayName("userId 为 null 时抛出异常")
        void shouldRejectNullUserId() {
            binding.setContext("ws-1", null, "tenant");

            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    binding.createScript(Map.of("title", "test")));
            assertTrue(ex.getMessage().contains("userId"));
        }

        @Test
        @DisplayName("userId 为空字符串时抛出异常")
        void shouldRejectBlankUserId() {
            binding.setContext("ws-1", "  ", "tenant");

            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    binding.createScript(Map.of("title", "test")));
            assertTrue(ex.getMessage().contains("userId"));
        }

        @Test
        @DisplayName("get 方法也需要上下文验证")
        void getMethodsShouldRequireContext() {
            // 不设置 context
            assertThrows(IllegalStateException.class, () -> binding.getScript("id-1"));
            assertThrows(IllegalStateException.class, () -> binding.getEpisode("id-1"));
            assertThrows(IllegalStateException.class, () -> binding.getStoryboard("id-1"));
            assertThrows(IllegalStateException.class, () -> binding.getCharacter("id-1"));
            assertThrows(IllegalStateException.class, () -> binding.getScene("id-1"));
            assertThrows(IllegalStateException.class, () -> binding.getProp("id-1"));
            assertThrows(IllegalStateException.class, () -> binding.getStyle("id-1"));
            assertThrows(IllegalStateException.class, () -> binding.getAsset("id-1"));
        }

        @Test
        @DisplayName("list 方法也需要上下文验证")
        void listMethodsShouldRequireContext() {
            assertThrows(IllegalStateException.class, () -> binding.listScripts());
            assertThrows(IllegalStateException.class, () -> binding.listAssets());
            assertThrows(IllegalStateException.class, () -> binding.listAssets("IMAGE"));
        }
    }

    @Nested
    @DisplayName("结果集大小限制 (limitResults)")
    class LimitResultsTests {

        @Test
        @DisplayName("null 输入返回空列表")
        void shouldReturnEmptyForNull() throws Exception {
            Method limitResults = DbBinding.class.getDeclaredMethod("limitResults", List.class);
            limitResults.setAccessible(true);

            @SuppressWarnings("unchecked")
            List<Object> result = (List<Object>) limitResults.invoke(binding, (List<?>) null);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("小于上限时原样返回")
        void shouldReturnOriginalWhenUnderLimit() throws Exception {
            Method limitResults = DbBinding.class.getDeclaredMethod("limitResults", List.class);
            limitResults.setAccessible(true);

            List<Map<String, Object>> input = List.of(
                    Map.of("id", "1"),
                    Map.of("id", "2"),
                    Map.of("id", "3")
            );

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> result = (List<Map<String, Object>>) limitResults.invoke(binding, input);
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("超过上限时截断至 MAX_RESULT_SIZE")
        void shouldTruncateWhenOverLimit() throws Exception {
            Method limitResults = DbBinding.class.getDeclaredMethod("limitResults", List.class);
            limitResults.setAccessible(true);

            // 获取 MAX_RESULT_SIZE 常量值
            var maxField = DbBinding.class.getDeclaredField("MAX_RESULT_SIZE");
            maxField.setAccessible(true);
            int maxSize = (int) maxField.get(null);

            // 构建超过上限的列表
            List<Map<String, Object>> input = new ArrayList<>();
            for (int i = 0; i < maxSize + 500; i++) {
                input.add(Map.of("id", String.valueOf(i)));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> result = (List<Map<String, Object>>) limitResults.invoke(binding, input);
            assertEquals(maxSize, result.size());
            // 验证保留的是前 N 个
            assertEquals("0", result.get(0).get("id"));
            assertEquals(String.valueOf(maxSize - 1), result.get(maxSize - 1).get("id"));
        }

        @Test
        @DisplayName("恰好等于上限时不截断")
        void shouldNotTruncateAtExactLimit() throws Exception {
            Method limitResults = DbBinding.class.getDeclaredMethod("limitResults", List.class);
            limitResults.setAccessible(true);

            var maxField = DbBinding.class.getDeclaredField("MAX_RESULT_SIZE");
            maxField.setAccessible(true);
            int maxSize = (int) maxField.get(null);

            List<Map<String, Object>> input = new ArrayList<>();
            for (int i = 0; i < maxSize; i++) {
                input.add(Map.of("id", String.valueOf(i)));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> result = (List<Map<String, Object>>) limitResults.invoke(binding, input);
            assertEquals(maxSize, result.size());
        }

        @Test
        @DisplayName("MAX_RESULT_SIZE 常量值为 10000")
        void maxResultSizeShouldBe10000() throws Exception {
            var maxField = DbBinding.class.getDeclaredField("MAX_RESULT_SIZE");
            maxField.setAccessible(true);
            assertEquals(10_000, (int) maxField.get(null));
        }
    }
}
