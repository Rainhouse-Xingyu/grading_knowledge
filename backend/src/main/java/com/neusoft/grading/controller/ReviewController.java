package com.neusoft.grading.controller;

import com.neusoft.grading.common.BizException;
import com.neusoft.grading.common.Result;
import com.neusoft.grading.dto.ReviewCommentRequest;
import com.neusoft.grading.dto.ReviewScoreRequest;
import com.neusoft.grading.dto.UserInfoResponse;
import com.neusoft.grading.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 评语微调与分数管理控制器
 *
 * 对应开发文档 2.2.4 节接口定义：
 * PUT /api/review/comment — 教师保存修改评语（直接覆盖 final_report_markdown）
 * PUT /api/review/score   — 教师保存最终分数（覆盖 AI 分数 teacher_score）
 *
 * 前提条件：提交记录状态必须为 2（AI 评测完成/待发布）
 */
@Tag(name = "评语微调与分数管理")
@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * 保存教师修改评语
     *
     * 教师在 v-md-editor 中在线编辑 Markdown 评语后保存。
     * 直接覆盖 t_submission_stage.final_report_markdown 字段。
     * 当前设计不保留历史版本。
     */
    @Operation(summary = "保存教师修改评语")
    @PutMapping("/comment")
    public Result<Void> saveComment(
            @RequestBody ReviewCommentRequest request,
            HttpServletRequest httpRequest) {

        UserInfoResponse currentUser = getCurrentTeacher(httpRequest);
        reviewService.saveComment(currentUser.getUserNo(), request);
        return Result.ok();
    }

    /**
     * 保存教师最终分数
     *
     * 教师输入最终评定分数，覆盖 AI 严格分。
     * 直接覆盖 t_submission_stage.teacher_score 字段。
     */
    @Operation(summary = "保存教师最终分数")
    @PutMapping("/score")
    public Result<Void> saveScore(
            @RequestBody ReviewScoreRequest request,
            HttpServletRequest httpRequest) {

        UserInfoResponse currentUser = getCurrentTeacher(httpRequest);
        reviewService.saveScore(currentUser.getUserNo(), request);
        return Result.ok();
    }

    /**
     * 从请求中获取当前登录教师信息，非教师角色抛出 401
     */
    private UserInfoResponse getCurrentTeacher(HttpServletRequest httpRequest) {
        UserInfoResponse currentUser = (UserInfoResponse) httpRequest.getAttribute("currentUser");
        if (currentUser == null || !"teacher".equals(currentUser.getRole())) {
            throw BizException.unauthorized("仅教师角色可操作评语和分数");
        }
        return currentUser;
    }
}
