package com.smartcane.backend.service.auth;

import java.time.Duration;

/**
 * 会话常量。签发与校验分处两个包，常量放这里避免互相引用实现类。
 */
public final class AuthSession {

    /** 会话有效期：每次请求都会续期，实际语义是「2 小时不操作才过期」 */
    public static final Duration TOKEN_TTL = Duration.ofHours(2);

    private static final String TOKEN_KEY_PREFIX = "smartcane:auth:token:";

    private AuthSession() {
    }

    public static String tokenKey(String token) {
        return TOKEN_KEY_PREFIX + token;
    }
}
