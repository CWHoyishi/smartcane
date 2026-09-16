package com.smartcane.backend.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "告警处理请求参数")
public class AlarmHandleDTO {

    @Schema(description = "告警记录ID")
    private Long id;

    @Schema(description = "处理结果：1已确认 2误报")
    private Integer status;

    @Schema(description = "处理人")
    private String handledBy;

    @Schema(description = "处理备注")
    private String handleNote;
}