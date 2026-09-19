package com.smartcane.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartcane.backend.entity.po.CrutchDevice;
import com.smartcane.backend.entity.po.CrutchSensorData;
import com.smartcane.backend.entity.po.HealthDailyStat;
import com.smartcane.backend.entity.po.HealthHourlyStat;
import com.smartcane.backend.entity.vo.DailyHealthStatVO;
import com.smartcane.backend.entity.vo.Result;
import com.smartcane.backend.entity.vo.WeeklyHealthStatVO;
import com.smartcane.backend.mapper.CrutchDeviceMapper;
import com.smartcane.backend.mapper.CrutchSensorDataMapper;
import com.smartcane.backend.mapper.HealthDailyStatMapper;
import com.smartcane.backend.mapper.HealthHourlyStatMapper;
import com.smartcane.backend.service.HealthStatAggregator;
import com.smartcane.backend.service.HealthStatService;
import com.smartcane.backend.service.auth.DataScopeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HealthStatServiceImpl implements HealthStatService {

    private static final Logger log = LoggerFactory.getLogger(HealthStatServiceImpl.class);

    private static final int DEFAULT_DAILY_DAYS = 7;

    /** 日统计查询上限：趋势图不需要更长的窗口，也避免一次拉出过多行 */
    private static final int MAX_DAILY_DAYS = 90;

    /** 重算上限：一天 24 个小时窗口，天数再大就会拖很久 */
    private static final int MAX_REBUILD_DAYS = 30;

    private static final int DEFAULT_WEEKLY_WEEKS = 4;

    private static final int MAX_WEEKLY_WEEKS = 12;

    @Autowired
    private HealthDailyStatMapper healthDailyStatMapper;

    @Autowired
    private HealthHourlyStatMapper healthHourlyStatMapper;

    @Autowired
    private HealthStatAggregator healthStatAggregator;

    @Autowired
    private CrutchDeviceMapper crutchDeviceMapper;

    @Autowired
    private CrutchSensorDataMapper sensorDataMapper;

    @Autowired
    private DataScopeService dataScopeService;

    @Override
    public Result<List<DailyHealthStatVO>> listDaily(String deviceSn, Integer days) {
        if (!StringUtils.hasText(deviceSn)) {
            return Result.error("设备序列号不能为空");
        }
        dataScopeService.assertDeviceAccess(deviceSn);
        int span = clamp(days, DEFAULT_DAILY_DAYS, MAX_DAILY_DAYS);
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(span - 1L);

        Map<LocalDate, HealthDailyStat> statMap = new HashMap<>();
        for (HealthDailyStat stat : selectDailyStats(deviceSn, start, end)) {
            statMap.put(stat.getStatDate(), stat);
        }

        // 没有统计行的日期补空行：趋势图横轴必须连续，缺一天就会错位
        List<DailyHealthStatVO> result = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            result.add(toDailyVO(date, statMap.get(date)));
        }
        return Result.success(result);
    }

    @Override
    public Result<List<WeeklyHealthStatVO>> listWeekly(String deviceSn, Integer weeks) {
        if (!StringUtils.hasText(deviceSn)) {
            return Result.error("设备序列号不能为空");
        }
        dataScopeService.assertDeviceAccess(deviceSn);
        int span = clamp(weeks, DEFAULT_WEEKLY_WEEKS, MAX_WEEKLY_WEEKS);
        LocalDate thisWeekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate firstWeekStart = thisWeekStart.minusWeeks(span - 1L);

        // 先按自然周把日统计分桶：周均值要按样本数加权，必须拿到每天的 sum/count 之后再算
        Map<LocalDate, List<HealthDailyStat>> buckets = new HashMap<>();
        for (HealthDailyStat stat : selectDailyStats(deviceSn, firstWeekStart, thisWeekStart.plusDays(6))) {
            LocalDate weekStart = stat.getStatDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            buckets.computeIfAbsent(weekStart, key -> new ArrayList<>()).add(stat);
        }

        // 没有数据的周同样补空行，保证周报按周递增排列
        List<WeeklyHealthStatVO> result = new ArrayList<>();
        for (int i = 0; i < span; i++) {
            LocalDate weekStart = firstWeekStart.plusWeeks(i);
            result.add(toWeeklyVO(weekStart, buckets.get(weekStart)));
        }
        return Result.success(result);
    }

    @Override
    public Result<Integer> rebuild(Integer days) {
        int span = clamp(days, DEFAULT_DAILY_DAYS, MAX_REBUILD_DAYS);
        // 顺序不能反：日统计是从小时表求和来的，必须先把小时表刷成最新的
        int hourlyRows = rebuildHourly(span * 24);
        int dailyRows = rebuildDaily(span);
        log.info("[健康统计] 重算完成 - 覆盖 {} 天，小时表 {} 行，日表 {} 行", span, hourlyRows, dailyRows);
        return Result.success(hourlyRows + dailyRows);
    }

    /**
     * 明细 → 小时表。覆盖 [当前整点 - hours + 1, 当前整点]，含正在进行的这一小时。
     */
    private int rebuildHourly(int hours) {
        LocalDateTime currentHour = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        LocalDateTime from = currentHour.minusHours(hours - 1L);
        int rows = 0;

        for (CrutchDevice device : listDevices()) {
            String deviceSn = device.getDeviceSn();
            if (!StringUtils.hasText(deviceSn)) {
                continue;
            }
            for (LocalDateTime hour = from; !hour.isAfter(currentHour); hour = hour.plusHours(1)) {
                List<CrutchSensorData> samples =
                        sensorDataMapper.selectByDeviceSnAndTimeRange(deviceSn, hour, hour.plusHours(1));
                if (samples.isEmpty()) {
                    // 该小时没有上报就不落行：接口读取时会补空，避免把「没有数据」记成「0 活动」
                    continue;
                }
                CrutchSensorData carryIn = sensorDataMapper.selectLastBefore(deviceSn, hour);
                healthHourlyStatMapper.upsert(healthStatAggregator.aggregateHour(deviceSn, hour, samples, carryIn));
                rows++;
            }
        }
        return rows;
    }

    /**
     * 小时表 → 日表。
     * 读的是小时表而不是原始明细：明细只留 7 天，靠明细回算的话超过 7 天的日报就补不回来了。
     */
    private int rebuildDaily(int days) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(days - 1L);
        int rows = 0;

        for (CrutchDevice device : listDevices()) {
            String deviceSn = device.getDeviceSn();
            if (!StringUtils.hasText(deviceSn)) {
                continue;
            }
            for (LocalDate date = from; !date.isAfter(today); date = date.plusDays(1)) {
                LocalDateTime start = date.atStartOfDay();
                List<HealthHourlyStat> hours = selectHourlyStats(deviceSn, start, start.plusDays(1));
                if (hours.isEmpty()) {
                    continue;
                }
                healthDailyStatMapper.upsert(healthStatAggregator.sumDaily(deviceSn, date, hours));
                rows++;
            }
        }
        return rows;
    }

    private List<CrutchDevice> listDevices() {
        return crutchDeviceMapper.selectList(new QueryWrapper<>());
    }

    private List<HealthDailyStat> selectDailyStats(String deviceSn, LocalDate start, LocalDate end) {
        QueryWrapper<HealthDailyStat> wrapper = new QueryWrapper<>();
        wrapper.eq("device_sn", deviceSn);
        wrapper.ge("stat_date", start);
        wrapper.le("stat_date", end);
        wrapper.orderByAsc("stat_date");
        return healthDailyStatMapper.selectList(wrapper);
    }

    private List<HealthHourlyStat> selectHourlyStats(String deviceSn, LocalDateTime start, LocalDateTime end) {
        QueryWrapper<HealthHourlyStat> wrapper = new QueryWrapper<>();
        wrapper.eq("device_sn", deviceSn);
        wrapper.ge("stat_hour", start);
        wrapper.lt("stat_hour", end);
        wrapper.orderByAsc("stat_hour");
        return healthHourlyStatMapper.selectList(wrapper);
    }

    private DailyHealthStatVO toDailyVO(LocalDate date, HealthDailyStat stat) {
        DailyHealthStatVO vo = new DailyHealthStatVO();
        vo.setStatDate(date);
        if (stat == null) {
            vo.setSampleCount(0);
            vo.setFallCount(0);
            vo.setHeartRateAbnormalCount(0);
            vo.setBloodOxygenAbnormalCount(0);
            vo.setActiveMinutes(0);
            return vo;
        }
        vo.setSampleCount(stat.getSampleCount());
        vo.setAvgHeartRate(average(stat.getHeartRateSum(), stat.getHeartRateCount()));
        vo.setAvgBloodOxygen(average(stat.getBloodOxygenSum(), stat.getBloodOxygenCount()));
        vo.setFallCount(stat.getFallCount());
        vo.setHeartRateAbnormalCount(stat.getHeartRateAbnormalCount());
        vo.setBloodOxygenAbnormalCount(stat.getBloodOxygenAbnormalCount());
        vo.setActiveMinutes(stat.getActiveMinutes());
        return vo;
    }

    private WeeklyHealthStatVO toWeeklyVO(LocalDate weekStart, List<HealthDailyStat> stats) {
        int statDays = 0;
        int sampleCount = 0;
        int heartRateSum = 0;
        int heartRateCount = 0;
        int bloodOxygenSum = 0;
        int bloodOxygenCount = 0;
        int fallCount = 0;
        int heartRateAbnormalCount = 0;
        int bloodOxygenAbnormalCount = 0;
        int activeMinutes = 0;

        if (stats != null) {
            for (HealthDailyStat stat : stats) {
                statDays++;
                sampleCount += stat.getSampleCount();
                heartRateSum += stat.getHeartRateSum();
                heartRateCount += stat.getHeartRateCount();
                bloodOxygenSum += stat.getBloodOxygenSum();
                bloodOxygenCount += stat.getBloodOxygenCount();
                fallCount += stat.getFallCount();
                heartRateAbnormalCount += stat.getHeartRateAbnormalCount();
                bloodOxygenAbnormalCount += stat.getBloodOxygenAbnormalCount();
                activeMinutes += stat.getActiveMinutes();
            }
        }

        WeeklyHealthStatVO vo = new WeeklyHealthStatVO();
        vo.setWeekStart(weekStart);
        vo.setWeekEnd(weekStart.plusDays(6));
        vo.setStatDays(statDays);
        vo.setSampleCount(sampleCount);
        vo.setAvgHeartRate(average(heartRateSum, heartRateCount));
        vo.setAvgBloodOxygen(average(bloodOxygenSum, bloodOxygenCount));
        vo.setFallCount(fallCount);
        vo.setHeartRateAbnormalCount(heartRateAbnormalCount);
        vo.setBloodOxygenAbnormalCount(bloodOxygenAbnormalCount);
        vo.setActiveMinutes(activeMinutes);
        return vo;
    }

    /** 均值保留 1 位小数；没有有效样本时返回 null，前端展示成「--」而不是误导性的 0 */
    private static Double average(int sum, int count) {
        if (count <= 0) {
            return null;
        }
        return Math.round(sum * 10.0 / count) / 10.0;
    }

    private static int clamp(Integer value, int defaultValue, int max) {
        if (value == null || value < 1) {
            return defaultValue;
        }
        return Math.min(value, max);
    }
}