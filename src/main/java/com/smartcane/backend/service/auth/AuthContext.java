package com.smartcane.backend.service.auth;

/**
 * 当前请求的登录人容器。
 *
 * 用 ThreadLocal 传递身份：拦截器在 preHandle 写入、afterCompletion 清理，
 * 这样数据隔离校验不必给每个 service 方法都加一个「当前用户」参数。
 */
public final class AuthContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
