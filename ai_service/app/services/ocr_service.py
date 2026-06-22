"""
OCR 图文解析子模块（开发文档 3.2.2 节）

核心职责：
  从 MinIO 下载学生上传的 .zip 代码包和 .docx/.pdf 报告，
  解析报告中的段落文本和嵌入图片，通过 PaddleOCR 识别截图中的文字，
  将 OCR 结果原位插入到对应段落位置，输出完整的结构化报告文本。

处理流程：
  1. 从 MinIO 下载学生上传的 .zip 文件到临时目录
  2. 解压 .zip，定位报告 Word 文档（.docx）
  3. 使用 python-docx 解析 .docx，提取正文段落文本
  4. 遍历文档中嵌入的图片（实验截图、控制台截图、流程图等），调用 PaddleOCR 识别文字
  5. OCR 结果按图片在文档中的出现顺序，原位插入到对应段落位置，拼接成完整的结构化报告文本
  6. 可选：从代码包中提取 README 文件内容作为补充信息
  7. 输出结构化文本，交给下游 RAG + LLM 模块

关键设计点：
  - OCR 的目标不是单独的图片文件，而是 Word 报告文档中嵌入的截图
  - 学生将实验过程截图粘贴到报告中，这些截图包含关键的代码和运行结果
  - 必须通过 OCR 提取后才能被 LLM 理解和评分
  - 图片置信度阈值：仅保留置信度 > 0.5 的 OCR 识别行，过滤噪声

状态更新：
  进入时 Redis 状态 → 20（OCR 解析中）
  完成后 Redis 状态 → 30（RAG 检索中），由调用方 EvalPipeline 负责更新

依赖：
  - PaddleOCR: 百度开源 OCR 引擎，中文识别精度最优，支持 GPU 加速
  - python-docx: 解析 .docx 文档，提取段落和嵌入图片
  - PyPDF2: 解析 .pdf 文档，提取纯文本（PDF 不支持嵌入图片 OCR）
  - minio: MinIO 对象存储客户端，下载学生上传的文件
"""
import os
import shutil
import tempfile
import zipfile
from typing import Optional
from loguru import logger
from minio import Minio
from app.config import settings


class OCRService:
    """
    OCR 图文解析服务 — 单例模式，懒加载 PaddleOCR 引擎和 MinIO 客户端。

    PaddleOCR 引擎初始化较慢（需加载模型），采用懒加载策略：
    首次调用 ocr_engine 属性时才初始化，后续调用复用同一实例。
    """

    def __init__(self):
        self._minio_client: Optional[Minio] = None  # MinIO 客户端实例（懒加载）
        self._ocr_engine = None  # PaddleOCR 引擎实例（懒加载）

    @property
    def minio_client(self) -> Minio:
        """懒加载 MinIO 客户端，配置与 Java 端 MinioConfig 保持一致"""
        if self._minio_client is None:
            self._minio_client = Minio(
                settings.MINIO_ENDPOINT,
                access_key=settings.MINIO_ACCESS_KEY,
                secret_key=settings.MINIO_SECRET_KEY,
                secure=settings.MINIO_SECURE,
            )
        return self._minio_client

    @property
    def ocr_engine(self):
        """
        懒加载 PaddleOCR 引擎。

        配置说明：
          - use_angle_cls=True: 启用文字方向分类器，处理旋转截图
          - lang="ch": 中英混合识别（学生报告通常包含中文+代码）
          - show_log=False: 关闭 PaddleOCR 内部日志，避免刷屏
        """
        if self._ocr_engine is None:
            from paddleocr import PaddleOCR
            logger.info("正在初始化 PaddleOCR 引擎（首次加载较慢）...")
            self._ocr_engine = PaddleOCR(use_angle_cls=True, lang=settings.OCR_LANG, show_log=False)
            logger.info("PaddleOCR 初始化完成")
        return self._ocr_engine

    async def parse_submission(self, code_package_path: str, report_path: str) -> str:
        """
        解析学生提交的完整流程 — 主入口方法。

        参数：
            code_package_path: 代码压缩包 MinIO 路径（如 course001/2022001/1/code/uuid.zip）
            report_path: 图文报告 MinIO 路径（如 course001/2022001/1/report/uuid.docx）

        返回：
            完整的结构化报告文本，包含段落文本 + OCR 识别结果 + 代码包 README

        异常：
            ValueError: 不支持的报告格式（非 .docx/.pdf）
            任何 MinIO 下载失败的异常会被向上传播
        """
        tmp_dir = tempfile.mkdtemp(prefix="ai_eval_")  # 创建临时目录，用完即删
        try:
            # 步骤 1：从 MinIO 下载报告文件
            report_local = self._download_from_minio(report_path, tmp_dir)
            logger.info(f"报告已下载到临时目录: {report_local}")

            # 步骤 2：根据报告格式选择解析策略
            if report_local.endswith(".docx"):
                structured_text = self._parse_docx(report_local, tmp_dir)
            elif report_local.endswith(".pdf"):
                structured_text = self._parse_pdf(report_local, tmp_dir)
            else:
                raise ValueError(f"不支持的报告格式: {report_local}，仅支持 .docx 和 .pdf")

            # 步骤 3：可选 — 提取代码包中的 README 文件
            code_text = ""
            if code_package_path and code_package_path.endswith(".zip"):
                code_local = self._download_from_minio(code_package_path, tmp_dir)
                code_text = self._extract_code_info(code_local, tmp_dir)

            # 步骤 4：拼接最终结构化文本
            final_text = structured_text
            if code_text:
                final_text += "\n\n---\n\n## 代码包补充信息\n\n" + code_text

            logger.info(f"文档解析完成，总长度: {len(final_text)} 字符")
            return final_text
        finally:
            # 清理临时目录，无论成功或失败都执行
            shutil.rmtree(tmp_dir, ignore_errors=True)

    def _download_from_minio(self, object_name: str, dest_dir: str) -> str:
        """
        从 MinIO 下载文件到本地临时目录。

        参数：
            object_name: MinIO 中的对象路径（混淆后的 UUID 路径）
            dest_dir: 本地临时目录路径

        返回：
            下载后的本地文件完整路径
        """
        filename = object_name.split("/")[-1]  # 从路径中提取文件名
        local_path = os.path.join(dest_dir, filename)
        self.minio_client.fget_object(settings.MINIO_BUCKET, object_name, local_path)
        return local_path

    def _parse_docx(self, docx_path: str, tmp_dir: str) -> str:
        """
        解析 .docx 文档 — 核心方法。

        处理逻辑：
          1. 遍历文档 body 中的所有元素（段落 p 和表格 tbl）
          2. 对于段落：提取文本内容 + 提取嵌入图片 → PaddleOCR 识别
          3. 对于表格：提取单元格文本，用 | 分隔
          4. OCR 结果按图片出现顺序原位插入到对应段落位置

        这是 OCR 子模块最关键的方法，实现了"截图中的文字 → 结构化文本"的转换。
        """
        from docx import Document
        doc = Document(docx_path)
        paragraphs = []  # 存储所有段落的最终文本（含 OCR 插入）
        image_counter = 0  # 全局图片计数器，用于标记 [图片N OCR]

        # 遍历文档 body 中的所有 XML 元素（段落和表格交替出现）
        for element in doc.element.body:
            # 提取 XML 标签名（去掉命名空间前缀）
            tag = element.tag.split("}")[-1] if "}" in element.tag else element.tag

            if tag == "p":
                # ========== 处理段落 ==========
                from docx.text.paragraph import Paragraph
                para = Paragraph(element, doc)
                text = para.text.strip()  # 提取段落纯文本

                # 提取该段落中嵌入的所有图片并进行 OCR 识别
                images = self._extract_images_from_paragraph(element, doc, tmp_dir)
                for img_path in images:
                    image_counter += 1
                    ocr_text = self._ocr_image(img_path)
                    if ocr_text:
                        # 将 OCR 结果原位追加到段落文本后面
                        text += f"\n[图片{image_counter} OCR]:\n{ocr_text}\n"

                paragraphs.append(text)

            elif tag == "tbl":
                # ========== 处理表格 ==========
                from docx.table import Table
                table = Table(element, doc)
                rows = []
                for row in table.rows:
                    # 将每行单元格用 | 分隔，模拟 Markdown 表格格式
                    rows.append(" | ".join(c.text.strip() for c in row.cells))
                paragraphs.append("\n".join(rows))

        logger.info(f"DOCX 解析完成：{len(paragraphs)} 段落，{image_counter} 张图片 OCR 识别")
        return "\n\n".join(paragraphs)

    def _extract_images_from_paragraph(self, para_element, doc, tmp_dir: str) -> list:
        """
        从 Word 段落中提取嵌入的图片文件。

        Word 文档的图片存储机制：
          1. 图片二进制数据存储在 docx 包的 word/media/ 目录下
          2. 段落 XML 中通过 <a:blip r:embed="rIdX"/> 引用图片
          3. rIdX 对应 doc.part.related_parts 中的图片资源

        参数：
            para_element: 段落的 lxml Element 对象
            doc: python-docx Document 对象
            tmp_dir: 临时目录，用于保存提取的图片文件

        返回：
            提取的图片文件路径列表
        """
        image_paths = []
        # Word XML 命名空间映射
        nsmap = {
            "a": "http://schemas.openxmlformats.org/drawingml/2006/main",  # DrawingML 命名空间
            "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",  # Relationships 命名空间
        }
        # 查找段落中所有 <a:blip> 元素（图片引用）
        blips = para_element.findall(".//a:blip", nsmap)
        for blip in blips:
            r_embed = blip.get("{http://schemas.openxmlformats.org/officeDocument/2006/relationships}embed")
            if r_embed:
                try:
                    # 通过 rId 获取图片资源
                    image_part = doc.part.related_parts[r_embed]
                    # 根据 MIME 类型确定文件扩展名
                    ext = ".jpg" if "jpeg" in image_part.content_type or "jpg" in image_part.content_type else ".png"
                    img_path = os.path.join(tmp_dir, f"img_{r_embed}{ext}")
                    # 将图片二进制数据写入临时文件
                    with open(img_path, "wb") as f:
                        f.write(image_part.blob)
                    image_paths.append(img_path)
                except (KeyError, Exception) as e:
                    logger.warning(f"提取图片失败 rId={r_embed}: {e}")
        return image_paths

    def _ocr_image(self, image_path: str) -> str:
        """
        使用 PaddleOCR 识别单张图片中的文字。

        参数：
            image_path: 图片文件的本地路径

        返回：
            OCR 识别结果文本（多行，每行对应图片中的一行文字）
            仅保留置信度 > 0.5 的识别行，过滤低质量噪声

        异常处理：
            OCR 失败时返回空字符串，不影响整体流程
        """
        try:
            result = self.ocr_engine.ocr(image_path, cls=True)
            if not result or not result[0]:
                return ""
            # 过滤置信度低于 0.5 的识别行，减少噪声
            lines = [line[1][0] for line in result[0] if line[1][1] > 0.5]
            return "\n".join(lines)
        except Exception as e:
            logger.warning(f"OCR 识别失败 {image_path}: {e}")
            return ""

    def _parse_pdf(self, pdf_path: str, tmp_dir: str) -> str:
        """
        解析 .pdf 文档 — 提取纯文本。

        注意：PDF 格式不支持嵌入图片的 OCR 识别（图片已嵌入为位图），
        仅提取文字层。如果 PDF 是扫描件（纯图片），则无法提取文字。
        建议学生优先使用 .docx 格式提交报告。
        """
        from PyPDF2 import PdfReader
        reader = PdfReader(pdf_path)
        texts = [page.extract_text().strip() for page in reader.pages if page.extract_text()]
        logger.info(f"PDF 解析完成：{len(texts)} 页")
        return "\n\n".join(texts)

    def _extract_code_info(self, zip_path: str, tmp_dir: str) -> str:
        """
        从代码压缩包中提取 README 文件内容。

        作为补充信息附加到报告文本后面，帮助 LLM 理解项目结构。
        仅提取 README.md 或 README.txt，最大读取 5000 字符。
        """
        code_tmp = os.path.join(tmp_dir, "code_extract")
        os.makedirs(code_tmp, exist_ok=True)
        try:
            with zipfile.ZipFile(zip_path, "r") as zf:
                zf.extractall(code_tmp)
        except zipfile.BadZipFile:
            logger.warning(f"代码包损坏或非 ZIP 格式: {zip_path}")
            return ""
        info_parts = []
        # 查找 README 文件（大小写不敏感）
        for name in ["README.md", "README.txt", "readme.md", "readme.txt"]:
            p = os.path.join(code_tmp, name)
            if os.path.exists(p):
                with open(p, "r", encoding="utf-8", errors="ignore") as f:
                    info_parts.append(f"### README\n\n{f.read()[:5000]}")
                break
        return "\n\n".join(info_parts)


# 全局单例 — 供 eval_pipeline.py 导入使用
ocr_service = OCRService()
