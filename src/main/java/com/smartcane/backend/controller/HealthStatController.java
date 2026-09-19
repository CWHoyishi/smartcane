package com.smartcane.backend.controller;

import com.smartcane.backend.entity.vo.DailyHealthStatVO;
import com.smartcane.backend.entity.vo.Result;
import com.smartcane.backend.entity.vo.WeeklyHealthStatVO;
import com.smartcane.backend.service.HealthStatService;
import com.smartcane.backend.service.auth.DataScopeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "健康统计")
@RestController
@RequestMapping("/api/health-stat")
public class HealthStatController {

    @Autowired
    private HealthStatService healthStatService;

    @Autowired
    private DataScopeService dataScopeService;

    @Operation(summary = "查询日统计(最近N天，默认7天，用于趋势图)")
    @GetMapping("/daily/{deviceSn}")
    public Result<List<DailyHealthStatVO>> listDaily(@PathVariable("deviceSn") String deviceSn,
                                                     @RequestParam(value = "days", required = false) Integer days) {
        return healthStatService.listDaily(deviceSn, days);
    }

    @Operation(summary = "查询周报(最近N周，默认4周)")
    @GetMapping("/weekly/{deviceSn}")
    public Result<List<WeeklyHealthStatVO>> listWeekly(@PathVariable("deviceSn") String deviceSn,
                                                       @RequestParam(value = "weeks", required = false) Integer weeks) {
        return healthStatService.listWeekly(deviceSn, weeks);
    }

    @Operation(summary = "手动重算最近N天日统计(默认7天)，用于回填历史或演示时立即出数")
    @PostMapping("/rebuild")
    public Result<Integer> rebuild(@RequestParam(value = "days", required = false) Integer days) {
        // 校验放控制器而不是 service：rebuild 也会被定时任务内部调用，service 层不能要求登录
        dataScopeService.assertAdmin();
        return healthStatService.rebuild(days == null ? 7 : days);
    }
}