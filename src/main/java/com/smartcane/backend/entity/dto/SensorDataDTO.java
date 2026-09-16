package com.smartcane.backend.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "传感器数据上报请求参数")
public class SensorDataDTO {

    @Schema(description = "设备序列号")
    private String deviceSn;

    @Schema(description = "心率HR，取值0-200，单位次/分钟")
    private Integer heartRate;

    @Schema(description = "血氧SP02，取值0-200，单位%")
    private Integer bloodOxygen;

    @Schema(description = "纬度Lat，范围-90~90°")
    private Double lat;

    @Schema(description = "经度Lon，范围-180~180°")
    private Double lon;

    @Schema(description = "摔倒状态：0正常 1摔倒告警")
    private Integer fallStatus;

    @Schema(description = "硬件数据上报时间，不传则使用服务器当前时间")
    private LocalDateTime reportTime;
}
