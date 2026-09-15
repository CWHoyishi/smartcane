package com.smartcane.backend;

import com.smartcane.backend.config.onenet.OneNetMqttProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.smartcane.backend.mapper")
@EnableConfigurationProperties(OneNetMqttProperties.class)
@EnableScheduling
public class SmartcaneApplication {

    private static final Logger log = LoggerFactory.getLogger(SmartcaneApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(SmartcaneApplication.class, args);
        log.info("================================================");
        log.info("  智能拐杖系统后端启动成功!");
        log.info("  Swagger文档: http://localhost:8080/doc.html");
        log.info("  OneNet API 定时同步已启动（间隔: 配置值）");
        log.info("================================================");
    }
}