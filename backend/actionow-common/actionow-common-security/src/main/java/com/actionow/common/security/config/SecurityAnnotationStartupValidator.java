package com.actionow.common.security.config;

import com.actionow.common.security.annotation.IgnoreAuth;
import com.actionow.common.security.annotation.RequireLogin;
import com.actionow.common.security.annotation.RequireRole;
import com.actionow.common.security.annotation.RequireSystemTenant;
import com.actionow.common.security.annotation.RequireWorkspaceMember;
import com.actionow.common.security.aspect.RequireSystemTenantAspect;
import com.actionow.common.security.aspect.RequireWorkspaceMemberAspect;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 启动时强制校验控制器接口必须声明显式安全注解。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityAnnotationStartupValidator implements ApplicationRunner {

    private static final String APP_BASE_PACKAGE = "com.actionow";
    private static final List<String> INFRASTRUCTURE_PATH_PREFIXES = List.of(
            "/actuator",
            "/health",
            "/error",
            "/favicon.ico",
            "/swagger-ui",
            "/v3/api-docs",
            "/ws",
            "/sockjs"
    );
    private static final String INTERNAL_PATH_PREFIX = "/internal";

    private final RequestMappingHandlerMapping requestMappingHandlerMapping;
    private final ApplicationContext applicationContext;

    @Override
    public void run(ApplicationArguments args) {
        // 验证安全 AOP 切面 Bean 已正确加载
        verifyAopBeans();

        List<String> violations = new ArrayList<>();

        requestMappingHandlerMapping.getHandlerMethods().forEach((mapping, handlerMethod) -> {
            Class<?> beanType = handlerMethod.getBeanType();
            if (!beanType.getName().startsWith(APP_BASE_PACKAGE)) {
                return;
            }

            Set<String> paths = extractPaths(mapping);
            Set<String> businessPaths = paths.stream()
                    .filter(path -> !isInfrastructurePath(path))
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (businessPaths.isEmpty()) {
                return;
            }

            boolean hasInternalPath = businessPaths.stream().anyMatch(this::isInternalPath);
            boolean hasExternalPath = businessPaths.stream().anyMatch(path -> !isInternalPath(path));

            String endpoint = describeEndpoint(handlerMethod, businessPaths);

            if (hasInternalPath && hasExternalPath) {
                violations.add(endpoint + " -> 不能混用内部与外部路径");
                return;
            }

            EnumSet<SecurityAnnotationType> classAnnotations = collectSecurityAnnotations(beanType);
            EnumSet<SecurityAnnotationType> methodAnnotations = collectSecurityAnnotations(handlerMethod.getMethod());
            EnumSet<SecurityAnnotationType> combinedAnnotations = EnumSet.noneOf(SecurityAnnotationType.class);
            combinedAnnotations.addAll(classAnnotations);
            combinedAnnotations.addAll(methodAnnotations);

            if (hasInternalPath) {
                validateInternalEndpoint(endpoint, combinedAnnotations, violations);
                return;
            }

            validateExternalEndpoint(endpoint, businessPaths, combinedAnnotations, violations);
        });

        if (!violations.isEmpty()) {
            String details = violations.stream().sorted().collect(Collectors.joining("\n - ", " - ", ""));
            String message = "Security annotation validation failed.\n"
                    + "规则: /internal/** 必须且只能使用 @IgnoreAuth；外部接口必须且只能声明一种安全注解。\n"
                    + "Violations:\n" + details;
            log.error(message);
            throw new IllegalStateException(message);
        }

        log.info("Security annotation startup validation passed");
    }

    private void validateInternalEndpoint(String endpoint,
                                          EnumSet<SecurityAnnotationType> annotations,
                                          List<String> violations) {
        if (annotations.equals(EnumSet.of(SecurityAnnotationType.IGNORE_AUTH))) {
            return;
        }
        if (annotations.isEmpty()) {
            violations.add(endpoint + " -> /internal/** 接口必须显式声明 @IgnoreAuth");
            return;
        }
        violations.add(endpoint + " -> /internal/** 接口仅允许 @IgnoreAuth，实际为: " + toAnnotationText(annotations));
    }

    private void validateExternalEndpoint(String endpoint,
                                          Set<String> paths,
                                          EnumSet<SecurityAnnotationType> annotations,
                                          List<String> violations) {
        if (annotations.isEmpty()) {
            violations.add(endpoint + " -> 外部接口必须声明安全注解");
            return;
        }

        if (annotations.size() > 1) {
            violations.add(endpoint + " -> 外部接口只能声明一种安全注解，实际为: " + toAnnotationText(annotations));
        }
    }

    private EnumSet<SecurityAnnotationType> collectSecurityAnnotations(java.lang.reflect.AnnotatedElement element) {
        EnumSet<SecurityAnnotationType> result = EnumSet.noneOf(SecurityAnnotationType.class);
        for (SecurityAnnotationType annotationType : SecurityAnnotationType.values()) {
            if (AnnotatedElementUtils.hasAnnotation(element, annotationType.annotationClass())) {
                result.add(annotationType);
            }
        }
        return result;
    }

    private String describeEndpoint(HandlerMethod handlerMethod, Set<String> paths) {
        Method method = handlerMethod.getMethod();
        String pathText = String.join(", ", paths);
        return handlerMethod.getBeanType().getName() + "#" + method.getName() + " -> [" + pathText + "]";
    }

    private String toAnnotationText(EnumSet<SecurityAnnotationType> annotations) {
        if (annotations.isEmpty()) {
            return "<none>";
        }
        return annotations.stream()
                .map(SecurityAnnotationType::displayName)
                .collect(Collectors.joining(", "));
    }

    private Set<String> extractPaths(RequestMappingInfo mappingInfo) {
        Set<String> paths = new LinkedHashSet<>();
        if (mappingInfo.getPathPatternsCondition() != null
                && !CollectionUtils.isEmpty(mappingInfo.getPathPatternsCondition().getPatterns())) {
            mappingInfo.getPathPatternsCondition().getPatterns()
                    .forEach(pathPattern -> paths.add(pathPattern.getPatternString()));
        }
        if (mappingInfo.getPatternsCondition() != null
                && !CollectionUtils.isEmpty(mappingInfo.getPatternsCondition().getPatterns())) {
            paths.addAll(mappingInfo.getPatternsCondition().getPatterns());
        }
        if (paths.isEmpty()) {
            paths.add("<unknown>");
        }
        return paths;
    }

    private boolean isInfrastructurePath(String path) {
        if (path == null || path.isBlank() || "<unknown>".equals(path)) {
            return true;
        }
        return INFRASTRUCTURE_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private boolean isInternalPath(String path) {
        return path != null && (path.equals(INTERNAL_PATH_PREFIX) || path.startsWith(INTERNAL_PATH_PREFIX + "/"));
    }

    private void verifyAopBeans() {
        List<String> missing = new ArrayList<>();
        if (!applicationContext.containsBean("requireSystemTenantAspect")
                && applicationContext.getBeansOfType(RequireSystemTenantAspect.class).isEmpty()) {
            missing.add("RequireSystemTenantAspect");
        }
        if (!applicationContext.containsBean("requireWorkspaceMemberAspect")
                && applicationContext.getBeansOfType(RequireWorkspaceMemberAspect.class).isEmpty()) {
            missing.add("RequireWorkspaceMemberAspect");
        }
        if (!missing.isEmpty()) {
            String message = "Security AOP enforcement beans missing: " + String.join(", ", missing)
                    + ". Check @EnableFeignClients basePackages includes com.actionow "
                    + "so that WorkspaceInternalClient is discoverable.";
            log.error(message);
            throw new IllegalStateException(message);
        }
        log.info("Security AOP enforcement beans verified: RequireSystemTenantAspect, RequireWorkspaceMemberAspect");
    }

    private enum SecurityAnnotationType {
        IGNORE_AUTH(IgnoreAuth.class, "@IgnoreAuth"),
        REQUIRE_LOGIN(RequireLogin.class, "@RequireLogin"),
        REQUIRE_ROLE(RequireRole.class, "@RequireRole"),
        REQUIRE_WORKSPACE_MEMBER(RequireWorkspaceMember.class, "@RequireWorkspaceMember"),
        REQUIRE_SYSTEM_TENANT(RequireSystemTenant.class, "@RequireSystemTenant");

        private final Class<? extends Annotation> annotationClass;
        private final String displayName;

        SecurityAnnotationType(Class<? extends Annotation> annotationClass, String displayName) {
            this.annotationClass = annotationClass;
            this.displayName = displayName;
        }

        public Class<? extends Annotation> annotationClass() {
            return annotationClass;
        }

        public String displayName() {
            return displayName;
        }
    }
}
