package com.smartcane.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartcane.backend.entity.po.CrutchDevice;
import com.smartcane.backend.entity.vo.DeviceLastSeenVO;
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
}
