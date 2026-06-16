package com.neusoft.grading.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 学生文件上传请求体
 *
 * 前端以 multipart/form-data 方式提交，包含代码压缩包和图文报告两个文件。
 * 同时携带课程 ID 和阶段编号，用于定位 t_submission_stage 记录。
 */
@Data
public class FileUploadRequest {

    /** 课程项目 ID */
    private String courseId;

    /** 阶段编号：1 / 2 / 3 */
    private Integer stageNum;

    /**
     * 代码压缩包文件（仅接受 .zip 格式，最大 100MB）
     * 字段名: codePackage
     */
    private MultipartFile codePackage;

    /**
     * 图文报告文件（接受 .docx / .pdf 格式）
     * 字段名: report
     */
    private MultipartFile report;
}
