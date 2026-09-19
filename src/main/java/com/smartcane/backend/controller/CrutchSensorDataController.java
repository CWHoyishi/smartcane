package com.smartcane.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smartcane.backend.entity.dto.SensorDataDTO;
import com.smartcane.backend.entity.dto.SensorDataQueryDTO;
import com.smartcane.backend.entity.vo.LatestSensorDataVO;
import com.smartcane.backend.entity.vo.Result;
import com.smartcane.backend.entity.vo.SensorDataVO;
import com.smartcane.backend.service.CrutchSensorDataService;
import com.smartcane.backend.service.auth.DataScopeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "传感器数据管理")
@RestController
@RequestMapping("/api/sensor")
public class CrutchSensorDataController {

    @Autowired
    private CrutchSensorDataService sensorDataService;

    @Autowired
    private DataScopeService dataScopeService;

    @Operation(summary = "分页查询传感器数据")
    @PostMapping("/page")
    public Result<IPage<SensorDataVO>> page(@RequestBody SensorDataQueryDTO dto) {
        return sensorDataService.page(dto);
    }

    @Operation(summary = "查询指定设备的传感器数据(最近100条)")
    @GetMapping("/list/{deviceSn}")
    public Result<List<SensorDataVO>> listByDeviceSn(@PathVariable("deviceSn") String deviceSn) {
        return sensorDataService.listByDeviceSn(deviceSn);
    }

    @Operation(summary = "获取指定设备的最新传感器数据")
    @GetMapping("/latest/{deviceSn}")
    public Result<LatestSensorDataVO> getLatest(@PathVariable("deviceSn") String deviceSn) {
        return sensorDataService.getLatest(deviceSn);
    }

    @Operation(summary = "查询摔倒告警记录")
    @GetMapping("/fall-alarms")
    public Result<List<SensorDataVO>> listFallAlarms() {
        return sensorDataService.listFallAlarms();
    }

    @Operation(summary = "上报传感器数据(心率/血氧/位置/摔倒状态)")
    @PostMapping("/report")
    public Result<Void> report(@RequestBody SensorDataDTO dto) {
        return sensorDataService.report(dto);
    }

    @Operation(summary = "删除传感器数据记录")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        dataScopeService.assertAdmin();
        return sensorDataService.delete(id);
    }
}
