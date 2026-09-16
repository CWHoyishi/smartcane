package com.smartcane.backend.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "健康统计周报（自然周，周一到周日）")
public class WeeklyHealthStatVO {

    @Schema(description = "周起始日(周一)")
    private LocalDate weekStart;

    @Schema(description = "周结束日(周日)")
    private LocalDate weekEnd;

    @Schema(description = "本周有统计数据的天数")
    private Integer statDays;

    @Schema(description = "本周采样条数")
    private Integer sampleCount;

    @Schema(description = "平均心率(次/分钟)，按有效样本加权，无样本时为 null")
    private Double avgHeartRate;

    @Schema(description = "平均血氧(%)，按有效样本加权，无样本时为 null")
    private Double avgBloodOxygen;

    @Schema(description = "摔倒次数")
    private Integer fallCount;

    @Schema(description = "心率异常次数")
    private Integer heartRateAbnormalCount;

    @Schema(description = "血氧异常次数")
    private Integer bloodOxygenAbnormalCount;

    @Schema(description = "活动时长(分钟)")
    private Integer activeMinutes;
}