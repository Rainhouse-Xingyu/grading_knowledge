package com.neusoft.grading.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserInfoResponse {

    // ========== 通用字段 ==========
    /** 角色：student / teacher */
    private String role;

    /** 用户编号：学号或工号 */
    private String userNo;

    /** 姓名 */
    private String name;

    // ========== 学生专属字段（对应 t_student 表） ==========
    /** 性别 */
    private String gender;

    /** 院(系)/部代码 */
    private String deptCode;

    /** 院(系)/部名称 */
    private String deptName;

    /** 专业代码 */
    private String majorCode;

    /** 专业名称 */
    private String majorName;

    /** 班级代码 */
    private String classCode;

    /** 班级名称 */
    private String className;

    // ========== 教师专属字段（对应 t_teacher 表） ==========
    /** 职称代码 */
    private String titleCode;

    /** 职称名称 */
    private String titleName;
}
