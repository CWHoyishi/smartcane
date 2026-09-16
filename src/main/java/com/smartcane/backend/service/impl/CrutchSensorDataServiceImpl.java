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
import com.smartcane.backend.service.SensorDataWriter;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CrutchSensorDataServiceImpl implements CrutchSensorDataService {

    /** 分页查询默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 分页单页上限：前端与小程序都是展示型查询，避免一次拉取过多数据 */
    private static final int MAX_PAGE_SIZE = 200;

    /** 单设备「最近数据」查询条数上限 */
    private static final int RECENT_LIMIT = 100;

    @Autowired
    private CrutchSensorDataMapper sensorDataMapper;

    @Autowired
    private SensorDataWriter sensorDataWriter;

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

        int pageNum = dto.getPageNum() == null || dto.getPageNum() < 1 ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null || dto.getPageSize() < 1
                ? DEFAULT_PAGE_SIZE : Math.min(dto.getPageSize(), MAX_PAGE_SIZE);
        Page<CrutchSensorData> page = new Page<>(pageNum, pageSize);
        IPage<CrutchSensorData> pageResult = sensorDataMapper.selectPage(page, wrapper);

        IPage<SensorDataVO> voPage = pageResult.convert(this::convertToVO);
        return Result.success(voPage);
    }

    @Override
    public Result<List<SensorDataVO>> listByDeviceSn(String deviceSn) {
        QueryWrapper<CrutchSensorData> wrapper = new QueryWrapper<>();
        wrapper.eq("device_sn", deviceSn);
        wrapper.orderByDesc("report_time");
        // 与接口注释保持一致：只取最近 100 条（按 10 秒一条计，单设备一天可达 8640 条）
        wrapper.last("LIMIT " + RECENT_LIMIT);
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
            data.setReportTime(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        }
        // 与两条采集通道保持一致的重复语义：命中唯一键按重复忽略，不向前端抛错
        sensorDataWriter.saveIgnoringDuplicate(data);
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
