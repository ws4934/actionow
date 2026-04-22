package com.actionow.wallet;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 钱包服务启动类
 *
 * @author Actionow
 */
@SpringBootApplication(scanBasePackages = "com.actionow")
@EnableFeignClients(basePackages = "com.actionow")
@MapperScan("com.actionow.wallet.**.mapper")
public class WalletApplication {

    public static void main(String[] args) {
        SpringApplication.run(WalletApplication.class, args);
    }
}
