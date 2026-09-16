package com.smartcane.backend.service.impl;

import com.smartcane.backend.service.SmsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 短信通道的模拟实现：只打日志，不产生真实费用与外呼。
 * 内网演示与联调用它，接入真实厂商前不需要改动上层代码。
 */
@Component
public class MockSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(MockSmsSender.class);

    @Override
    public void send(String phone, String content) {
        log.info("[短信-模拟] 发送至 {} 内容: {}", phone, content);
    }
}