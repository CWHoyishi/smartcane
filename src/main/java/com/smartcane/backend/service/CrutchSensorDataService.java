package com.smartcane.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smartcane.backend.entity.dto.SensorDataDTO;
import com.smartcane.backend.entity.dto.SensorDataQueryDTO;
import com.smartcane.backend.entity.vo.LatestSensorDataVO;
import com.smartcane.backend.entity.vo.Result;
import com.smartcane.backend.entity.vo.SensorDataVO;

import java.util.List;

public interface CrutchSensorDataService {

    Result<IPage<SensorDataVO>> page(SensorDataQueryDTO dto);

    Result<List<SensorDataVO>> listByDeviceSn(String deviceSn);

    Result<LatestSensorDataVO> getLatest(String deviceSn);

    Result<List<SensorDataVO>> listFallAlarms();

    Result<Void> report(SensorDataDTO dto);

    Result<Void> delete(Long id);
}
