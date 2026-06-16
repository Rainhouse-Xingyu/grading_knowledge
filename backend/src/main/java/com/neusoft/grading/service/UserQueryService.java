package com.neusoft.grading.service;

import com.neusoft.grading.dto.CourseStudentResponse;
import com.neusoft.grading.dto.FinalScoreResponse;
import com.neusoft.grading.dto.StudentProgressResponse;

import java.util.List;

/**
 * 用户与课程管理服务接口
 *
 * 职责：
 * 1. 教师查看课程下所有学生（JOIN t_student_course + t_student）
 * 2. 查询学生三阶段提交与评审状态
 * 3. 查询期末总分与总评语
 */
public interface UserQueryService {

    /**
     * 获取课程学生列表
     *
     * @param courseId 课程项目 ID
     * @return 该课程下所有学生的基本信息 + 三阶段状态概览
     */
    List<CourseStudentResponse> getCourseStudents(String courseId);

    /**
     * 获取学生三阶段进度
     *
     * @param studentNo 学号
     * @param courseId  课程项目 ID
     * @return 三阶段提交与评审状态详情
     */
    StudentProgressResponse getStudentProgress(String studentNo, String courseId);

    /**
     * 获取期末总分
     *
     * @param studentNo 学号
     * @param courseId  课程项目 ID
     * @return 期末总分与总评语
     */
    FinalScoreResponse getFinalScore(String studentNo, String courseId);
}
