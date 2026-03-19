package com.aiaps.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = "com.aiaps")
@MapperScan("com.aiaps.mapper")
@EnableAsync
@EnableScheduling
public class AiapsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiapsApplication.class, args);
    }
}
