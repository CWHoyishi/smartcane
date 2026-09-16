package com.smartcane.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartcane.backend.entity.po.HealthHourlyStat;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HealthHourlyStatMapper extends BaseMapper<HealthHourlyStat> {

    /**
     * 按 (device_sn, stat_hour) 唯一键写入或覆盖。
     *
     * 与日统计同理：定时任务按「最近若干小时」反复重算，必须 upsert 才能既幂等
     * 又不用先查后写。使用 MySQL 8.0.19+ 的行别名语法。
     */
    @Insert("INSERT INTO t_health_hourly_stat (device_sn, stat_hour, sample_count, " +
            "heart_rate_sum, heart_rate_count, blood_oxygen_sum, blood_oxygen_count, " +
            "fall_count, heart_rate_abnormal_count, blood_oxygen_abnormal_count, active_minutes) " +
            "VALUES (#{deviceSn}, #{statHour}, #{sampleCount}, " +
            "#{heartRateSum}, #{heartRateCount}, #{bloodOxygenSum}, #{bloodOxygenCount}, " +
            "#{fallCount}, #{heartRateAbnormalCount}, #{bloodOxygenAbnormalCount}, #{activeMinutes}) AS new " +
            "ON DUPLICATE KEY UPDATE " +
            "sample_count = new.sample_count, " +
            "heart_rate_sum = new.heart_rate_sum, " +
            "heart_rate_count = new.heart_rate_count, " +
            "blood_oxygen_sum = new.blood_oxygen_sum, " +
            "blood_oxygen_count = new.blood_oxygen_count, " +
            "fall_count = new.fall_count, " +
            "heart_rate_abnormal_count = new.heart_rate_abnormal_count, " +
            "blood_oxygen_abnormal_count = new.blood_oxygen_abnormal_count, " +
            "active_minutes = new.active_minutes, " +
            "update_time = CURRENT_TIMESTAMP")
    int upsert(HealthHourlyStat stat);
}