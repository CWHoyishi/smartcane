package com.smartcane.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI (springdoc) 配置，替代原 knife4j + springfox 方案。
 *
 * 迁移原因：springfox 依赖的路径匹配机制在 Spring Framework 6 中已变更，无法在 Spring Boot 3 下工作，
 * 故改用 springdoc-openapi。文档入口：/swagger-ui.html（UI）、/v3/api-docs（JSON）。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI smartcaneOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("智能拐杖系统API文档")
                        .description("基于STM32的智能拐杖系统后端接口文档")
                        .contact(new Contact().name("Smartcane"))
                        .version("1.0.0"));
    }
}
