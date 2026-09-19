package com.smartcane.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartcane.backend.entity.dto.LoginDTO;
import com.smartcane.backend.entity.po.SysUser;
import com.smartcane.backend.entity.vo.LoginVO;
import com.smartcane.backend.entity.vo.Result;
import com.smartcane.backend.mapper.SysUserMapper;
import com.smartcane.backend.service.AuthService;
import com.smartcane.backend.service.auth.AuthContext;
import com.smartcane.backend.service.auth.AuthSession;
import com.smartcane.backend.service.auth.LoginUser;
import com.smartcane.backend.service.auth.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private StringRedisTemplate redis;

    @Override
    public Result<LoginVO> login(LoginDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getUsername()) || !StringUtils.hasText(dto.getPassword())) {
            return Result.error("用户名和密码不能为空");
        }
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.eq("username", dto.getUsername().trim());
        SysUser user = userMapper.selectOne(wrapper);

        // 账号不存在与密码错误返回同一提示：否则接口会变成用户名枚举器
        if (user == null || !PasswordUtil.matches(dto.getPassword(), user.getPasswordHash())) {
            log.warn("[登录失败] username={}", dto.getUsername());
            return Result.error(401, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            log.warn("[登录失败] 账号已停用 username={}", user.getUsername());
            return Result.error(403, "账号已停用");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        // 会话值只存 userId：角色/状态每次请求回查数据库，改权限或停用账号能立即生效
        redis.opsForValue().set(AuthSession.tokenKey(token), String.valueOf(user.getId()), AuthSession.TOKEN_TTL);
        log.info("[登录成功] username={}, role={}", user.getUsername(), user.getRole());

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setRole(user.getRole());
        return Result.success(vo);
    }

    @Override
    public Result<Void> logout() {
        LoginUser current = AuthContext.get();
        if (current != null && current.getToken() != null) {
            redis.delete(AuthSession.tokenKey(current.getToken()));
            log.info("[登出] username={}", current.getUsername());
        }
        return Result.success();
    }

    @Override
    public Result<LoginVO> currentUser() {
        LoginUser current = AuthContext.get();
        if (current == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        LoginVO vo = new LoginVO();
        vo.setUserId(current.getUserId());
        vo.setUsername(current.getUsername());
        vo.setRealName(current.getRealName());
        vo.setRole(current.getRole());
        return Result.success(vo);
    }
}
