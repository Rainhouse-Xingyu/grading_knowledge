package com.neusoft.grading.service;

import com.neusoft.grading.dto.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 本地登录认证服务接口
 *
 * 与 CAS 登录并行的独立认证方式，支持用户名密码登录、注册、修改密码。
 * 内置暴力破解防护：连续失败 5 次锁定 30 分钟。
 */
public interface LocalAuthService {

    /**
     * 本地用户名密码登录（支持学号直接登录）
     */
    LoginResponse login(LocalLoginRequest request);

    /**
     * 注册本地用户（仅管理员可操作）
     */
    void register(LocalRegisterRequest request);

    /**
     * 修改密码
     */
    void changePassword(String username, ChangePasswordRequest request);

    /**
     * 检查账户是否被锁定
     */
    long checkLocked(String username);

    /**
     * 通过用户编号和角色解析本地用户名（用于 change-password 接口）
     *
     * @param userNo 用户编号（学号/工号）
     * @param role   角色
     * @return 本地登录用户名，找不到时返回 userNo 本身作为降级
     */
    String resolveUsername(String userNo, String role);

    /**
     * 批量导入学生信息并创建本地登录账户
     *
     * @param file           上传的 Excel 文件
     * @param defaultPassword 统一默认密码（当 Excel 中某行密码为空时使用）
     * @param courseId        课程 ID（可选，自动将该批学生选入课程）
     * @return 导入结果（成功数 + 失败详情）
     */
    BatchImportResult batchImportStudents(MultipartFile file, String defaultPassword, String courseId);
}
