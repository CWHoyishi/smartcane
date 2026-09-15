package com.smartcane.backend.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@ApiModel(description = "传感器数据响应")
public class SensorDataVO {

    @ApiModelProperty("主键ID")
    private Long id;

    @ApiModelProperty("设备序列号")
    private String deviceSn;

    @ApiModelProperty("心率(次/分钟)")
    private Integer heartRate;

    @ApiModelProperty("血氧饱和度(%)")
    private Integer bloodOxygen;

    @ApiModelProperty("纬度")
    private Double lat;

    @ApiModelProperty("经度")
    private Double lon;

    @ApiModelProperty("摔倒状态：0正常 1摔倒告警")
    private Integer fallStatus;

    @ApiModelProperty("硬件上报时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime reportTime;

    @ApiModelProperty("后端入库时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createTime;
}
