package com.smartcane.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartcane.backend.entity.dto.CrutchDeviceDTO;
import com.smartcane.backend.entity.po.AlarmRecord;
import com.smartcane.backend.entity.po.CrutchDevice;
import com.smartcane.backend.entity.po.CrutchSensorData;
import com.smartcane.backend.entity.vo.CrutchDeviceVO;
import com.smartcane.backend.entity.vo.DeviceLastSeenVO;
import com.smartcane.backend.entity.vo.Result;
import com.smartcane.backend.mapper.AlarmRecordMapper;
import com.smartcane.backend.mapper.CrutchDeviceMapper;
import com.smartcane.backend.mapper.CrutchSensorDataMapper;
import com.smartcane.backend.service.CrutchDeviceService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CrutchDeviceServiceImpl implements CrutchDeviceService {

    /** 离线判定窗口（秒）：超过该时长没有新采样即视为离线。拉取间隔 60 秒，这里留 5 倍余量，
     *  避免一次网络抖动就把在线设备判成离线。 */
    private static final long OFFLINE_THRESHOLD_SECONDS = 300;

    @Autowired
    private CrutchDeviceMapper crutchDeviceMapper;

    @Autowired
    private CrutchSensorDataMapper sensorDataMapper;

    @Autowired
    private AlarmRecordMapper alarmRecordMapper;

    @Override
    public Result<List<CrutchDeviceVO>> list() {
        QueryWrapper<CrutchDevice> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("create_time");
        List<CrutchDevice> list = crutchDeviceMapper.selectList(wrapper);
        List<CrutchDeviceVO> voList = list.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        applyOnlineStatus(voList);
        return Result.success(voList);
    }

    @Override
    public Result<CrutchDeviceVO> getById(Long id) {
        CrutchDevice device = crutchDeviceMapper.selectById(id);
        if (device == null) {
            return Result.error("设备不存在");
        }
        CrutchDeviceVO vo = convertToVO(device);
        applyOnlineStatus(Collections.singletonList(vo));
        return Result.success(vo);
    }

    @Override
    public Result<CrutchDeviceVO> getByDeviceSn(String deviceSn) {
        QueryWrapper<CrutchDevice> wrapper = new QueryWrapper<>();
        wrapper.eq("device_sn", deviceSn);
        CrutchDevice device = crutchDeviceMapper.selectOne(wrapper);
        if (device == null) {
            return Result.error("设备不存在");
        }
        CrutchDeviceVO vo = convertToVO(device);
        applyOnlineStatus(Collections.singletonList(vo));
        return Result.success(vo);
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
    public Result<Void> delete(Long id, boolean force) {
        CrutchDevice device = crutchDeviceMapper.selectById(id);
        if (device == null) {
            return Result.error("设备不存在");
        }
        if (!force) {
            // 外键 fk_crutch_dev / fk_alarm_dev 都是 ON DELETE CASCADE：
            // 直接删设备会静默带走该设备的全部历史采样与告警记录，所以默认拒绝，
            // 必须由调用方显式传 force=true 才真正执行。
            long sensorCount = countSensorData(device.getDeviceSn());
            long alarmCount = countAlarmRecords(device.getDeviceSn());
            if (sensorCount > 0 || alarmCount > 0) {
                return Result.error(409, String.format(
                        "该设备存在 %d 条传感器数据、%d 条告警记录，删除会一并清除。确认删除请再次确认。",
                        sensorCount, alarmCount));
            }
        }
        crutchDeviceMapper.deleteById(id);
        return Result.success();
    }

    private long countSensorData(String deviceSn) {
        QueryWrapper<CrutchSensorData> wrapper = new QueryWrapper<>();
        wrapper.eq("device_sn", deviceSn);
        return sensorDataMapper.selectCount(wrapper);
    }

    private long countAlarmRecords(String deviceSn) {
        QueryWrapper<AlarmRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("device_sn", deviceSn);
        return alarmRecordMapper.selectCount(wrapper);
    }

    /**
     * 用采样表反推在线状态：设备表没有 last_seen 字段，MQTT 掉线也不会有回调，
     * 「最后一条采样距今多久」是唯一可信的在线依据。
     */
    private void applyOnlineStatus(List<CrutchDeviceVO> voList) {
        if (voList.isEmpty()) {
            return;
        }
        Map<String, LocalDateTime> lastSeenMap = new HashMap<>();
        for (DeviceLastSeenVO item : crutchDeviceMapper.selectLastSeen()) {
            lastSeenMap.put(item.getDeviceSn(), item.getLastReportTime());
        }
        LocalDateTime onlineAfter = LocalDateTime.now().minusSeconds(OFFLINE_THRESHOLD_SECONDS);
        for (CrutchDeviceVO vo : voList) {
            LocalDateTime lastReportTime = lastSeenMap.get(vo.getDeviceSn());
            vo.setLastReportTime(lastReportTime);
            vo.setDeviceStatus(lastReportTime != null && lastReportTime.isAfter(onlineAfter) ? 1 : 0);
        }
    }

    private CrutchDeviceVO convertToVO(CrutchDevice device) {
        CrutchDeviceVO vo = new CrutchDeviceVO();
        BeanUtils.copyProperties(device, vo);
        return vo;
    }
}
