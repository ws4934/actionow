package com.actionow.ai.plugin.groovy;

import com.actionow.ai.plugin.groovy.exception.GroovySecurityException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.stmt.ForStatement;
import org.codehaus.groovy.ast.stmt.WhileStatement;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Groovy脚本验证器
 * 验证脚本语法和安全性
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroovyScriptValidator {

    private final GroovySandboxConfig sandboxConfig;

    /**
     * 验证脚本
     *
     * @param scriptContent 脚本内容
     * @return 验证结果
     */
    public ValidationResult validate(String scriptContent) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 1. 空脚本检查
        if (scriptContent == null || scriptContent.isBlank()) {
            errors.add("Script content cannot be empty");
            return new ValidationResult(false, errors, warnings);
        }

        // 2. 语法检查
        try {
            checkSyntax(scriptContent);
        } catch (CompilationFailedException e) {
            errors.add("Syntax error: " + e.getMessage());
            return new ValidationResult(false, errors, warnings);
        }

        // 3. 安全检查
        if (sandboxConfig.isEnabled()) {
            SecurityCheckResult securityResult = checkSecurity(scriptContent);
            errors.addAll(securityResult.errors());
            warnings.addAll(securityResult.warnings());
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * 检查语法
     * 使用 CompilationUnit 只进行到 PARSING 阶段，避免加载全局 AST 转换
     * 解决 Spring Boot fat JAR 环境中的兼容性问题
     */
    private void checkSyntax(String scriptContent) throws CompilationFailedException {
        CompilerConfiguration config = new CompilerConfiguration();
        // 使用 CompilationUnit 进行语法检查，只解析到 PARSING 阶段
        // 避免加载全局 AST 转换，解决 Spring Boot fat JAR 兼容性问题
        CompilationUnit cu = new CompilationUnit(config);
        cu.addSource("Script", scriptContent);
        cu.compile(Phases.PARSING);
    }

    /**
     * 检查安全性
     */
    private SecurityCheckResult checkSecurity(String scriptContent) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 检查禁止的类
        for (String blockedClass : sandboxConfig.getBlockedClasses()) {
            String simpleClassName = blockedClass.substring(blockedClass.lastIndexOf('.') + 1);
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(simpleClassName) + "\\b");
            Matcher matcher = pattern.matcher(scriptContent);
            if (matcher.find()) {
                errors.add("Blocked class detected: " + blockedClass);
            }
        }

        // 检查禁止的方法
        for (String blockedMethod : sandboxConfig.getBlockedMethods()) {
            Pattern pattern = Pattern.compile("\\." + Pattern.quote(blockedMethod) + "\\s*\\(");
            Matcher matcher = pattern.matcher(scriptContent);
            if (matcher.find()) {
                errors.add("Blocked method detected: " + blockedMethod);
            }
        }

        // 检查危险模式
        checkDangerousPatterns(scriptContent, errors, warnings);

        return new SecurityCheckResult(errors, warnings);
    }

    /**
     * 检查危险模式
     */
    private void checkDangerousPatterns(String scriptContent, List<String> errors, List<String> warnings) {
        // 检查 import 语句
        Pattern importPattern = Pattern.compile("import\\s+([\\w.]+\\*?)");
        Matcher importMatcher = importPattern.matcher(scriptContent);
        while (importMatcher.find()) {
            String importStatement = importMatcher.group(1);
            if (!sandboxConfig.isImportAllowed(importStatement)) {
                if (sandboxConfig.isClassBlocked(importStatement)) {
                    errors.add("Blocked import: " + importStatement);
                } else {
                    warnings.add("Potentially unsafe import: " + importStatement);
                }
            }
        }

        // 检查 evaluate/execute
        if (scriptContent.contains("evaluate(") || scriptContent.contains(".evaluate(")) {
            errors.add("Dynamic code evaluation is not allowed");
        }

        // 检查 GroovyShell
        if (scriptContent.contains("GroovyShell")) {
            errors.add("GroovyShell usage is not allowed");
        }

        // 检查反射
        if (scriptContent.contains(".class") && scriptContent.contains("forName")) {
            errors.add("Reflection is not allowed");
        }

        // 检查无限循环风险
        if (scriptContent.contains("while(true)") || scriptContent.contains("while (true)")) {
            warnings.add("Potential infinite loop detected");
        }
    }

    /**
     * 创建安全的编译器配置
     */
    public CompilerConfiguration createSecureCompilerConfig() {
        CompilerConfiguration config = new CompilerConfiguration();

        if (sandboxConfig.isEnabled()) {
            SecureASTCustomizer secureCustomizer = new SecureASTCustomizer();

            // 禁止的类
            secureCustomizer.setDisallowedImports(new ArrayList<>(sandboxConfig.getBlockedClasses()));

            // 禁止的方法
            secureCustomizer.setDisallowedStaticImports(new ArrayList<>(sandboxConfig.getBlockedClasses()));

            // 禁止包导入
            secureCustomizer.setIndirectImportCheckEnabled(true);

            config.addCompilationCustomizers(secureCustomizer);
        }

        return config;
    }

    /**
     * 验证结果
     */
    @Data
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;

        public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = errors != null ? errors : new ArrayList<>();
            this.warnings = warnings != null ? warnings : new ArrayList<>();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }

    /**
     * 安全检查结果
     */
    private record SecurityCheckResult(List<String> errors, List<String> warnings) {
    }
}
