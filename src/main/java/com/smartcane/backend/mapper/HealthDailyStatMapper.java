package com.smartcane.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartcane.backend.entity.po.HealthDailyStat;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HealthDailyStatMapper extends BaseMapper<HealthDailyStat> {

    /**
     * 按 (device_sn, stat_date) 唯一键写入或覆盖统计结果。
     *
     * 重算必须走 upsert：定时任务是按「最近若干天」反复重算的，直接 insert 会撞唯一键、
     * 或者被迫先查后写（多一次往返且有并发竞态）。
     * 使用 MySQL 8.0.19+ 的行别名语法（VALUES() 引用列已废弃）。
     */
    @Insert("INSERT INTO t_health_daily_stat (device_sn, stat_date, sample_count, " +
            "heart_rate_sum, heart_rate_count, blood_oxygen_sum, blood_oxygen_count, " +
            "fall_count, heart_rate_abnormal_count, blood_oxygen_abnormal_count, active_minutes) " +
            "VALUES (#{deviceSn}, #{statDate}, #{sampleCount}, " +
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
    int upsert(HealthDailyStat stat);
}