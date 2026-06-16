package com.neusoft.grading.service;

import com.neusoft.grading.dto.LoginResponse;
import com.neusoft.grading.dto.UserInfoResponse;

/**
 * 认证授权业务逻辑
 */
public interface AuthService {

    /**
     * 处理 CAS 回调：验证 ticket → 入库用户信息 → 生成 Token → 返回登录结果
     *
     * @param ticket  CAS 返回的 ticket
     * @param service 前端传来的当前服务地址（可选）
     * @return 登录响应，包含 Token 和基本用户信息
     */
    LoginResponse handleCasCallback(String ticket, String service);

    /**
     * 登出：清除 Redis 会话，返回 CAS 登出重定向 URL
     *
     * @param token 当前用户的 Access-Token
     * @return CAS 登出页完整 URL
     */
    String logout(String token);

    /**
     * 获取当前登录用户的详细信息
     *
     * @param token Access-Token
     * @return 用户详情（含角色区分字段）
     */
    UserInfoResponse getUserInfo(String token);
}
