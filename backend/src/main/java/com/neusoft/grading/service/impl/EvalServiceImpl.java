package com.neusoft.grading.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.neusoft.grading.common.BizException;
import com.neusoft.grading.config.AiServiceConfig;
import com.neusoft.grading.dto.*;
import com.neusoft.grading.entity.SubmissionStage;
import com.neusoft.grading.service.EvalService;
import com.neusoft.grading.service.SubmissionStageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * AI 评测触发与任务管理服务实现
 *
 * 核心流程：
 * 1. 教师触发评测 → 获取 Redisson 分布式锁 → 更新 MySQL 状态为 1 → 生成 task_id
 * 2. 初始化 Redis 任务状态机 (status=10) → 投递异步任务给 FastAPI
 * 3. FastAPI 完成后更新 Redis 状态为 50 + MySQL 状态为 2
 * 4. 教师确认下发 → 更新 MySQL 状态为 3
 *
 * 配额控制：
 * - 每次触发前检查 Redis 配额计数器
 * - 配额耗尽时设置降级标识，FastAPI 会切换到本地 Ollama 模型
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalServiceImpl implements EvalService {

    private final SubmissionStageService submissionStageService;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;
    private final AiServiceConfig aiServiceConfig;

    /** Redis 任务状态 Hash Key 前缀 */
    private static final String TASK_STATUS_PREFIX = "neusoft:task:status:";

    /** Redis 配额计数器 Key 前缀 */
    private static final String QUOTA_PREFIX = "neusoft:quota:deepseek:date:";

    /** Redis 模型降级标识 Key */
    private static final String MODEL_FALLBACK_KEY = "neusoft:config:model_fallback";

    /** 教师评测分布式锁 Key 前缀 */
    private static final String TEACHER_EVAL_LOCK_PREFIX = "neusoft:lock:teacher_eval:";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ==================== 触发 AI 评测 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> triggerEval(String teacherNo, EvalTriggerRequest request) {
        // 参数校验
        validateTriggerRequest(request);

        String courseId = request.getCourseId();
        int stageNum = request.getStageNum();
        int isJointReview = request.getIsJointReview() != null ? request.getIsJointReview() : 0;

        // 查询需要评测的提交记录
        List<SubmissionStage> submissions = findSubmissionsToEvaluate(
                courseId, request.getStudentNo(), stageNum);

        if (submissions.isEmpty()) {
            throw new BizException("没有找到需要评测的提交记录");
        }

        // 配额检查
        checkAndSetQuota(submissions.size());

        List<String> taskIds = new ArrayList<>();
        List<String> skippedStudents = new ArrayList<>();

        for (SubmissionStage submission : submissions) {
            String studentNo = submission.getStudentNo();
            String lockKey = TEACHER_EVAL_LOCK_PREFIX + studentNo + ":" + courseId + ":" + stageNum;
            RLock lock = redissonClient.getLock(lockKey);

            boolean locked;
            try {
                locked = lock.tryLock(5, 180, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                skippedStudents.add(studentNo);
                log.warn("获取锁被中断: studentNo={}", studentNo);
                continue;
            }

            if (!locked) {
                skippedStudents.add(studentNo);
                log.warn("该学生作业正在进行 AI 评测中，跳过: studentNo={}", studentNo);
                continue;
            }

            try {
                // 再次检查状态（获取锁期间可能已被其他教师触发）
                SubmissionStage latest = submissionStageService.getById(submission.getSubmissionId());
                if (latest.getStatus() != 0) {
                    skippedStudents.add(studentNo);
                    log.info("学生 {} 状态已变更(status={})，跳过", studentNo, latest.getStatus());
                    continue;
                }

                // 生成任务 ID
                String taskId = UUID.randomUUID().toString().replace("-", "");

                // 初始化 Redis 任务状态机：10（等待队列中）
                String taskKey = TASK_STATUS_PREFIX + taskId;
                redisTemplate.opsForHash().put(taskKey, "status", "10");
                redisTemplate.opsForHash().put(taskKey, "student_no", studentNo);
                redisTemplate.opsForHash().put(taskKey, "stage_num", String.valueOf(stageNum));
                redisTemplate.expire(taskKey, Duration.ofHours(24));

                // 更新 MySQL 状态为 1（AI 评测中）
                SubmissionStage update = new SubmissionStage();
                update.setSubmissionId(submission.getSubmissionId());
                update.setStatus(1);
                update.setEvalTriggerTime(LocalDateTime.now());
                update.setIsJointReview(isJointReview);
                submissionStageService.updateById(update);

                // 投递异步任务给 FastAPI
                submitToFastApi(taskId, studentNo, courseId, stageNum);

                taskIds.add(taskId);
                log.info("已触发 AI 评测: studentNo={}, taskId={}", studentNo, taskId);

            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }

        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("taskIds", taskIds);
        result.put("triggeredCount", taskIds.size());
        result.put("totalCount", submissions.size());
        if (!skippedStudents.isEmpty()) {
            result.put("skippedStudents", skippedStudents);
            result.put("skippedCount", skippedStudents.size());
        }
        return result;
    }

    // ==================== 查询任务状态 ====================

    @Override
    public TaskStatusResponse getTaskStatus(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BizException("任务 ID 不能为空");
        }

        String taskKey = TASK_STATUS_PREFIX + taskId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(taskKey);

        if (entries.isEmpty()) {
            throw new BizException(404, "任务不存在或已过期: " + taskId);
        }

        int status = Integer.parseInt((String) entries.getOrDefault("status", "-1"));
        String studentNo = (String) entries.get("student_no");
        String stageNumStr = (String) entries.get("stage_num");
        String errorMsg = (String) entries.get("error_msg");

        return TaskStatusResponse.builder()
                .taskId(taskId)
                .status(status)
                .statusText(getStatusText(status))
                .errorMsg(errorMsg)
                .studentNo(studentNo)
                .stageNum(stageNumStr != null ? Integer.parseInt(stageNumStr) : null)
                .build();
    }

    // ==================== 发布评审结果 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishResult(String teacherNo, EvalPublishRequest request) {
        if (request.getStudentNo() == null || request.getStudentNo().isBlank()) {
            throw new BizException("学号不能为空");
        }
        if (request.getCourseId() == null || request.getCourseId().isBlank()) {
            throw new BizException("课程 ID 不能为空");
        }
        if (request.getStageNum() == null || request.getStageNum() < 1 || request.getStageNum() > 3) {
            throw new BizException("阶段编号必须为 1、2 或 3");
        }

        // 查询提交记录
        SubmissionStage submission = submissionStageService.lambdaQuery()
                .eq(SubmissionStage::getStudentNo, request.getStudentNo())
                .eq(SubmissionStage::getCourseId, request.getCourseId())
                .eq(SubmissionStage::getStageNum, request.getStageNum())
                .one();

        if (submission == null) {
            throw new BizException(404, "未找到提交记录");
        }

        if (submission.getStatus() != 2) {
            throw new BizException("当前状态不允许下发，需要状态为 2（待发布），当前状态: " + submission.getStatus());
        }

        // 更新状态为 3（已下发），记录下发时间
        SubmissionStage update = new SubmissionStage();
        update.setSubmissionId(submission.getSubmissionId());
        update.setStatus(3);
        update.setReviewTime(LocalDateTime.now());

        // 如果教师没有修改分数，使用 AI 分数作为最终分数
        if (update.getTeacherScore() == null && submission.getTeacherScore() == null) {
            update.setTeacherScore(submission.getAiScore());
        }
        // 如果教师没有修改报告，使用 AI 报告作为最终报告
        if (update.getFinalReportMarkdown() == null && submission.getFinalReportMarkdown() == null) {
            update.setFinalReportMarkdown(submission.getAiReportMarkdown());
        }

        submissionStageService.updateById(update);

        log.info("评审结果已下发: studentNo={}, courseId={}, stageNum={}, teacherNo={}",
                request.getStudentNo(), request.getCourseId(), request.getStageNum(), teacherNo);
    }

    // ==================== 获取评审报告 ====================

    @Override
    public EvalReportResponse getReport(String studentNo, String courseId, Integer stageNum) {
        if (studentNo == null || studentNo.isBlank()) {
            throw new BizException("学号不能为空");
        }
        if (courseId == null || courseId.isBlank()) {
            throw new BizException("课程 ID 不能为空");
        }
        if (stageNum == null || stageNum < 1 || stageNum > 3) {
            throw new BizException("阶段编号必须为 1、2 或 3");
        }

        SubmissionStage submission = submissionStageService.lambdaQuery()
                .eq(SubmissionStage::getStudentNo, studentNo)
                .eq(SubmissionStage::getCourseId, courseId)
                .eq(SubmissionStage::getStageNum, stageNum)
                .one();

        if (submission == null) {
            throw new BizException(404, "未找到提交记录");
        }

        return buildReportResponse(submission);
    }

    @Override
    public List<EvalReportResponse> getReportsByCourseAndStage(String courseId, Integer stageNum) {
        if (courseId == null || courseId.isBlank()) {
            throw new BizException("课程 ID 不能为空");
        }
        if (stageNum == null || stageNum < 1 || stageNum > 3) {
            throw new BizException("阶段编号必须为 1、2 或 3");
        }

        List<SubmissionStage> submissions = submissionStageService.lambdaQuery()
                .eq(SubmissionStage::getCourseId, courseId)
                .eq(SubmissionStage::getStageNum, stageNum)
                .orderByAsc(SubmissionStage::getStudentNo)
                .list();

        return submissions.stream()
                .map(this::buildReportResponse)
                .collect(Collectors.toList());
    }

    // ==================== 内部方法 ====================

    /**
     * 校验触发请求参数
     */
    private void validateTriggerRequest(EvalTriggerRequest request) {
        if (request.getCourseId() == null || request.getCourseId().isBlank()) {
            throw new BizException("课程 ID 不能为空");
        }
        if (request.getStageNum() == null || request.getStageNum() < 1 || request.getStageNum() > 3) {
            throw new BizException("阶段编号必须为 1、2 或 3");
        }
    }

    /**
     * 查询需要评测的提交记录
     *
     * - 单人模式：studentNo 非空，查询该学生该课程该阶段状态为 0 的记录
     * - 批量模式：studentNo 为空，查询该课程该阶段所有状态为 0 的记录
     */
    private List<SubmissionStage> findSubmissionsToEvaluate(
            String courseId, String studentNo, int stageNum) {

        LambdaQueryWrapper<SubmissionStage> wrapper = new LambdaQueryWrapper<SubmissionStage>()
                .eq(SubmissionStage::getCourseId, courseId)
                .eq(SubmissionStage::getStageNum, stageNum)
                .eq(SubmissionStage::getStatus, 0); // 0-已提交待评测

        if (studentNo != null && !studentNo.isBlank()) {
            wrapper.eq(SubmissionStage::getStudentNo, studentNo);
        }

        return submissionStageService.list(wrapper);
    }

    /**
     * 检查并更新配额计数器
     *
     * Key: neusoft:quota:deepseek:date:{yyyyMMdd}
     * 如果当日配额已耗尽，设置降级标识 neusoft:config:model_fallback = "local"
     */
    private void checkAndSetQuota(int requestCount) {
        String today = LocalDate.now().format(DATE_FMT);
        String quotaKey = QUOTA_PREFIX + today;

        // 获取当前已用配额
        String currentStr = redisTemplate.opsForValue().get(quotaKey);
        long current = currentStr != null ? Long.parseLong(currentStr) : 0;

        int dailyQuota = aiServiceConfig.getDailyQuota();

        if (current + requestCount > dailyQuota) {
            // 配额耗尽，设置降级标识
            redisTemplate.opsForValue().set(MODEL_FALLBACK_KEY, "local");
            log.warn("DeepSeek 配额耗尽({}/{})，已切换到本地 Ollama 模型", current, dailyQuota);
        }

        // 递增配额计数器（预增 requestCount）
        redisTemplate.opsForValue().increment(quotaKey, requestCount);
        // 设置 24 小时过期
        redisTemplate.expire(quotaKey, Duration.ofHours(24));
    }

    /**
     * 向 FastAPI 算法服务投递异步评测任务
     *
     * POST /ai/eval/submit
     * 请求体: {task_id, student_no, course_id, stage}
     * FastAPI 接收后立即返回，后台异步执行评测流程
     */
    private void submitToFastApi(String taskId, String studentNo,
                                  String courseId, int stageNum) {
        Map<String, Object> body = new HashMap<>();
        body.put("task_id", taskId);
        body.put("student_no", studentNo);
        body.put("course_id", courseId);
        body.put("stage", stageNum);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    aiServiceConfig.getSubmitUrl(),
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("FastAPI 接收任务成功: taskId={}", taskId);
            } else {
                log.error("FastAPI 返回异常状态: taskId={}, status={}", taskId, response.getStatusCode());
                markTaskFailed(taskId, "FastAPI 服务返回异常: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("调用 FastAPI 失败: taskId={}", taskId, e);
            markTaskFailed(taskId, "调用 AI 服务失败: " + e.getMessage());
        }
    }

    /**
     * 标记任务失败：更新 Redis 状态为 -1，回退 MySQL 状态为 0
     */
    private void markTaskFailed(String taskId, String errorMsg) {
        String taskKey = TASK_STATUS_PREFIX + taskId;
        redisTemplate.opsForHash().put(taskKey, "status", "-1");
        redisTemplate.opsForHash().put(taskKey, "error_msg", errorMsg);

        // 从 Redis 获取关联的学号和阶段，回退 MySQL 状态
        String studentNo = (String) redisTemplate.opsForHash().get(taskKey, "student_no");
        String stageNumStr = (String) redisTemplate.opsForHash().get(taskKey, "stage_num");

        if (studentNo != null && stageNumStr != null) {
            // 注意：这里无法获取 courseId，MySQL 状态回退需要在调用方处理
            log.warn("任务失败，需手动处理 MySQL 状态回退: taskId={}, studentNo={}", taskId, studentNo);
        }
    }

    /**
     * 任务状态码 → 文本描述
     */
    private String getStatusText(int status) {
        return switch (status) {
            case 10 -> "等待队列中";
            case 20 -> "PaddleOCR 图文解析中";
            case 30 -> "Milvus 评分标准检索中";
            case 40 -> "DeepSeek 深度分析中";
            case 50 -> "完成";
            case -1 -> "失败";
            default -> "未知状态";
        };
    }

    /**
     * 构建评审报告响应体
     */
    private EvalReportResponse buildReportResponse(SubmissionStage submission) {
        return EvalReportResponse.builder()
                .submissionId(submission.getSubmissionId())
                .studentNo(submission.getStudentNo())
                .courseId(submission.getCourseId())
                .stageNum(submission.getStageNum())
                .aiScore(submission.getAiScore())
                .aiReportMarkdown(submission.getAiReportMarkdown())
                .teacherScore(submission.getTeacherScore())
                .finalReportMarkdown(submission.getFinalReportMarkdown())
                .modelUsed(submission.getModelUsed())
                .status(submission.getStatus())
                .build();
    }
}
