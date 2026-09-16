package com.smartcane.backend.scheduler;

import com.smartcane.backend.service.HealthStatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 健康统计预聚合调度器。
 */
@Component
public class HealthStatScheduler {

    private static final Logger log = LoggerFactory.getLogger(HealthStatScheduler.class);

    /** 每次重算覆盖的天数：今天的数据还在增长，昨天可能被设备补传 */
    private static final int REBUILD_DAYS = 2;

    @Autowired
    private HealthStatService healthStatService;

    /**
     * 每 10 分钟重算「今天 + 昨天」的日统计。
     *
     * 用 fixedDelay 而非每天凌晨跑一次：日统计要能当「今日实时汇总」用，
     * 只在凌晨跑就意味着前端一整天都看不到当天数据。重算幂等，多跑几次没有副作用。
     * 首次执行在应用启动后立即开始，所以刚启动就能查到数据。
     */
    @Scheduled(fixedDelay = 10 * 60 * 1000L)
    public void rebuildRecentStats() {
        try {
            healthStatService.rebuild(REBUILD_DAYS);
        } catch (Exception e) {
            log.error("[健康统计] 定时重算失败: {}", e.getMessage(), e);
        }
    }
}