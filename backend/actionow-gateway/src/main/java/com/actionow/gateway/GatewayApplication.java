package com.actionow.gateway;

import com.actionow.common.core.security.InternalAuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 网关服务启动类
 *
 * @author Actionow
 */
@SpringBootApplication(scanBasePackages = "com.actionow")
@Import(InternalAuthProperties.class)
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
