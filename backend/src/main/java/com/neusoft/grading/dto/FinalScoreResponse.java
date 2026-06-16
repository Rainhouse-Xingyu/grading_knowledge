package com.neusoft.grading.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 期末总分响应
 *
 * 查询学生某课程的期末总分与总评语。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FinalScoreResponse {

    /** 学号 */
    private String studentNo;

    /** 课程项目 ID */
    private String courseId;

    /** 期末总分（加权计算或教师覆盖） */
    private BigDecimal finalScore;

    /** 教师期末总评语 */
    private String teacherFinalComment;

    /** 总分状态：0-未生成 / 1-已下发 */
    private Integer gradeStatus;
}
