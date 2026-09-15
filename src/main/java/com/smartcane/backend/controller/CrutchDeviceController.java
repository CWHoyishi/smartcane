package com.smartcane.backend.controller;

import com.smartcane.backend.entity.dto.CrutchDeviceDTO;
import com.smartcane.backend.entity.vo.CrutchDeviceVO;
import com.smartcane.backend.entity.vo.Result;
import com.smartcane.backend.service.CrutchDeviceService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "智能拐杖设备管理")
@RestController
@RequestMapping("/api/device")
public class CrutchDeviceController {

    @Autowired
    private CrutchDeviceService crutchDeviceService;

    @ApiOperation("查询设备列表")
    @GetMapping("/list")
    public Result<List<CrutchDeviceVO>> list() {
        return crutchDeviceService.list();
    }

    @ApiOperation("根据ID查询设备详情")
    @GetMapping("/{id}")
    public Result<CrutchDeviceVO> getById(@PathVariable("id") Long id) {
        return crutchDeviceService.getById(id);
    }

    @ApiOperation("根据设备序列号查询")
    @GetMapping("/sn/{deviceSn}")
    public Result<CrutchDeviceVO> getByDeviceSn(@PathVariable("deviceSn") String deviceSn) {
        return crutchDeviceService.getByDeviceSn(deviceSn);
    }

    @ApiOperation("新增设备")
    @PostMapping("/add")
    public Result<Void> add(@RequestBody CrutchDeviceDTO dto) {
        return crutchDeviceService.add(dto);
    }

    @ApiOperation("更新设备信息")
    @PutMapping("/update")
    public Result<Void> update(@RequestBody CrutchDeviceDTO dto) {
        return crutchDeviceService.update(dto);
    }

    @ApiOperation("删除设备")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        return crutchDeviceService.delete(id);
    }
}
