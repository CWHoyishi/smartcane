package com.smartcane.backend.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "设备录入/更新请求参数")
public class CrutchDeviceDTO {

    @Schema(description = "主键ID(更新时传入)")
    private Long id;

    @Schema(description = "设备唯一序列号(云平台DeviceId)")
    private String deviceSn;

    @Schema(description = "绑定老人姓名")
    private String elderName;

    @Schema(description = "老人紧急联系电话")
    private String elderPhone;

    @Schema(description = "监护人联系电话")
    private String guardianPhone;

    @Schema(description = "设备在线状态：0离线 1在线")
    private Integer deviceStatus;
}
