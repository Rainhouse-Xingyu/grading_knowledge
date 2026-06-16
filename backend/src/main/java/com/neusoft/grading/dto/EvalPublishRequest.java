package com.neusoft.grading.dto;

import lombok.Data;

/**
 * 评审结果发布请求体
 *
 * 教师确认评语和分数后点击"一键下发"时使用。
 */
@Data
public class EvalPublishRequest {

    /** 学号 */
    private String studentNo;

    /** 课程项目 ID */
    private String courseId;

    /** 阶段编号 */
    private Integer stageNum;
}
