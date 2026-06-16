package com.neusoft.grading.dto;

import lombok.Data;

/**
 * 教师修改评语请求体
 *
 * 教师在 v-md-editor 中在线编辑 Markdown 评语后保存。
 * 直接覆盖 t_submission_stage.final_report_markdown 字段。
 */
@Data
public class ReviewCommentRequest {

    /** 学号 */
    private String studentNo;

    /** 课程项目 ID */
    private String courseId;

    /** 阶段编号：1 / 2 / 3 */
    private Integer stageNum;

    /** 教师修改后的 Markdown 评语（完整覆盖） */
    private String finalReportMarkdown;
}
