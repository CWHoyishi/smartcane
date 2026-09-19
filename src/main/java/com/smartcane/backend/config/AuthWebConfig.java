package com.smartcane.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcane.backend.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 鉴权拦截器注册。
 *
 * 只拦 /api/**：actuator、swagger 在别的路径下，不受影响。
 * 放行两个接口：
 *   - /api/auth/login   登录本身不能要 token
 *   - /api/sensor/report 设备/云平台侧调用，设备没有登录能力（演示环境未下发设备密钥）
 */
@Configuration
public class AuthWebConfig implements WebMvcConfigurer {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor(userMapper, redis, objectMapper))
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/sensor/report"
                );
    }
}
