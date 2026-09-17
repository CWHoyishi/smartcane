package com.smartcane.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartcane.backend.entity.po.CrutchDevice;
import com.smartcane.backend.entity.vo.DeviceLastSeenVO;
import com.smartcane.backend.entity.vo.LatestDeviceLocationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CrutchDeviceMapper extends BaseMapper<CrutchDevice> {

    /**
     * 每台设备的最后上报时间。设备表没有 last_seen 字段，设备离线也不会有人通知后端，
     * 因此在线状态只能由采样表反推；一次查全量设备（拐杖设备数量级很小），避免 N+1。
     */
    @Select("SELECT device_sn AS deviceSn, MAX(report_time) AS lastReportTime " +
            "FROM t_crutch_sensor_data GROUP BY device_sn")
    List<DeviceLastSeenVO> selectLastSeen();

    /**
     * 每台设备的最新一条采样（含坐标与生命体征），地图轮询用。
     * 设备没有采样记录时也返回一行，坐标/时间为 null，前端自行过滤。
     * 用 NOT EXISTS 取每台设备 report_time 最新的行，兼容 MySQL 5.7/8.0，
     * 避免 ROW_NUMBER() 对低版本不兼容。
     */
    @Select("SELECT d.device_sn, d.elder_name, s.heart_rate, s.blood_oxygen, " +
            "s.lat, s.lon, s.fall_status, s.report_time " +
            "FROM t_crutch_device d " +
            "LEFT JOIN t_crutch_sensor_data s ON s.device_sn = d.device_sn " +
            "AND NOT EXISTS (" +
            "  SELECT 1 FROM t_crutch_sensor_data s2 " +
            "  WHERE s2.device_sn = s.device_sn " +
            "    AND (s2.report_time > s.report_time OR (s2.report_time = s.report_time AND s2.id > s.id))" +
            ") " +
            "ORDER BY d.create_time DESC")
    List<LatestDeviceLocationVO> selectLatestLocations();
}
