package com.smartcane.backend.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 健康统计日报。
 *
 * 定时任务按 (设备, 自然日) 预聚合，接口只读这张表，不在查询时扫采样明细。
 * 心率/血氧存 sum + count 而不是平均数：周报要用 SUM(sum)/SUM(count) 做加权平均，
 * 存平均数无法还原权重，天数不等时会算错。
 */
@Data
@TableName("t_health_daily_stat")
@Schema(description = "健康统计日报（预聚合）")
public class HealthDailyStat {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "关联设备序列号")
    private String deviceSn;

    @Schema(description = "统计日期")
    private LocalDate statDate;

    @Schema(description = "当日采样总条数")
    private Integer sampleCount;

    @Schema(description = "有效心率之和（heart_rate > 0）")
    private Integer heartRateSum;

    @Schema(description = "有效心率样本数")
    private Integer heartRateCount;

    @Schema(description = "有效血氧之和（blood_oxygen > 0）")
    private Integer bloodOxygenSum;

    @Schema(description = "有效血氧样本数")
    private Integer bloodOxygenCount;

    @Schema(description = "摔倒次数（fall_status = 1 的采样数）")
    private Integer fallCount;

    @Schema(description = "心率异常次数（<50 或 >120）")
    private Integer heartRateAbnormalCount;

    @Schema(description = "血氧异常次数（<90）")
    private Integer bloodOxygenAbnormalCount;

    @Schema(description = "在线时长（分钟）")
    private Integer activeMinutes;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "重算更新时间")
    private LocalDateTime updateTime;
}