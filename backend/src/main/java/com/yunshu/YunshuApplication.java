package com.yunshu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.yunshu.modules.**.mapper")
public class YunshuApplication {

    public static void main(String[] args) {
        SpringApplication.run(YunshuApplication.class, args);
    }
}
