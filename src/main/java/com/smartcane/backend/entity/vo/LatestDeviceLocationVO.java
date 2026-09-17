package com.smartcane.backend.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "设备最新位置（地图轮询用）")
public class LatestDeviceLocationVO {

    @Schema(description = "设备唯一序列号")
    private String deviceSn;

    @Schema(description = "绑定老人姓名")
    private String elderName;

    @Schema(description = "最新心率(次/分钟)")
    private Integer heartRate;

    @Schema(description = "最新血氧饱和度(%)")
    private Integer bloodOxygen;

    @Schema(description = "纬度")
    private Double lat;

    @Schema(description = "经度")
    private Double lon;

    @Schema(description = "最新摔倒状态：0正常 1摔倒告警")
    private Integer fallStatus;

    @Schema(description = "最新上报时间，无采样记录时为 null")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime reportTime;

    @Schema(description = "设备在线状态：0离线 1在线（由最新上报时间派生）")
    private Integer deviceStatus;
}
