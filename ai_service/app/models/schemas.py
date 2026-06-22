"""
AI/OCR 算法服务 — Pydantic 数据模型定义（开发文档 3.2 节）

本模块定义三类数据模型：
  1. API 请求/响应体 — 与 Java 端通过 HTTP 交互的数据契约
  2. 内部数据传输对象 — 各子模块之间传递的结构化数据
  3. 状态枚举 — Redis 任务状态机，与 Java 端状态值严格一致

状态机流转：
  QUEUED(10) → OCR_PARSING(20) → RAG_RETRIEVAL(30) → LLM_ANALYSIS(40) → COMPLETED(50)
  任意阶段异常 → FAILED(-1)

设计原则：
  - 所有模型继承 BaseModel，自动支持 JSON 序列化/反序列化
  - 使用 Field() 提供字段描述，便于 OpenAPI 文档自动生成
  - 枚举值与 Java 端 TaskStatus 保持一致，避免状态映射错误
"""

from pydantic import BaseModel, Field
from typing import Optional
from enum import IntEnum


# ========== 任务状态枚举（与 Java 端状态机一致） ==========

class TaskStatus(IntEnum):
    """
    AI 评测任务状态机（与 Java 端 Redis TaskStatus 完全一致）

    状态流转路径：
      10(等待) → 20(OCR) → 30(RAG) → 40(LLM) → 50(完成)
      任意阶段异常 → -1(失败)

    Redis 存储格式：
      Key:   neusoft:task:status:{task_id}
      Type:  Hash
      Field: status → 状态值字符串
      Field: error_msg → 失败原因（仅 FAILED 时有值）
      TTL:   24h

    前端轮询逻辑：教师端通过 task_id 轮询 GET /api/task/status/{task_id}，
    根据状态值渲染多阶段进度条。
    """
    QUEUED = 10          # 等待队列中 — 任务已投递，尚未开始处理
    OCR_PARSING = 20     # PaddleOCR 图文解析中 — 正在解压 .zip + 解析 .docx + OCR 识别截图
    RAG_RETRIEVAL = 30   # Milvus 评分标准检索中 — 正在向量化报告文本并检索相关评分标准
    LLM_ANALYSIS = 40    # DeepSeek/Ollama 深度分析中 — 正在调用 LLM 进行评分
    COMPLETED = 50       # 完成 — AI 分数和报告已写入 MySQL，状态更新为 2（待发布）
    FAILED = -1          # 失败 — error_msg 字段记录具体失败原因


# ========== API 请求模型 ==========

class EvalSubmitRequest(BaseModel):
    """
    评测任务提交请求体（由 Java 端 EvalServiceImpl 通过 HTTP POST 投递）

    Java 端调用示例：
      POST http://fastapi-host:8000/ai/eval/submit
      Content-Type: application/json
      {"task_id": "uuid-xxx", "student_no": "2022001", "course_id": "course-001", "stage": 1}

    处理流程：接收后立即返回 task_id，后台异步执行 OCR → RAG → LLM 全流程。
    """
    task_id: str = Field(..., description="任务 UUID，由 Java 端生成，用于状态轮询")
    student_no: str = Field(..., description="学号，关联 t_submission_stage.student_no")
    course_id: str = Field(..., description="课程项目 ID，关联 t_course_project.course_id")
    stage: int = Field(..., ge=1, le=3, description="阶段编号：1/2/3，对应 t_submission_stage.stage_num")


# ========== API 响应模型 ==========

class EvalSubmitResponse(BaseModel):
    """评测任务提交响应 — 立即返回，不等待评测完成"""
    task_id: str = Field(..., description="回传任务 UUID，前端可立即开始轮询")
    message: str = Field(default="任务已接收，后台异步执行", description="提示信息")


class HealthResponse(BaseModel):
    """健康检查响应 — Java 端心跳探针，检测 FastAPI 服务存活"""
    status: str = Field(default="ok", description="服务状态：ok 表示正常")
    service: str = Field(default="ai-eval-service", description="服务标识")


# ========== 内部数据传输模型 ==========

class SubmissionData(BaseModel):
    """
    从 MySQL t_submission_stage 表读取的提交记录数据。

    各字段与数据库表列一一对应，由 PersistenceService.get_submission() 查询后映射。
    """
    submission_id: int = Field(..., description="提交记录主键 t_submission_stage.submission_id")
    student_no: str = Field(..., description="学号")
    course_id: str = Field(..., description="课程项目 ID")
    stage_num: int = Field(..., description="阶段编号 1/2/3")
    code_package_path: str = Field(..., description="代码压缩包 MinIO 混淆路径")
    report_path: str = Field(..., description="图文报告 MinIO 混淆路径")
    is_joint_review: int = Field(default=0, description="是否联合评审：0=不联合 / 1=联合（引入上一轮教师终审）")
    model_used: str = Field(default="DeepSeek-R1", description="实际执行的模型名称，用于监控降级切换")
    status: int = Field(default=0, description="MySQL 业务状态：0=已提交 / 1=AI评测中 / 2=待发布 / 3=已下发")


class JointReviewContext(BaseModel):
    """
    联合评审上下文 — 来自上一阶段教师终审数据。

    仅当 is_joint_review=1 且 stage>1 时才有值。
    从 MySQL 查询上一阶段 status=3（已下发）的记录获取。
    将被拼接到 LLM System Prompt 中，要求审查"实质性工作增量"。
    """
    teacher_score: float = Field(..., description="上一阶段教师最终评定分数")
    final_report_markdown: str = Field(..., description="上一阶段教师修改后的最终 Markdown 报告")


class EvalResult(BaseModel):
    """
    AI 评测最终结果 — 由 LLMService.evaluate() 生成。

    包含 AI 严格分数、Markdown 评审报告、实际使用的模型名称。
    由 PersistenceService.save_eval_result() 写入 MySQL t_submission_stage 表。
    写入后 MySQL status 更新为 2（待发布），Redis 状态更新为 50（完成）。
    """
    ai_score: float = Field(..., ge=0, le=100, description="AI 严格分数 0-100，由 LLM 输出解析")
    ai_report_markdown: str = Field(..., description="Markdown 格式评审报告，由 LLM 直接生成")
    model_used: str = Field(default="DeepSeek-R1", description="实际使用的模型：DeepSeek-R1 或 Ollama-Local")
