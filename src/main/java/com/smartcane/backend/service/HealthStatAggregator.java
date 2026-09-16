package com.smartcane.backend.service;

import com.smartcane.backend.entity.po.CrutchSensorData;
import com.smartcane.backend.entity.po.HealthDailyStat;
import com.smartcane.backend.entity.po.HealthHourlyStat;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 健康统计聚合：采样明细 → 小时统计 → 日统计。纯计算、不碰数据库，统计口径集中在这里。
 *
 * 分层的关系是「明细最细、只留 7 天；小时表长期保留；日统计由小时表求和」。
 * 日统计之所以不再直接扫明细，是因为明细会被清理，超过 7 天的报表只能靠小时表撑住。
 *
 * 注意：这里的阈值只用于「统计异常次数」，与告警无关 —— 告警目前只判摔倒，
 * 心率/血氧越界不会报警，但会在这里计数，用于日报/周报。
 */
@Component
public class HealthStatAggregator {

    /** 统计口径：心率低于此值或高于上限，计一次心率异常 */
    private static final int HEART_RATE_MIN = 50;

    /** 统计口径：心率高于此值计一次心率异常 */
    private static final int HEART_RATE_MAX = 120;

    /** 统计口径：血氧低于此值计一次血氧异常 */
    private static final int BLOOD_OXYGEN_MIN = 90;

    /** 判定「这段时间在活动」的最大采样间隔（秒）：中间断了就说明时间不可信，不计入 */
    private static final long ACTIVE_GAP_SECONDS = 300;

    /** 判定「这段时间在活动」的最小位移（米）：低于此值视为原地不动或 GPS 漂移 */
    private static final double ACTIVE_DISTANCE_METERS = 10.0;

    private static final double EARTH_RADIUS_METERS = 6371000d;

    /**
     * 汇总一个小时。
     *
     * @param samples 该小时内的采样，必须按 report_time 升序（活动时长依赖相邻点的先后顺序）
     * @param carryIn 上一个小时的最后一条采样，可为 null。只用它的时间和位置来补上跨小时边界的
     *                那一段活动时长，它的心率/血氧等指标不会重复计入本小时
     */
    public HealthHourlyStat aggregateHour(String deviceSn, LocalDateTime statHour,
                                          List<CrutchSensorData> samples, CrutchSensorData carryIn) {
        int heartRateSum = 0;
        int heartRateCount = 0;
        int bloodOxygenSum = 0;
        int bloodOxygenCount = 0;
        int fallCount = 0;
        int heartRateAbnormalCount = 0;
        int bloodOxygenAbnormalCount = 0;
        long activeSeconds = 0;
        CrutchSensorData previous = carryIn;

        for (CrutchSensorData sample : samples) {
            // 心率/血氧为 0 视为未佩戴或无效读数：既不计入均值，也不计为异常
            Integer heartRate = sample.getHeartRate();
            if (heartRate != null && heartRate > 0) {
                heartRateSum += heartRate;
                heartRateCount++;
                if (heartRate < HEART_RATE_MIN || heartRate > HEART_RATE_MAX) {
                    heartRateAbnormalCount++;
                }
            }
            Integer bloodOxygen = sample.getBloodOxygen();
            if (bloodOxygen != null && bloodOxygen > 0) {
                bloodOxygenSum += bloodOxygen;
                bloodOxygenCount++;
                if (bloodOxygen < BLOOD_OXYGEN_MIN) {
                    bloodOxygenAbnormalCount++;
                }
            }
            if (Integer.valueOf(1).equals(sample.getFallStatus())) {
                fallCount++;
            }
            activeSeconds += activeSecondsBetween(previous, sample);
            previous = sample;
        }

        HealthHourlyStat stat = new HealthHourlyStat();
        stat.setDeviceSn(deviceSn);
        stat.setStatHour(statHour);
        stat.setSampleCount(samples.size());
        stat.setHeartRateSum(heartRateSum);
        stat.setHeartRateCount(heartRateCount);
        stat.setBloodOxygenSum(bloodOxygenSum);
        stat.setBloodOxygenCount(bloodOxygenCount);
        stat.setFallCount(fallCount);
        stat.setHeartRateAbnormalCount(heartRateAbnormalCount);
        stat.setBloodOxygenAbnormalCount(bloodOxygenAbnormalCount);
        stat.setActiveMinutes((int) (activeSeconds / 60));
        return stat;
    }

    /**
     * 日统计 = 当天各小时统计之和。
     * 均值不在这里算：sum 与 count 直接相加，输出时再相除，这样按样本数加权才不会算错。
     */
    public HealthDailyStat sumDaily(String deviceSn, LocalDate statDate, List<HealthHourlyStat> hours) {
        int sampleCount = 0;
        int heartRateSum = 0;
        int heartRateCount = 0;
        int bloodOxygenSum = 0;
        int bloodOxygenCount = 0;
        int fallCount = 0;
        int heartRateAbnormalCount = 0;
        int bloodOxygenAbnormalCount = 0;
        int activeMinutes = 0;

        for (HealthHourlyStat hour : hours) {
            sampleCount += hour.getSampleCount();
            heartRateSum += hour.getHeartRateSum();
            heartRateCount += hour.getHeartRateCount();
            bloodOxygenSum += hour.getBloodOxygenSum();
            bloodOxygenCount += hour.getBloodOxygenCount();
            fallCount += hour.getFallCount();
            heartRateAbnormalCount += hour.getHeartRateAbnormalCount();
            bloodOxygenAbnormalCount += hour.getBloodOxygenAbnormalCount();
            activeMinutes += hour.getActiveMinutes();
        }

        HealthDailyStat stat = new HealthDailyStat();
        stat.setDeviceSn(deviceSn);
        stat.setStatDate(statDate);
        stat.setSampleCount(sampleCount);
        stat.setHeartRateSum(heartRateSum);
        stat.setHeartRateCount(heartRateCount);
        stat.setBloodOxygenSum(bloodOxygenSum);
        stat.setBloodOxygenCount(bloodOxygenCount);
        stat.setFallCount(fallCount);
        stat.setHeartRateAbnormalCount(heartRateAbnormalCount);
        stat.setBloodOxygenAbnormalCount(bloodOxygenAbnormalCount);
        stat.setActiveMinutes(activeMinutes);
        return stat;
    }

    /**
     * 相邻两次采样之间是否算「在活动」：间隔在可信范围内、且有实际位移，则整段间隔计入活动时长。
     * 只看位移不看心率：老人静坐时心率也可能偏低，用位移判定更贴近「走动」这个语义。
     */
    private long activeSecondsBetween(CrutchSensorData previous, CrutchSensorData current) {
        if (previous == null || previous.getReportTime() == null || current.getReportTime() == null) {
            return 0;
        }
        if (previous.getLat() == null || previous.getLon() == null
                || current.getLat() == null || current.getLon() == null) {
            return 0;
        }
        long gapSeconds = Duration.between(previous.getReportTime(), current.getReportTime()).getSeconds();
        if (gapSeconds <= 0 || gapSeconds > ACTIVE_GAP_SECONDS) {
            return 0;
        }
        double distance = distanceMeters(previous.getLat(), previous.getLon(), current.getLat(), current.getLon());
        return distance >= ACTIVE_DISTANCE_METERS ? gapSeconds : 0;
    }

    /** Haversine 球面距离，单位米 */
    private double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1d, Math.sqrt(a)));
    }
}