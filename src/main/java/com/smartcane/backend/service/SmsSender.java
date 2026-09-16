package com.smartcane.backend.service;

/**
 * 短信发送通道。
 *
 * 当前只有 MockSmsSender（打日志）。接正式厂商时新增一个实现类即可，
 * 但两个实现类会同时被扫描到，记得用 @ConditionalOnProperty 二选一。
 */
public interface SmsSender {

    /**
     * 发送一条短信，失败时抛异常（由调用方决定是否重试）。
     *
     * @param phone   接收手机号
     * @param content 短信内容
     */
    void send(String phone, String content);
}