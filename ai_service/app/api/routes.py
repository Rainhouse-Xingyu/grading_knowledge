"""
API 路由模块（开发文档 3.2.1 节）

核心职责：
  定义 FastAPI 路由，接收 Java 端投递的评测任务和健康检查请求。

接口定义：
  POST /ai/eval/submit — 接收评测任务，异步执行 OCR → RAG → LLM 全流程
  GET  /ai/health       — Java 端心跳探针，检测 FastAPI 服务存活

设计原则：
  1. 接收任务后立即返回 task_id，后台异步执行评测流程
  2. 通过 Redis 更新任务状态机（10→20→30→40→50/-1）
  3. 前端通过 Java 端轮询 Redis 获取任务进度
  4. 健康检查接口用于服务存活探测，不涉及业务逻辑
"""
import asyncio
from fastapi import APIRouter, HTTPException
from loguru import logger
from app.models.schemas import EvalSubmitRequest, EvalSubmitResponse, HealthResponse
from app.services.eval_pipeline import eval_pipeline

# 创建路由器 — 前缀 /ai，与开发文档接口路径一致
router = APIRouter(prefix="/ai")


@router.post("/eval/submit", response_model=EvalSubmitResponse, summary="接收评测任务")
async def submit_eval_task(request: EvalSubmitRequest):
    """
    接收 Java 端投递的评测任务 — 异步执行。

    请求体（由 Java 端 EvalServiceImpl 通过 HTTP POST 投递）：
      {
        "task_id": "uuid-xxx",
        "student_no": "2022001",
        "course_id": "course-001",
        "stage": 1
      }

    处理流程：
      1. 接收请求后立即返回 task_id（不等待评测完成）
      2. 通过 asyncio.create_task() 在后台异步启动评测管道
      3. 评测管道按 OCR → RAG → LLM 顺序执行，通过 Redis 更新状态

    返回：
      EvalSubmitResponse: 包含 task_id 和提示信息

    注意：
      - 此接口立即返回，不等待评测完成
      - Java 端通过 task_id 轮询 GET /api/task/status/{task_id} 获取进度
      - 评测结果完成后写入 MySQL，状态更新为 2（待发布）
    """
    logger.info(f"收到评测任务: task_id={request.task_id}, student={request.student_no}, "
                f"course={request.course_id}, stage={request.stage}")

    # 异步启动评测管道，不阻塞当前请求
    asyncio.create_task(eval_pipeline.run(request))

    return EvalSubmitResponse(
        task_id=request.task_id,
        message="任务已接收，后台异步执行"
    )


@router.get("/health", response_model=HealthResponse, summary="健康检查")
async def health_check():
    """
    健康检查接口 — Java 端心跳探针。

    用途：
      - Java 端定期探测 FastAPI 服务是否存活
      - 如果探测失败，Java 端可以记录告警或触发降级

    返回：
      HealthResponse: 包含 status="ok" 和 service 标识
    """
    return HealthResponse(status="ok", service="ai-eval-service")
