package com.smartcane.backend.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 健康统计小时表。
 *
 * 数据分层的中间层：原始明细只保留 7 天，这张表长期保留，
 * 日统计由它求和而来，所以超过 7 天的报表不会因为明细被清理而断档。
 * 字段与日统计一致（sum + count），只是粒度换成小时。
 */
@Data
@TableName("t_health_hourly_stat")
@Schema(description = "健康统计小时表（预聚合，长期保留）")
public class HealthHourlyStat {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "关联设备序列号")
    private String deviceSn;

    @Schema(description = "统计小时（整点）")
    private LocalDateTime statHour;

    @Schema(description = "该小时采样条数")
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

    @Schema(description = "活动时长（分钟）")
    private Integer activeMinutes;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "重算更新时间")
    private LocalDateTime updateTime;
}