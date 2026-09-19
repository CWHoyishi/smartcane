package com.smartcane.backend.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "登录请求参数")
public class LoginDTO {

    @Schema(description = "登录名")
    private String username;

    @Schema(description = "密码（内网演示环境未启用 HTTPS，正式部署需加密传输）")
    private String password;
}
