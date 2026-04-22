package com.actionow.common.data.config;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.actionow.common.data.handler.TimestampTzTypeHandler;
import com.actionow.common.data.interceptor.TenantSchemaInterceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * MyBatis Plus 配置
 *
 * @author Actionow
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 自定义 UUIDv7 ID 生成器
     * 用于替换 MyBatis Plus 默认的 UUID 生成器
     */
    @Bean
    public IdentifierGenerator identifierGenerator() {
        return new UuidV7IdentifierGenerator();
    }

    /**
     * Register TimestampTzTypeHandler globally so PostgreSQL TIMESTAMPTZ
     * columns map correctly to Java LocalDateTime in all services.
     */
    @Bean
    public ConfigurationCustomizer timestampTzCustomizer() {
        return configuration -> {
            TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();
            TimestampTzTypeHandler handler = new TimestampTzTypeHandler();
            registry.register(LocalDateTime.class, handler);
            registry.register(LocalDateTime.class, org.apache.ibatis.type.JdbcType.TIMESTAMP, handler);
            registry.register(LocalDateTime.class, org.apache.ibatis.type.JdbcType.OTHER, handler);
        };
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 分页插件
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        paginationInterceptor.setMaxLimit(500L);  // 最大分页限制
        paginationInterceptor.setOverflow(false); // 溢出总页数后是否进行处理
        interceptor.addInnerInterceptor(paginationInterceptor);

        // 乐观锁插件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        return interceptor;
    }

    /**
     * 租户 Schema 拦截器
     */
    @Bean
    public TenantSchemaInterceptor tenantSchemaInterceptor() {
        return new TenantSchemaInterceptor();
    }

    /**
     * 将租户 Schema 拦截器注册到所有 SqlSessionFactory
     * 使用 ApplicationRunner 在所有 Bean 初始化后执行
     */
    @Bean
    public ApplicationRunner registerTenantInterceptor(ApplicationContext applicationContext) {
        return args -> {
            Map<String, SqlSessionFactory> factories = applicationContext.getBeansOfType(SqlSessionFactory.class);
            if (!factories.isEmpty()) {
                TenantSchemaInterceptor interceptor = applicationContext.getBean(TenantSchemaInterceptor.class);
                for (SqlSessionFactory sqlSessionFactory : factories.values()) {
                    org.apache.ibatis.session.Configuration configuration = sqlSessionFactory.getConfiguration();
                    if (!configuration.getInterceptors().contains(interceptor)) {
                        configuration.addInterceptor(interceptor);
                    }
                }
            }
        };
    }
}
