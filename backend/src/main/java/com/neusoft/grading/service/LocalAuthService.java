package com.neusoft.grading.service;

import com.neusoft.grading.dto.ChangePasswordRequest;
import com.neusoft.grading.dto.LocalLoginRequest;
import com.neusoft.grading.dto.LocalRegisterRequest;
import com.neusoft.grading.dto.LoginResponse;

/**
 * 本地登录认证服务接口
 *
 * 与 CAS 登录并行的独立认证方式，支持用户名密码登录、注册、修改密码。
 * 内置暴力破解防护：连续失败 5 次锁定 30 分钟。
 */
public interface LocalAuthService {

    /**
     * 本地用户名密码登录
     *
     * @param request 登录请求（用户名 + 密码）
     * @return 登录响应（Token + 角色 + 用户信息）
     * @throws com.neusoft.grading.common.BizException 登录失败时抛出
     */
    LoginResponse login(LocalLoginRequest request);

    /**
     * 注册本地用户（仅管理员可操作）
     *
     * @param request 注册请求
     */
    void register(LocalRegisterRequest request);

    /**
     * 修改密码
     *
     * @param username  当前用户名
     * @param request   修改密码请求
     */
    void changePassword(String username, ChangePasswordRequest request);

    /**
     * 检查账户是否被锁定
     *
     * @param username 用户名
     * @return 锁定剩余秒数，0 表示未锁定
     */
    long checkLocked(String username);
}
