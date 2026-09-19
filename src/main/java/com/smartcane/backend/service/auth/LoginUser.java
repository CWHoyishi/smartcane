package com.smartcane.backend.service.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前请求的登录人（会话内身份，不含密码）。
 */
@Data
@AllArgsConstructor
public class LoginUser {

    private Long userId;

    private String username;

    private String realName;

    /** ADMIN 管理员 / GUARDIAN 监护人 */
    private String role;

    /** 本次请求携带的 token，登出时用它删 Redis 会话 */
    private String token;

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
