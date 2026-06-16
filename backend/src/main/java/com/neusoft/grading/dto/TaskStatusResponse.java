package com.neusoft.grading.dto;

import lombok.Builder;
import lombok.Data;

/**
 * AI 评测任务状态响应
 *
 * 前端通过 task_id 轮询此接口获取 AI 评测实时进度。
 * 状态机：10(等待) → 20(OCR) → 30(RAG) → 40(LLM) → 50(完成)，-1(失败)
 */
@Data
@Builder
public class TaskStatusResponse {

    /** 任务 ID */
    private String taskId;

    /** 状态码：10/20/30/40/50/-1 */
    private Integer status;

    /** 状态文本描述 */
    private String statusText;

    /** 错误信息（仅 status=-1 时有值） */
    private String errorMsg;

    /** 关联的学号 */
    private String studentNo;

    /** 关联的阶段编号 */
    private Integer stageNum;
}
