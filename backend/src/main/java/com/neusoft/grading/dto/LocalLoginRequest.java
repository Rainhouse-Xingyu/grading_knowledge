package com.neusoft.grading.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 本地登录请求 DTO
 */
@Data
public class LocalLoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
