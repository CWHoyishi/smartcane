package com.smartcane.backend.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@ApiModel(description = "设备信息响应")
public class CrutchDeviceVO {

    @ApiModelProperty("主键ID")
    private Long id;

    @ApiModelProperty("设备唯一序列号")
    private String deviceSn;

    @ApiModelProperty("绑定老人姓名")
    private String elderName;

    @ApiModelProperty("老人紧急联系电话")
    private String elderPhone;

    @ApiModelProperty("监护人联系电话")
    private String guardianPhone;

    @ApiModelProperty("设备在线状态：0离线 1在线")
    private Integer deviceStatus;

    @ApiModelProperty("设备录入时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createTime;

    @ApiModelProperty("更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime updateTime;
}
