package com.smartcane.backend.controller;

import com.smartcane.backend.entity.dto.LoginDTO;
import com.smartcane.backend.entity.vo.LoginVO;
import com.smartcane.backend.entity.vo.Result;
import com.smartcane.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "登录鉴权")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Operation(summary = "登录，返回会话 token")
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginDTO dto) {
        return authService.login(dto);
    }

    @Operation(summary = "登出，服务端立即失效当前 token")
    @PostMapping("/logout")
    public Result<Void> logout() {
        return authService.logout();
    }

    @Operation(summary = "获取当前登录用户")
    @GetMapping("/me")
    public Result<LoginVO> me() {
        return authService.currentUser();
    }
}
