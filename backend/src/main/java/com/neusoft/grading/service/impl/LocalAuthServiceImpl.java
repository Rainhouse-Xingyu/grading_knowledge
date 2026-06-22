package com.neusoft.grading.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.neusoft.grading.common.BizException;
import com.neusoft.grading.dto.*;
import com.neusoft.grading.entity.LocalUser;
import com.neusoft.grading.entity.Student;
import com.neusoft.grading.entity.StudentCourse;
import com.neusoft.grading.entity.Teacher;
import com.neusoft.grading.mapper.LocalUserMapper;
import com.neusoft.grading.mapper.StudentCourseMapper;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalAuthServiceImpl implements LocalAuthService {

    private static final int MAX_FAIL_COUNT = 5;
    private static final long LOCK_MINUTES = 30;
    private static final String LOCK_KEY_PREFIX = "neusoft:auth:login_lock:";
    private static final String FAIL_COUNT_KEY_PREFIX = "neusoft:auth:login_fail:";

    /** Excel 表头列索引 */
    private static final int COL_STUDENT_NO   = 0;
    private static final int COL_NAME         = 1;
    private static final int COL_GENDER       = 2;
    private static final int COL_DEPT_CODE    = 3;
    private static final int COL_DEPT_NAME    = 4;
    private static final int COL_MAJOR_CODE   = 5;
    private static final int COL_MAJOR_NAME   = 6;
    private static final int COL_CLASS_CODE   = 7;
    private static final int COL_CLASS_NAME   = 8;
    private static final int COL_PASSWORD     = 9;

    private final LocalUserMapper localUserMapper;
    private final StudentMapper studentMapper;
    private final StudentCourseMapper studentCourseMapper;
    private final TeacherMapper teacherMapper;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public LoginResponse login(LocalLoginRequest request) {
        String username = request.getUsername().trim();

        long lockedSeconds = checkLocked(username);
        if (lockedSeconds > 0) {
            log.warn("账户已锁定: username={}, 剩余 {} 秒", username, lockedSeconds);
            throw BizException.unauthorized("账户已锁定，请 " + (lockedSeconds / 60 + 1) + " 分钟后再试");
        }

        // 支持两种登录方式：
        // 1. 直接用 username 查找 t_local_user
        // 2. 若 username 是学号，通过 user_no 查找
        LocalUser user = localUserMapper.selectOne(
                new LambdaQueryWrapper<LocalUser>().eq(LocalUser::getUsername, username));
        if (user == null) {
            user = localUserMapper.selectOne(
                    new LambdaQueryWrapper<LocalUser>()
                            .eq(LocalUser::getUserNo, username)
                            .eq(LocalUser::getRole, "student"));
        }

        if (user == null) {
            recordFail(username);
            throw BizException.unauthorized("用户名或密码错误");
        }

        if (user.getStatus() != null && user.getStatus() == 1) {
            throw BizException.unauthorized("账户已被管理员禁用，请联系管理员");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            recordFail(username);
            throw BizException.unauthorized("用户名或密码错误");
        }

        clearFailCount(username);
        user.setLoginFailCount(0);
        user.setLockedUntil(null);
        user.setLastLoginTime(LocalDateTime.now());
        localUserMapper.updateById(user);

        String displayName = resolveDisplayName(user);
        String token = tokenService.createToken(user.getRole(), user.getUserNo(), displayName);

        log.info("本地登录成功: username={}, role={}, userNo={}",
                user.getUsername(), user.getRole(), user.getUserNo());

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

        Long count = localUserMapper.selectCount(
                new LambdaQueryWrapper<LocalUser>().eq(LocalUser::getUsername, username));
        if (count > 0) {
            throw new BizException(400, "用户名已存在");
        }

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

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BizException(400, "原密码错误");
        }

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

    @Override
    public String resolveUsername(String userNo, String role) {
        if (userNo == null) {
            return null;
        }
        LocalUser user = localUserMapper.selectOne(
                new LambdaQueryWrapper<LocalUser>()
                        .eq(LocalUser::getUserNo, userNo)
                        .eq(LocalUser::getRole, role));
        return user != null ? user.getUsername() : userNo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchImportResult batchImportStudents(MultipartFile file, String defaultPassword, String courseId) {
        List<BatchImportResult.FailRow> failRows = new ArrayList<>();
        int totalRows = 0;
        int successCount = 0;

        try (InputStream in = file.getInputStream()) {
            ExcelReader reader = ExcelUtil.getReader(in);
            List<List<Object>> rows = reader.read();

            if (rows.size() < 2) {
                throw new BizException(400, "Excel 文件为空或只有表头，无数据行");
            }

            // 跳过表头行（第 0 行）
            for (int i = 1; i < rows.size(); i++) {
                totalRows++;
                List<Object> row = rows.get(i);
                int rowNum = i + 1; // Excel 行号

                try {
                    String studentNo = getCellString(row, COL_STUDENT_NO);
                    String name = getCellString(row, COL_NAME);
                    String password = getCellString(row, COL_PASSWORD);

                    if (StrUtil.isBlank(studentNo)) {
                        failRows.add(BatchImportResult.FailRow.builder()
                                .rowNum(rowNum).studentNo("").reason("学号为空").build());
                        continue;
                    }
                    if (StrUtil.isBlank(name)) {
                        failRows.add(BatchImportResult.FailRow.builder()
                                .rowNum(rowNum).studentNo(studentNo).reason("姓名为空").build());
                        continue;
                    }

                    // 使用默认密码
                    if (StrUtil.isBlank(password)) {
                        password = defaultPassword;
                    }
                    if (StrUtil.isBlank(password)) {
                        failRows.add(BatchImportResult.FailRow.builder()
                                .rowNum(rowNum).studentNo(studentNo).reason("密码为空且未设置默认密码")
                                .build());
                        continue;
                    }

                    // --- 1. 写入/更新 t_student ---
                    Student student = studentMapper.selectById(studentNo);
                    boolean isNewStudent = (student == null);
                    if (isNewStudent) {
                        student = new Student();
                        student.setStudentNo(studentNo);
                        student.setName(name);
                        student.setGender(getCellString(row, COL_GENDER));
                        student.setDeptCode(getCellString(row, COL_DEPT_CODE));
                        student.setDeptName(getCellString(row, COL_DEPT_NAME));
                        student.setMajorCode(getCellString(row, COL_MAJOR_CODE));
                        student.setMajorName(getCellString(row, COL_MAJOR_NAME));
                        student.setClassCode(getCellString(row, COL_CLASS_CODE));
                        student.setClassName(getCellString(row, COL_CLASS_NAME));
                        student.setCreateTime(LocalDateTime.now());
                        studentMapper.insert(student);
                    } else {
                        // 已有记录，只更新姓名等关键信息
                        student.setName(name);
                        if (StrUtil.isNotBlank(getCellString(row, COL_GENDER)))
                            student.setGender(getCellString(row, COL_GENDER));
                        if (StrUtil.isNotBlank(getCellString(row, COL_DEPT_CODE)))
                            student.setDeptCode(getCellString(row, COL_DEPT_CODE));
                        if (StrUtil.isNotBlank(getCellString(row, COL_DEPT_NAME)))
                            student.setDeptName(getCellString(row, COL_DEPT_NAME));
                        if (StrUtil.isNotBlank(getCellString(row, COL_MAJOR_CODE)))
                            student.setMajorCode(getCellString(row, COL_MAJOR_CODE));
                        if (StrUtil.isNotBlank(getCellString(row, COL_MAJOR_NAME)))
                            student.setMajorName(getCellString(row, COL_MAJOR_NAME));
                        if (StrUtil.isNotBlank(getCellString(row, COL_CLASS_CODE)))
                            student.setClassCode(getCellString(row, COL_CLASS_CODE));
                        if (StrUtil.isNotBlank(getCellString(row, COL_CLASS_NAME)))
                            student.setClassName(getCellString(row, COL_CLASS_NAME));
                        studentMapper.updateById(student);
                    }

                    // --- 2. 创建/更新 t_local_user（以学号 studentNo 为 username） ---
                    LocalUser localUser = localUserMapper.selectOne(
                            new LambdaQueryWrapper<LocalUser>().eq(LocalUser::getUsername, studentNo));
                    if (localUser == null) {
                        localUser = new LocalUser();
                        localUser.setUsername(studentNo); // 学号即用户名
                        localUser.setPasswordHash(passwordEncoder.encode(password));
                        localUser.setRole("student");
                        localUser.setUserNo(studentNo);
                        localUser.setStatus(0);
                        localUser.setLoginFailCount(0);
                        localUser.setCreateTime(LocalDateTime.now());
                        localUserMapper.insert(localUser);
                    } else {
                        // 已存在账号：仅更新密码（如果传入新密码且非默认值，说明是该行指定密码）
                        if (StrUtil.isNotBlank(getCellString(row, COL_PASSWORD))) {
                            localUser.setPasswordHash(passwordEncoder.encode(password));
                        } else if (StrUtil.isNotBlank(defaultPassword)) {
                            // 使用默认密码更新
                            localUser.setPasswordHash(passwordEncoder.encode(password));
                        }
                        localUser.setUserNo(studentNo); // 确保关联正确
                        localUser.setUpdateTime(LocalDateTime.now());
                        localUserMapper.updateById(localUser);
                    }

                    // --- 3. 选课绑定（如果指定了 courseId） ---
                    if (StrUtil.isNotBlank(courseId)) {
                        Long enrollCount = studentCourseMapper.selectCount(
                                new LambdaQueryWrapper<StudentCourse>()
                                        .eq(StudentCourse::getStudentNo, studentNo)
                                        .eq(StudentCourse::getCourseId, courseId));
                        if (enrollCount == 0) {
                            StudentCourse sc = new StudentCourse();
                            sc.setStudentNo(studentNo);
                            sc.setCourseId(courseId);
                            studentCourseMapper.insert(sc);
                        }
                    }

                    successCount++;

                } catch (Exception e) {
                    log.warn("导入第 {} 行失败: {}", rowNum, e.getMessage());
                    failRows.add(BatchImportResult.FailRow.builder()
                            .rowNum(rowNum)
                            .studentNo(getCellString(row, COL_STUDENT_NO))
                            .reason("解析异常: " + e.getMessage())
                            .build());
                }
            }

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Excel 文件读取失败", e);
            throw new BizException(400, "Excel 文件解析失败: " + e.getMessage());
        }

        log.info("批量导入完成: total={}, success={}, fail={}", totalRows, successCount, failRows.size());
        return BatchImportResult.builder()
                .totalRows(totalRows)
                .successCount(successCount)
                .failRows(failRows)
                .build();
    }

    // ==================== 内部方法 ====================

    private void recordFail(String username) {
        String failKey = FAIL_COUNT_KEY_PREFIX + username;
        Long count = stringRedisTemplate.opsForValue().increment(failKey);
        if (count == null) count = 1L;
        if (count == 1) {
            stringRedisTemplate.expire(failKey, LOCK_MINUTES + 1, TimeUnit.MINUTES);
        }
        if (count >= MAX_FAIL_COUNT) {
            String lockKey = LOCK_KEY_PREFIX + username;
            stringRedisTemplate.opsForValue().set(lockKey, "1", LOCK_MINUTES, TimeUnit.MINUTES);
            log.warn("账户连续失败 {} 次，已锁定 {} 分钟: username={}", count, LOCK_MINUTES, username);
        }
    }

    private void clearFailCount(String username) {
        stringRedisTemplate.delete(FAIL_COUNT_KEY_PREFIX + username);
        stringRedisTemplate.delete(LOCK_KEY_PREFIX + username);
    }

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

    /** 安全获取单元格字符串值 */
    private String getCellString(List<Object> row, int index) {
        if (row == null || index >= row.size()) return null;
        Object val = row.get(index);
        if (val == null) return null;
        String s = val.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
