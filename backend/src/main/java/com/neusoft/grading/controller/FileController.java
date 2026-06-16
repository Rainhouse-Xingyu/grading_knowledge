package com.neusoft.grading.controller;

import com.neusoft.grading.common.BizException;
import com.neusoft.grading.common.Result;
import com.neusoft.grading.dto.FileInfoResponse;
import com.neusoft.grading.dto.FileUploadRequest;
import com.neusoft.grading.dto.UserInfoResponse;
import com.neusoft.grading.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 文件上传与管理控制器
 *
 * 对应开发文档 2.2.2 节接口定义：
 * POST /api/file/upload          — 学生上传文件
 * GET  /api/file/download/{path} — 下载文件（流式中转）
 * GET  /api/file/info/{path}     — 获取文件元信息
 *
 * 安全策略：
 * - 所有接口均需 Bearer Token 认证（由 TokenAuthFilter 统一校验）
 * - 下载接口额外校验：仅学生本人、授课教师有权访问对应文件
 */
@Tag(name = "文件上传与管理")
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * 学生上传文件
     *
     * 前端以 multipart/form-data 方式提交：
     * - courseId: 课程项目 ID
     * - stageNum: 阶段编号（1/2/3）
     * - codePackage: 代码压缩包（.zip，最大 100MB）
     * - report: 图文报告（.docx / .pdf）
     *
     * 后端会通过 Redisson 分布式锁防止重复提交，
     * UUID 混淆物理路径后写入 MinIO，文件地址映射到 t_submission_stage 表。
     */
    @Operation(summary = "学生上传文件（代码包 + 图文报告）")
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(
            @ModelAttribute FileUploadRequest request,
            HttpServletRequest httpRequest) {

        // 从 SecurityContext 中获取当前登录学生信息
        UserInfoResponse currentUser = (UserInfoResponse) httpRequest.getAttribute("currentUser");
        if (currentUser == null || !"student".equals(currentUser.getRole())) {
            throw BizException.unauthorized("仅学生角色可上传文件");
        }

        Long submissionId = fileService.upload(currentUser.getUserNo(), request);

        Map<String, Object> data = new HashMap<>();
        data.put("submissionId", submissionId);
        data.put("message", "文件上传成功");
        return Result.ok(data);
    }

    /**
     * 下载文件
     *
     * objectName 为文件在 MinIO 中的 UUID 混淆路径（含 / 分隔符），
     * 由于路径中包含斜杠，使用 ** 通配符匹配整个路径。
     * 后端校验 Redis Token 权限后从 MinIO 流式返回，前端无法感知 MinIO 地址。
     */
    @Operation(summary = "下载文件（流式中转）")
    @GetMapping("/download/**")
    public void download(HttpServletRequest request, HttpServletResponse response) {
        String objectName = extractPathFromRequest(request, "/api/file/download/");
        if (objectName == null || objectName.isBlank()) {
            throw new BizException("文件路径不能为空");
        }
        fileService.download(objectName, response);
    }

    /**
     * 获取文件元信息
     *
     * 返回文件名、大小、类型等，用于前端展示。
     */
    @Operation(summary = "获取文件元信息")
    @GetMapping("/info/**")
    public Result<FileInfoResponse> info(HttpServletRequest request) {
        String objectName = extractPathFromRequest(request, "/api/file/info/");
        if (objectName == null || objectName.isBlank()) {
            throw new BizException("文件路径不能为空");
        }
        return Result.ok(fileService.getFileInfo(objectName));
    }

    /**
     * 从 HttpServletRequest 中提取完整请求路径（去除前缀）
     * 例如：/api/file/download/course1/student1/1/code/xxx.zip
     *   → course1/student1/1/code/xxx.zip
     */
    private String extractPathFromRequest(HttpServletRequest request, String prefix) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        // 去除 contextPath 和前缀
        String path = uri;
        if (contextPath != null && !contextPath.isEmpty()) {
            path = path.substring(contextPath.length());
        }
        if (path.startsWith(prefix)) {
            return path.substring(prefix.length());
        }
        return null;
    }
}
