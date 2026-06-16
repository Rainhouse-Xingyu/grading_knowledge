package com.neusoft.grading.service.impl;

import com.neusoft.grading.common.BizException;
import com.neusoft.grading.dto.*;
import com.neusoft.grading.entity.Student;
import com.neusoft.grading.entity.StudentCourse;
import com.neusoft.grading.entity.SubmissionStage;
import com.neusoft.grading.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户与课程管理服务实现
 *
 * 核心查询：
 * 1. 课程学生列表：先查 t_student_course 获取学号列表，再批量查 t_student 补全信息，
 *    同时查 t_submission_stage 获取各阶段状态
 * 2. 学生三阶段进度：直接查 t_submission_stage 三条记录
 * 3. 期末总分：查 t_student_course
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserQueryServiceImpl implements UserQueryService {

    private final StudentCourseService studentCourseService;
    private final StudentService studentService;
    private final SubmissionStageService submissionStageService;

    // ==================== 课程学生列表 ====================

    @Override
    public List<CourseStudentResponse> getCourseStudents(String courseId) {
        if (courseId == null || courseId.isBlank()) {
            throw new BizException("课程 ID 不能为空");
        }

        // 1. 查询选课记录
        List<StudentCourse> enrollments = studentCourseService.lambdaQuery()
                .eq(StudentCourse::getCourseId, courseId)
                .list();

        if (enrollments.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 批量查询学生信息
        List<String> studentNos = enrollments.stream()
                .map(StudentCourse::getStudentNo)
                .collect(Collectors.toList());

        Map<String, Student> studentMap = studentService.listByIds(studentNos).stream()
                .collect(Collectors.toMap(Student::getStudentNo, s -> s));

        // 3. 查询该课程所有提交记录
        List<SubmissionStage> submissions = submissionStageService.lambdaQuery()
                .eq(SubmissionStage::getCourseId, courseId)
                .list();

        // 按学号分组
        Map<String, List<SubmissionStage>> submissionMap = submissions.stream()
                .collect(Collectors.groupingBy(SubmissionStage::getStudentNo));

        // 4. 组装响应
        List<CourseStudentResponse> result = new ArrayList<>();
        for (StudentCourse enrollment : enrollments) {
            String studentNo = enrollment.getStudentNo();
            Student student = studentMap.get(studentNo);
            List<SubmissionStage> stuSubmissions = submissionMap.getOrDefault(studentNo, Collections.emptyList());

            // 按阶段编号索引状态
            Map<Integer, SubmissionStage> stageMap = stuSubmissions.stream()
                    .collect(Collectors.toMap(SubmissionStage::getStageNum, s -> s));

            CourseStudentResponse resp = CourseStudentResponse.builder()
                    .studentNo(studentNo)
                    .name(student != null ? student.getName() : null)
                    .className(student != null ? student.getClassName() : null)
                    .majorName(student != null ? student.getMajorName() : null)
                    .stage1Status(getStageStatus(stageMap, 1))
                    .stage2Status(getStageStatus(stageMap, 2))
                    .stage3Status(getStageStatus(stageMap, 3))
                    .build();

            result.add(resp);
        }

        return result;
    }

    // ==================== 学生三阶段进度 ====================

    @Override
    public StudentProgressResponse getStudentProgress(String studentNo, String courseId) {
        if (studentNo == null || studentNo.isBlank()) {
            throw new BizException("学号不能为空");
        }
        if (courseId == null || courseId.isBlank()) {
            throw new BizException("课程 ID 不能为空");
        }

        List<SubmissionStage> submissions = submissionStageService.lambdaQuery()
                .eq(SubmissionStage::getStudentNo, studentNo)
                .eq(SubmissionStage::getCourseId, courseId)
                .orderByAsc(SubmissionStage::getStageNum)
                .list();

        List<StudentProgressResponse.StageDetail> stageDetails = new ArrayList<>();
        for (SubmissionStage sub : submissions) {
            StudentProgressResponse.StageDetail detail = StudentProgressResponse.StageDetail.builder()
                    .stageNum(sub.getStageNum())
                    .status(sub.getStatus())
                    .aiScore(sub.getAiScore())
                    .teacherScore(sub.getTeacherScore())
                    .submitTime(sub.getSubmitTime())
                    .evalTriggerTime(sub.getEvalTriggerTime())
                    .reviewTime(sub.getReviewTime())
                    .isJointReview(sub.getIsJointReview())
                    .build();
            stageDetails.add(detail);
        }

        return StudentProgressResponse.builder()
                .studentNo(studentNo)
                .courseId(courseId)
                .stages(stageDetails)
                .build();
    }

    // ==================== 期末总分 ====================

    @Override
    public FinalScoreResponse getFinalScore(String studentNo, String courseId) {
        if (studentNo == null || studentNo.isBlank()) {
            throw new BizException("学号不能为空");
        }
        if (courseId == null || courseId.isBlank()) {
            throw new BizException("课程 ID 不能为空");
        }

        StudentCourse record = studentCourseService.lambdaQuery()
                .eq(StudentCourse::getStudentNo, studentNo)
                .eq(StudentCourse::getCourseId, courseId)
                .one();

        if (record == null) {
            throw new BizException(404, "未找到选课记录");
        }

        return FinalScoreResponse.builder()
                .studentNo(studentNo)
                .courseId(courseId)
                .finalScore(record.getFinalScore())
                .teacherFinalComment(record.getTeacherFinalComment())
                .gradeStatus(record.getGradeStatus())
                .build();
    }

    // ==================== 内部方法 ====================

    /**
     * 获取指定阶段的状态值，未提交返回 null
     */
    private Integer getStageStatus(Map<Integer, SubmissionStage> stageMap, int stageNum) {
        SubmissionStage stage = stageMap.get(stageNum);
        return stage != null ? stage.getStatus() : null;
    }
}
