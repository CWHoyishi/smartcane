package com.smartcane.backend.entity.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "传感器数据查询条件")
public class SensorDataQueryDTO {

    @Schema(description = "设备序列号")
    private String deviceSn;

    // 前端 el-date-picker 以 value-format="YYYY-MM-DD HH:mm:ss" 提交，JSR-310 默认只认 ISO-8601（T 分隔），
    // 必须显式声明格式，否则反序列化抛 InvalidFormatException → 接口 400
    @Schema(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime startTime;

    @Schema(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime endTime;

    @Schema(description = "摔倒状态：0正常 1摔倒告警")
    private Integer fallStatus;

    @Schema(description = "页码，默认1")
    private Integer pageNum = 1;

    @Schema(description = "每页条数，默认10")
    private Integer pageSize = 10;
}
