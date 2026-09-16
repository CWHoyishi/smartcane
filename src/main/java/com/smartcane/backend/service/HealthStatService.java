package com.smartcane.backend.service;

import com.smartcane.backend.entity.vo.DailyHealthStatVO;
import com.smartcane.backend.entity.vo.Result;
import com.smartcane.backend.entity.vo.WeeklyHealthStatVO;

import java.util.List;

public interface HealthStatService {

    /**
     * 日统计（趋势图用），缺数据的日期会补空行，保证横轴连续。
     *
     * @param days 统计天数，含今天，默认 7，上限 90
     */
    Result<List<DailyHealthStatVO>> listDaily(String deviceSn, Integer days);

    /**
     * 周统计（周报），按自然周（周一到周日）聚合日统计。
     *
     * @param weeks 周数，含本周，默认 4，上限 12
     */
    Result<List<WeeklyHealthStatVO>> listWeekly(String deviceSn, Integer weeks);

    /**
     * 重算最近 days 天（含今天）的统计，返回写入行数：
     * 先把原始明细刷成小时统计，再由小时统计求和出日统计（顺序不可颠倒）。
     * 幂等（按唯一键 upsert），可用于回填历史或演示时立即出数。
     */
    Result<Integer> rebuild(Integer days);
}