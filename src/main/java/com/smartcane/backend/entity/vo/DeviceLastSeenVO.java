package com.smartcane.backend.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "设备最后上报时间(离线判定用)")
public class DeviceLastSeenVO {

    @Schema(description = "设备序列号")
    private String deviceSn;

    @Schema(description = "该设备最后一条采样数据的上报时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime lastReportTime;
}