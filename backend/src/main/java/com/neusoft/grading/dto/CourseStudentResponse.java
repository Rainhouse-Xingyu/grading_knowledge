package com.neusoft.grading.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * 课程学生信息响应
 *
 * 教师查看课程下所有学生时返回，
 * 包含学生基本信息 + 三阶段提交状态概览。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CourseStudentResponse {

    /** 学号 */
    private String studentNo;

    /** 姓名 */
    private String name;

    /** 班级名称 */
    private String className;

    /** 专业名称 */
    private String majorName;

    /** 阶段 1 状态：null-未提交 / 0-待评测 / 1-评测中 / 2-待发布 / 3-已下发 */
    private Integer stage1Status;

    /** 阶段 2 状态 */
    private Integer stage2Status;

    /** 阶段 3 状态 */
    private Integer stage3Status;

    /** 阶段 1 任务 ID（评测中时用于轮询） */
    private String stage1TaskId;

    /** 阶段 2 任务 ID */
    private String stage2TaskId;

    /** 阶段 3 任务 ID */
    private String stage3TaskId;
}
