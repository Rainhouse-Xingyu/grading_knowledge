package com.neusoft.grading.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 三阶段分段提交与AI/教师双轨评审主表（核心业务流转表）
 */
@Data
@TableName("t_submission_stage")
public class SubmissionStage {

    /** 自增主键 */
    @TableId(value = "submission_id", type = IdType.AUTO)
    private Long submissionId;

    /** 学号 */
    private String studentNo;

    /** 课程项目ID */
    private String courseId;

    /** 阶段编号: 1-第一次提交, 2-第二次提交, 3-第三次最终提交 */
    private Integer stageNum;

    /** 代码压缩包在MinIO中的映射混淆地址 */
    private String codePackagePath;

    /** 图文报告在MinIO中的映射混淆地址 */
    private String reportPath;

    /** 是否联合评审: 0-不联合, 1-联合评审 */
    private Integer isJointReview;

    /** 本次评测实际执行的大模型名称 */
    private String modelUsed;

    /** AI评定的严格初始分数 */
    private BigDecimal aiScore;

    /** AI原生输出的Markdown评审报告 */
    private String aiReportMarkdown;

    /** 教师审查/调分后的最终阶段分数 */
    private BigDecimal teacherScore;

    /** 教师修改后的最终版Markdown评审报告 */
    private String finalReportMarkdown;

    /**
     * 业务状态:
     * 0-已提交待评测,
     * 1-AI评测中,
     * 2-AI评测完成/待发布,
     * 3-已下发
     */
    private Integer status;

    /** 学生静态文件提交时间 */
    private LocalDateTime submitTime;

    /** 教师端触发AI评测的启动时间 */
    private LocalDateTime evalTriggerTime;

    /** 教师最终微调批阅完成并执行一键下发的时间 */
    private LocalDateTime reviewTime;
}
