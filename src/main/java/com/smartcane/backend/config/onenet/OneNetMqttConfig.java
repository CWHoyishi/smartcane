package com.smartcane.backend.config.onenet;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class OneNetMqttConfig {

    private static final Logger log = LoggerFactory.getLogger(OneNetMqttConfig.class);

    @Autowired
    private OneNetMqttProperties properties;

    @Autowired
    private com.smartcane.backend.service.OneNetDataProcessor dataProcessor;

    private org.eclipse.paho.client.mqttv3.MqttClient mqttClient;

    @PostConstruct
    public void init() throws UnsupportedEncodingException {
        String clientId = "smartcane_backend_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        String token = OneNetTokenUtil.generateToken(
                properties.getMqtt().getProductId(),
                properties.getMqtt().getAccessKey()
        );

        log.info("====== OneNet MQTT 连接参数 ======");
        log.info("  Host       : {}", properties.getMqtt().getHost());
        log.info("  Username   : {}", properties.getMqtt().getProductId());
        log.info("  ClientId   : {}", clientId);
        log.info("================================");

        try {
            mqttClient = new org.eclipse.paho.client.mqttv3.MqttClient(
                    properties.getMqtt().getHost(),
                    clientId,
                    new MemoryPersistence()
            );

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log.error("OneNet MQTT 连接丢失: {}", cause.getMessage());
                    attemptReconnect();
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                    // 设备高频上报时逐条打印原始报文会拖慢 Paho 回调线程，故降为 debug 并截断
                    if (log.isDebugEnabled()) {
                        log.debug("[MQTT接收] Topic: {}, Payload: {}", topic,
                                payload.length() > 500 ? payload.substring(0, 500) + "...[截断]" : payload);
                    }

                    try {
                        dataProcessor.process(topic, payload);
                    } catch (Exception e) {
                        log.error("处理消息异常", e);
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });

            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(properties.getMqtt().getProductId());
            options.setPassword(token.toCharArray());
            options.setKeepAliveInterval(properties.getMqtt().getKeepAlive());
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(30);

            log.info("正在连接 OneNet MQTT Broker...");
            mqttClient.connect(options);
            log.info("MQTT 连接成功！");

            subscribeTopics();

        } catch (MqttException e) {
            log.error("OneNet MQTT 连接失败: reason={} msg={}", e.getReasonCode(), e.getMessage(), e);
        }
    }

    private void subscribeTopics() {
        try {
            String productId = properties.getMqtt().getProductId();
            int qos = properties.getMqtt().getQos();

            String[] topics = {
                    "$sys/" + productId + "/+/thing/event/property/post",
                    "$sys/" + productId + "/+/dp/post/json",
                    "$sys/" + productId + "/+/thing/event/+/post",
                    "$sys/" + productId + "/+/dp/+"
            };

            for (String topic : topics) {
                try {
                    mqttClient.subscribe(topic, qos);
                    log.info("订阅 Topic 成功: {}", topic);
                } catch (Exception e) {
                    log.warn("订阅 Topic 失败: {} - {}", topic, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("订阅 Topic 异常: {}", e.getMessage(), e);
        }
    }

    private void attemptReconnect() {
        int attempts = 0;
        while (attempts < 10) {
            try {
                Thread.sleep(3000);
                log.info("尝试重新连接 OneNet (第{}次)...", attempts + 1);
                mqttClient.reconnect();
                if (mqttClient.isConnected()) {
                    log.info("重新连接成功！");
                    subscribeTopics();
                    return;
                }
            } catch (Exception e) {
                attempts++;
                log.warn("重连失败: {}", e.getMessage());
            }
        }
        log.error("多次重连失败，放弃自动重连");
    }

    @PreDestroy
    public void destroy() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                log.info("已断开 OneNet MQTT 连接");
            }
        } catch (MqttException e) {
            log.warn("断开连接异常: {}", e.getMessage());
        }
    }
}
