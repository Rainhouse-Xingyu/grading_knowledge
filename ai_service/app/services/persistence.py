"""
结果持久化模块（开发文档 3.2.5 节）— MySQL + Redis 操作

核心职责：
  1. Redis 任务状态机管理 — 更新任务状态（10→20→30→40→50/-1）
  2. MySQL 数据读取 — 查询提交记录、联合评审上下文、课程名称
  3. MySQL 结果写入 — AI 评测完成后写入分数和报告，更新状态为 2（待发布）
  4. MySQL 状态回退 — 评测失败时回退状态为 0（已提交待评测）

Redis Key 设计（与 Java 端保持一致）：
  任务状态:  neusoft:task:status:{task_id}  (Hash, TTL 24h)
    - status: 状态值（10/20/30/40/50/-1）
    - error_msg: 失败原因（仅 FAILED 时有值）

Redis 任务状态 → MySQL 业务状态映射：
  Redis 10/20/30/40 → MySQL 1（AI 评测中）
  Redis 50 → MySQL 2（待发布，由本模块同步更新）
  Redis -1 → MySQL 保持 1 或回退 0（由业务逻辑决定）

依赖：
  - pymysql: MySQL 客户端（原生 SQL，不使用 ORM，保持轻量）
  - redis: Redis 客户端（读写任务状态机、降级开关）
"""
import pymysql
import redis
from datetime import datetime
from loguru import logger
from app.config import settings
from app.models.schemas import SubmissionData, JointReviewContext, EvalResult, TaskStatus


class PersistenceService:
    """
    MySQL + Redis 持久化服务 — 单例模式，懒加载 Redis 连接。

    MySQL 连接采用短连接策略：每次操作创建新连接，操作完成后立即关闭。
    Redis 连接采用长连接策略：首次创建后复用，通过连接池管理。
    """

    def __init__(self):
        self._redis = None  # Redis 客户端实例（懒加载）

    @property
    def redis(self):
        """懒加载 Redis 客户端，配置与 Java 端 application.yml 保持一致"""
        if self._redis is None:
            self._redis = redis.Redis(
                host=settings.REDIS_HOST,
                port=settings.REDIS_PORT,
                db=settings.REDIS_DB,
                password=settings.REDIS_PASSWORD,
                decode_responses=True  # 自动将 bytes 解码为 str
            )
            logger.info(f"Redis 连接已建立: {settings.REDIS_HOST}:{settings.REDIS_PORT}")
        return self._redis

    def _get_conn(self):
        """
        创建 MySQL 短连接。

        使用 pymysql 原生连接，DictCursor 返回字典格式结果。
        连接参数与 Java 端 DataSource 配置保持一致。
        """
        return pymysql.connect(
            host=settings.MYSQL_HOST,
            port=settings.MYSQL_PORT,
            user=settings.MYSQL_USER,
            password=settings.MYSQL_PASSWORD,
            database=settings.MYSQL_DATABASE,
            charset="utf8mb4",
            cursorclass=pymysql.cursors.DictCursor
        )

    # ========== Redis 状态机管理 ==========

    def update_task_status(self, task_id: str, status: TaskStatus, error_msg: str = None):
        """
        更新 Redis 任务状态机 — 核心方法。

        Redis 存储格式：
          Key:   neusoft:task:status:{task_id}
          Type:  Hash
          Field: status → 状态值字符串（"10"/"20"/"30"/"40"/"50"/"-1"）
          Field: error_msg → 失败原因（仅 FAILED 时有值）
          TTL:   24h（86400 秒）

        前端轮询逻辑：教师端通过 task_id 轮询 GET /api/task/status/{task_id}，
        Java 端读取此 Hash 返回给前端，渲染多阶段进度条。

        参数：
            task_id: 任务 UUID（由 Java 端生成）
            status: TaskStatus 枚举值
            error_msg: 失败原因（仅 FAILED 时需要）
        """
        key = f"{settings.REDIS_TASK_PREFIX}{task_id}"
        self.redis.hset(key, "status", str(int(status)))
        if error_msg:
            self.redis.hset(key, "error_msg", error_msg)
        self.redis.expire(key, 86400)  # TTL 24h
        logger.debug(f"Redis 状态已更新: {key} → {int(status)}")

    # ========== MySQL 数据读取 ==========

    def get_submission(self, student_no: str, course_id: str, stage: int) -> SubmissionData:
        """
        查询学生提交记录 — 从 t_submission_stage 表读取。

        参数：
            student_no: 学号
            course_id: 课程项目 ID
            stage: 阶段编号 1/2/3

        返回：
            SubmissionData 对象，包含提交记录的所有字段

        异常：
            ValueError: 未找到提交记录
        """
        conn = self._get_conn()
        try:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT * FROM t_submission_stage "
                    "WHERE student_no=%s AND course_id=%s AND stage_num=%s",
                    (student_no, course_id, stage)
                )
                row = cur.fetchone()
                if not row:
                    raise ValueError(f"未找到提交记录: student_no={student_no}, course_id={course_id}, stage={stage}")
                return SubmissionData(
                    submission_id=row["submission_id"],
                    student_no=row["student_no"],
                    course_id=row["course_id"],
                    stage_num=row["stage_num"],
                    code_package_path=row["code_package_path"],
                    report_path=row["report_path"],
                    is_joint_review=row.get("is_joint_review", 0),
                    model_used=row.get("model_used", "DeepSeek-R1"),
                    status=row["status"]
                )
        finally:
            conn.close()

    def get_joint_review_context(self, student_no: str, course_id: str, stage: int):
        """
        获取上一阶段教师终审上下文 — 联合评审专用。

        查询条件：
          - 同一学生、同一课程、上一阶段（stage - 1）
          - 状态必须为 3（已下发）— 确保是教师最终锁定的版本

        仅当 stage > 1 时才有上一阶段数据。
        如果上一阶段未下发或不存在，返回 None。

        返回：
            JointReviewContext 对象（含 teacher_score + final_report_markdown）
            或 None（无上一阶段数据）
        """
        if stage <= 1:
            return None  # 第一阶段没有上一阶段

        conn = self._get_conn()
        try:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT teacher_score, final_report_markdown FROM t_submission_stage "
                    "WHERE student_no=%s AND course_id=%s AND stage_num=%s AND status=3",
                    (student_no, course_id, stage - 1)
                )
                row = cur.fetchone()
                if row and row["teacher_score"] is not None:
                    return JointReviewContext(
                        teacher_score=float(row["teacher_score"]),
                        final_report_markdown=row["final_report_markdown"] or ""
                    )
                return None
        finally:
            conn.close()

    def get_course_name(self, course_id: str) -> str:
        """
        查询课程名称 — 用于 LLM System Prompt 上下文。

        从 t_course_project 表查询课程名称，
        如果课程不存在返回空字符串。
        """
        conn = self._get_conn()
        try:
            with conn.cursor() as cur:
                cur.execute("SELECT course_name FROM t_course_project WHERE course_id=%s", (course_id,))
                row = cur.fetchone()
                return row["course_name"] if row else ""
        finally:
            conn.close()

    # ========== MySQL 结果写入 ==========

    def save_eval_result(self, submission_id: int, result: EvalResult):
        """
        保存 AI 评测结果到 MySQL — 评测完成后的核心写入操作。

        写入字段：
          - ai_score: AI 严格分数
          - ai_report_markdown: AI 原始 Markdown 评审报告
          - final_report_markdown: 初始化为 AI 报告（教师后续可修改）
          - model_used: 实际使用的模型名称
          - status: 更新为 2（AI 评测完成/待发布）

        此时学生端完全屏蔽，教师端可预览、改分、改报告。
        """
        conn = self._get_conn()
        try:
            with conn.cursor() as cur:
                cur.execute(
                    "UPDATE t_submission_stage SET "
                    "ai_score=%s, ai_report_markdown=%s, "
                    "final_report_markdown=%s, model_used=%s, status=2 "
                    "WHERE submission_id=%s",
                    (result.ai_score, result.ai_report_markdown,
                     result.ai_report_markdown,  # 初始版本 = AI 原始报告
                     result.model_used, submission_id)
                )
            conn.commit()
            logger.info(f"评测结果已写入 MySQL: submission_id={submission_id}, score={result.ai_score}")
        finally:
            conn.close()

    def revert_submission_status(self, submission_id: int):
        """
        失败时回退 MySQL 状态为 0 — 评测异常时的恢复操作。

        将提交记录状态从 1（AI 评测中）回退为 0（已提交待评测），
        教师可以重新触发评测。
        """
        conn = self._get_conn()
        try:
            with conn.cursor() as cur:
                cur.execute(
                    "UPDATE t_submission_stage SET status=0 WHERE submission_id=%s",
                    (submission_id,)
                )
            conn.commit()
            logger.info(f"提交状态已回退: submission_id={submission_id} → status=0")
        finally:
            conn.close()


# 全局单例 — 供 eval_pipeline.py 和 routes.py 导入使用
persistence_service = PersistenceService()
