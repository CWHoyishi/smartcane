package com.smartcane.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 上报消息队列（Redis List 实现的可靠队列，生产端）。
 *
 * 为什么不用 Redis Stream：实例是 2.8.19，不支持 Stream（XADD 报 unknown command），
 * 只能在 List 上自行补齐「不丢消息」的语义：投递 LPUSH 到主队列 →
 * 消费端 BRPOPLPUSH 原子搬到自己的处理中队列 → 处理完 LREM 确认。
 * 只有进程被杀才会把消息留在处理中队列，由消费者启动时搬回主队列重投；
 * 重投造成的重复由 t_crutch_sensor_data 的唯一键 (device_sn, report_time) 吸收。
 */
@Component
public class SensorMessageQueue {

    private static final Logger log = LoggerFactory.getLogger(SensorMessageQueue.class);

    /** 主队列：生产端 LPUSH 到头部、消费端从尾部取，保证先入先出 */
    private static final String QUEUE_KEY = "smartcane:sensor:queue";

    /** 处理中队列前缀：每个消费 worker 一个，确认与启动恢复时才知道消息归属谁 */
    private static final String PROCESSING_KEY_PREFIX = "smartcane:sensor:processing:";

    /** 报文分隔符：topic 不含换行，按第一个换行切分永远安全，比 JSON 少一层转义 */
    static final char SEPARATOR = '\n';

    private final StringRedisTemplate redis;

    public SensorMessageQueue(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 投递一条上报消息。
     *
     * @return true 已入队；false 投递失败（Redis 不可用），调用方应退化为同步处理，避免丢数据
     */
    public boolean enqueue(String topic, String payload) {
        try {
            redis.opsForList().leftPush(QUEUE_KEY, topic + SEPARATOR + payload);
            return true;
        } catch (Exception e) {
            log.error("[队列] 投递失败，改由调用方同步处理 - topic: {}, 原因: {}", topic, e.getMessage());
            return false;
        }
    }

    String queueKey() {
        return QUEUE_KEY;
    }

    String processingKey(int workerId) {
        return PROCESSING_KEY_PREFIX + workerId;
    }
}