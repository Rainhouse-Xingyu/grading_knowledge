package com.neusoft.grading.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教师信息表（CAS统一身份认证缓存）
 */
@Data
@TableName("t_teacher")
public class Teacher {

    /** 教师工号 */
    @TableId(value = "teacher_no", type = IdType.INPUT)
    private String teacherNo;

    /** 教师姓名 */
    private String teacherName;

    /** 院(系)/部代码 */
    private String deptCode;

    /** 院(系)/部名称 */
    private String deptName;

    /** 职称代码 */
    private String titleCode;

    /** 职称名称 */
    private String titleName;

    /** 首次通过CAS登录录入时间 */
    private LocalDateTime createTime;
}
