"""
评分标准处理服务模块（开发文档 4.3 节）

核心职责：
  教师上传评分标准文档后，由本服务进行文档解析、文本切片、Embedding 向量化，
  最终写入 Milvus 向量数据库，供 RAG 检索模块在评测时使用。

处理流程：
  1. 从 MinIO 下载教师上传的评分标准文档（.docx / .pdf / .txt）
  2. 解析文档，提取纯文本内容
  3. 将文本按固定长度切片（chunk），每个切片约 500 字符，重叠 50 字符
  4. 使用 bge-large-zh-v1.5 模型将每个切片向量化（1024 维）
  5. 将切片文本 + 向量 + 元数据写入 Milvus grading_standards 集合
  6. 删除 Milvus 中该课程该阶段的旧数据（实现覆盖更新）

Milvus 集合设计（grading_standards）：
  chunk_id: INT64 主键，自动递增
  vector: FLOAT_VECTOR(1024)，bge-large-zh-v1.5 生成
  course_id: VARCHAR(32)，标量索引
  teacher_id: VARCHAR(32)
  stage: INT32，标量索引（0=通用 / 1=阶段一 / 2=阶段二 / 3=阶段三）
  content: VARCHAR(4000)，原始评分规则文本明文

依赖：
  - pymilvus: Milvus 客户端，写入向量数据
  - sentence-transformers: Embedding 模型
  - python-docx: 解析 .docx 文档
  - PyPDF2: 解析 .pdf 文档
  - minio: 下载评分标准文档
"""
import os
import tempfile
from typing import Optional
from loguru import logger
from minio import Minio
from app.config import settings


class StandardService:
    """
    评分标准处理服务 — 单例模式，懒加载 Embedding 模型和 Milvus 连接。

    与 RAGService 共享 Embedding 模型配置，但独立实例化（避免循环依赖）。
    """

    def __init__(self):
        self._minio_client: Optional[Minio] = None
        self._embedding_model = None
        self._milvus_connected = False

    @property
    def minio_client(self) -> Minio:
        """懒加载 MinIO 客户端"""
        if self._minio_client is None:
            self._minio_client = Minio(
                settings.MINIO_ENDPOINT,
                access_key=settings.MINIO_ACCESS_KEY,
                secret_key=settings.MINIO_SECRET_KEY,
                secure=settings.MINIO_SECURE,
            )
        return self._minio_client

    @property
    def embedding_model(self):
        """懒加载 Embedding 模型"""
        if self._embedding_model is None:
            from sentence_transformers import SentenceTransformer
            logger.info(f"正在加载 Embedding 模型: {settings.EMBEDDING_MODEL}")
            self._embedding_model = SentenceTransformer(settings.EMBEDDING_MODEL)
            logger.info("Embedding 模型加载完成")
        return self._embedding_model

    def _ensure_milvus(self):
        """确保 Milvus 连接已建立"""
        if not self._milvus_connected:
            from pymilvus import connections
            connections.connect(alias="default", host=settings.MILVUS_HOST, port=settings.MILVUS_PORT)
            self._milvus_connected = True
            logger.info(f"Milvus 连接已建立: {settings.MILVUS_HOST}:{settings.MILVUS_PORT}")

    async def process_standard(self, object_name: str, course_id: str, stage: int, teacher_id: str = ""):
        """
        处理评分标准文档 — 主入口方法。

        参数：
            object_name: MinIO 中的文档路径（如 standards/{course_id}/{uuid}.docx）
            course_id: 课程项目 ID
            stage: 适用阶段（0=通用 / 1=阶段一 / 2=阶段二 / 3=阶段三）
            teacher_id: 上传该标准的教师工号

        流程：
          1. 从 MinIO 下载文档
          2. 解析文档提取纯文本
          3. 文本切片（chunk）
          4. Embedding 向量化
          5. 删除旧数据 + 写入 Milvus
        """
        logger.info(f"开始处理评分标准: object={object_name}, course={course_id}, stage={stage}")

        # 步骤 1：从 MinIO 下载文档
        tmp_dir = tempfile.mkdtemp(prefix="standard_")
        try:
            filename = object_name.split("/")[-1]
            local_path = os.path.join(tmp_dir, filename)
            self.minio_client.fget_object(settings.MINIO_BUCKET, object_name, local_path)
            logger.info(f"评分标准文档已下载: {local_path}")

            # 步骤 2：解析文档提取纯文本
            text = self._parse_document(local_path)
            logger.info(f"文档解析完成，文本长度: {len(text)} 字符")

            # 步骤 3：文本切片
            chunks = self._split_text(text, chunk_size=500, overlap=50)
            logger.info(f"文本切片完成，共 {len(chunks)} 个切片")

            # 步骤 4：Embedding 向量化
            vectors = self.embedding_model.encode(chunks, normalize_embeddings=True).tolist()
            logger.info(f"向量化完成，维度: {len(vectors[0]) if vectors else 0}")

            # 步骤 5：删除旧数据 + 写入 Milvus
            self._save_to_milvus(chunks, vectors, course_id, stage, teacher_id)
            logger.info(f"评分标准处理完成: {len(chunks)} 个切片已写入 Milvus")

        finally:
            import shutil
            shutil.rmtree(tmp_dir, ignore_errors=True)

    def _parse_document(self, file_path: str) -> str:
        """
        解析评分标准文档，提取纯文本。

        支持格式：
          - .docx: 使用 python-docx 解析段落文本
          - .pdf: 使用 PyPDF2 提取文字层
          - .txt: 直接读取
        """
        if file_path.endswith(".docx"):
            from docx import Document
            doc = Document(file_path)
            return "\n\n".join(p.text.strip() for p in doc.paragraphs if p.text.strip())
        elif file_path.endswith(".pdf"):
            from PyPDF2 import PdfReader
            reader = PdfReader(file_path)
            texts = [page.extract_text().strip() for page in reader.pages if page.extract_text()]
            return "\n\n".join(texts)
        elif file_path.endswith(".txt"):
            with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
                return f.read()
        else:
            raise ValueError(f"不支持的文档格式: {file_path}")

    def _split_text(self, text: str, chunk_size: int = 500, overlap: int = 50) -> list:
        """
        文本切片 — 按固定长度切分，带重叠区域。

        参数：
            text: 原始文本
            chunk_size: 每个切片的最大字符数（默认 500）
            overlap: 相邻切片的重叠字符数（默认 50）

        切片策略：
          - 按 chunk_size 切分，overlap 保证上下文连续性
          - 最后一个切片可能不足 chunk_size，直接保留剩余文本
        """
        if not text:
            return []
        chunks = []
        start = 0
        while start < len(text):
            end = start + chunk_size
            chunks.append(text[start:end])
            start = end - overlap  # 重叠区域
        return chunks

    def _save_to_milvus(self, chunks: list, vectors: list, course_id: str, stage: int, teacher_id: str):
        """
        将切片数据写入 Milvus — 先删除旧数据，再插入新数据。

        删除策略：
          删除该课程该阶段的所有旧切片，实现覆盖更新。
          expr: course_id == "{course_id}" and stage == {stage}
        """
        self._ensure_milvus()
        from pymilvus import Collection, utility, FieldSchema, CollectionSchema, DataType

        collection_name = settings.MILVUS_COLLECTION

        # 如果集合不存在，创建集合
        if not utility.has_collection(collection_name):
            self._create_collection(collection_name)

        col = Collection(collection_name)

        # 删除该课程该阶段的旧数据
        expr = f'course_id == "{course_id}" and stage == {stage}'
        try:
            col.delete(expr)
            logger.info(f"已删除旧数据: {expr}")
        except Exception as e:
            logger.warning(f"删除旧数据时异常（可能无旧数据）: {e}")

        # 插入新数据
        entities = [
            [course_id] * len(chunks),    # course_id 列
            [teacher_id] * len(chunks),   # teacher_id 列
            [stage] * len(chunks),        # stage 列
            chunks,                        # content 列
            vectors                        # vector 列
        ]
        col.insert(entities)
        col.flush()
        logger.info(f"已写入 {len(chunks)} 条数据到 Milvus 集合 {collection_name}")

    def _create_collection(self, collection_name: str):
        """
        创建 Milvus 集合 grading_standards — 仅在集合不存在时调用。

        集合 Schema（与开发文档 4.2 节一致）：
          chunk_id: INT64 主键，自动递增
          course_id: VARCHAR(32)，标量索引
          teacher_id: VARCHAR(32)
          stage: INT32，标量索引
          content: VARCHAR(4000)
          vector: FLOAT_VECTOR(1024)

        索引策略：
          向量字段：HNSW 索引（M=16, efConstruction=200），余弦相似度
          标量字段：course_id + stage 联合标量索引
        """
        from pymilvus import FieldSchema, CollectionSchema, DataType, Collection, utility

        fields = [
            FieldSchema(name="chunk_id", dtype=DataType.INT64, is_primary=True, auto_id=True),
            FieldSchema(name="course_id", dtype=DataType.VARCHAR, max_length=32),
            FieldSchema(name="teacher_id", dtype=DataType.VARCHAR, max_length=32),
            FieldSchema(name="stage", dtype=DataType.INT32),
            FieldSchema(name="content", dtype=DataType.VARCHAR, max_length=4000),
            FieldSchema(name="vector", dtype=DataType.FLOAT_VECTOR, dim=settings.EMBEDDING_DIM),
        ]
        schema = CollectionSchema(fields, description="评分标准切片集合")
        col = Collection(collection_name, schema)

        # 创建 HNSW 向量索引
        col.create_index(
            field_name="vector",
            index_params={
                "index_type": "HNSW",
                "metric_type": "COSINE",
                "params": {"M": 16, "efConstruction": 200}
            }
        )
        # 创建标量索引
        col.create_index(field_name="course_id", index_params={"index_type": "TRIE"})
        col.create_index(field_name="stage", index_params={"index_type": "TRIE"})

        logger.info(f"Milvus 集合已创建: {collection_name}")


# 全局单例
standard_service = StandardService()
