package com.actionow.billing;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 计费服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.actionow")
@EnableFeignClients(basePackages = "com.actionow")
@EnableScheduling
@MapperScan("com.actionow.billing.**.mapper")
public class BillingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingApplication.class, args);
    }
}
