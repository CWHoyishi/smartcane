package com.smartcane.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smartcane.backend.entity.dto.AlarmHandleDTO;
import com.smartcane.backend.entity.dto.AlarmQueryDTO;
import com.smartcane.backend.entity.po.AlarmRecord;
import com.smartcane.backend.entity.vo.Result;

import java.util.List;

public interface AlarmService {

    Result<IPage<AlarmRecord>> page(AlarmQueryDTO dto);

    Result<List<AlarmRecord>> listPending();

    Result<List<AlarmRecord>> listByDeviceSn(String deviceSn);

    Result<Void> ack(AlarmHandleDTO dto);
}