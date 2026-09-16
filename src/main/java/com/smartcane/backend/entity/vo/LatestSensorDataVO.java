package com.smartcane.backend.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "设备最新传感器数据响应")
public class LatestSensorDataVO {

    @Schema(description = "设备序列号")
    private String deviceSn;

    @Schema(description = "心率(次/分钟)")
    private Integer heartRate;

    @Schema(description = "血氧饱和度(%)")
    private Integer bloodOxygen;

    @Schema(description = "纬度")
    private Double lat;

    @Schema(description = "经度")
    private Double lon;

    @Schema(description = "摔倒状态：0正常 1摔倒告警")
    private Integer fallStatus;

    @Schema(description = "最新上报时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime reportTime;
}
