package com.neusoft.grading.service;

import com.neusoft.grading.dto.FileInfoResponse;
import com.neusoft.grading.dto.FileUploadRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 文件上传与管理服务接口
 *
 * 职责：
 * 1. 接收学生上传的 .zip 代码包 + 图文报告，UUID 混淆路径后写入 MinIO
 * 2. 将文件路径映射到 t_submission_stage 表
 * 3. 校验权限后从 MinIO 流式下载文件
 * 4. 返回文件元信息
 *
 * 分布式锁保障幂等：
 *   Key: neusoft:lock:student_upload:{student_no}:{course_id}:{stage_num}
 *   策略: tryLock(wait=3s, lease=10s)
 */
public interface FileService {

    /**
     * 学生上传文件
     *
     * @param studentNo 当前登录学生的学号
     * @param request   包含 courseId、stageNum、codePackage、report
     * @return 提交记录 submissionId
     */
    Long upload(String studentNo, FileUploadRequest request);

    /**
     * 从 MinIO 流式下载文件
     *
     * @param objectName 文件在 MinIO 中的混淆路径（UUID 路径）
     * @param response   HttpServletResponse，用于写入文件流
     */
    void download(String objectName, HttpServletResponse response);

    /**
     * 获取文件元信息
     *
     * @param objectName 文件在 MinIO 中的混淆路径（UUID 路径）
     * @return 文件名、大小、类型等
     */
    FileInfoResponse getFileInfo(String objectName);
}
