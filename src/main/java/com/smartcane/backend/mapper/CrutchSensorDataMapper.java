package com.smartcane.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartcane.backend.entity.po.CrutchSensorData;
import com.smartcane.backend.entity.vo.LatestSensorDataVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CrutchSensorDataMapper extends BaseMapper<CrutchSensorData> {

    @Select("SELECT device_sn, heart_rate, blood_oxygen, lat, lon, fall_status, report_time " +
            "FROM t_crutch_sensor_data " +
            "WHERE device_sn = #{deviceSn} " +
            "ORDER BY report_time DESC " +
            "LIMIT 1")
    LatestSensorDataVO selectLatestByDeviceSn(@Param("deviceSn") String deviceSn);
}
