package com.neusoft.grading.controller;

import com.neusoft.grading.common.BizException;
import com.neusoft.grading.common.Result;
import com.neusoft.grading.dto.StandardInfoResponse;
import com.neusoft.grading.dto.StandardUploadRequest;
import com.neusoft.grading.dto.UserInfoResponse;
import com.neusoft.grading.service.StandardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 评分标准管理控制器
 *
 * 对应开发文档 4.3 节接口定义：
 * POST /api/standard/upload          — 教师上传评分标准文档
 * GET  /api/standard/list/{course_id} — 查询课程已上传的评分标准信息
 */
@Tag(name = "评分标准管理")
@RestController
@RequestMapping("/api/standard")
@RequiredArgsConstructor
public class StandardController {

    private final StandardService standardService;

    @Operation(summary = "上传评分标准文档")
    @PostMapping("/upload")
    public Result<Void> uploadStandard(
            @ModelAttribute StandardUploadRequest request,
            HttpServletRequest httpRequest) {

        UserInfoResponse currentUser = getCurrentTeacher(httpRequest);
        standardService.uploadStandard(currentUser.getUserNo(), request);
        return Result.ok();
    }

    @Operation(summary = "查询课程评分标准信息")
    @GetMapping("/list/{course_id}")
    public Result<StandardInfoResponse> getStandardInfo(
            @PathVariable("course_id") String courseId,
            HttpServletRequest httpRequest) {

        ensureLoggedIn(httpRequest);
        return Result.ok(standardService.getStandardInfo(courseId));
    }

    private UserInfoResponse getCurrentTeacher(HttpServletRequest httpRequest) {
        UserInfoResponse currentUser = (UserInfoResponse) httpRequest.getAttribute("currentUser");
        if (currentUser == null || !"teacher".equals(currentUser.getRole())) {
            throw BizException.unauthorized("仅教师角色可上传评分标准");
        }
        return currentUser;
    }

    private void ensureLoggedIn(HttpServletRequest httpRequest) {
        UserInfoResponse currentUser = (UserInfoResponse) httpRequest.getAttribute("currentUser");
        if (currentUser == null) {
            throw BizException.unauthorized("请先登录");
        }
    }
}
