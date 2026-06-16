package com.neusoft.grading.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {

    /** 自定义 Access-Token，前端存入 localStorage */
    private String accessToken;

    /** 用户角色：student / teacher */
    private String role;

    /** 用户姓名 */
    private String name;

    /** 用户编号（学号或工号） */
    private String userNo;
}
