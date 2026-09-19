package com.smartcane.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartcane.backend.entity.po.UserDevice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserDeviceMapper extends BaseMapper<UserDevice> {

    /**
     * 该用户绑定的设备序列号。
     * 数据隔离的查询入口：监护人每次访问都要用它判断设备归属。
     */
    @Select("SELECT device_sn FROM t_user_device WHERE user_id = #{userId}")
    List<String> selectDeviceSnsByUserId(@Param("userId") Long userId);
}
