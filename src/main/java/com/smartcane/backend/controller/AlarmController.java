package com.smartcane.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smartcane.backend.entity.dto.AlarmHandleDTO;
import com.smartcane.backend.entity.dto.AlarmQueryDTO;
import com.smartcane.backend.entity.po.AlarmRecord;
import com.smartcane.backend.entity.vo.Result;
import com.smartcane.backend.service.AlarmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "告警记录管理")
@RestController
@RequestMapping("/api/alarm")
public class AlarmController {

    @Autowired
    private AlarmService alarmService;

    @Operation(summary = "分页查询告警记录")
    @PostMapping("/page")
    public Result<IPage<AlarmRecord>> page(@RequestBody AlarmQueryDTO dto) {
        return alarmService.page(dto);
    }

    @Operation(summary = "查询待处理告警(最近100条)")
    @GetMapping("/pending")
    public Result<List<AlarmRecord>> listPending() {
        return alarmService.listPending();
    }

    @Operation(summary = "查询指定设备的告警记录(最近100条)")
    @GetMapping("/list/{deviceSn}")
    public Result<List<AlarmRecord>> listByDeviceSn(@PathVariable("deviceSn") String deviceSn) {
        return alarmService.listByDeviceSn(deviceSn);
    }

    @Operation(summary = "处置告警(确认/标记误报)")
    @PutMapping("/ack")
    public Result<Void> ack(@RequestBody AlarmHandleDTO dto) {
        return alarmService.ack(dto);
    }
}