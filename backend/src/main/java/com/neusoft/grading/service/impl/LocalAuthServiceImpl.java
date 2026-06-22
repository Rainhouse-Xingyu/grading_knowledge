package com.neusoft.grading.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.neusoft.grading.common.BizException;
import com.neusoft.grading.dto.*;
import com.neusoft.grading.entity.LocalUser;
import com.neusoft.grading.entity.Student;
import com.neusoft.grading.entity.Teacher;
import com.neusoft.grading.mapper.LocalUserMapper;
import com.neusoft.grading.mapper.StudentMapper;
import com.neusoft.grading.mapper.TeacherMapper;
import com.neusoft.grading.service.LocalAuthService;
import com.neusoft.grading.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 本地登录认证服务实现
 *
 * 安全策略：
 * 1. 密码使用 BCrypt（cost=12）加盐哈希，永不明文存储
 * 2. 连续登录失败 5 次 → Redis 锁定账户 30 分钟
 * 3. 锁定期间任何登录尝试直接拒绝，不验证密码（防时序攻击）
 * 4. 登录成功后重置失败计数
 * 5. 注册时校验关联用户是否存在（student/teacher 角色）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalAuthServiceImpl implements LocalAuthService {

    private static final int MAX_FAIL_COUNT = 5;
    private static final long LOCK_MINUTES = 30;
    private static final String LOCK_KEY_PREFIX = "neusoft:auth:login_lock:";
    private static final String FAIL_COUNT_KEY_PREFIX = "neusoft:auth:login_fail:";

    private final LocalUserMapper localUserMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public LoginResponse login(LocalLoginRequest request) {
        String username = request.getUsername().trim();

        // 1. 检查是否处于锁定状态
        long lockedSeconds = checkLocked(username);
        if (lockedSeconds > 0) {
            log.warn("账户已锁定: username={}, 剩余 {} 秒", username, lockedSeconds);
            throw BizException.unauthorized(
                    "账户已锁定，请 " + (lockedSeconds / 60 + 1) + " 分钟后再试");
        }

        // 2. 查找用户
        LocalUser user = localUserMapper.selectOne(
                new LambdaQueryWrapper<LocalUser>().eq(LocalUser::getUsername, username));
        if (user == null) {
            // 用户不存在也要记录失败（防枚举攻击）
            recordFail(username);
            throw BizException.unauthorized("用户名或密码错误");
        }

        // 3. 检查账户状态
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw BizException.unauthorized("账户已被管理员禁用，请联系管理员");
        }

        // 4. 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            recordFail(username);
            throw BizException.unauthorized("用户名或密码错误");
        }

        // 5. 登录成功 — 重置失败计数
        clearFailCount(username);
        user.setLoginFailCount(0);
        user.setLockedUntil(null);
        user.setLastLoginTime(LocalDateTime.now());
        localUserMapper.updateById(user);

        // 6. 生成 Token（复用现有 TokenService）
        String displayName = resolveDisplayName(user);
        String token = tokenService.createToken(user.getRole(), user.getUserNo(), displayName);

        log.info("本地登录成功: username={}, role={}, userNo={}",
                username, user.getRole(), user.getUserNo());

        return LoginResponse.builder()
                .accessToken(token)
                .role(user.getRole())
                .userNo(user.getUserNo())
                .name(displayName)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(LocalRegisterRequest request) {
        String username = request.getUsername().trim();

        // 1. 检查用户名是否已存在
        Long count = localUserMapper.selectCount(
                new LambdaQueryWrapper<LocalUser>().eq(LocalUser::getUsername, username));
        if (count > 0) {
            throw new BizException(400, "用户名已存在");
        }

        // 2. 校验关联用户
        String userNo = request.getUserNo();
        if ("student".equals(request.getRole())) {
            if (userNo == null || userNo.isBlank()) {
                throw new BizException(400, "学生角色必须填写学号");
            }
            Student student = studentMapper.selectById(userNo);
            if (student == null) {
                throw new BizException(400, "学号 " + userNo + " 不存在，请先通过 CAS 登录一次");
            }
        } else if ("teacher".equals(request.getRole())) {
            if (userNo == null || userNo.isBlank()) {
                throw new BizException(400, "教师角色必须填写工号");
            }
            Teacher teacher = teacherMapper.selectById(userNo);
            if (teacher == null) {
                throw new BizException(400, "工号 " + userNo + " 不存在，请先通过 CAS 登录一次");
            }
        }
        // admin 角色不需要关联用户编号

        // 3. 创建本地用户
        LocalUser user = new LocalUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setUserNo(userNo);
        user.setStatus(0);
        user.setLoginFailCount(0);
        user.setCreateTime(LocalDateTime.now());
        localUserMapper.insert(user);

        log.info("本地用户注册成功: username={}, role={}, userNo={}", username, user.getRole(), userNo);
    }

    @Override
    public void changePassword(String username, ChangePasswordRequest request) {
        LocalUser user = localUserMapper.selectOne(
                new LambdaQueryWrapper<LocalUser>().eq(LocalUser::getUsername, username));
        if (user == null) {
            throw BizException.unauthorized("用户不存在");
        }

        // 验证原密码
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BizException(400, "原密码错误");
        }

        // 更新密码
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdateTime(LocalDateTime.now());
        localUserMapper.updateById(user);

        log.info("用户修改密码成功: username={}", username);
    }

    @Override
    public long checkLocked(String username) {
        String lockKey = LOCK_KEY_PREFIX + username;
        String ttl = stringRedisTemplate.opsForValue().get(lockKey);
        if (ttl == null) {
            return 0;
        }
        Long remaining = stringRedisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
        return remaining != null && remaining > 0 ? remaining : 0;
    }

    // ==================== 内部方法 ====================

    /**
     * 记录一次登录失败。
     * Redis 中维护失败计数，达到上限后设置锁定 Key。
     */
    private void recordFail(String username) {
        String failKey = FAIL_COUNT_KEY_PREFIX + username;
        Long count = stringRedisTemplate.opsForValue().increment(failKey);
        if (count == null) {
            count = 1L;
        }
        // 首次失败时设置 Key 过期（与锁定窗口一致，防止永久残留）
        if (count == 1) {
            stringRedisTemplate.expire(failKey, LOCK_MINUTES + 1, TimeUnit.MINUTES);
        }

        if (count >= MAX_FAIL_COUNT) {
            String lockKey = LOCK_KEY_PREFIX + username;
            stringRedisTemplate.opsForValue().set(lockKey, "1", LOCK_MINUTES, TimeUnit.MINUTES);
            log.warn("账户连续失败 {} 次，已锁定 {} 分钟: username={}", count, LOCK_MINUTES, username);
        }

        log.debug("登录失败记录: username={}, 失败次数={}", username, count);
    }

    /**
     * 登录成功后清除失败计数
     */
    private void clearFailCount(String username) {
        stringRedisTemplate.delete(FAIL_COUNT_KEY_PREFIX + username);
        stringRedisTemplate.delete(LOCK_KEY_PREFIX + username);
    }

    /**
     * 解析用户显示名称
     */
    private String resolveDisplayName(LocalUser user) {
        if ("student".equals(user.getRole()) && user.getUserNo() != null) {
            Student student = studentMapper.selectById(user.getUserNo());
            return student != null ? student.getName() : user.getUsername();
        } else if ("teacher".equals(user.getRole()) && user.getUserNo() != null) {
            Teacher teacher = teacherMapper.selectById(user.getUserNo());
            return teacher != null ? teacher.getTeacherName() : user.getUsername();
        }
        return user.getUsername();
    }
}
