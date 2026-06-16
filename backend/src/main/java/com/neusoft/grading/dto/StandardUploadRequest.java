package com.neusoft.grading.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 评分标准文档上传请求体
 *
 * 教师上传评分标准文档，后端切片 + Embedding 写入 Milvus。
 * 支持格式：.docx / .pdf / .txt
 */
@Data
public class StandardUploadRequest {

    /** 课程项目 ID */
    private String courseId;

    /** 适用阶段：0-通用 / 1-阶段一 / 2-阶段二 / 3-阶段三 */
    private Integer stage;

    /** 评分标准文档文件 */
    private MultipartFile file;
}
