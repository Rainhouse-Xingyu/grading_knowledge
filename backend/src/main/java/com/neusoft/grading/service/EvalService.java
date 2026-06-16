package com.neusoft.grading.service;

import com.neusoft.grading.dto.*;

import java.util.List;
import java.util.Map;

/**
 * AI 评测触发与任务管理服务接口
 *
 * 职责：
 * 1. 教师触发批量/单人 AI 评测，投递异步任务给 FastAPI
 * 2. 前端轮询查询 Redis 中任务状态机当前值
 * 3. 教师确认下发，状态变更为 3-已下发
 * 4. 获取评审报告（含 AI 原始版本和教师修改后版本）
 *
 * 教师端分布式锁：
 *   Key: neusoft:lock:teacher_eval:{student_no}:{course_id}:{stage_num}
 *   策略: tryLock(wait=5s, lease=180s)
 *
 * 配额控制：
 *   Key: neusoft:quota:deepseek:date:{yyyyMMdd}
 *   类型: Redis INCR 计数器，与企业配额上限对比
 */
public interface EvalService {

    /**
     * 触发 AI 评测（单人或批量）
     *
     * @param teacherNo 当前登录教师工号
     * @param request   包含 courseId、studentNo（可选）、stageNum、isJointReview
     * @return 触发结果：包含 taskId 列表和触发数量
     */
    Map<String, Object> triggerEval(String teacherNo, EvalTriggerRequest request);

    /**
     * 查询 AI 评测任务状态
     *
     * @param taskId 任务 ID
     * @return 任务状态信息（状态码、状态文本、错误信息等）
     */
    TaskStatusResponse getTaskStatus(String taskId);

    /**
     * 发布评审结果（教师一键下发）
     *
     * @param teacherNo 当前登录教师工号
     * @param request   包含 studentNo、courseId、stageNum
     */
    void publishResult(String teacherNo, EvalPublishRequest request);

    /**
     * 获取评审报告
     *
     * @param studentNo 学号
     * @param courseId  课程项目 ID
     * @param stageNum  阶段编号
     * @return 评审报告（含 AI 原始版本和教师修改后版本）
     */
    EvalReportResponse getReport(String studentNo, String courseId, Integer stageNum);

    /**
     * 获取课程某阶段所有学生的评测报告列表
     *
     * @param courseId 课程项目 ID
     * @param stageNum 阶段编号
     * @return 该阶段所有学生的评测报告列表
     */
    List<EvalReportResponse> getReportsByCourseAndStage(String courseId, Integer stageNum);
}
