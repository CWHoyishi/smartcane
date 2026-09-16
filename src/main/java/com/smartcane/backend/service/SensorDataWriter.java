package com.smartcane.backend.service;

import com.smartcane.backend.entity.po.CrutchSensorData;
import com.smartcane.backend.mapper.CrutchSensorDataMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 传感器数据落库入口。
 *
 * MQTT 推送与 HTTP 拉取是两条独立通道，同一条采样可能被两边各写一次，
 * 因此表上存在唯一索引（device_sn, report_time）。这里把唯一键冲突当作
 * "重复上报" 静默忽略，避免两条通道各自处理异常、也避免把正常重复当故障告警。
 */
@Component
public class SensorDataWriter {

    private static final Logger log = LoggerFactory.getLogger(SensorDataWriter.class);

    private final CrutchSensorDataMapper sensorDataMapper;

    public SensorDataWriter(CrutchSensorDataMapper sensorDataMapper) {
        this.sensorDataMapper = sensorDataMapper;
    }

    /**
     * 写入一条传感器数据，重复上报不视为错误。
     *
     * @return true 已写入；false 命中唯一键冲突（重复上报），已忽略
     */
    public boolean saveIgnoringDuplicate(CrutchSensorData data) {
        try {
            sensorDataMapper.insert(data);
            return true;
        } catch (DuplicateKeyException e) {
            log.debug("[落库] 重复上报已忽略 - 设备: {}, 上报时间: {}", data.getDeviceSn(), data.getReportTime());
            return false;
        }
    }
}