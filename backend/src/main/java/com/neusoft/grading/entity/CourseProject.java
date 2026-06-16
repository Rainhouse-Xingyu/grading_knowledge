package com.neusoft.grading.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课程项目主表
 */
@Data
@TableName("t_course_project")
public class CourseProject {

    /** 课程项目唯一UUID/代码 */
    @TableId(value = "course_id", type = IdType.INPUT)
    private String courseId;

    /** 课程项目名称 */
    private String courseName;

    /** 负责教师工号 */
    private String teacherNo;

    /** 开课学期 */
    private String semester;

    /** 评分标准文档在MinIO中的物理路径映射 */
    private String standardDocUrl;

    /** 阶段1分数权重（默认0.20） */
    private BigDecimal weightStage1;

    /** 阶段2分数权重（默认0.30） */
    private BigDecimal weightStage2;

    /** 阶段3分数权重（默认0.50） */
    private BigDecimal weightStage3;

    /** 课程创建时间 */
    private LocalDateTime createTime;
}
