"""
RAG 评分标准检索子模块（开发文档 3.2.3 节）

核心职责：
  根据 course_id 和 stage 查询 Milvus 向量数据库中的评分标准切片，
  将学生报告文本通过 Embedding 模型向量化后，在 Milvus 中执行向量相似度检索，
  取 Top-K 相关评分标准切片，拼接为 System Prompt 的上下文。

处理流程：
  1. 根据 course_id 和 stage 查询 Milvus grading_standards 集合
  2. 将学生报告文本 Embedding（bge-large-zh-v1.5，1024 维）
  3. 在 Milvus 中执行向量相似度检索，取 Top-K 相关评分标准切片
  4. 拼接为 System Prompt 的上下文，交给 LLM 模块

Embedding 模型：
  模型: BAAI/bge-large-zh-v1.5
  维度: 1024
  加载方式: sentence-transformers 本地加载，GPU 加速（如有）

Milvus 集合设计（grading_standards）：
  chunk_id: INT64 主键，自动递增
  vector: FLOAT_VECTOR(1024)，bge-large-zh-v1.5 生成
  course_id: VARCHAR(32)，标量索引，多课程并发时做数据隔离
  teacher_id: VARCHAR(32)，上传评分标准的教师工号
  stage: INT32，标量索引（0=通用 / 1=阶段一 / 2=阶段二 / 3=阶段三）
  content: VARCHAR(4000)，原始评分规则文本明文

索引策略：
  向量字段：HNSW 索引（M=16, efConstruction=200），余弦相似度
  标量字段：course_id + stage 联合标量索引

状态更新：
  进入时 Redis 状态 → 30（RAG 检索中）
  完成后 Redis 状态 → 40（LLM 分析中），由调用方 EvalPipeline 负责更新
"""
from typing import Optional
from loguru import logger
from app.config import settings


class RAGService:
    """
    RAG 评分标准检索服务 — 单例模式，懒加载 Embedding 模型和 Milvus 连接。

    Embedding 模型初始化较慢（需加载 ~1.3GB 模型权重），采用懒加载策略。
    Milvus 连接在首次检索时建立，后续复用。
    """

    def __init__(self):
        self._embedding_model = None       # SentenceTransformer 模型实例（懒加载）
        self._milvus_connected = False     # Milvus 连接状态标志

    def _ensure_milvus(self):
        """确保 Milvus 连接已建立 — 幂等操作，多次调用无副作用"""
        if not self._milvus_connected:
            from pymilvus import connections
            connections.connect(alias="default", host=settings.MILVUS_HOST, port=settings.MILVUS_PORT)
            self._milvus_connected = True
            logger.info(f"Milvus 连接已建立: {settings.MILVUS_HOST}:{settings.MILVUS_PORT}")

    @property
    def embedding_model(self):
        """
        懒加载 Embedding 模型。

        使用 sentence-transformers 加载 bge-large-zh-v1.5 模型，
        该模型是中文向量化 SOTA 模型，输出 1024 维密集向量。
        首次加载较慢（需下载模型权重），后续调用复用同一实例。
        """
        if self._embedding_model is None:
            from sentence_transformers import SentenceTransformer
            logger.info(f"正在加载 Embedding 模型: {settings.EMBEDDING_MODEL}（首次加载较慢）")
            self._embedding_model = SentenceTransformer(settings.EMBEDDING_MODEL)
            logger.info("Embedding 模型加载完成")
        return self._embedding_model

    async def retrieve_grading_standards(self, course_id: str, stage: int, query_text: str, top_k: int = None) -> str:
        """
        检索与学生报告最相关的评分标准切片 — 主入口方法。

        参数：
            course_id: 课程项目 ID，用于 Milvus 标量过滤
            stage: 阶段编号 1/2/3，用于 Milvus 标量过滤（同时检索 stage=0 的通用标准）
            query_text: 学生报告文本，用于生成查询向量
            top_k: 返回的最相关切片数，默认使用配置中的 RAG_TOP_K

        返回：
            拼接好的评分标准文本，格式为：
            [标准 1] (相关度:0.856)
            <评分规则文本>

            [标准 2] (相关度:0.743)
            <评分规则文本>

            如果检索失败或无结果，返回空字符串（不影响后续 LLM 调用）
        """
        if top_k is None:
            top_k = settings.RAG_TOP_K

        try:
            # 步骤 1：将学生报告文本向量化（归一化，用于余弦相似度）
            vector = self.embedding_model.encode(query_text, normalize_embeddings=True).tolist()
            logger.info(f"报告文本已向量化，维度: {len(vector)}")

            # 步骤 2：在 Milvus 中执行向量相似度检索
            hits = self._search_milvus(course_id, stage, vector, top_k)

            if not hits:
                logger.warning(f"未检索到相关评分标准: course_id={course_id}, stage={stage}")
                return ""

            # 步骤 3：拼接检索结果为结构化文本
            parts = [
                f"[标准 {i}] (相关度:{h['distance']:.3f})\n{h['content']}"
                for i, h in enumerate(hits, 1)
            ]
            result = "\n\n".join(parts)
            logger.info(f"RAG 检索完成，返回 {len(hits)} 条评分标准切片")
            return result

        except Exception as e:
            logger.error(f"RAG 检索失败: {e}")
            return ""  # 检索失败时返回空字符串，LLM 将使用默认评审标准

    def _search_milvus(self, course_id: str, stage: int, vector: list, top_k: int) -> list:
        """
        在 Milvus 中执行向量相似度检索。

        检索策略：
          - 标量过滤：course_id 精确匹配 + stage 匹配（同时检索通用标准 stage=0）
          - 向量检索：HNSW 索引，余弦相似度，ef=64
          - 返回字段：content（原始文本）、course_id、stage

        参数：
            course_id: 课程 ID
            stage: 阶段编号
            vector: 查询向量（1024 维，已归一化）
            top_k: 返回数量

        返回：
            [{"content": "评分规则文本", "distance": 0.856}, ...]
        """
        self._ensure_milvus()

        from pymilvus import Collection, utility

        # 检查集合是否存在
        if not utility.has_collection(settings.MILVUS_COLLECTION):
            logger.warning(f"Milvus 集合不存在: {settings.MILVUS_COLLECTION}")
            return []

        col = Collection(settings.MILVUS_COLLECTION)
        col.load()  # 将集合数据加载到内存（加速检索）

        # 标量过滤表达式：匹配当前课程 + 当前阶段或通用标准
        expr = f'course_id == "{course_id}" and (stage == 0 or stage == {stage})'

        results = col.search(
            data=[vector],                    # 查询向量
            anns_field="vector",              # 搜索的向量字段名
            param={"metric_type": "COSINE", "params": {"ef": 64}},  # HNSW 搜索参数
            limit=top_k,                      # 返回 Top-K
            expr=expr,                        # 标量过滤条件
            output_fields=["content", "course_id", "stage"]  # 返回的标量字段
        )

        if not results:
            return []

        return [
            {"content": h.entity.get("content", ""), "distance": h.distance}
            for h in results[0]
        ]


# 全局单例 — 供 eval_pipeline.py 导入使用
rag_service = RAGService()
