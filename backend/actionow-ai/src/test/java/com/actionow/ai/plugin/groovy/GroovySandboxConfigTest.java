package com.actionow.ai.plugin.groovy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GroovySandboxConfig 单元测试
 * 测试沙箱安全配置的各项功能
 */
class GroovySandboxConfigTest {

    private GroovySandboxConfig config;

    @BeforeEach
    void setUp() {
        config = new GroovySandboxConfig();
    }

    @Nested
    @DisplayName("类黑名单测试")
    class BlockedClassesTests {

        @Test
        @DisplayName("应该阻止 Runtime 类")
        void shouldBlockRuntimeClass() {
            assertTrue(config.isClassBlocked("java.lang.Runtime"));
        }

        @Test
        @DisplayName("应该阻止 ProcessBuilder 类")
        void shouldBlockProcessBuilderClass() {
            assertTrue(config.isClassBlocked("java.lang.ProcessBuilder"));
        }

        @Test
        @DisplayName("应该阻止文件操作类")
        void shouldBlockFileClasses() {
            assertTrue(config.isClassBlocked("java.io.File"));
            assertTrue(config.isClassBlocked("java.io.FileInputStream"));
            assertTrue(config.isClassBlocked("java.nio.file.Files"));
        }

        @Test
        @DisplayName("应该阻止反射类")
        void shouldBlockReflectionClasses() {
            assertTrue(config.isClassBlocked("java.lang.Class"));
            assertTrue(config.isClassBlocked("java.lang.reflect.Method"));
            assertTrue(config.isClassBlocked("java.lang.invoke.MethodHandle"));
        }

        @Test
        @DisplayName("应该允许安全的类")
        void shouldAllowSafeClasses() {
            assertFalse(config.isClassBlocked("java.lang.String"));
            assertFalse(config.isClassBlocked("java.util.ArrayList"));
            assertFalse(config.isClassBlocked("java.time.LocalDateTime"));
        }

        @Test
        @DisplayName("null 类名应该返回 false")
        void shouldReturnFalseForNullClassName() {
            assertFalse(config.isClassBlocked(null));
        }
    }

    @Nested
    @DisplayName("方法黑名单测试")
    class BlockedMethodsTests {

        @Test
        @DisplayName("应该阻止 exec 方法")
        void shouldBlockExecMethod() {
            assertTrue(config.isMethodBlocked("exec"));
        }

        @Test
        @DisplayName("应该阻止 execute 方法")
        void shouldBlockExecuteMethod() {
            assertTrue(config.isMethodBlocked("execute"));
        }

        @Test
        @DisplayName("应该阻止反射相关方法")
        void shouldBlockReflectionMethods() {
            assertTrue(config.isMethodBlocked("invoke"));
            assertTrue(config.isMethodBlocked("forName"));
            assertTrue(config.isMethodBlocked("getMethod"));
            assertTrue(config.isMethodBlocked("setAccessible"));
        }

        @Test
        @DisplayName("不应该阻止 start 方法（已修复）")
        void shouldNotBlockStartMethod() {
            // start 方法被移除，因为它会阻断 String.startsWith()
            assertFalse(config.isMethodBlocked("start"));
        }

        @Test
        @DisplayName("应该允许常用的安全方法")
        void shouldAllowSafeMethods() {
            assertFalse(config.isMethodBlocked("toString"));
            assertFalse(config.isMethodBlocked("equals"));
            assertFalse(config.isMethodBlocked("hashCode"));
            assertFalse(config.isMethodBlocked("startsWith")); // 重要：这个方法应该被允许
            assertFalse(config.isMethodBlocked("substring"));
        }

        @Test
        @DisplayName("null 方法名应该返回 false")
        void shouldReturnFalseForNullMethodName() {
            assertFalse(config.isMethodBlocked(null));
        }
    }

    @Nested
    @DisplayName("getClass 方法拦截测试")
    class GetClassBlockingTests {

        @Test
        @DisplayName("应该阻止 getClass 方法（防止沙箱逃逸）")
        void shouldBlockGetClass() {
            assertTrue(config.isMethodBlocked("getClass"));
        }

        @Test
        @DisplayName("应该阻止 getDeclaredMethods")
        void shouldBlockGetDeclaredMethods() {
            assertTrue(config.isMethodBlocked("getDeclaredMethods"));
        }

        @Test
        @DisplayName("应该阻止 getDeclaredFields")
        void shouldBlockGetDeclaredFields() {
            assertTrue(config.isMethodBlocked("getDeclaredFields"));
        }

        @Test
        @DisplayName("应该阻止 getConstructor")
        void shouldBlockGetConstructor() {
            assertTrue(config.isMethodBlocked("getConstructor"));
        }

        @Test
        @DisplayName("应该阻止 getDeclaredConstructor")
        void shouldBlockGetDeclaredConstructor() {
            assertTrue(config.isMethodBlocked("getDeclaredConstructor"));
        }
    }

    @Nested
    @DisplayName("新增类黑名单测试")
    class AdditionalBlockedClassesTests {

        @Test
        @DisplayName("应该阻止 Compiler 类")
        void shouldBlockCompiler() {
            assertTrue(config.isClassBlocked("java.lang.Compiler"));
        }

        @Test
        @DisplayName("应该阻止 Instrumentation 类")
        void shouldBlockInstrumentation() {
            assertTrue(config.isClassBlocked("java.lang.instrument.Instrumentation"));
        }
    }

    @Nested
    @DisplayName("白名单模式测试")
    class WhitelistModeTests {

        @Test
        @DisplayName("默认应该启用白名单模式")
        void shouldBeEnabledByDefault() {
            assertTrue(config.isUseWhitelistMode());
        }

        @Test
        @DisplayName("白名单模式下应该只允许白名单中的类")
        void shouldOnlyAllowWhitelistedClassesWhenEnabled() {
            // 白名单中的类应该被允许
            assertTrue(config.isReceiverClassAllowed("java.lang.String"));
            assertTrue(config.isReceiverClassAllowed("java.util.ArrayList"));
            assertTrue(config.isReceiverClassAllowed("java.time.LocalDateTime"));

            // 不在白名单中的类应该被拒绝
            assertFalse(config.isReceiverClassAllowed("java.lang.Runtime"));
            assertFalse(config.isReceiverClassAllowed("some.unknown.Class"));
        }

        @Test
        @DisplayName("Binding 类应该始终被允许")
        void shouldAlwaysAllowBindingClasses() {
            assertTrue(config.isReceiverClassAllowed(
                "com.actionow.ai.plugin.groovy.binding.JsonBinding"));
            assertTrue(config.isReceiverClassAllowed(
                "com.actionow.ai.plugin.groovy.binding.AssetBinding"));
        }
    }

    @Nested
    @DisplayName("综合方法调用检查测试")
    class MethodCallAllowedTests {

        @Test
        @DisplayName("黑名单模式下应该允许安全的方法调用")
        void shouldAllowSafeMethodCallsInBlacklistMode() {
            config.setUseWhitelistMode(false);

            assertTrue(config.isMethodCallAllowed("java.lang.String", "startsWith"));
            assertTrue(config.isMethodCallAllowed("java.util.List", "add"));
            assertTrue(config.isMethodCallAllowed("java.util.Map", "get"));
        }

        @Test
        @DisplayName("黑名单模式下应该阻止危险的方法调用")
        void shouldBlockDangerousMethodCallsInBlacklistMode() {
            config.setUseWhitelistMode(false);

            assertFalse(config.isMethodCallAllowed("java.lang.String", "exec"));
            assertFalse(config.isMethodCallAllowed("java.lang.Runtime", "getRuntime"));
        }

        @Test
        @DisplayName("默认白名单模式下应该只允许白名单类的安全方法")
        void shouldOnlyAllowWhitelistedClassMethodsInWhitelistMode() {
            // 默认已启用白名单模式

            // 白名单类的安全方法应该被允许
            assertTrue(config.isMethodCallAllowed("java.lang.String", "startsWith"));
            assertTrue(config.isMethodCallAllowed("java.util.ArrayList", "add"));

            // 非白名单类的方法应该被拒绝（即使方法名安全）
            assertFalse(config.isMethodCallAllowed("some.unknown.Class", "safeMethod"));
        }

        @Test
        @DisplayName("白名单模式下仍应阻止黑名单方法")
        void shouldStillBlockBlacklistedMethodsInWhitelistMode() {
            // 即使类在白名单中，黑名单方法仍应被阻止
            assertFalse(config.isMethodCallAllowed("java.lang.String", "exec"));
        }

        @Test
        @DisplayName("getClass 应在所有模式下被阻止")
        void shouldBlockGetClassInAllModes() {
            assertFalse(config.isMethodCallAllowed("java.lang.String", "getClass"));
            config.setUseWhitelistMode(false);
            assertFalse(config.isMethodCallAllowed("java.lang.String", "getClass"));
        }
    }

    @Nested
    @DisplayName("导入白名单测试")
    class ImportAllowedTests {

        @Test
        @DisplayName("应该允许 java.util.* 包的导入")
        void shouldAllowJavaUtilImports() {
            assertTrue(config.isImportAllowed("java.util.List"));
            assertTrue(config.isImportAllowed("java.util.ArrayList"));
            assertTrue(config.isImportAllowed("java.util.HashMap"));
        }

        @Test
        @DisplayName("应该允许 java.time.* 包的导入")
        void shouldAllowJavaTimeImports() {
            assertTrue(config.isImportAllowed("java.time.LocalDateTime"));
            assertTrue(config.isImportAllowed("java.time.Instant"));
        }

        @Test
        @DisplayName("应该允许特定的数学类导入")
        void shouldAllowSpecificMathImports() {
            assertTrue(config.isImportAllowed("java.math.BigDecimal"));
            assertTrue(config.isImportAllowed("java.math.BigInteger"));
        }

        @Test
        @DisplayName("不应该允许未授权的包导入")
        void shouldNotAllowUnauthorizedImports() {
            assertFalse(config.isImportAllowed("java.io.File"));
            assertFalse(config.isImportAllowed("java.net.Socket"));
            assertFalse(config.isImportAllowed("java.lang.Runtime"));
        }
    }
}
