package com.smartcane.backend.entity.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(description = "设备录入/更新请求参数")
public class CrutchDeviceDTO {

    @ApiModelProperty("主键ID(更新时传入)")
    private Long id;

    @ApiModelProperty("设备唯一序列号(云平台DeviceId)")
    private String deviceSn;

    @ApiModelProperty("绑定老人姓名")
    private String elderName;

    @ApiModelProperty("老人紧急联系电话")
    private String elderPhone;

    @ApiModelProperty("监护人联系电话")
    private String guardianPhone;

    @ApiModelProperty("设备在线状态：0离线 1在线")
    private Integer deviceStatus;
}
