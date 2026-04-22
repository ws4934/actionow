package com.actionow.project;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 项目服务启动类
 *
 * @author Actionow
 */
@SpringBootApplication(scanBasePackages = "com.actionow")
@EnableFeignClients(basePackages = "com.actionow")
@MapperScan("com.actionow.project.**.mapper")
@EnableAsync
@EnableScheduling
public class ProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectApplication.class, args);
    }
}
