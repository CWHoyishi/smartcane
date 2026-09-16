package com.smartcane.backend.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "告警记录查询条件")
public class AlarmQueryDTO {

    @Schema(description = "设备序列号")
    private String deviceSn;

    @Schema(description = "告警类型：FALL / HEART_RATE / BLOOD_OXYGEN")
    private String alarmType;

    @Schema(description = "处理状态：0待处理 1已确认 2误报，不传表示全部")
    private Integer status;

    @Schema(description = "页码，默认1")
    private Integer pageNum = 1;

    @Schema(description = "每页条数，默认10")
    private Integer pageSize = 10;
}