package com.neusoft.grading.controller;

import com.neusoft.grading.common.BizException;
import com.neusoft.grading.common.Result;
import com.neusoft.grading.dto.*;
import com.neusoft.grading.service.UserQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "用户与课程管理")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserQueryService userQueryService;

    @Operation(summary = "获取课程学生列表")
    @GetMapping("/students/{course_id}")
    public Result<List<CourseStudentResponse>> getCourseStudents(
            @PathVariable("course_id") String courseId,
            HttpServletRequest httpRequest) {

        ensureLoggedIn(httpRequest);
        return Result.ok(userQueryService.getCourseStudents(courseId));
    }

    @Operation(summary = "获取学生三阶段进度")
    @GetMapping("/progress/{student_no}/{course_id}")
    public Result<StudentProgressResponse> getStudentProgress(
            @PathVariable("student_no") String studentNo,
            @PathVariable("course_id") String courseId,
            HttpServletRequest httpRequest) {

        ensureLoggedIn(httpRequest);
        return Result.ok(userQueryService.getStudentProgress(studentNo, courseId));
    }

    @Operation(summary = "获取期末总分")
    @GetMapping("/final-score/{student_no}/{course_id}")
    public Result<FinalScoreResponse> getFinalScore(
            @PathVariable("student_no") String studentNo,
            @PathVariable("course_id") String courseId,
            HttpServletRequest httpRequest) {

        UserInfoResponse currentUser = (UserInfoResponse) httpRequest.getAttribute("currentUser");
        if (currentUser == null) {
            throw BizException.unauthorized("请先登录");
        }

        FinalScoreResponse score = userQueryService.getFinalScore(studentNo, courseId);

        // 学生只能查看已下发的总分
        if ("student".equals(currentUser.getRole()) && score.getGradeStatus() != 1) {
            throw new BizException(403, "总分尚未发布，无法查看");
        }

        return Result.ok(score);
    }

    private void ensureLoggedIn(HttpServletRequest httpRequest) {
        UserInfoResponse currentUser = (UserInfoResponse) httpRequest.getAttribute("currentUser");
        if (currentUser == null) {
            throw BizException.unauthorized("请先登录");
        }
    }
}
