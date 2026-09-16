package com.smartcane.backend.service;

import com.smartcane.backend.entity.po.AlarmRecord;
import com.smartcane.backend.entity.po.CrutchSensorData;
import com.smartcane.backend.mapper.AlarmRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 告警判定：把一条采样数据折算成 0~N 条告警记录。
 *
 * 由 SensorDataWriter 在采样落库成功之后调用，因此同一条采样只判定一次；
 * t_alarm_record 上的唯一键 (device_sn, alarm_type, report_time) 是第二道保险。
 * 阈值以常量固化，现场要调只改这里，不额外引入配置项。
 */
@Component
public class AlarmEvaluator {

    private static final Logger log = LoggerFactory.getLogger(AlarmEvaluator.class);

    /** 告警类型：摔倒 */
    public static final String TYPE_FALL = "FALL";

    /** 告警类型：心率异常 */
    public static final String TYPE_HEART_RATE = "HEART_RATE";

    /** 告警类型：血氧异常 */
    public static final String TYPE_BLOOD_OXYGEN = "BLOOD_OXYGEN";

    /** 告警级别：2重要 */
    public static final int LEVEL_IMPORTANT = 2;

    /** 告警级别：3紧急 */
    public static final int LEVEL_URGENT = 3;

    /** 处理状态：0待处理 */
    public static final int STATUS_PENDING = 0;

    /** 处理状态：1已确认（真实告警，已介入处置） */
    public static final int STATUS_ACKED = 1;

    /** 处理状态：2误报 */
    public static final int STATUS_FALSE = 2;

    /** 心率下限（次/分钟），低于此值告警 */
    private static final int HEART_RATE_MIN = 50;

    /** 心率上限（次/分钟），高于此值告警 */
    private static final int HEART_RATE_MAX = 120;

    /** 血氧下限（%），低于此值告警 */
    private static final int BLOOD_OXYGEN_MIN = 90;

    @Autowired
    private AlarmRecordMapper alarmRecordMapper;

    /**
     * 判定一条采样数据并写入命中的告警记录。
     * 心率为 0、血氧为 0 视为「未佩戴/无效读数」，不判定为异常，避免夜间误告警。
     */
    public void evaluate(CrutchSensorData data) {
        if (data == null || !StringUtils.hasText(data.getDeviceSn()) || data.getReportTime() == null) {
            return;
        }

        if (Integer.valueOf(1).equals(data.getFallStatus())) {
            save(data, TYPE_FALL, LEVEL_URGENT, "fallStatus=1");
        }

        Integer heartRate = data.getHeartRate();
        if (heartRate != null && heartRate > 0 && (heartRate < HEART_RATE_MIN || heartRate > HEART_RATE_MAX)) {
            save(data, TYPE_HEART_RATE, LEVEL_IMPORTANT, "heartRate=" + heartRate);
        }

        Integer bloodOxygen = data.getBloodOxygen();
        if (bloodOxygen != null && bloodOxygen > 0 && bloodOxygen < BLOOD_OXYGEN_MIN) {
            save(data, TYPE_BLOOD_OXYGEN, LEVEL_IMPORTANT, "bloodOxygen=" + bloodOxygen);
        }
    }

    private void save(CrutchSensorData data, String alarmType, int level, String alarmValue) {
        AlarmRecord record = new AlarmRecord();
        record.setDeviceSn(data.getDeviceSn());
        record.setAlarmType(alarmType);
        record.setLevel(level);
        record.setAlarmValue(alarmValue);
        record.setReportTime(data.getReportTime());
        record.setStatus(STATUS_PENDING);
        try {
            alarmRecordMapper.insert(record);
            log.info("[告警] 设备 {} 触发 {} 告警 - 采样时间: {}, 判定值: {}",
                    data.getDeviceSn(), alarmType, data.getReportTime(), alarmValue);
        } catch (DuplicateKeyException e) {
            // 同一采样的同类告警已存在（双通道重复上报），忽略即可
            log.debug("[告警] 设备 {} 采样 {} 的 {} 告警已存在，忽略", data.getDeviceSn(), data.getReportTime(), alarmType);
        }
    }
}