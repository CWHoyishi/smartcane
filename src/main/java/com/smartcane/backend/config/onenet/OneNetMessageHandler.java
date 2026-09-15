package com.smartcane.backend.config.onenet;

import com.smartcane.backend.service.OneNetDataProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Component;

@Component
public class OneNetMessageHandler implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(OneNetMessageHandler.class);

    @Autowired
    private OneNetDataProcessor dataProcessor;

    @Override
    public void handleMessage(Message<?> message) throws MessagingException {
        String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
        String payload = message.getPayload().toString();

        log.info("========== 收到 OneNet 消息 ==========");
        log.info("  Topic   : {}", topic);
        log.info("  Payload   : {}", payload);
        log.info("====================================");

        try {
            dataProcessor.process(topic, payload);
        } catch (Exception e) {
            log.error("处理 OneNet 消息失败", e);
        }
    }
}
