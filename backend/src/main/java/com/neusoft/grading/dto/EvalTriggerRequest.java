package com.neusoft.grading.dto;

import lombok.Data;

/**
 * AI 评测触发请求体
 *
 * 教师触发批量/单人 AI 评测时使用。
 * - studentNo 为空时，对整个课程该阶段所有待评测学生批量触发
 * - studentNo 非空时，仅对该学生触发
 */
@Data
public class EvalTriggerRequest {

    /** 课程项目 ID（必填） */
    private String courseId;

    /** 学号（选填，为空则批量触发全班） */
    private String studentNo;

    /** 阶段编号：1 / 2 / 3（必填） */
    private Integer stageNum;

    /** 是否联合评审：0-不联合 / 1-联合（引入上一轮教师终审评语） */
    private Integer isJointReview;
}
