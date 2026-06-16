package com.neusoft.grading;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.neusoft.grading.mapper")
public class GradingApplication {

    public static void main(String[] args) {
        SpringApplication.run(GradingApplication.class, args);
    }
}
