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
 * 告警判定也挂在这里：只有采样真正落库成功后才判定，同一条采样不会重复告警。
 */
@Component
public class SensorDataWriter {

    private static final Logger log = LoggerFactory.getLogger(SensorDataWriter.class);

    private final CrutchSensorDataMapper sensorDataMapper;

    private final AlarmEvaluator alarmEvaluator;

    public SensorDataWriter(CrutchSensorDataMapper sensorDataMapper, AlarmEvaluator alarmEvaluator) {
        this.sensorDataMapper = sensorDataMapper;
        this.alarmEvaluator = alarmEvaluator;
    }

    /**
     * 写入一条传感器数据，重复上报不视为错误。
     *
     * @return true 已写入；false 命中唯一键冲突（重复上报），已忽略
     */
    public boolean saveIgnoringDuplicate(CrutchSensorData data) {
        try {
            sensorDataMapper.insert(data);
        } catch (DuplicateKeyException e) {
            log.debug("[落库] 重复上报已忽略 - 设备: {}, 上报时间: {}", data.getDeviceSn(), data.getReportTime());
            return false;
        }
        evaluateAlarms(data);
        return true;
    }

    /**
     * 告警判定失败不能影响采集主链路（数据已经入库，重试由下一次采样覆盖），
     * 因此这里兜住异常并记录错误日志，而不是向上抛。
     */
    private void evaluateAlarms(CrutchSensorData data) {
        try {
            alarmEvaluator.evaluate(data);
        } catch (Exception e) {
            log.error("[告警] 判定失败 - 设备: {}, 上报时间: {}", data.getDeviceSn(), data.getReportTime(), e);
        }
    }
}