package com.neusoft.grading.service.impl;

import com.neusoft.grading.common.BizException;
import com.neusoft.grading.dto.LoginResponse;
import com.neusoft.grading.dto.UserInfoResponse;
import com.neusoft.grading.entity.Student;
import com.neusoft.grading.entity.Teacher;
import com.neusoft.grading.mapper.StudentMapper;
import com.neusoft.grading.mapper.TeacherMapper;
import com.neusoft.grading.service.AuthService;
import com.neusoft.grading.service.CasService;
import com.neusoft.grading.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final CasService casService;
    private final TokenService tokenService;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;

    @Override
    public LoginResponse handleCasCallback(String ticket, String service) {
        // 1. 尝试验证为学生
        Student student = casService.validateAndExtractStudent(ticket, service);
        if (student != null) {
            // 存库：首次 INSERT，后续 UPDATE
            Student existing = studentMapper.selectById(student.getStudentNo());
            if (existing == null) {
                studentMapper.insert(student);
                log.info("CAS 学生首次登录，已入库: {}", student.getStudentNo());
            } else {
                studentMapper.updateById(student);
                log.info("CAS 学生再次登录，已更新: {}", student.getStudentNo());
            }

            // 生成 Token，Redis 会话中携带完整用户信息
            String token = tokenService.createToken("student", student.getStudentNo(), student.getName());
            return LoginResponse.builder()
                    .accessToken(token)
                    .role("student")
                    .userNo(student.getStudentNo())
                    .name(student.getName())
                    .build();
        }

        // 2. 尝试验证为教师
        Teacher teacher = casService.validateAndExtractTeacher(ticket, service);
        if (teacher != null) {
            Teacher existing = teacherMapper.selectById(teacher.getTeacherNo());
            if (existing == null) {
                teacherMapper.insert(teacher);
                log.info("CAS 教师首次登录，已入库: {}", teacher.getTeacherNo());
            } else {
                teacherMapper.updateById(teacher);
                log.info("CAS 教师再次登录，已更新: {}", teacher.getTeacherNo());
            }

            String token = tokenService.createToken("teacher", teacher.getTeacherNo(), teacher.getTeacherName());
            return LoginResponse.builder()
                    .accessToken(token)
                    .role("teacher")
                    .userNo(teacher.getTeacherNo())
                    .name(teacher.getTeacherName())
                    .build();
        }

        // 3. ticket 无效或无法识别角色
        log.warn("CAS ticket 验证失败: {}", ticket);
        throw BizException.unauthorized("CAS ticket 无效，登录失败");
    }

    @Override
    public String logout(String token) {
        if (token == null || token.isBlank()) {
            throw BizException.unauthorized("未登录");
        }
        tokenService.removeToken(token);
        return casService.getCasLogoutUrl();
    }

    @Override
    public UserInfoResponse getUserInfo(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        UserInfoResponse base = tokenService.getUserInfo(token);
        if (base == null) {
            return null;
        }

        // 补充数据库中的完整用户信息（覆盖 Redis 中可能不完整的快照）
        if ("student".equals(base.getRole())) {
            Student student = studentMapper.selectById(base.getUserNo());
            if (student != null) {
                base.setName(student.getName());
                base.setGender(student.getGender());
                base.setDeptCode(student.getDeptCode());
                base.setDeptName(student.getDeptName());
                base.setMajorCode(student.getMajorCode());
                base.setMajorName(student.getMajorName());
                base.setClassCode(student.getClassCode());
                base.setClassName(student.getClassName());
            }
        } else if ("teacher".equals(base.getRole())) {
            Teacher teacher = teacherMapper.selectById(base.getUserNo());
            if (teacher != null) {
                base.setName(teacher.getTeacherName());
                base.setDeptCode(teacher.getDeptCode());
                base.setDeptName(teacher.getDeptName());
                base.setTitleCode(teacher.getTitleCode());
                base.setTitleName(teacher.getTitleName());
            }
        }

        return base;
    }
}
