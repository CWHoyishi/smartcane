package com.smartcane.backend.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警记录。
 *
 * 与采样数据一样按 (device_sn, alarm_type, report_time) 唯一：同一条采样只产生一条同类告警，
 * 双重通道重复上报不会重复告警。
 */
@Data
@TableName("t_alarm_record")
@Schema(description = "告警记录")
public class AlarmRecord {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "关联设备序列号")
    private String deviceSn;

    @Schema(description = "告警类型：FALL摔倒 / HEART_RATE心率异常 / BLOOD_OXYGEN血氧异常")
    private String alarmType;

    @Schema(description = "告警级别：2重要 3紧急")
    private Integer level;

    @Schema(description = "触发告警的指标值，用于回溯判定依据")
    private String alarmValue;

    @Schema(description = "触发告警的采样时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime reportTime;

    @Schema(description = "处理状态：0待处理 1已确认 2误报")
    private Integer status;

    @Schema(description = "处理人")
    private String handledBy;

    @Schema(description = "处理时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime handleTime;

    @Schema(description = "处理备注")
    private String handleNote;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "记录创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createTime;
}