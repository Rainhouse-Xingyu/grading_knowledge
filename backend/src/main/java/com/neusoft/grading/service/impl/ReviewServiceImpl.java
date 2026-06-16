package com.neusoft.grading.service.impl;

import com.neusoft.grading.common.BizException;
import com.neusoft.grading.dto.ReviewCommentRequest;
import com.neusoft.grading.dto.ReviewScoreRequest;
import com.neusoft.grading.entity.SubmissionStage;
import com.neusoft.grading.service.ReviewService;
import com.neusoft.grading.service.SubmissionStageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 评语微调与分数管理服务实现
 *
 * 教师在 AI 评测完成后（status=2），可在线修改评语和分数：
 * - 评语修改：直接覆盖 final_report_markdown
 * - 分数修改：直接覆盖 teacher_score
 *
 * 设计原则：每次修改直接覆盖原值，不保留历史版本。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final SubmissionStageService submissionStageService;

    // ==================== 保存教师修改评语 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveComment(String teacherNo, ReviewCommentRequest request) {
        validateCommonParams(request.getStudentNo(), request.getCourseId(), request.getStageNum());

        if (request.getFinalReportMarkdown() == null) {
            throw new BizException("评语内容不能为空");
        }

        // 查询提交记录
        SubmissionStage submission = findAndValidateSubmission(
                request.getStudentNo(), request.getCourseId(), request.getStageNum());

        // 更新评语
        SubmissionStage update = new SubmissionStage();
        update.setSubmissionId(submission.getSubmissionId());
        update.setFinalReportMarkdown(request.getFinalReportMarkdown());
        submissionStageService.updateById(update);

        log.info("教师 {} 修改评语: studentNo={}, courseId={}, stageNum={}",
                teacherNo, request.getStudentNo(), request.getCourseId(), request.getStageNum());
    }

    // ==================== 保存教师最终分数 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveScore(String teacherNo, ReviewScoreRequest request) {
        validateCommonParams(request.getStudentNo(), request.getCourseId(), request.getStageNum());

        if (request.getTeacherScore() == null) {
            throw new BizException("分数不能为空");
        }
        if (request.getTeacherScore().compareTo(java.math.BigDecimal.ZERO) < 0
                || request.getTeacherScore().compareTo(new java.math.BigDecimal("100")) > 0) {
            throw new BizException("分数必须在 0-100 之间");
        }

        // 查询提交记录
        SubmissionStage submission = findAndValidateSubmission(
                request.getStudentNo(), request.getCourseId(), request.getStageNum());

        // 更新分数
        SubmissionStage update = new SubmissionStage();
        update.setSubmissionId(submission.getSubmissionId());
        update.setTeacherScore(request.getTeacherScore());
        submissionStageService.updateById(update);

        log.info("教师 {} 修改分数: studentNo={}, courseId={}, stageNum={}, score={}",
                teacherNo, request.getStudentNo(), request.getCourseId(),
                request.getStageNum(), request.getTeacherScore());
    }

    // ==================== 内部方法 ====================

    /**
     * 校验通用参数
     */
    private void validateCommonParams(String studentNo, String courseId, Integer stageNum) {
        if (studentNo == null || studentNo.isBlank()) {
            throw new BizException("学号不能为空");
        }
        if (courseId == null || courseId.isBlank()) {
            throw new BizException("课程 ID 不能为空");
        }
        if (stageNum == null || stageNum < 1 || stageNum > 3) {
            throw new BizException("阶段编号必须为 1、2 或 3");
        }
    }

    /**
     * 查询并校验提交记录
     *
     * 前提条件：状态必须为 2（AI 评测完成/待发布）
     */
    private SubmissionStage findAndValidateSubmission(
            String studentNo, String courseId, Integer stageNum) {

        SubmissionStage submission = submissionStageService.lambdaQuery()
                .eq(SubmissionStage::getStudentNo, studentNo)
                .eq(SubmissionStage::getCourseId, courseId)
                .eq(SubmissionStage::getStageNum, stageNum)
                .one();

        if (submission == null) {
            throw new BizException(404, "未找到提交记录");
        }

        if (submission.getStatus() != 2) {
            throw new BizException("当前状态不允许修改，需要状态为 2（待发布），当前状态: " + submission.getStatus());
        }

        return submission;
    }
}
