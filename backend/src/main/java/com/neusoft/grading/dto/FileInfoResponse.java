package com.neusoft.grading.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 文件元信息响应体
 *
 * 前端通过 /api/file/info/{file_id} 获取文件的基本属性，
 * 用于展示文件名、大小、上传时间等。
 */
@Data
@Builder
public class FileInfoResponse {

    /** 文件在 MinIO 中的混淆路径（即 file_id） */
    private String objectName;

    /** 原始文件名（如 作业.zip） */
    private String originalName;

    /** 文件大小（字节） */
    private long size;

    /** 文件类型标识：code_package / report */
    private String type;
}
