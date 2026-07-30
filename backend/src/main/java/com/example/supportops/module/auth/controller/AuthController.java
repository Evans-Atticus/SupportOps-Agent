package com.example.supportops.module.auth.controller;

import com.example.supportops.common.response.ApiResponse;
import com.example.supportops.infrastructure.web.RequestIdFilter;
import com.example.supportops.module.auth.model.dto.LoginDTO;
import com.example.supportops.module.auth.model.dto.RegisterDTO;
import com.example.supportops.module.auth.model.vo.TokenVO;
import com.example.supportops.module.auth.model.vo.UserVO;
import com.example.supportops.module.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "用户名密码登录")
    @PostMapping("/login")
    public ApiResponse<TokenVO> login(@Valid @RequestBody LoginDTO body, HttpServletRequest request) {
        return ApiResponse.success(authService.login(body, clientIp(request)), RequestIdFilter.getRequestId(request));
    }

    /** 仅信任本机或容器私网反向代理写入的 X-Forwarded-For，避免公网直连伪造 IP。 */
    private String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (isTrustedProxy(remote)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null) {
                String first = forwarded.split(",", 2)[0].trim();
                if (first.matches("[0-9A-Fa-f:.]{2,64}")) return first;
            }
        }
        return remote;
    }

    private boolean isTrustedProxy(String ip) {
        if (ip == null) return false;
        return ip.equals("127.0.0.1") || ip.equals("::1") || ip.startsWith("10.")
                || ip.startsWith("192.168.") || ip.matches("^172\\.(1[6-9]|2\\d|3[01])\\..*");
    }

    @Operation(summary = "注册客服账号")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserVO> register(@Valid @RequestBody RegisterDTO body, HttpServletRequest request) {
        return ApiResponse.success(authService.register(body), RequestIdFilter.getRequestId(request));
    }

    @Operation(summary = "获取当前用户")
    @GetMapping("/me")
    public ApiResponse<UserVO> me(Authentication authentication, HttpServletRequest request) {
        return ApiResponse.success(authService.currentUser(authentication.getName()),
                RequestIdFilter.getRequestId(request));
    }
}
