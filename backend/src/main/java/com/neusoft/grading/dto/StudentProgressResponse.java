package com.neusoft.grading.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生三阶段进度响应
 *
 * 查询学生某课程的三阶段提交与评审状态。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StudentProgressResponse {

    /** 学号 */
    private String studentNo;

    /** 课程项目 ID */
    private String courseId;

    /** 三阶段详情列表 */
    private List<StageDetail> stages;

    /**
     * 单阶段详情
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StageDetail {

        /** 阶段编号 */
        private Integer stageNum;

        /** 业务状态：0-待评测 / 1-评测中 / 2-待发布 / 3-已下发 */
        private Integer status;

        /** AI 严格分数 */
        private BigDecimal aiScore;

        /** 教师最终分数 */
        private BigDecimal teacherScore;

        /** 提交时间 */
        private LocalDateTime submitTime;

        /** AI 评测触发时间 */
        private LocalDateTime evalTriggerTime;

        /** 教师下发时间 */
        private LocalDateTime reviewTime;

        /** 是否联合评审 */
        private Integer isJointReview;
    }
}
