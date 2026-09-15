package com.smartcane.backend.entity.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@ApiModel(description = "传感器数据查询条件")
public class SensorDataQueryDTO {

    @ApiModelProperty("设备序列号")
    private String deviceSn;

    @ApiModelProperty("开始时间")
    private LocalDateTime startTime;

    @ApiModelProperty("结束时间")
    private LocalDateTime endTime;

    @ApiModelProperty("摔倒状态：0正常 1摔倒告警")
    private Integer fallStatus;

    @ApiModelProperty("页码，默认1")
    private Integer pageNum = 1;

    @ApiModelProperty("每页条数，默认10")
    private Integer pageSize = 10;
}
