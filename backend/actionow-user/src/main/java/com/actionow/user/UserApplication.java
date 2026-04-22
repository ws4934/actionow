package com.actionow.user;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 用户服务启动类
 *
 * @author Actionow
 */
@SpringBootApplication(scanBasePackages = "com.actionow")
@EnableFeignClients(basePackages = "com.actionow")
@MapperScan("com.actionow.user.**.mapper")
@OpenAPIDefinition(info = @Info(
        title = "Actionow 用户服务 API",
        version = "3.1.0",
        description = "用户服务API - 注册、登录、认证、OAuth"
))
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
