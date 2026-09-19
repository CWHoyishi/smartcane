package com.smartcane.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcane.backend.entity.po.SysUser;
import com.smartcane.backend.entity.vo.Result;
import com.smartcane.backend.mapper.SysUserMapper;
import com.smartcane.backend.service.auth.AuthContext;
import com.smartcane.backend.service.auth.AuthSession;
import com.smartcane.backend.service.auth.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 登录校验拦截器。
 *
 * 会话存在 Redis（key 就是 token），没有签发 JWT：内网演示不需要跨服务验签，
 * 存 Redis 可以随时登出/踢人，也不用管理签名密钥。
 *
 * 每个请求回查一次 t_user：多一次主键查询换来「停用账号立即生效」，
 * 用户量级是个位数，这个代价可以接受。
 */
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final SysUserMapper userMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(SysUserMapper userMapper, StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // CORS 预检请求不带 Authorization，放行交给 CorsConfig 处理
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            writeResult(response, 401, "未登录或登录已过期");
            return false;
        }
        String userId = redis.opsForValue().get(AuthSession.tokenKey(token));
        if (userId == null) {
            writeResult(response, 401, "未登录或登录已过期");
            return false;
        }
        SysUser user;
        try {
            user = userMapper.selectById(Long.valueOf(userId));
        } catch (NumberFormatException e) {
            redis.delete(AuthSession.tokenKey(token));
            writeResult(response, 401, "登录状态异常，请重新登录");
            return false;
        }
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            redis.delete(AuthSession.tokenKey(token));
            writeResult(response, 403, "账号已停用");
            return false;
        }
        // 滑动续期：只要在有效期内有操作，就不会被踢下线
        redis.expire(AuthSession.tokenKey(token), AuthSession.TOKEN_TTL);
        AuthContext.set(new LoginUser(user.getId(), user.getUsername(), user.getRealName(), user.getRole(), token));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // Tomcat 线程会复用，必须清理，否则身份会串到下一个请求
        AuthContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    private void writeResult(HttpServletResponse response, int code, String message) {
        // 沿用项目约定：HTTP 状态固定 200，业务码放在 body 的 code 字段
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            response.getWriter().write(objectMapper.writeValueAsString(Result.error(code, message)));
        } catch (Exception e) {
            log.error("写鉴权失败响应出错: {}", e.getMessage());
        }
    }
}
