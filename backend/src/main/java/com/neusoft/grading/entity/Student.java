package com.neusoft.grading.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生信息表（CAS统一身份认证缓存）
 */
@Data
@TableName("t_student")
public class Student {

    /** 学号 */
    @TableId(value = "student_no", type = IdType.INPUT)
    private String studentNo;

    /** 姓名 */
    private String name;

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

    /** 首次通过CAS登录录入时间 */
    private LocalDateTime createTime;
}
