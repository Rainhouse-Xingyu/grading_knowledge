package com.neusoft.grading.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 评审报告响应体
 *
 * 包含 AI 原始评语和教师修改后的最终版本。
 * status=2 时教师可查看 AI 原始报告和进行微调；
 * status=3 时学生可查看教师最终版报告。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EvalReportResponse {

    private Long submissionId;
    private String studentNo;
    private String courseId;
    private Integer stageNum;

    /** AI 严格初始分数 */
    private BigDecimal aiScore;

    /** AI 原始 Markdown 评审报告 */
    private String aiReportMarkdown;

    /** 教师微调后的最终阶段分数 */
    private BigDecimal teacherScore;

    /** 教师修改后的最终版 Markdown 报告 */
    private String finalReportMarkdown;

    /** 实际使用的模型名称 */
    private String modelUsed;

    /** 业务状态：0-待评测 / 1-评测中 / 2-待发布 / 3-已下发 */
    private Integer status;
}
