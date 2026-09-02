package com.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Node-RED 中心管理平台启动类
 */
@EnableScheduling
@MapperScan("com.platform.mapper")
@SpringBootApplication
public class NodeServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(NodeServerApplication.class, args);
    }
}