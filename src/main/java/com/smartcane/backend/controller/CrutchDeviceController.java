package com.smartcane.backend.controller;

import com.smartcane.backend.entity.dto.CrutchDeviceDTO;
import com.smartcane.backend.entity.vo.CrutchDeviceVO;
import com.smartcane.backend.entity.vo.Result;
import com.smartcane.backend.service.CrutchDeviceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "智能拐杖设备管理")
@RestController
@RequestMapping("/api/device")
public class CrutchDeviceController {

    @Autowired
    private CrutchDeviceService crutchDeviceService;

    @Operation(summary = "查询设备列表")
    @GetMapping("/list")
    public Result<List<CrutchDeviceVO>> list() {
        return crutchDeviceService.list();
    }

    @Operation(summary = "根据ID查询设备详情")
    @GetMapping("/{id}")
    public Result<CrutchDeviceVO> getById(@PathVariable("id") Long id) {
        return crutchDeviceService.getById(id);
    }

    @Operation(summary = "根据设备序列号查询")
    @GetMapping("/sn/{deviceSn}")
    public Result<CrutchDeviceVO> getByDeviceSn(@PathVariable("deviceSn") String deviceSn) {
        return crutchDeviceService.getByDeviceSn(deviceSn);
    }

    @Operation(summary = "新增设备")
    @PostMapping("/add")
    public Result<Void> add(@RequestBody CrutchDeviceDTO dto) {
        return crutchDeviceService.add(dto);
    }

    @Operation(summary = "更新设备信息")
    @PutMapping("/update")
    public Result<Void> update(@RequestBody CrutchDeviceDTO dto) {
        return crutchDeviceService.update(dto);
    }

    @Operation(summary = "删除设备")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        return crutchDeviceService.delete(id);
    }
}
