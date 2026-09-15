package com.smartcane.backend.entity.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@ApiModel(description = "传感器数据上报请求参数")
public class SensorDataDTO {

    @ApiModelProperty("设备序列号")
    private String deviceSn;

    @ApiModelProperty("心率HR，取值0-200，单位次/分钟")
    private Integer heartRate;

    @ApiModelProperty("血氧SP02，取值0-200，单位%")
    private Integer bloodOxygen;

    @ApiModelProperty("纬度Lat，范围-90~90°")
    private Double lat;

    @ApiModelProperty("经度Lon，范围-180~180°")
    private Double lon;

    @ApiModelProperty("摔倒状态：0正常 1摔倒告警")
    private Integer fallStatus;

    @ApiModelProperty("硬件数据上报时间，不传则使用服务器当前时间")
    private LocalDateTime reportTime;
}
