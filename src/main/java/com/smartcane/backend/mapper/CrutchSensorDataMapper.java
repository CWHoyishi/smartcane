package com.smartcane.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartcane.backend.entity.po.CrutchSensorData;
import com.smartcane.backend.entity.vo.LatestSensorDataVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CrutchSensorDataMapper extends BaseMapper<CrutchSensorData> {

    /**
     * 查询某设备在 [start, end) 内的采样，按上报时间升序（健康统计聚合用）。
     * 必须升序：在线时长依赖相邻采样点的先后顺序。
     */
    @Select("SELECT id, device_sn, heart_rate, blood_oxygen, lat, lon, fall_status, report_time, create_time " +
            "FROM t_crutch_sensor_data " +
            "WHERE device_sn = #{deviceSn} AND report_time >= #{start} AND report_time < #{end} " +
            "ORDER BY report_time")
    List<CrutchSensorData> selectByDeviceSnAndTimeRange(@Param("deviceSn") String deviceSn,
                                                        @Param("start") LocalDateTime start,
                                                        @Param("end") LocalDateTime end);

    @Select("SELECT device_sn, heart_rate, blood_oxygen, lat, lon, fall_status, report_time " +
            "FROM t_crutch_sensor_data " +
            "WHERE device_sn = #{deviceSn} " +
            "ORDER BY report_time DESC " +
            "LIMIT 1")
    LatestSensorDataVO selectLatestByDeviceSn(@Param("deviceSn") String deviceSn);

    /**
     * 查询某设备在 time 之前的最后一条采样（没有则返回 null）。
     * 小时统计用它把跨小时边界的在线时长接上，否则每小时都会丢掉边界那一段。
     */
    @Select("SELECT id, device_sn, heart_rate, blood_oxygen, lat, lon, fall_status, report_time, create_time " +
            "FROM t_crutch_sensor_data " +
            "WHERE device_sn = #{deviceSn} AND report_time < #{time} " +
            "ORDER BY report_time DESC LIMIT 1")
    CrutchSensorData selectLastBefore(@Param("deviceSn") String deviceSn, @Param("time") LocalDateTime time);
}
