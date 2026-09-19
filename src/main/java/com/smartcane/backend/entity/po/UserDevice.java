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
 * 监护人-设备绑定关系。
 * 数据隔离的依据：GUARDIAN 角色只能访问这里绑定过的 device_sn。
 */
@Data
@TableName("t_user_device")
@Schema(description = "监护人-设备绑定关系")
public class UserDevice {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "设备序列号")
    private String deviceSn;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "绑定时间")
    private LocalDateTime createTime;
}
