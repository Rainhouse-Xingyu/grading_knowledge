package com.neusoft.grading.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 本地注册请求 DTO（管理员操作）
 */
@Data
public class LocalRegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 32, message = "用户名长度 4-32 个字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度 8-64 个字符")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
             message = "密码必须包含大小写字母和数字")
    private String password;

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "^(student|teacher|admin)$", message = "角色只能是 student/teacher/admin")
    private String role;

    /**
     * 关联用户编号：
     * - role=student 时填写学号（需已存在于 t_student）
     * - role=teacher 时填写工号（需已存在于 t_teacher）
     * - role=admin 时可为空
     */
    private String userNo;
}
