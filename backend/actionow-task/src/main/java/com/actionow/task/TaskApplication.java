package com.actionow.task;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 任务服务启动类
 *
 * @author Actionow
 */
@SpringBootApplication(scanBasePackages = "com.actionow")
@EnableFeignClients(basePackages = "com.actionow")
@MapperScan("com.actionow.task.**.mapper")
@EnableAsync
@EnableScheduling
public class TaskApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskApplication.class, args);
    }
}
