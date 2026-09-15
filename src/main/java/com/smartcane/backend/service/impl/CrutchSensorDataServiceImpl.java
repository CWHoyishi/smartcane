package com.smartcane.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartcane.backend.entity.dto.SensorDataDTO;
import com.smartcane.backend.entity.dto.SensorDataQueryDTO;
import com.smartcane.backend.entity.po.CrutchSensorData;
import com.smartcane.backend.entity.vo.LatestSensorDataVO;
import com.smartcane.backend.entity.vo.Result;
import com.smartcane.backend.entity.vo.SensorDataVO;
import com.smartcane.backend.mapper.CrutchSensorDataMapper;
import com.smartcane.backend.service.CrutchSensorDataService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CrutchSensorDataServiceImpl implements CrutchSensorDataService {

    @Autowired
    private CrutchSensorDataMapper sensorDataMapper;

    @Override
    public Result<IPage<SensorDataVO>> page(SensorDataQueryDTO dto) {
        QueryWrapper<CrutchSensorData> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(dto.getDeviceSn())) {
            wrapper.eq("device_sn", dto.getDeviceSn());
        }
        if (dto.getStartTime() != null) {
            wrapper.ge("report_time", dto.getStartTime());
        }
        if (dto.getEndTime() != null) {
            wrapper.le("report_time", dto.getEndTime());
        }
        if (dto.getFallStatus() != null) {
            wrapper.eq("fall_status", dto.getFallStatus());
        }
        wrapper.orderByDesc("report_time");

        Page<CrutchSensorData> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        IPage<CrutchSensorData> pageResult = sensorDataMapper.selectPage(page, wrapper);

        IPage<SensorDataVO> voPage = pageResult.convert(this::convertToVO);
        return Result.success(voPage);
    }

    @Override
    public Result<List<SensorDataVO>> listByDeviceSn(String deviceSn) {
        QueryWrapper<CrutchSensorData> wrapper = new QueryWrapper<>();
        wrapper.eq("device_sn", deviceSn);
        wrapper.orderByDesc("report_time");
        List<CrutchSensorData> list = sensorDataMapper.selectList(wrapper);
        List<SensorDataVO> voList = list.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return Result.success(voList);
    }

    @Override
    public Result<LatestSensorDataVO> getLatest(String deviceSn) {
        LatestSensorDataVO vo = sensorDataMapper.selectLatestByDeviceSn(deviceSn);
        if (vo == null) {
            return Result.error("暂无传感器数据");
        }
        return Result.success(vo);
    }

    @Override
    public Result<List<SensorDataVO>> listFallAlarms() {
        QueryWrapper<CrutchSensorData> wrapper = new QueryWrapper<>();
        wrapper.eq("fall_status", 1);
        wrapper.orderByDesc("report_time");
        wrapper.last("LIMIT 50");
        List<CrutchSensorData> list = sensorDataMapper.selectList(wrapper);
        List<SensorDataVO> voList = list.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return Result.success(voList);
    }

    @Override
    public Result<Void> report(SensorDataDTO dto) {
        if (!StringUtils.hasText(dto.getDeviceSn())) {
            return Result.error("设备序列号不能为空");
        }
        CrutchSensorData data = new CrutchSensorData();
        BeanUtils.copyProperties(dto, data);
        if (data.getFallStatus() == null) {
            data.setFallStatus(0);
        }
        if (data.getReportTime() == null) {
            data.setReportTime(LocalDateTime.now());
        }
        sensorDataMapper.insert(data);
        return Result.success();
    }

    @Override
    public Result<Void> delete(Long id) {
        if (sensorDataMapper.selectById(id) == null) {
            return Result.error("数据不存在");
        }
        sensorDataMapper.deleteById(id);
        return Result.success();
    }

    private SensorDataVO convertToVO(CrutchSensorData data) {
        SensorDataVO vo = new SensorDataVO();
        BeanUtils.copyProperties(data, vo);
        return vo;
    }
}
