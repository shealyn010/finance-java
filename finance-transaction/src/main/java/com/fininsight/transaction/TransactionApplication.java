package com.fininsight.transaction;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@EnableRabbit
@SpringBootApplication(scanBasePackages = {"com.fininsight"})
@MapperScan("com.fininsight.transaction.mapper")
public class TransactionApplication {
    public static void main(String[] args) {
        SpringApplication.run(TransactionApplication.class, args);
    }
}
