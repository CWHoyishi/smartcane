package com.smartcane.backend.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "健康统计日报")
public class DailyHealthStatVO {

    @Schema(description = "统计日期")
    private LocalDate statDate;

    @Schema(description = "当日采样条数")
    private Integer sampleCount;

    @Schema(description = "平均心率(次/分钟)，无有效样本时为 null")
    private Double avgHeartRate;

    @Schema(description = "平均血氧(%)，无有效样本时为 null")
    private Double avgBloodOxygen;

    @Schema(description = "摔倒次数")
    private Integer fallCount;

    @Schema(description = "心率异常次数")
    private Integer heartRateAbnormalCount;

    @Schema(description = "血氧异常次数")
    private Integer bloodOxygenAbnormalCount;

    @Schema(description = "在线时长(分钟)")
    private Integer activeMinutes;
}