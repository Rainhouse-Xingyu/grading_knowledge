package com.neusoft.grading.service;

import com.neusoft.grading.dto.StandardInfoResponse;
import com.neusoft.grading.dto.StandardUploadRequest;

/**
 * 评分标准管理服务接口
 *
 * 职责：
 * 1. 教师上传评分标准文档，存储到 MinIO 并触发 FastAPI 切片 + Embedding 写入 Milvus
 * 2. 查询课程下已上传的评分标准信息和切片数量
 */
public interface StandardService {

    /**
     * 上传评分标准文档
     *
     * @param teacherNo 当前登录教师工号
     * @param request   包含 courseId、stage、file
     */
    void uploadStandard(String teacherNo, StandardUploadRequest request);

    /**
     * 查询课程的评分标准信息
     *
     * @param courseId 课程项目 ID
     * @return 评分标准文档信息和切片数量
     */
    StandardInfoResponse getStandardInfo(String courseId);
}
