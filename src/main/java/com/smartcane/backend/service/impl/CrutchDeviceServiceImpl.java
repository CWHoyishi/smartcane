package com.smartcane.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartcane.backend.entity.dto.CrutchDeviceDTO;
import com.smartcane.backend.entity.po.CrutchDevice;
import com.smartcane.backend.entity.vo.CrutchDeviceVO;
import com.smartcane.backend.entity.vo.Result;
import com.smartcane.backend.mapper.CrutchDeviceMapper;
import com.smartcane.backend.service.CrutchDeviceService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CrutchDeviceServiceImpl implements CrutchDeviceService {

    @Autowired
    private CrutchDeviceMapper crutchDeviceMapper;

    @Override
    public Result<List<CrutchDeviceVO>> list() {
        QueryWrapper<CrutchDevice> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("create_time");
        List<CrutchDevice> list = crutchDeviceMapper.selectList(wrapper);
        List<CrutchDeviceVO> voList = list.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return Result.success(voList);
    }

    @Override
    public Result<CrutchDeviceVO> getById(Long id) {
        CrutchDevice device = crutchDeviceMapper.selectById(id);
        if (device == null) {
            return Result.error("设备不存在");
        }
        return Result.success(convertToVO(device));
    }

    @Override
    public Result<CrutchDeviceVO> getByDeviceSn(String deviceSn) {
        QueryWrapper<CrutchDevice> wrapper = new QueryWrapper<>();
        wrapper.eq("device_sn", deviceSn);
        CrutchDevice device = crutchDeviceMapper.selectOne(wrapper);
        if (device == null) {
            return Result.error("设备不存在");
        }
        return Result.success(convertToVO(device));
    }

    @Override
    public Result<Void> add(CrutchDeviceDTO dto) {
        if (!StringUtils.hasText(dto.getDeviceSn())) {
            return Result.error("设备序列号不能为空");
        }
        QueryWrapper<CrutchDevice> wrapper = new QueryWrapper<>();
        wrapper.eq("device_sn", dto.getDeviceSn());
        if (crutchDeviceMapper.selectCount(wrapper) > 0) {
            return Result.error("该设备序列号已存在");
        }
        CrutchDevice device = new CrutchDevice();
        BeanUtils.copyProperties(dto, device);
        if (device.getDeviceStatus() == null) {
            device.setDeviceStatus(1);
        }
        crutchDeviceMapper.insert(device);
        return Result.success();
    }

    @Override
    public Result<Void> update(CrutchDeviceDTO dto) {
        if (dto.getId() == null) {
            return Result.error("设备ID不能为空");
        }
        CrutchDevice device = crutchDeviceMapper.selectById(dto.getId());
        if (device == null) {
            return Result.error("设备不存在");
        }
        CrutchDevice updateDevice = new CrutchDevice();
        BeanUtils.copyProperties(dto, updateDevice);
        crutchDeviceMapper.updateById(updateDevice);
        return Result.success();
    }

    @Override
    public Result<Void> delete(Long id) {
        if (crutchDeviceMapper.selectById(id) == null) {
            return Result.error("设备不存在");
        }
        crutchDeviceMapper.deleteById(id);
        return Result.success();
    }

    private CrutchDeviceVO convertToVO(CrutchDevice device) {
        CrutchDeviceVO vo = new CrutchDeviceVO();
        BeanUtils.copyProperties(device, vo);
        return vo;
    }
}
