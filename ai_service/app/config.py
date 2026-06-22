"""
AI/OCR 算法服务 — 全局配置模块（开发文档 3.1 节）

职责：
  统一管理所有外部依赖的连接参数和业务常量，支持从环境变量或 .env 文件加载。
  与 Java 端 application.yml 中的 ai-service.* 配置段保持一致。

配置项分类：
  1. 服务配置 — FastAPI 监听地址与调试开关
  2. MySQL — 学生提交记录、联合评审上下文的读写
  3. Redis — 任务状态机更新、降级开关读取、配额计数
  4. MinIO — 学生上传文件（.zip 代码包 + 报告）的下载
  5. Milvus — 评分标准向量检索
  6. Embedding 模型 — bge-large-zh-v1.5，1024 维
  7. DeepSeek API — 云端 LLM（优先）
  8. Ollama — 本地私有化降级模型
  9. OCR — PaddleOCR 语言设置
"""

from pydantic_settings import BaseSettings
from typing import Optional


class Settings(BaseSettings):
    """
    全局配置类，继承 pydantic-settings 的 BaseSettings。
    加载优先级：环境变量 > .env 文件 > 类默认值。
    所有字段使用 UPPER_SNAKE_CASE 命名，与环境变量风格一致。
    """

    # ========== 服务配置 ==========
    SERVICE_HOST: str = "0.0.0.0"   # FastAPI 监听地址，生产环境建议 0.0.0.0
    SERVICE_PORT: int = 8000         # FastAPI 监听端口，Java 端通过此端口投递任务
    DEBUG: bool = True               # 调试模式，生产环境设为 False

    # ========== MySQL 数据库 ==========
    MYSQL_HOST: str = "localhost"          # MySQL 主机地址
    MYSQL_PORT: int = 3306                 # MySQL 端口
    MYSQL_USER: str = "root"               # MySQL 用户名
    MYSQL_PASSWORD: str = ""               # MySQL 密码（生产环境务必通过环境变量注入）
    MYSQL_DATABASE: str = "neusoft_ai_grading"  # 数据库名，与 Java 端保持一致

    # ========== Redis ==========
    REDIS_HOST: str = "localhost"           # Redis 主机地址
    REDIS_PORT: int = 6379                  # Redis 端口
    REDIS_DB: int = 0                       # Redis 数据库编号
    REDIS_PASSWORD: Optional[str] = None    # Redis 密码（无密码则为 None）

    # Redis Key 前缀 — 与 Java 端 Key 设计保持严格一致，避免键冲突
    REDIS_TASK_PREFIX: str = "neusoft:task:status:"          # 任务状态机 Hash 前缀，如 neusoft:task:status:{task_id}
    REDIS_QUOTA_PREFIX: str = "neusoft:quota:deepseek:date:" # 每日配额计数器前缀，如 neusoft:quota:deepseek:date:20260616
    REDIS_FALLBACK_KEY: str = "neusoft:config:model_fallback"  # 模型降级开关 Key，值为 "local" 时切换到 Ollama

    # ========== MinIO 对象存储 ==========
    MINIO_ENDPOINT: str = "localhost:9000"    # MinIO 服务地址（不含协议前缀）
    MINIO_ACCESS_KEY: str = "minioadmin"      # MinIO Access Key
    MINIO_SECRET_KEY: str = "minioadmin"      # MinIO Secret Key
    MINIO_BUCKET: str = "neusoft-submissions" # 学生提交文件桶，与 Java 端配置一致
    MINIO_SECURE: bool = False                # 是否使用 HTTPS，本地部署默认 False

    # ========== Milvus 向量数据库 ==========
    MILVUS_HOST: str = "localhost"            # Milvus 服务主机
    MILVUS_PORT: int = 19530                  # Milvus 服务端口（standalone 模式默认 19530）
    MILVUS_COLLECTION: str = "grading_standards"  # 评分标准集合名，与开发文档 4.2 节一致

    # ========== Embedding 模型 ==========
    EMBEDDING_MODEL: str = "BAAI/bge-large-zh-v1.5"  # 中文向量化 SOTA 模型，由 sentence-transformers 本地加载
    EMBEDDING_DIM: int = 1024                         # 向量维度，必须与 Milvus 集合 schema 中的维度一致
    RAG_TOP_K: int = 5                                # RAG 检索返回的最相关评分标准切片数

    # ========== DeepSeek API ==========
    DEEPSEEK_API_KEY: str = ""                        # DeepSeek API 密钥（通过环境变量注入）
    DEEPSEEK_BASE_URL: str = "https://api.deepseek.com"  # DeepSeek API 地址（OpenAI 兼容接口）
    DEEPSEEK_MODEL: str = "deepseek-chat"             # DeepSeek 模型名称

    # ========== Ollama 本地模型（降级方案） ==========
    OLLAMA_BASE_URL: str = "http://localhost:11434"   # Ollama 服务地址
    OLLAMA_MODEL: str = "deepseek-r1-distill-qwen-14b"  # 本地降级模型，显存需求 >= 16GB

    # ========== OCR ==========
    OCR_LANG: str = "ch"  # PaddleOCR 语言设置：ch=中英混合（学生报告通常包含中文+代码）

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


# 全局单例
settings = Settings()


def get_mysql_url() -> str:
    """生成 MySQL 连接 URL"""
    return (
        f"mysql+pymysql://{settings.MYSQL_USER}:{settings.MYSQL_PASSWORD}"
        f"@{settings.MYSQL_HOST}:{settings.MYSQL_PORT}/{settings.MYSQL_DATABASE}"
        f"?charset=utf8mb4"
    )
