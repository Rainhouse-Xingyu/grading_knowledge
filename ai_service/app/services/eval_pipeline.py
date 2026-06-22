"""
评测编排管道模块 — 串联 OCR → RAG → LLM 全流程（开发文档 3.2 节）

核心职责：
  作为 AI 评测的编排器（Orchestrator），协调 OCR、RAG、LLM 三个子模块，
  按照固定的流水线顺序执行评测任务，并通过 Redis 更新任务状态机。

流水线顺序：
  1. 任务接收 → Redis 状态设为 10（等待）
  2. OCR 图文解析 → Redis 状态设为 20（OCR 解析中）
  3. RAG 评分标准检索 → Redis 状态设为 30（RAG 检索中）
  4. LLM 深度分析 → Redis 状态设为 40（LLM 分析中）
  5. 结果持久化 → Redis 状态设为 50（完成），MySQL status 更新为 2

异常处理：
  任意阶段失败 → Redis 状态设为 -1（失败），MySQL status 回退为 0
  error_msg 字段记录具体失败原因

调用方式：
  由 API 路由模块接收 Java 端投递的评测任务后，
  通过 asyncio.create_task() 异步启动管道，立即返回 task_id。
"""
import asyncio
from loguru import logger
from app.models.schemas import EvalSubmitRequest, TaskStatus
from app.services.ocr_service import ocr_service
from app.services.rag_service import rag_service
from app.services.llm_service import llm_service
from app.services.persistence import persistence_service


class EvalPipeline:
    """
    评测编排管道 — 协调 OCR → RAG → LLM 全流程。

    设计原则：
      1. 异步执行：通过 asyncio.create_task() 在后台运行，不阻塞 API 响应
      2. 状态驱动：每个阶段开始/结束时更新 Redis 状态机
      3. 异常安全：任意阶段失败时记录错误并回退 MySQL 状态
      4. 降级自适应：LLM 模块自动处理 DeepSeek → Ollama 降级
    """

    async def run(self, request: EvalSubmitRequest):
        """
        执行完整评测流程 — 主入口方法。

        参数：
            request: 评测任务提交请求，包含 task_id、student_no、course_id、stage

        流程：
          1. 从 MySQL 读取提交记录
          2. 获取联合评审上下文（如有）
          3. 执行 OCR 图文解析
          4. 执行 RAG 评分标准检索
          5. 执行 LLM 深度分析
          6. 保存结果到 MySQL，更新 Redis 状态为完成
        """
        task_id = request.task_id
        student_no = request.student_no
        course_id = request.course_id
        stage = request.stage

        logger.info(f"开始评测流程: task_id={task_id}, student={student_no}, course={course_id}, stage={stage}")

        try:
            # ========== 步骤 1：从 MySQL 读取提交记录 ==========
            submission = persistence_service.get_submission(student_no, course_id, stage)
            logger.info(f"提交记录已读取: submission_id={submission.submission_id}")

            # ========== 步骤 2：获取联合评审上下文（如有） ==========
            joint_context = ""
            if submission.is_joint_review == 1:
                ctx = persistence_service.get_joint_review_context(student_no, course_id, stage)
                if ctx:
                    joint_context = (
                        f"上一阶段教师评分：{ctx.teacher_score} 分\n"
                        f"上一阶段教师评语：\n{ctx.final_report_markdown}"
                    )
                    logger.info(f"联合评审上下文已获取: teacher_score={ctx.teacher_score}")

            # ========== 步骤 3：OCR 图文解析（Redis 状态 → 20） ==========
            persistence_service.update_task_status(task_id, TaskStatus.OCR_PARSING)
            logger.info("开始 OCR 图文解析...")
            student_report = await ocr_service.parse_submission(
                submission.code_package_path, submission.report_path
            )
            logger.info(f"OCR 解析完成，报告长度: {len(student_report)} 字符")

            # ========== 步骤 4：RAG 评分标准检索（Redis 状态 → 30） ==========
            persistence_service.update_task_status(task_id, TaskStatus.RAG_RETRIEVAL)
            logger.info("开始 RAG 评分标准检索...")
            course_name = persistence_service.get_course_name(course_id)
            grading_standards = await rag_service.retrieve_grading_standards(
                course_id, stage, student_report
            )
            logger.info(f"RAG 检索完成，标准长度: {len(grading_standards)} 字符")

            # ========== 步骤 5：LLM 深度分析（Redis 状态 → 40） ==========
            persistence_service.update_task_status(task_id, TaskStatus.LLM_ANALYSIS)
            logger.info("开始 LLM 深度分析...")
            eval_result = await llm_service.evaluate(
                student_report=student_report,
                grading_standards=grading_standards,
                course_name=course_name,
                stage=stage,
                joint_review_context=joint_context
            )
            logger.info(f"LLM 分析完成: score={eval_result.ai_score}, model={eval_result.model_used}")

            # ========== 步骤 6：结果持久化（Redis 状态 → 50，MySQL status → 2） ==========
            persistence_service.save_eval_result(submission.submission_id, eval_result)
            persistence_service.update_task_status(task_id, TaskStatus.COMPLETED)
            logger.info(f"评测流程完成: task_id={task_id}, score={eval_result.ai_score}")

        except Exception as e:
            # ========== 异常处理：记录错误，回退状态 ==========
            error_msg = f"评测失败: {str(e)}"
            logger.error(f"评测流程异常: task_id={task_id}, error={error_msg}")
            persistence_service.update_task_status(task_id, TaskStatus.FAILED, error_msg=error_msg)
            # 尝试回退 MySQL 状态（如果 submission 已读取）
            try:
                if 'submission' in locals():
                    persistence_service.revert_submission_status(submission.submission_id)
            except Exception as revert_err:
                logger.error(f"状态回退失败: {revert_err}")


# 全局单例 — 供 API 路由模块导入使用
eval_pipeline = EvalPipeline()
