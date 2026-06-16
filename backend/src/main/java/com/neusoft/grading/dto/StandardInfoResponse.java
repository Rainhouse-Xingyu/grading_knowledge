package com.neusoft.grading.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * 评分标准信息响应
 *
 * 查询课程下已上传的评分标准文档信息。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StandardInfoResponse {

    /** 课程项目 ID */
    private String courseId;

    /** 评分标准文档在 MinIO 中的路径 */
    private String standardDocUrl;

    /** 已入库的评分标准切片数（Milvus 中的 chunk 数量） */
    private Integer chunkCount;

    /** 上传教师工号 */
    private String teacherNo;
}
