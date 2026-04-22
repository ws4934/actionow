package com.actionow.ai.plugin.groovy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.kohsuke.groovy.sandbox.SandboxTransformer;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Groovy脚本编译缓存
 * 使用Caffeine缓存编译后的脚本，提升执行性能
 *
 * @author Actionow
 */
@Slf4j
@Component
public class GroovyScriptCache {

    /**
     * 编译后脚本缓存
     * Key: 脚本内容的hash
     * Value: 编译后的Script类
     */
    private final Cache<String, Class<? extends Script>> scriptCache;

    /**
     * 缓存命中统计
     */
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    /**
     * Groovy编译配置
     */
    private final CompilerConfiguration compilerConfiguration;

    /**
     * 专用 GroovyClassLoader，避免扫描 Spring Boot fat JAR
     */
    private final GroovyClassLoader groovyClassLoader;

    public GroovyScriptCache() {
        // 配置缓存
        this.scriptCache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterAccess(Duration.ofHours(1))
                .expireAfterWrite(Duration.ofHours(4))
                .recordStats()
                .build();

        // 配置编译器
        this.compilerConfiguration = new CompilerConfiguration();
        this.compilerConfiguration.setSourceEncoding("UTF-8");
        // 在编译时（AST 阶段）注入 SandboxTransformer，确保字节码本身通过拦截器路由。
        // 仅靠运行时 GroovyInterceptor 是不够的，因为 Groovy 元编程（metaclass 替换）可以绕过
        // 运行时拦截。编译期变换确保每一次方法/属性/new 调用都被插桩，无法通过元编程逃逸。
        // 注意：此处直接实例化 SandboxTransformer 而非依赖 ServiceLoader 发现，
        // 避免 TransformSafeClassLoader 屏蔽 META-INF/services 的副作用。
        this.compilerConfiguration.addCompilationCustomizers(new SandboxTransformer());

        // 创建专用 GroovyClassLoader，使用 TransformSafeClassLoader 作为父加载器
        // TransformSafeClassLoader 代理 Spring Boot 的类加载器，但过滤掉 AST transform 资源扫描
        // 这样既能访问 Groovy 类，又能避免扫描 fat JAR 导致的 IO 异常
        ClassLoader parentLoader = new TransformSafeClassLoader(GroovyScriptCache.class.getClassLoader());
        this.groovyClassLoader = new GroovyClassLoader(parentLoader, compilerConfiguration);
    }

    /**
     * 获取或编译脚本
     *
     * @param scriptContent 脚本内容
     * @return 编译后的Script类
     */
    public Class<? extends Script> getOrCompile(String scriptContent) {
        String cacheKey = generateCacheKey(scriptContent);

        Class<? extends Script> scriptClass = scriptCache.getIfPresent(cacheKey);
        if (scriptClass != null) {
            cacheHits.incrementAndGet();
            log.debug("Script cache hit for key: {}", cacheKey.substring(0, Math.min(8, cacheKey.length())));
            return scriptClass;
        }

        cacheMisses.incrementAndGet();
        log.debug("Script cache miss, compiling script...");

        // 编译脚本
        scriptClass = compileScript(scriptContent);
        scriptCache.put(cacheKey, scriptClass);

        return scriptClass;
    }

    /**
     * 编译脚本
     */
    @SuppressWarnings("unchecked")
    private Class<? extends Script> compileScript(String scriptContent) {
        // 使用专用的 GroovyClassLoader 编译脚本
        GroovyShell shell = new GroovyShell(groovyClassLoader, compilerConfiguration);
        Script script = shell.parse(scriptContent);
        return (Class<? extends Script>) script.getClass();
    }

    /**
     * 生成缓存键
     * 使用 SHA-256 哈希避免碰撞风险
     */
    private String generateCacheKey(String scriptContent) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(scriptContent.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 在所有 JVM 实现中都必须支持，不应该抛出此异常
            log.warn("SHA-256 not available, falling back to content-based key");
            // 备用方案：使用内容长度 + 前后缀哈希组合
            int len = scriptContent.length();
            int prefixHash = scriptContent.substring(0, Math.min(100, len)).hashCode();
            int suffixHash = scriptContent.substring(Math.max(0, len - 100)).hashCode();
            return String.format("%d_%08x_%08x", len, prefixHash, suffixHash);
        }
    }

    /**
     * 清除缓存
     */
    public void clear() {
        scriptCache.invalidateAll();
        log.info("Script cache cleared");
    }

    /**
     * 从缓存中移除指定脚本
     */
    public void invalidate(String scriptContent) {
        String cacheKey = generateCacheKey(scriptContent);
        scriptCache.invalidate(cacheKey);
    }

    /**
     * 获取缓存大小
     */
    public long size() {
        return scriptCache.estimatedSize();
    }

    /**
     * 获取缓存命中率
     */
    public double getHitRate() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        return total > 0 ? (double) hits / total : 0.0;
    }

    /**
     * 获取缓存统计信息
     */
    public CacheStats getStats() {
        return new CacheStats(
                scriptCache.estimatedSize(),
                cacheHits.get(),
                cacheMisses.get(),
                getHitRate()
        );
    }

    /**
     * 缓存统计信息
     */
    public record CacheStats(long size, long hits, long misses, double hitRate) {
    }

    /**
     * 安全的 ClassLoader 代理，避免 Groovy 扫描 Spring Boot fat JAR 中的 AST transforms
     * 对于 META-INF/services/org.codehaus.groovy* 和 META-INF/groovy/ 资源返回空
     * 这样可以避免扫描 nested JAR 导致的 IO 异常，同时保留对 Groovy 类的正常访问
     */
    private static class TransformSafeClassLoader extends ClassLoader {
        private final ClassLoader delegate;

        TransformSafeClassLoader(ClassLoader delegate) {
            super(delegate);
            this.delegate = delegate;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            // 过滤掉 Groovy AST transform 相关的资源扫描
            if (name.startsWith("META-INF/services/org.codehaus.groovy") ||
                name.startsWith("META-INF/groovy/")) {
                return Collections.emptyEnumeration();
            }
            return delegate.getResources(name);
        }

        @Override
        public URL getResource(String name) {
            // 过滤掉 Groovy AST transform 相关的资源扫描
            if (name.startsWith("META-INF/services/org.codehaus.groovy") ||
                name.startsWith("META-INF/groovy/")) {
                return null;
            }
            return delegate.getResource(name);
        }
    }
}
