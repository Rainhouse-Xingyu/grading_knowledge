package com.neusoft.grading.controller;

import com.neusoft.grading.common.Result;
import com.neusoft.grading.dto.*;
import com.neusoft.grading.service.AuthService;
import com.neusoft.grading.service.LocalAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "认证授权")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String AUTH_HEADER = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    private final AuthService authService;
    private final LocalAuthService localAuthService;

    // ==================== 本地登录 ====================

    @Operation(summary = "本地用户名密码登录")
    @PostMapping("/login")
    public Result<LoginResponse> localLogin(@RequestBody @Valid LocalLoginRequest request) {
        LoginResponse response = localAuthService.login(request);
        return Result.ok(response);
    }

    @Operation(summary = "注册本地用户（仅管理员）")
    @PostMapping("/register")
    public Result<Void> localRegister(@RequestBody @Valid LocalRegisterRequest request) {
        localAuthService.register(request);
        return Result.ok();
    }

    @Operation(summary = "修改密码")
    @PostMapping("/change-password")
    public Result<Void> changePassword(HttpServletRequest request,
                                       @RequestBody @Valid ChangePasswordRequest body) {
        String token = extractToken(request);
        if (token == null) {
            return Result.fail(401, "未登录");
        }

        UserInfoResponse info = authService.getUserInfo(token);
        if (info == null) {
            return Result.fail(401, "Token 无效或已过期");
        }

        // 通过 userNo 解析本地用户名
        String username = localAuthService.resolveUsername(info.getUserNo(), info.getRole());
        localAuthService.changePassword(username, body);
        return Result.ok();
    }

    @Operation(summary = "查询账户锁定状态")
    @GetMapping("/lock-status")
    public Result<Long> lockStatus(@RequestParam String username) {
        long remaining = localAuthService.checkLocked(username);
        return Result.ok(remaining);
    }

    // ==================== CAS 登录 ====================

    @Operation(summary = "CAS 回调登录")
    @PostMapping("/cas/callback")
    public Result<LoginResponse> casCallback(@RequestBody CasCallbackRequest request) {
        LoginResponse response = authService.handleCasCallback(request.getTicket(), request.getService());
        return Result.ok(response);
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Map<String, String>> logout(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null) {
            return Result.fail(401, "未登录");
        }

        String casLogoutUrl = authService.logout(token);

        Map<String, String> data = new HashMap<>();
        data.put("casLogoutUrl", casLogoutUrl);
        return Result.ok(data);
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public Result<UserInfoResponse> me(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null) {
            return Result.fail(401, "未登录");
        }

        UserInfoResponse info = authService.getUserInfo(token);
        if (info == null) {
            return Result.fail(401, "Token 无效或已过期，请重新登录");
        }

        return Result.ok(info);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (header != null && header.startsWith(TOKEN_PREFIX)) {
            return header.substring(TOKEN_PREFIX.length());
        }
        return null;
    }
}
