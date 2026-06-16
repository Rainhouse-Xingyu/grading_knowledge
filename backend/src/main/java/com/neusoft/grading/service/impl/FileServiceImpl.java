package com.neusoft.grading.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.neusoft.grading.common.BizException;
import com.neusoft.grading.config.MinioConfig;
import com.neusoft.grading.dto.FileInfoResponse;
import com.neusoft.grading.dto.FileUploadRequest;
import com.neusoft.grading.entity.SubmissionStage;
import com.neusoft.grading.service.FileService;
import com.neusoft.grading.service.SubmissionStageService;
import io.minio.*;
import io.minio.http.Method;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 文件上传与管理服务实现
 *
 * 核心流程：
 * 1. 获取 Redisson 分布式锁（防学生网络重试导致重复上传）
 * 2. 检查该学生该课程该阶段是否已提交（幂等校验）
 * 3. 为文件生成 UUID 混淆物理路径，写入 MinIO
 * 4. 在 t_submission_stage 表创建记录，状态置为 0（已提交待评测）
 * 5. 释放锁
 *
 * 文件存储路径格式：{course_id}/{student_no}/{stage}/{uuid}.原文件后缀
 * 所有文件访问均通过后端流式中转，向前端隐藏 MinIO 真实地址。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final MinioConfig minioConfig;
    private final MinioClient minioClient;
    private final RedissonClient redissonClient;
    private final SubmissionStageService submissionStageService;

    /** 代码压缩包允许的最大大小：100MB */
    private static final long MAX_CODE_PACKAGE_SIZE = 100L * 1024 * 1024;

    // ==================== 文件上传 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long upload(String studentNo, FileUploadRequest request) {
        // 参数校验
        validateUploadRequest(request);

        String courseId = request.getCourseId();
        int stageNum = request.getStageNum();

        // 分布式锁 Key：防学生网络重试导致 MinIO 重复文件 + MySQL 重复记录
        // neusoft:lock:student_upload:{student_no}:{course_id}:{stage_num}
        String lockKey = "neusoft:lock:student_upload:" + studentNo + ":" + courseId + ":" + stageNum;
        RLock lock = redissonClient.getLock(lockKey);

        boolean locked;
        try {
            // 尝试获取锁：等待 3 秒，自动释放 10 秒
            locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("系统繁忙，请稍后重试");
        }

        if (!locked) {
            throw new BizException("提交过于频繁，请稍后重试");
        }

        try {
            // 幂等校验：若该学生该课程该阶段已有记录且状态为 0，视为重复提交
            SubmissionStage existing = submissionStageService.lambdaQuery()
                    .eq(SubmissionStage::getStudentNo, studentNo)
                    .eq(SubmissionStage::getCourseId, courseId)
                    .eq(SubmissionStage::getStageNum, stageNum)
                    .one();
            if (existing != null && existing.getStatus() == 0) {
                log.info("学生 {} 课程 {} 阶段 {} 已提交，返回已有记录", studentNo, courseId, stageNum);
                return existing.getSubmissionId();
            }

            // 上传代码压缩包到 MinIO
            String codePackagePath = uploadToMinio(
                    request.getCodePackage(), courseId, studentNo, stageNum);

            // 上传图文报告到 MinIO
            String reportPath = uploadToMinio(
                    request.getReport(), courseId, studentNo, stageNum);

            // 构建提交记录
            SubmissionStage stage = new SubmissionStage();
            stage.setStudentNo(studentNo);
            stage.setCourseId(courseId);
            stage.setStageNum(stageNum);
            stage.setCodePackagePath(codePackagePath);
            stage.setReportPath(reportPath);
            stage.setIsJointReview(0);
            stage.setModelUsed("DeepSeek-R1");
            stage.setStatus(0); // 0-已提交待评测
            stage.setSubmitTime(LocalDateTime.now());

            // 保存到数据库
            if (existing != null) {
                // 已有记录但状态不为 0（可能是重新提交覆盖），更新文件路径
                stage.setSubmissionId(existing.getSubmissionId());
                submissionStageService.updateById(stage);
                log.info("学生 {} 重新提交，已更新文件路径: submissionId={}", studentNo, existing.getSubmissionId());
            } else {
                submissionStageService.save(stage);
                log.info("学生 {} 首次提交: courseId={}, stageNum={}, submissionId={}",
                        studentNo, courseId, stageNum, stage.getSubmissionId());
            }

            return stage.getSubmissionId();

        } finally {
            // 释放分布式锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ==================== 文件下载 ====================

    @Override
    public void download(String objectName, HttpServletResponse response) {
        // 从 MinIO 获取文件流并写入 HttpServletResponse
        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(objectName)
                        .build())) {

            // 获取文件信息以设置 Content-Length
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .build());

            // 从 objectName 中提取原始文件名（UUID 后面的部分）
            String fileName = extractOriginalName(objectName);
            String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

            // 设置响应头：强制下载
            response.setContentType("application/octet-stream");
            response.setContentLengthLong(stat.size());
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedName);

            // 流式写入响应
            try (OutputStream out = response.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
            }

            log.debug("文件下载完成: objectName={}, size={}", objectName, stat.size());

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件下载失败: objectName={}", objectName, e);
            throw new BizException("文件下载失败，请联系管理员");
        }
    }

    // ==================== 文件元信息 ====================

    @Override
    public FileInfoResponse getFileInfo(String objectName) {
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .build());

            return FileInfoResponse.builder()
                    .objectName(objectName)
                    .originalName(extractOriginalName(objectName))
                    .size(stat.size())
                    .type(objectName.contains("/code/") ? "code_package" : "report")
                    .build();

        } catch (Exception e) {
            log.error("获取文件元信息失败: objectName={}", objectName, e);
            throw new BizException("文件不存在或已被删除");
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 将 MultipartFile 上传到 MinIO，返回混淆后的 objectName
     *
     * 存储路径格式：{courseId}/{studentNo}/{stage}/code/{uuid}.zip
     *                  或 {courseId}/{studentNo}/{stage}/report/{uuid}.原后缀
     */
    private String uploadToMinio(MultipartFile file, String courseId,
                                  String studentNo, int stageNum) {
        String originalName = file.getOriginalFilename();
        String extension = "";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf("."));
        }

        // 判断文件类型：code 或 report
        String subDir = isCodePackage(originalName) ? "code" : "report";

        // 生成 UUID 混淆路径
        String objectName = courseId + "/" + studentNo + "/" + stageNum
                + "/" + subDir + "/" + UUID.randomUUID().toString().replace("-", "") + extension;

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());

            log.debug("文件已上传到 MinIO: objectName={}, originalName={}, size={}",
                    objectName, originalName, file.getSize());
        } catch (Exception e) {
            log.error("MinIO 上传失败: objectName={}", objectName, e);
            throw new BizException("文件上传失败，请重试");
        }

        return objectName;
    }

    /**
     * 校验上传请求参数
     */
    private void validateUploadRequest(FileUploadRequest request) {
        if (request.getCourseId() == null || request.getCourseId().isBlank()) {
            throw new BizException("课程 ID 不能为空");
        }
        if (request.getStageNum() == null || request.getStageNum() < 1 || request.getStageNum() > 3) {
            throw new BizException("阶段编号必须为 1、2 或 3");
        }
        if (request.getCodePackage() == null || request.getCodePackage().isEmpty()) {
            throw new BizException("代码压缩包不能为空");
        }
        if (request.getReport() == null || request.getReport().isEmpty()) {
            throw new BizException("图文报告不能为空");
        }

        // 校验代码包格式（仅 .zip）
        String codeName = request.getCodePackage().getOriginalFilename();
        if (codeName == null || !codeName.toLowerCase().endsWith(".zip")) {
            throw new BizException("代码压缩包仅支持 .zip 格式");
        }
        // 校验代码包大小
        if (request.getCodePackage().getSize() > MAX_CODE_PACKAGE_SIZE) {
            throw new BizException("代码压缩包不能超过 100MB");
        }

        // 校验报告格式（.docx / .pdf）
        String reportName = request.getReport().getOriginalFilename();
        if (reportName == null) {
            throw new BizException("报告文件名不能为空");
        }
        String lower = reportName.toLowerCase();
        if (!lower.endsWith(".docx") && !lower.endsWith(".pdf")) {
            throw new BizException("图文报告仅支持 .docx / .pdf 格式");
        }
    }

    /**
     * 判断文件是否为代码压缩包
     */
    private boolean isCodePackage(String originalName) {
        return originalName != null && originalName.toLowerCase().endsWith(".zip");
    }

    /**
     * 从 objectName 中提取原始文件名（取最后一段 UUID 后的后缀）
     * 例如 a/b/c/code/abc123.zip → abc123.zip
     */
    private String extractOriginalName(String objectName) {
        if (objectName == null || !objectName.contains("/")) {
            return objectName;
        }
        return objectName.substring(objectName.lastIndexOf("/") + 1);
    }
}
