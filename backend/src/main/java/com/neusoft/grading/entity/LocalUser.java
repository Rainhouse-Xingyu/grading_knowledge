package com.neusoft.grading.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地登录用户表（与 CAS 登录并行的独立认证方式）
 *
 * 设计原则：
 * - 密码使用 BCrypt 加盐哈希存储，永不明文保存
 * - 登录失败计数 + Redis 锁定策略防暴力破解
 * - user_no 逻辑关联 t_student.student_no 或 t_teacher.teacher_no
 */
@Data
@TableName("t_local_user")
public class LocalUser {

    /** 自增主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 登录用户名（全局唯一） */
    private String username;

    /** BCrypt 哈希后的密码（60 字符） */
    private String passwordHash;

    /** 角色：student / teacher / admin */
    private String role;

    /**
     * 关联用户编号：
     * - role=student 时对应 t_student.student_no
     * - role=teacher 时对应 t_teacher.teacher_no
     * - role=admin 时为 null（系统管理员不关联具体人员）
     */
    private String userNo;

    /** 账户状态：0-正常，1-锁定（管理员手动锁定） */
    private Integer status;

    /** 连续登录失败次数（成功登录后重置为 0） */
    private Integer loginFailCount;

    /** 账户锁定截止时间（连续失败 5 次自动锁定 30 分钟） */
    private LocalDateTime lockedUntil;

    /** 首次注册时间 */
    private LocalDateTime createTime;

    /** 最后修改时间 */
    private LocalDateTime updateTime;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;
}
