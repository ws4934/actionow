package com.actionow.collab;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 协作服务启动类
 * 提供实时协作功能：在线状态、用户位置、实体协作、评论、通知、审核
 *
 * @author Actionow
 */
@SpringBootApplication(
        scanBasePackages = "com.actionow"
)
@EnableFeignClients(basePackages = "com.actionow")
@EnableScheduling
@MapperScan("com.actionow.collab.**.mapper")
public class CollabApplication {

    public static void main(String[] args) {
        SpringApplication.run(CollabApplication.class, args);
    }
}
