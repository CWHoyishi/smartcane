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
 * 当前只对摔倒告警：心率/血氧容易受佩戴状态影响（取下拐杖就可能读到 0 或异常值），
 * 误报代价高，因此不参与判定，只在采样表中留档。以后要放开，在 evaluate() 里加分支即可。
 */
@Component
public class AlarmEvaluator {

    private static final Logger log = LoggerFactory.getLogger(AlarmEvaluator.class);

    /** 告警类型：摔倒 */
    public static final String TYPE_FALL = "FALL";

    /** 告警级别：3紧急 */
    public static final int LEVEL_URGENT = 3;

    /** 处理状态：0待处理 */
    public static final int STATUS_PENDING = 0;

    /** 处理状态：1已确认（真实告警，已介入处置） */
    public static final int STATUS_ACKED = 1;

    /** 处理状态：2误报 */
    public static final int STATUS_FALSE = 2;

    @Autowired
    private AlarmRecordMapper alarmRecordMapper;

    @Autowired
    private SmsNotifier smsNotifier;

    /**
     * 判定一条采样数据并写入命中的告警记录。
     * 只判摔倒：fall_status = 1 时产生一条紧急告警，心率/血氧不参与判定。
     */
    public void evaluate(CrutchSensorData data) {
        if (data == null || !StringUtils.hasText(data.getDeviceSn()) || data.getReportTime() == null) {
            return;
        }
        if (Integer.valueOf(1).equals(data.getFallStatus())) {
            save(data, TYPE_FALL, LEVEL_URGENT, "fallStatus=1");
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
            // 同一采样的同类告警已存在（双通道重复上报），忽略即可，也不再重复通知
            log.debug("[告警] 设备 {} 采样 {} 的 {} 告警已存在，忽略", data.getDeviceSn(), data.getReportTime(), alarmType);
            return;
        }
        notifySms(record);
    }

    /**
     * 短信通知失败不能影响已经落库的告警，因此单独兜住异常。
     */
    private void notifySms(AlarmRecord record) {
        try {
            smsNotifier.notify(record);
        } catch (Exception e) {
            log.error("[短信] 设备 {} 的 {} 告警通知异常", record.getDeviceSn(), record.getAlarmType(), e);
        }
    }
}