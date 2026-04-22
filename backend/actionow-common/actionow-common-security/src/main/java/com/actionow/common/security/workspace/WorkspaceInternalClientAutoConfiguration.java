package com.actionow.common.security.workspace;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 工作空间内部客户端配置
 * 注册基于 JDBC 的本地实现，直接查询 public schema，
 * 避免各服务通过 Feign 远程调用 workspace 服务
 * <p>
 * 所有依赖 actionow-common-security 的服务均同时依赖 actionow-common-data（提供 DataSource/JdbcTemplate），
 * 不依赖 common-security 的 gateway 服务不会扫描此包，因此无需条件注解。
 *
 * @author Actionow
 */
@Configuration
public class WorkspaceInternalClientAutoConfiguration {

    @Bean
    @Primary
    public JdbcWorkspaceInternalClient jdbcWorkspaceInternalClient(JdbcTemplate jdbcTemplate) {
        return new JdbcWorkspaceInternalClient(jdbcTemplate);
    }
}
