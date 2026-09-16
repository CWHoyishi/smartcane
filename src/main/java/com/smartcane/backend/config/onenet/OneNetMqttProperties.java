package com.smartcane.backend.config.onenet;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "onenet")
public class OneNetMqttProperties {

    /**
     * MQTT 连接配置
     */
    private Mqtt mqtt = new Mqtt();

    /**
     * HTTP API 配置（多端点自动探测）
     */
    private Api api = new Api();

    @Data
    public static class Mqtt {
        private String host = "tcp://896VnUK204.mqtts.acc.cmcconenet.cn:6002";
        private String productId;
        private String accessKey;
        private String subscribeTopic;
        private int qos = 1;
        private int keepAlive = 60;
    }

    @Data
    public static class Api {
        /**
         * 候选 API 端点列表（按顺序尝试，第一个成功即停止）
         * 每个端点包含：baseUrl、path、method
         */
        private List<ApiEndpoint> endpoints = new ArrayList<>();

        /** 产品ID */
        private String productId;

        /** 访问密钥（Base64编码） */
        private String accessKey;

        /** 要拉取数据的设备名列表 */
        private List<String> deviceNames = new ArrayList<>();

        /** 定时拉取间隔（毫秒），默认60000毫秒(60秒) */
        private int fetchIntervalMs = 60000;

        /**
         * 兼容旧配置：如果没有配置 endpoints 列表，则从 baseUrl 生成一个默认端点
         */
        public List<ApiEndpoint> getEffectiveEndpoints() {
            if (endpoints != null && !endpoints.isEmpty()) {
                return endpoints;
            }
            // 降级：使用旧的 baseUrl 配置生成默认端点
            List<ApiEndpoint> defaults = new ArrayList<>();
            ApiEndpoint ep = new ApiEndpoint();
            ep.setBaseUrl("https://dmp.api.cmaiot.cn");
            ep.setPath("/dmp/v1/thingmodel/query-device-property");
            ep.setMethod("POST");
            defaults.add(ep);
            return defaults;
        }
    }

    @Data
    public static class ApiEndpoint {
        /** API 基础地址 */
        private String baseUrl;

        /** 接口路径（支持 {productId} 和 {deviceName} 占位符） */
        private String path;

        /** HTTP 方法：GET / POST */
        private String method = "GET";

        /** 构建完整 URL */
        public String buildUrl(String productId, String deviceName) {
            String resolvedPath = path
                    .replace("{productId}", productId)
                    .replace("{deviceName}", deviceName);
            // 确保路径以 / 开头且 baseUrl 不以 / 结尾
            String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            String pathPart = resolvedPath.startsWith("/") ? resolvedPath : "/" + resolvedPath;
            return base + pathPart;
        }
    }
}