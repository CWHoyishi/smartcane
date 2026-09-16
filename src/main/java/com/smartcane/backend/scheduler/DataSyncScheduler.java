package com.smartcane.backend.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartcane.backend.config.onenet.OneNetMqttProperties;
import com.smartcane.backend.entity.po.CrutchSensorData;
import com.smartcane.backend.mapper.CrutchSensorDataMapper;
import com.smartcane.backend.service.OneNetApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OneNet 数据定时同步调度器
 * 作为 MQTT 推送的兜底，定期通过 HTTP API 拉取设备最新数据
 */
@Component
public class DataSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataSyncScheduler.class);

    @Autowired
    private OneNetApiService apiService;

    @Autowired
    private OneNetMqttProperties properties;

    @Autowired
    private CrutchSensorDataMapper sensorDataMapper;

    /**
     * 定时拉取所有配置设备的最新数据
     * 使用 fixedDelayString 从配置文件读取间隔（毫秒），默认 60000 毫秒(60 秒)
     */
    @Scheduled(fixedDelayString = "${onenet.api.fetchIntervalMs:60000}")
    public void syncDeviceData() {
        List<String> deviceNames = properties.getApi().getDeviceNames();
        if (deviceNames == null || deviceNames.isEmpty()) {
            log.debug("[定时同步] 未配置设备列表，跳过");
            return;
        }

        log.debug("[定时同步] 开始拉取 {} 个设备的数据", deviceNames.size());
        int successCount = 0;
        int failCount = 0;

        for (String deviceName : deviceNames) {
            if (deviceName == null || deviceName.trim().isEmpty()) {
                continue;
            }
            try {
                boolean ok = apiService.fetchDeviceProperties(deviceName.trim());
                if (ok) {
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                failCount++;
                log.error("[定时同步] 设备 {} 拉取异常: {}", deviceName, e.getMessage());
            }
        }

        log.debug("[定时同步] 完成 - 成功: {}, 失败: {}", successCount, failCount);
    }

    /**
     * 每天凌晨3点清理前天的传感器历史数据（保留最近48小时）
     * 避免数据量过大导致数据库压力
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanExpiredSensorData() {
        try {
            // 删除 2 天前的数据（前天及更早）
            LocalDateTime cutoff = LocalDateTime.now().minusDays(2);
            QueryWrapper<CrutchSensorData> wrapper = new QueryWrapper<>();
            wrapper.lt("create_time", cutoff);

            int deleted = sensorDataMapper.delete(wrapper);
            if (deleted > 0) {
                log.info("[数据清理] 已删除 {} 条前天之前的传感器数据（截止时间: {}）", deleted, cutoff);
            } else {
                log.debug("[数据清理] 无需清理，无过期数据");
            }
        } catch (Exception e) {
            log.error("[数据清理] 清理失败: {}", e.getMessage(), e);
        }
    }
}