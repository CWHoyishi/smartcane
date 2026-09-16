package com.smartcane.backend.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 上报消息消费者：固定大小线程池从队列取消息，交给解析 / 落库 / 告警链路。
 *
 * 每个 worker 有独立的处理中队列（BRPOPLPUSH 的目的地），处理完再 LREM 确认；
 * 进程被杀时留在处理中队列的消息由启动恢复搬回主队列重投。
 */
@Component
public class SensorMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(SensorMessageConsumer.class);

    /** 消费线程数：落库是 IO 等待型，4 个足够，也不至于把数据库连接池占满 */
    private static final int WORKER_COUNT = 4;

    /**
     * 队列空时的轮询间隔。这里刻意不用阻塞式 BRPOPLPUSH：它在超时后返回的是 nil 多批量（*-1），
     * 而不是普通 nil，解析细节依赖客户端在旧版本 Redis 上的实现；轮询代价只有最多 100ms 的额外延迟，
     * 换来的是「空队列就是返回 null」这一条直白语义。
     */
    private static final long IDLE_POLL_MILLIS = 100L;

    /** 取消息失败后的退避：Redis 抖动时不刷屏、也不空转烧 CPU */
    private static final long ERROR_BACKOFF_MILLIS = 1000L;

    private final StringRedisTemplate redis;

    private final SensorMessageQueue queue;

    private final OneNetDataProcessor dataProcessor;

    private ExecutorService workers;

    public SensorMessageConsumer(StringRedisTemplate redis, SensorMessageQueue queue,
                                 OneNetDataProcessor dataProcessor) {
        this.redis = redis;
        this.queue = queue;
        this.dataProcessor = dataProcessor;
    }

    @PostConstruct
    public void start() {
        recoverInFlightMessages();

        AtomicInteger seq = new AtomicInteger();
        workers = Executors.newFixedThreadPool(WORKER_COUNT, runnable -> {
            Thread thread = new Thread(runnable, "sensor-consumer-" + seq.incrementAndGet());
            // 守护线程：不阻塞 JVM 退出；没确认完的消息留给下次启动恢复
            thread.setDaemon(true);
            return thread;
        });
        for (int workerId = 0; workerId < WORKER_COUNT; workerId++) {
            int id = workerId;
            workers.submit(() -> consumeLoop(id));
        }
        log.info("[队列] 消费者已启动，worker 数: {}", WORKER_COUNT);
    }

    @PreDestroy
    public void stop() {
        if (workers != null) {
            workers.shutdownNow();
            log.info("[队列] 消费者已停止");
        }
    }

    /**
     * 消费循环：阻塞取一条 → 处理 → 确认。
     *
     * 处理失败也确认（不重投）：同一条报文反复重投会变成毒消息死循环，
     * 而设备会持续上报新采样，丢一条的影响远小于卡住整个队列；原始报文已记在错误日志里。
     */
    private void consumeLoop(int workerId) {
        String processingKey = queue.processingKey(workerId);
        while (!Thread.currentThread().isInterrupted()) {
            String message;
            try {
                message = redis.opsForList().rightPopAndLeftPush(queue.queueKey(), processingKey);
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                log.error("[队列] worker {} 取消息失败，{}ms 后重试: {}", workerId, ERROR_BACKOFF_MILLIS, e.getMessage());
                sleepQuietly(ERROR_BACKOFF_MILLIS);
                continue;
            }
            if (message == null) {
                sleepQuietly(IDLE_POLL_MILLIS);
                continue;
            }
            try {
                handle(workerId, message);
            } catch (Throwable t) {
                // 单条消息失败（含 Error）不能让 worker 退出，否则队列会没人消费
                log.error("[队列] worker {} 处理消息失败: {}", workerId, message, t);
            } finally {
                ack(processingKey, message);
            }
        }
    }

    /** 切分出 topic 与 payload，复用 MQTT 通道原有的解析落库逻辑 */
    private void handle(int workerId, String message) {
        int split = message.indexOf(SensorMessageQueue.SEPARATOR);
        if (split < 0) {
            log.warn("[队列] worker {} 收到格式非法的报文，已丢弃: {}", workerId, message);
            return;
        }
        dataProcessor.process(message.substring(0, split), message.substring(split + 1));
    }

    /** 确认消费：从自己的处理中队列删掉这一条（LREM 只删一个匹配项） */
    private void ack(String processingKey, String message) {
        try {
            redis.opsForList().remove(processingKey, 1, message);
        } catch (Exception e) {
            log.error("[队列] 确认失败，该消息会在下次启动时被重投（唯一键会挡住重复入库）: {}", e.getMessage());
        }
    }

    /**
     * 启动恢复：把上次进程被杀时残留在各处理中队列的消息搬回主队列。
     * 重投顺序不保证，对本场景无影响：每条采样按 report_time 幂等落库，统计也按时间排序聚合。
     */
    private void recoverInFlightMessages() {
        for (int workerId = 0; workerId < WORKER_COUNT; workerId++) {
            String processingKey = queue.processingKey(workerId);
            try {
                int moved = 0;
                while (redis.opsForList().rightPopAndLeftPush(processingKey, queue.queueKey()) != null) {
                    moved++;
                }
                if (moved > 0) {
                    log.warn("[队列] 启动恢复：worker {} 残留的 {} 条消息已搬回主队列", workerId, moved);
                }
            } catch (Exception e) {
                log.error("[队列] 启动恢复失败（Redis 不可用？）worker {}: {}", workerId, e.getMessage());
            }
        }
    }

    /** 休眠期间被中断（关停）就立刻恢复中断标记，让消费循环退出 */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}