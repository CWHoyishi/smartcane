package com.smartcane.backend.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_crutch_device")
@ApiModel(description = "智能拐杖设备基础信息")
public class CrutchDevice {

    @TableId(type = IdType.AUTO)
    @ApiModelProperty("主键自增ID")
    private Long id;

    @ApiModelProperty("设备唯一序列号(云平台DeviceId)")
    private String deviceSn;

    @ApiModelProperty("绑定老人姓名")
    private String elderName;

    @ApiModelProperty("老人紧急联系电话")
    private String elderPhone;

    @ApiModelProperty("监护人联系电话")
    private String guardianPhone;

    @ApiModelProperty("设备在线状态：0离线 1在线")
    private Integer deviceStatus;

    @TableField(fill = FieldFill.INSERT)
    @ApiModelProperty("设备录入时间")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty("更新时间")
    private LocalDateTime updateTime;
}
