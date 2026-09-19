package com.smartcane.backend.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "登录响应（不含密码字段）")
public class LoginVO {

    @Schema(description = "会话令牌，后续请求放 Authorization: Bearer <token>")
    private String token;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "登录名")
    private String username;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "角色：ADMIN 管理员 / GUARDIAN 监护人")
    private String role;
}
