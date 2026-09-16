package com.smartcane.backend.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_crutch_sensor_data")
@Schema(description = "智能拐杖传感器实时上报数据")
public class CrutchSensorData {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "关联拐杖设备序列号")
    private String deviceSn;

    @Schema(description = "心率HR，取值0-200，单位次/分钟")
    private Integer heartRate;

    @Schema(description = "血氧SP02，取值0-200，单位%")
    private Integer bloodOxygen;

    @Schema(description = "纬度Lat，范围-90~90°")
    private Double lat;

    @Schema(description = "经度Lon，范围-180~180°")
    private Double lon;

    @Schema(description = "倾角判断摔倒状态MPU6050：0正常 1摔倒告警")
    private Integer fallStatus;

    @Schema(description = "硬件数据上传云平台时间")
    private LocalDateTime reportTime;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "后端入库时间")
    private LocalDateTime createTime;
}
