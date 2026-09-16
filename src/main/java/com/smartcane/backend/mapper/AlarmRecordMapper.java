package com.smartcane.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartcane.backend.entity.po.AlarmRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AlarmRecordMapper extends BaseMapper<AlarmRecord> {
}