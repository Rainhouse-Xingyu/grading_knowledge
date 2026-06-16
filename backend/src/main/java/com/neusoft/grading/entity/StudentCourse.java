package com.neusoft.grading.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生选课与期末总分结算表
 */
@Data
@TableName("t_student_course")
public class StudentCourse {

    /** 自增主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 学号 */
    private String studentNo;

    /** 课程项目ID */
    private String courseId;

    /** 期末总分（加权计算或教师覆盖） */
    private BigDecimal finalScore;

    /** 教师期末总评语 */
    private String teacherFinalComment;

    /** 期末总分状态: 0-未生成, 1-已下发学生可见 */
    private Integer gradeStatus;

    /** 最后修改时间 */
    private LocalDateTime updateTime;
}
