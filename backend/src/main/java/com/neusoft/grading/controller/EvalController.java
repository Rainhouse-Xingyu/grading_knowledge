package com.neusoft.grading.controller;

import com.neusoft.grading.common.BizException;
import com.neusoft.grading.common.Result;
import com.neusoft.grading.dto.*;
import com.neusoft.grading.service.EvalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 评测触发与任务管理控制器
 *
 * 对应开发文档 2.2.3 节接口定义：
 * POST /api/eval/trigger              — 教师触发批量/单人 AI 评测
 * GET  /api/task/status/{task_id}     — 前端轮询任务状态机
 * POST /api/eval/publish              — 教师确认下发评审结果
 * GET  /api/eval/report/{student_no}  — 获取评审报告
 *
 * 教师端分布式锁保障：
 *   Key: neusoft:lock:teacher_eval:{student_no}:{course_id}:{stage_num}
 *   策略: tryLock(wait=5s, lease=180s)
 */
@Tag(name = "AI 评测触发与任务管理")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class EvalController {

    private final EvalService evalService;

    /**
     * 触发 AI 评测（教师专用）
     *
     * - studentNo 为空时：对整个课程该阶段所有待评测学生批量触发
     * - studentNo 非空时：仅对该学生触发
     *
     * 流程：
     * 1. 获取 Redisson 分布式锁（防同组教师或重复点击）
     * 2. 检查配额，配额耗尽时自动降级到本地 Ollama 模型
     * 3. 更新 MySQL 状态为 1（AI 评测中）
     * 4. 生成 task_id，初始化 Redis 任务状态机（status=10）
     * 5. 投递异步任务给 FastAPI 算法服务
     * 6. 返回 task_id 列表，前端通过 task_id 轮询进度
     */
    @Operation(summary = "触发 AI 评测（教师专用）")
    @PostMapping("/api/eval/trigger")
    public Result<Map<String, Object>> triggerEval(
            @RequestBody EvalTriggerRequest request,
            HttpServletRequest httpRequest) {

        UserInfoResponse currentUser = getCurrentTeacher(httpRequest);
        Map<String, Object> result = evalService.triggerEval(currentUser.getUserNo(), request);
        return Result.ok(result);
    }

    /**
     * 查询 AI 评测任务状态（前端轮询）
     *
     * 状态机：
     *   10 (等待队列中) → 20 (PaddleOCR 图文解析中) → 30 (Milvus 评分标准检索中)
     *   → 40 (DeepSeek 深度分析中) → 50 (完成)
     *   异常：-1 (失败)，附带 error_msg
     *
     * 前端以 task_id 定时轮询（建议间隔 2-3 秒），渲染多阶段进度条。
     */
    @Operation(summary = "查询 AI 评测任务状态")
    @GetMapping("/api/task/status/{task_id}")
    public Result<TaskStatusResponse> getTaskStatus(@PathVariable("task_id") String taskId) {
        return Result.ok(evalService.getTaskStatus(taskId));
    }

    /**
     * 发布评审结果（教师一键下发）
     *
     * 前提条件：提交记录状态必须为 2（AI 评测完成/待发布）
     * 操作结果：状态变更为 3（已下发），学生端解锁可查看
     *
     * 若教师未手动修改分数/评语，系统自动使用 AI 原始分数和报告作为最终版本。
     */
    @Operation(summary = "发布评审结果（教师一键下发）")
    @PostMapping("/api/eval/publish")
    public Result<Void> publishResult(
            @RequestBody EvalPublishRequest request,
            HttpServletRequest httpRequest) {

        UserInfoResponse currentUser = getCurrentTeacher(httpRequest);
        evalService.publishResult(currentUser.getUserNo(), request);
        return Result.ok();
    }

    /**
     * 获取评审报告
     *
     * 返回 AI 原始评语和教师修改后的最终版本。
     * - status=2 时教师可查看 AI 原始报告并进行微调
     * - status=3 时学生可查看教师最终版报告
     */
    @Operation(summary = "获取单个学生评审报告")
    @GetMapping("/api/eval/report/{student_no}/{course_id}/{stage}")
    public Result<EvalReportResponse> getReport(
            @PathVariable("student_no") String studentNo,
            @PathVariable("course_id") String courseId,
            @PathVariable("stage") Integer stage) {
        return Result.ok(evalService.getReport(studentNo, courseId, stage));
    }

    /**
     * 获取课程某阶段所有学生的评测报告列表
     *
     * 教师工作台使用，展示全班评测结果。
     */
    @Operation(summary = "获取课程某阶段所有学生评测报告")
    @GetMapping("/api/eval/reports/{course_id}/{stage}")
    public Result<List<EvalReportResponse>> getReports(
            @PathVariable("course_id") String courseId,
            @PathVariable("stage") Integer stage) {
        return Result.ok(evalService.getReportsByCourseAndStage(courseId, stage));
    }

    /**
     * 从请求中获取当前登录教师信息，非教师角色抛出 403
     */
    private UserInfoResponse getCurrentTeacher(HttpServletRequest httpRequest) {
        UserInfoResponse currentUser = (UserInfoResponse) httpRequest.getAttribute("currentUser");
        if (currentUser == null || !"teacher".equals(currentUser.getRole())) {
            throw BizException.unauthorized("仅教师角色可操作评测功能");
        }
        return currentUser;
    }
}
