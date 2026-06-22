"""
FastAPI 应用主入口（开发文档 3.1 节）

核心职责：
  1. 创建 FastAPI 应用实例，配置元数据
  2. 注册 API 路由（/ai/eval/submit、/ai/health）
  3. 配置生命周期事件（startup / shutdown）
  4. 启动 Uvicorn ASGI 服务器

启动方式：
  开发模式：python -m app.main
  生产模式：uvicorn app.main:app --host 0.0.0.0 --port 8000

依赖：
  - fastapi: 异步 Web 框架
  - uvicorn: ASGI 服务器
  - loguru: 结构化日志
"""
from fastapi import FastAPI
from loguru import logger
from app.config import settings
from app.api.routes import router


def create_app() -> FastAPI:
    """
    创建并配置 FastAPI 应用实例。

    配置项：
      - title: API 文档标题
      - description: API 文档描述
      - version: 服务版本号
      - docs_url: Swagger UI 路径（开发模式可用）
      - redoc_url: ReDoc 路径（开发模式可用）
    """
    app = FastAPI(
        title="AI/OCR 算法服务",
        description="双轨制 AI 评分系统 — Python FastAPI 服务，负责 OCR 图文解析、RAG 评分标准检索、LLM 深度分析",
        version="1.0.0",
        docs_url="/docs" if settings.DEBUG else None,    # 生产环境禁用 Swagger UI
        redoc_url="/redoc" if settings.DEBUG else None,  # 生产环境禁用 ReDoc
    )

    # 注册 API 路由
    app.include_router(router)

    # ========== 生命周期事件 ==========
    @app.on_event("startup")
    async def startup_event():
        """应用启动时执行 — 预热连接和模型"""
        logger.info("=" * 60)
        logger.info("AI/OCR 算法服务启动中...")
        logger.info(f"服务地址: {settings.SERVICE_HOST}:{settings.SERVICE_PORT}")
        logger.info(f"MySQL: {settings.MYSQL_HOST}:{settings.MYSQL_PORT}/{settings.MYSQL_DATABASE}")
        logger.info(f"Redis: {settings.REDIS_HOST}:{settings.REDIS_PORT}")
        logger.info(f"MinIO: {settings.MINIO_ENDPOINT}")
        logger.info(f"Milvus: {settings.MILVUS_HOST}:{settings.MILVUS_PORT}")
        logger.info(f"DeepSeek: {settings.DEEPSEEK_BASE_URL}")
        logger.info(f"Ollama: {settings.OLLAMA_BASE_URL}")
        logger.info(f"Embedding 模型: {settings.EMBEDDING_MODEL}")
        logger.info(f"调试模式: {settings.DEBUG}")
        logger.info("=" * 60)
        logger.info("AI/OCR 算法服务启动完成")

    @app.on_event("shutdown")
    async def shutdown_event():
        """应用关闭时执行 — 清理资源"""
        logger.info("AI/OCR 算法服务正在关闭...")

    return app


# 创建应用实例 — 供 uvicorn 导入
app = create_app()


if __name__ == "__main__":
    """
    直接运行入口 — 开发模式下使用。
    生产环境建议使用 uvicorn 命令行启动：
      uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers 2
    """
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host=settings.SERVICE_HOST,
        port=settings.SERVICE_PORT,
        reload=settings.DEBUG,  # 开发模式启用热重载
        log_level="info"
    )
