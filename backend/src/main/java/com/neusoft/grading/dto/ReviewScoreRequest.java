package com.neusoft.grading.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 教师修改分数请求体
 *
 * 教师输入最终评定分数，覆盖 AI 严格分。
 * 直接覆盖 t_submission_stage.teacher_score 字段。
 */
@Data
public class ReviewScoreRequest {

    /** 学号 */
    private String studentNo;

    /** 课程项目 ID */
    private String courseId;

    /** 阶段编号：1 / 2 / 3 */
    private Integer stageNum;

    /** 教师最终评定分数（0-100） */
    private BigDecimal teacherScore;
}
