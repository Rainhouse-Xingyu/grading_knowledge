package com.neusoft.grading.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 批量导入学生 Excel 请求
 *
 * Excel 模板格式（表头固定）：
 * | 学号 | 姓名 | 性别 | 院系代码 | 院系名称 | 专业代码 | 专业名称 | 班级代码 | 班级名称 | 初始密码 |
 *
 * 第一行为表头行，从第二行开始为数据行。
 */
@Data
public class StudentBatchImportRequest {

    /** 上传的 Excel 文件 (.xlsx) */
    private MultipartFile file;

    /** 统一初始密码（若 Excel 中某行未填密码，则使用此默认值） */
    private String defaultPassword;

    /** 课程项目 ID（自动将该批学生选入指定课程） */
    private String courseId;
}
