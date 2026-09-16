package com.smartcane.backend.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "设备信息响应")
public class CrutchDeviceVO {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "设备唯一序列号")
    private String deviceSn;

    @Schema(description = "绑定老人姓名")
    private String elderName;

    @Schema(description = "老人紧急联系电话")
    private String elderPhone;

    @Schema(description = "监护人联系电话")
    private String guardianPhone;

    @Schema(description = "设备在线状态：0离线 1在线（由最近上报时间派生，不是设备表字段）")
    private Integer deviceStatus;

    @Schema(description = "最后上报时间，无采样记录时为 null")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime lastReportTime;

    @Schema(description = "设备录入时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime updateTime;
}
