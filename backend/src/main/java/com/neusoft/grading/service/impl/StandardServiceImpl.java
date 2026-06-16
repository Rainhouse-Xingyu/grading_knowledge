package com.neusoft.grading.service.impl;

import com.neusoft.grading.common.BizException;
import com.neusoft.grading.config.AiServiceConfig;
import com.neusoft.grading.config.MinioConfig;
import com.neusoft.grading.dto.StandardInfoResponse;
import com.neusoft.grading.dto.StandardUploadRequest;
import com.neusoft.grading.entity.CourseProject;
import com.neusoft.grading.service.CourseProjectService;
import com.neusoft.grading.service.StandardService;
import io.minio.PutObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 评分标准管理服务实现
 *
 * 处理流程：
 * 1. 校验文件格式（.docx / .pdf / .txt）
 * 2. 上传文件到 MinIO，UUID 混淆路径
 * 3. 更新 t_course_project.standard_doc_url
 * 4. 触发 FastAPI 算法服务进行文档切片 + Embedding 写入 Milvus
 *
 * Milvus 集合设计（grading_standards）：
 * - chunk_id: INT64 主键
 * - vector: FLOAT_VECTOR(1024) bge-large-zh-v1.5 向量
 * - course_id: VARCHAR(32) 标量索引
 * - stage: INT32 标量索引
 * - content: VARCHAR(4000) 原始评分规则文本
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StandardServiceImpl implements StandardService {

    private final CourseProjectService courseProjectService;
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final AiServiceConfig aiServiceConfig;

    // ==================== 上传评分标准 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uploadStandard(String teacherNo, StandardUploadRequest request) {
        // 参数校验
        validateUploadRequest(request);

        String courseId = request.getCourseId();

        // 校验课程存在且属于当前教师
        CourseProject course = courseProjectService.getById(courseId);
        if (course == null) {
            throw new BizException(404, "课程不存在");
        }
        if (!course.getTeacherNo().equals(teacherNo)) {
            throw new BizException(403, "无权操作此课程的评分标准");
        }

        // 上传文件到 MinIO
        String objectName = uploadToMinio(request.getFile(), courseId);

        // 更新课程项目的评分标准文档路径
        course.setStandardDocUrl(objectName);
        courseProjectService.updateById(course);

        // 触发 FastAPI 进行文档切片 + Embedding 写入 Milvus
        triggerFastApiProcessing(courseId, objectName, request.getStage());

        log.info("评分标准上传成功: courseId={}, stage={}, teacherNo={}, path={}",
                courseId, request.getStage(), teacherNo, objectName);
    }

    // ==================== 查询评分标准信息 ====================

    @Override
    public StandardInfoResponse getStandardInfo(String courseId) {
        if (courseId == null || courseId.isBlank()) {
            throw new BizException("课程 ID 不能为空");
        }

        CourseProject course = courseProjectService.getById(courseId);
        if (course == null) {
            throw new BizException(404, "课程不存在");
        }

        // TODO: 从 Milvus 查询实际切片数量（需引入 Milvus Java SDK 或通过 FastAPI 查询）
        int chunkCount = 0;

        return StandardInfoResponse.builder()
                .courseId(courseId)
                .standardDocUrl(course.getStandardDocUrl())
                .chunkCount(chunkCount)
                .teacherNo(course.getTeacherNo())
                .build();
    }

    // ==================== 内部方法 ====================

    private void validateUploadRequest(StandardUploadRequest request) {
        if (request.getCourseId() == null || request.getCourseId().isBlank()) {
            throw new BizException("课程 ID 不能为空");
        }
        if (request.getStage() == null || request.getStage() < 0 || request.getStage() > 3) {
            throw new BizException("阶段编号必须为 0（通用）、1、2 或 3");
        }
        if (request.getFile() == null || request.getFile().isEmpty()) {
            throw new BizException("评分标准文档不能为空");
        }

        String fileName = request.getFile().getOriginalFilename();
        if (fileName == null) {
            throw new BizException("文件名不能为空");
        }
        String lower = fileName.toLowerCase();
        if (!lower.endsWith(".docx") && !lower.endsWith(".pdf") && !lower.endsWith(".txt")) {
            throw new BizException("评分标准文档仅支持 .docx / .pdf / .txt 格式");
        }
    }

    private String uploadToMinio(org.springframework.web.multipart.MultipartFile file, String courseId) {
        String originalName = file.getOriginalFilename();
        String extension = "";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf("."));
        }

        String objectName = "standards/" + courseId + "/" + UUID.randomUUID().toString().replace("-", "") + extension;

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());
        } catch (Exception e) {
            log.error("MinIO 上传评分标准文档失败", e);
            throw new BizException("文件上传失败，请重试");
        }

        return objectName;
    }

    /**
     * 触发 FastAPI 算法服务处理评分标准文档
     *
     * FastAPI 接收文档路径后：
     * 1. 从 MinIO 下载文档
     * 2. 解析文档内容（python-docx / PyPDF2 / 纯文本）
     * 3. 文本切片
     * 4. bge-large-zh-v1.5 Embedding 生成向量
     * 5. 写入 Milvus grading_standards 集合
     *
     * TODO: FastAPI 端实现 /ai/standard/process 接口后启用
     */
    private void triggerFastApiProcessing(String courseId, String objectName, int stage) {
        log.info("触发 FastAPI 处理评分标准: courseId={}, stage={}, path={} (待 FastAPI 实现)", courseId, stage, objectName);
        // TODO: 调用 FastAPI POST /ai/standard/process
        // Map<String, Object> body = new HashMap<>();
        // body.put("course_id", courseId);
        // body.put("stage", stage);
        // body.put("object_name", objectName);
        // restTemplate.postForObject(aiServiceConfig.getBaseUrl() + "/ai/standard/process", body, Map.class);
    }
}
