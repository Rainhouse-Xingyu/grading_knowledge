package com.neusoft.grading.service;

import com.neusoft.grading.dto.LoginResponse;
import com.neusoft.grading.dto.UserInfoResponse;

/**
 * Token / Redis 会话管理
 */
public interface TokenService {

    /**
     * 生成 Token 并存储到 Redis
     *
     * @param role   角色 student/teacher
     * @param userNo 用户编号
     * @param name   用户姓名
     * @return 生成的 Token 字符串
     */
    String createToken(String role, String userNo, String name);

    /**
     * 从 Token 对应的 Redis 会话中获取用户信息
     *
     * @param token Token 字符串
     * @return 用户信息，若 Token 无效或已过期则返回 null
     */
    UserInfoResponse getUserInfo(String token);

    /**
     * 根据 Token 获取用户角色
     */
    String getRole(String token);

    /**
     * 根据 Token 获取用户编号
     */
    String getUserNo(String token);

    /**
     * 删除 Token 对应的 Redis 会话
     */
    void removeToken(String token);
}
