package com.smartcane.backend.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * HTTP 客户端配置。
 *
 * OneNet 数据拉取原先直接 new RestTemplate()，既无超时也无连接复用：
 * 平台不响应时调度线程会被一直挂住。这里统一设置 3s 连接超时 / 10s 读超时，
 * 并复用 Boot 探测出的底层客户端（JDK HttpClient，自带连接池）。
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(3))
                .withReadTimeout(Duration.ofSeconds(10));
        return new RestTemplate(ClientHttpRequestFactoryBuilder.detect().build(settings));
    }
}