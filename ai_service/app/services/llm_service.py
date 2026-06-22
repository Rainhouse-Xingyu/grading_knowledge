"""
LLM 深度分析子模块（开发文档 3.2.4 节）

核心职责：
  接收 OCR 解析后的结构化报告文本和 RAG 检索到的评分标准，
  调用 DeepSeek API 或 Ollama 本地模型进行深度分析评分，
  输出 AI 严格分数（0-100）和 Markdown 格式评审报告。

模型路由逻辑（降级自适应）：
  1. 读取 Redis 中的降级开关 neusoft:config:model_fallback
  2. 如果值为 "local"，则使用 Ollama 本地模型
  3. 否则使用 DeepSeek API（优先）
  4. 如果 DeepSeek API 调用失败，自动降级到 Ollama

联合评审上下文（教师开启 is_joint_review=1 时）：
  - 从 MySQL t_submission_stage 查询该学生上一阶段（stage_num - 1）
    被教师最终锁定的 teacher_score、final_report_markdown（状态必须为 3-已下发）
  - 拼接到 System Prompt，要求 LLM 审查本次提交是否有"实质性工作增量"

输出：
  - AI 严格分（0-100）
  - Markdown 格式评审报告
  - 实际使用的模型名称（用于监控降级切换）

状态更新：
  进入时 Redis 状态 → 40（LLM 分析中）
  完成后写入 MySQL，Redis 状态 → 50（完成）

依赖：
  - openai: DeepSeek 通过 OpenAI 兼容接口调用
  - ollama: 本地私有化模型调用（降级方案）
"""
import re
from loguru import logger
from app.config import settings
from app.models.schemas import EvalResult


class LLMService:
    """
    LLM 评审服务 — 支持 DeepSeek API / Ollama 自适应切换。

    设计原则：
      1. 优先使用 DeepSeek 云端 API（学校已购企业配额）
      2. DeepSeek 失败时自动降级到 Ollama 本地模型
      3. 降级时记录实际使用的模型名称，便于监控
      4. 报告文本截断到 30000 字符，避免超出 LLM 上下文窗口
    """

    async def evaluate(self, student_report: str, grading_standards: str,
                       course_name: str = "", stage: int = 1,
                       joint_review_context: str = "",
                       use_local_model: bool = False) -> EvalResult:
        """
        执行 AI 评分 — 主入口方法。

        参数：
            student_report: OCR 解析后的完整结构化报告文本
            grading_standards: RAG 检索到的评分标准文本
            course_name: 课程名称，用于 System Prompt 上下文
            stage: 阶段编号 1/2/3
            joint_review_context: 联合评审上下文（上一阶段教师终审评语），可为空
            use_local_model: 是否强制使用本地模型（降级标志）

        返回：
            EvalResult: 包含 ai_score、ai_report_markdown、model_used

        异常处理：
            DeepSeek 调用失败时自动降级到 Ollama
            Ollama 也失败时抛出异常，由调用方处理
        """
        # 构建 System Prompt（包含评分标准 + 联合评审上下文）
        system_prompt = self._build_system_prompt(grading_standards, course_name, stage, joint_review_context)
        # 构建 User Prompt（截断报告文本到 30000 字符，避免超出上下文窗口）
        user_prompt = f"以下是学生提交的实验报告：\n\n{student_report[:30000]}"

        model_name = "Ollama-Local" if use_local_model else "DeepSeek-R1"
        logger.info(f"开始 LLM 评分，模型: {model_name}，报告长度: {len(student_report)} 字符")

        try:
            # 根据标志选择调用 DeepSeek 或 Ollama
            if use_local_model:
                text = self._call_ollama(system_prompt, user_prompt)
            else:
                text = self._call_deepseek(system_prompt, user_prompt)

            # 解析 LLM 输出，提取分数和报告
            result = self._parse_output(text)
            result.model_used = model_name
            logger.info(f"LLM 评分完成: score={result.ai_score}, model={model_name}")
            return result

        except Exception as e:
            logger.error(f"LLM 调用失败({model_name}): {e}")
            # DeepSeek 失败时自动降级到 Ollama
            if not use_local_model:
                logger.warning("DeepSeek 调用失败，自动降级到 Ollama 本地模型")
                return await self.evaluate(
                    student_report, grading_standards, course_name,
                    stage, joint_review_context, use_local_model=True
                )
            raise  # Ollama 也失败时抛出异常

    def _build_system_prompt(self, standards: str, name: str, stage: int, joint_ctx: str) -> str:
        """
        构建 LLM System Prompt。

        Prompt 结构：
          1. 角色设定 — 严格的大学课程作业评审专家
          2. 课程和阶段信息
          3. 评分标准（来自 RAG 检索）
          4. 联合评审上下文（如有）— 要求审查实质性工作增量
          5. 输出格式要求 — 逐项评分 + Markdown 报告 + 最终分数
        """
        prompt = (
            f"你是严格的大学课程作业评审专家。\n"
            f"## 课程：{name or '未指定'} | 阶段：{stage}\n"
            f"## 评分标准\n{standards or '（无标准，按学术规范评审）'}\n"
        )
        # 联合评审：拼接上一阶段教师终审上下文
        if joint_ctx:
            prompt += (
                f"\n## 上阶段教师终审（联合评审）\n{joint_ctx}\n"
                f"请审查本次提交相比上一阶段是否有实质性的工作增量。\n"
            )
        # 输出格式要求
        prompt += (
            "\n请逐项评分(0-100)，输出Markdown报告，"
            "末尾写【最终分数】: XX 分\n"
        )
        return prompt

    def _call_deepseek(self, sys_prompt: str, usr_prompt: str) -> str:
        """
        调用 DeepSeek API（OpenAI 兼容接口）。

        配置：
          - temperature=0.3: 较低温度，确保评分一致性
          - max_tokens=4096: 足够生成完整评审报告
          - model: 从配置读取，默认 deepseek-chat
        """
        from openai import OpenAI
        client = OpenAI(api_key=settings.DEEPSEEK_API_KEY, base_url=settings.DEEPSEEK_BASE_URL)
        resp = client.chat.completions.create(
            model=settings.DEEPSEEK_MODEL,
            messages=[
                {"role": "system", "content": sys_prompt},
                {"role": "user", "content": usr_prompt}
            ],
            temperature=0.3,
            max_tokens=4096
        )
        return resp.choices[0].message.content

    def _call_ollama(self, sys_prompt: str, usr_prompt: str) -> str:
        """
        调用 Ollama 本地模型（降级方案）。

        模型：deepseek-r1-distill-qwen-14b（显存需求 >= 16GB）
        通过 Ollama Python SDK 调用，使用本地 HTTP 接口。
        """
        import ollama
        resp = ollama.chat(
            model=settings.OLLAMA_MODEL,
            messages=[
                {"role": "system", "content": sys_prompt},
                {"role": "user", "content": usr_prompt}
            ]
        )
        return resp["message"]["content"]

    def _parse_output(self, text: str) -> EvalResult:
        """
        解析 LLM 输出，提取最终分数。

        支持多种分数格式（优先级从高到低）：
          1. 【最终分数】: 85 分
          2. 最终分数: 85 / 总分: 85
          3. 85/100

        如果无法解析分数，默认返回 60 分（保守估计）。
        分数限制在 0-100 范围内。
        """
        score = None
        # 按优先级尝试多种正则匹配
        patterns = [
            r"【最终分数】\s*[:：]\s*(\d+\.?\d*)\s*分",   # 【最终分数】: 85 分
            r"(?:最终分数|总分)\s*[:：]\s*(\d+\.?\d*)",    # 最终分数: 85 或 总分: 85
            r"(\d+\.?\d*)\s*/\s*100",                     # 85/100
        ]
        for pattern in patterns:
            m = re.search(pattern, text)
            if m:
                score = float(m.group(1))
                break

        # 无法解析时使用默认分数
        if score is None:
            logger.warning("无法从 LLM 输出中解析分数，使用默认分数 60")
            score = 60.0

        return EvalResult(
            ai_score=max(0, min(100, score)),     # 限制在 0-100 范围
            ai_report_markdown=text                # 完整的 LLM 输出作为 Markdown 报告
        )


# 全局单例 — 供 eval_pipeline.py 导入使用
llm_service = LLMService()
