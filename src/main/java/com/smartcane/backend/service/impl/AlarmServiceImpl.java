package com.smartcane.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartcane.backend.entity.dto.AlarmHandleDTO;
import com.smartcane.backend.entity.dto.AlarmQueryDTO;
import com.smartcane.backend.entity.po.AlarmRecord;
import com.smartcane.backend.entity.vo.Result;
import com.smartcane.backend.mapper.AlarmRecordMapper;
import com.smartcane.backend.service.AlarmEvaluator;
import com.smartcane.backend.service.AlarmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AlarmServiceImpl implements AlarmService {

    /** 分页查询默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 分页单页上限：与传感器数据查询保持一致的保护口径 */
    private static final int MAX_PAGE_SIZE = 200;

    /** 待处理告警一次最多返回条数 */
    private static final int PENDING_LIMIT = 100;

    /** 单设备告警查询条数上限 */
    private static final int RECENT_LIMIT = 100;

    @Autowired
    private AlarmRecordMapper alarmRecordMapper;

    @Override
    public Result<IPage<AlarmRecord>> page(AlarmQueryDTO dto) {
        QueryWrapper<AlarmRecord> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(dto.getDeviceSn())) {
            wrapper.eq("device_sn", dto.getDeviceSn());
        }
        if (StringUtils.hasText(dto.getAlarmType())) {
            wrapper.eq("alarm_type", dto.getAlarmType());
        }
        if (dto.getStatus() != null) {
            wrapper.eq("status", dto.getStatus());
        }
        wrapper.orderByDesc("report_time");

        int pageNum = dto.getPageNum() == null || dto.getPageNum() < 1 ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null || dto.getPageSize() < 1
                ? DEFAULT_PAGE_SIZE : Math.min(dto.getPageSize(), MAX_PAGE_SIZE);
        Page<AlarmRecord> page = new Page<>(pageNum, pageSize);
        return Result.success(alarmRecordMapper.selectPage(page, wrapper));
    }

    @Override
    public Result<List<AlarmRecord>> listPending() {
        QueryWrapper<AlarmRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("status", AlarmEvaluator.STATUS_PENDING);
        wrapper.orderByDesc("report_time");
        wrapper.last("LIMIT " + PENDING_LIMIT);
        return Result.success(alarmRecordMapper.selectList(wrapper));
    }

    @Override
    public Result<List<AlarmRecord>> listByDeviceSn(String deviceSn) {
        if (!StringUtils.hasText(deviceSn)) {
            return Result.error("设备序列号不能为空");
        }
        QueryWrapper<AlarmRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("device_sn", deviceSn);
        wrapper.orderByDesc("report_time");
        wrapper.last("LIMIT " + RECENT_LIMIT);
        return Result.success(alarmRecordMapper.selectList(wrapper));
    }

    @Override
    public Result<Void> ack(AlarmHandleDTO dto) {
        if (dto.getId() == null) {
            return Result.error("告警ID不能为空");
        }
        int targetStatus = dto.getStatus() == null ? -1 : dto.getStatus();
        if (targetStatus != AlarmEvaluator.STATUS_ACKED && targetStatus != AlarmEvaluator.STATUS_FALSE) {
            return Result.error("处理结果只能为 1(已确认) 或 2(误报)");
        }
        AlarmRecord record = alarmRecordMapper.selectById(dto.getId());
        if (record == null) {
            return Result.error("告警记录不存在");
        }
        // 处置是一次性动作：已确认/已标记误报的记录不允许再次改写，避免现场重复点击覆盖处置结论
        if (record.getStatus() == null || record.getStatus() != AlarmEvaluator.STATUS_PENDING) {
            return Result.error("该告警已处理，不能重复处置");
        }
        AlarmRecord update = new AlarmRecord();
        update.setId(dto.getId());
        update.setStatus(targetStatus);
        update.setHandledBy(dto.getHandledBy());
        update.setHandleNote(dto.getHandleNote());
        update.setHandleTime(LocalDateTime.now());
        alarmRecordMapper.updateById(update);
        return Result.success();
    }
}