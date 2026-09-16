package com.smartcane.backend.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "传感器数据查询条件")
public class SensorDataQueryDTO {

    @Schema(description = "设备序列号")
    private String deviceSn;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "摔倒状态：0正常 1摔倒告警")
    private Integer fallStatus;

    @Schema(description = "页码，默认1")
    private Integer pageNum = 1;

    @Schema(description = "每页条数，默认10")
    private Integer pageSize = 10;
}
