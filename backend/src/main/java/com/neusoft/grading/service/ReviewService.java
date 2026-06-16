package com.neusoft.grading.service;

import com.neusoft.grading.dto.ReviewCommentRequest;
import com.neusoft.grading.dto.ReviewScoreRequest;

/**
 * 评语微调与分数管理服务接口
 *
 * 职责：
 * 1. 教师在线编辑 Markdown 评语后保存（直接覆盖 final_report_markdown）
 * 2. 教师输入最终分数，覆盖 AI 分数（直接覆盖 teacher_score）
 *
 * 前提条件：提交记录状态必须为 2（AI 评测完成/待发布）
 *
 * 设计说明：
 * 当前 SQL 设计中教师每次修改直接覆盖原值，不保留历史版本。
 * 若未来需要修改历史追溯，可在 t_submission_stage 基础上增加 t_review_history 扩展表。
 */
public interface ReviewService {

    /**
     * 保存教师修改评语
     *
     * @param teacherNo 当前登录教师工号
     * @param request   包含 studentNo、courseId、stageNum、finalReportMarkdown
     */
    void saveComment(String teacherNo, ReviewCommentRequest request);

    /**
     * 保存教师最终分数
     *
     * @param teacherNo 当前登录教师工号
     * @param request   包含 studentNo、courseId、stageNum、teacherScore
     */
    void saveScore(String teacherNo, ReviewScoreRequest request);
}
