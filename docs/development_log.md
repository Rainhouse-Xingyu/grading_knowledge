# 双轨制 AI 评分系统 — 后端开发日志

> 本文档记录每次开发任务的变更内容、新增文件、改动要点及测试情况。
> 每次任务完成后在此追加记录，便于回溯与交接。

---

## 2026-06-15 — 模块 2.2.1 认证授权模块 `auth`

### 目标

根据开发文档 2.2.1 节实现认证授权模块，字段参考 `database_schema_v3.sql`。

### 新增文件

| 文件路径 | 说明 |
|:---|:---|
| `common/BizException.java` | 业务异常基类，带 HTTP 状态码，提供 `unauthorized()` 快捷构造 |
| `common/GlobalExceptionHandler.java` | `@RestControllerAdvice` 统一异常处理，捕获 BizException 和未知异常返回标准 Result |

### 改动文件

| 文件 | 改动要点 |
|:---|:---|
| `dto/UserInfoResponse.java` | 补全数据库全部字段：`deptCode`、`majorCode`、`classCode`、`titleCode`；加 `@JsonInclude(NON_NULL)` |
| `service/impl/AuthServiceImpl.java` | `getUserInfo()` 从 DB 补全所有新字段；`handleCasCallback()` 改抛 `BizException`；`logout()` 增加空 token 校验 |
| `service/impl/TokenServiceImpl.java` | 重写：Redis 会话仅存 role/userNo/name 三要素，移除读取不存在字段的误导代码 |
| `service/impl/CasServiceImpl.java` | 重写：`mock=true` 走模拟逻辑（`stu-`/`tea-` 前缀）；`mock=false` 走真实 CAS 3.0 `/serviceValidate` XML 协议解析 |
| `config/CasConfig.java` | 新增 `mock` 布尔开关，默认 `true` |
| `resources/application.yml` | CAS 配置新增 `mock: true` |

### 认证流程

- `POST /api/auth/cas/callback` → 验证 ticket → 首次 INSERT / 后续 UPDATE 写入 MySQL → 生成 UUID Token 存入 Redis（TTL 2h）→ 返回 `LoginResponse`
- `GET /api/auth/me` → 从 Redis 读身份 → 从 MySQL 补全完整字段 → 返回 `UserInfoResponse`
- `POST /api/auth/logout` → 删除 Redis 会话 → 返回 CAS 登出 URL

### 测试方式

开发模式下发送 `{"ticket": "stu-2022001", "service": "http://localhost:8080"}` 模拟学生登录，`tea-xxx` 模拟教师。

---

## 2026-06-15 — 模块 2.2.2 文件上传与管理模块 `file`

### 目标

根据开发文档 2.2.2 节实现文件上传与管理模块，包含 MinIO 对象存储、分布式锁防重提交、流式文件下载。

### 新增文件

| 文件路径 | 说明 |
|:---|:---|
| `config/MinioConfig.java` | MinIO 客户端配置类，读取 `minio.*` 配置项，创建 `MinioClient` Bean，启动时自动检测/创建 Bucket |
| `dto/FileUploadRequest.java` | 学生文件上传请求体：`courseId`、`stageNum`、`codePackage`(MultipartFile)、`report`(MultipartFile) |
| `dto/FileInfoResponse.java` | 文件元信息响应体：`objectName`、`originalName`、`size`、`type` |
| `service/FileService.java` | 文件服务接口：`upload()`、`download()`、`getFileInfo()` |
| `service/impl/FileServiceImpl.java` | 文件服务实现：MinIO 读写 + Redisson 分布式锁 + t_submission_stage 表映射 |
| `controller/FileController.java` | 文件控制器：`POST /upload`、`GET /download/**`、`GET /info/**` |

### 接口定义

| 接口 | 方法 | 路径 | 说明 |
|:---|:---|:---|:---|
| 学生上传文件 | `POST` | `/api/file/upload` | multipart/form-data，接收 .zip 代码包 + 图文报告 |
| 下载文件 | `GET` | `/api/file/download/**` | 校验 Token 权限后从 MinIO 流式返回 |
| 获取文件元信息 | `GET` | `/api/file/info/**` | 返回文件名、大小、类型 |

### 核心设计

**物理隔离策略**：
- UUID 混淆物理路径，存储格式：`{courseId}/{studentNo}/{stageNum}/code/{uuid}.zip`
- 文件地址写入 `t_submission_stage` 表的 `code_package_path` / `report_path` 字段
- 前端无法感知 MinIO 真实地址，所有文件访问通过后端流式中转

**分布式锁防重提交**：
- Key: `neusoft:lock:student_upload:{student_no}:{course_id}:{stage_num}`
- 策略: `tryLock(wait=3s, lease=10s)`
- 目的: 防止学生网络重试导致 MinIO 重复文件 + MySQL 重复记录

**幂等校验**：
- 若该学生该课程该阶段已有记录且状态为 0（已提交待评测），直接返回已有 submissionId

**文件校验**：
- 代码包：仅接受 `.zip` 格式，最大 100MB
- 报告：仅接受 `.docx` / `.pdf` 格式

### 测试方式

- 先通过 `POST /api/auth/cas/callback` 获取 Token
- 使用 multipart/form-data 向 `POST /api/file/upload` 上传文件
- 从返回的 submissionId 查询 `t_submission_stage` 表验证文件路径已写入
- 使用 `GET /api/file/download/{objectName}` 下载验证流式返回

---

## 2026-06-16 — 模块 2.2.3 AI 评测触发与任务管理模块 `eval`

### 目标

根据开发文档 2.2.3 节实现 AI 评测触发与任务管理模块，包含教师端分布式锁防重触发、配额控制与模型降级、Redis 任务状态机流转、异步任务投递 FastAPI、评审结果发布等功能。

### 新增文件

| 文件路径 | 说明 |
|:---|:---|
| `dto/EvalTriggerRequest.java` | AI 评测触发请求体：`courseId`、`studentNo`（选填，为空则批量）、`stageNum`、`isJointReview` |
| `dto/EvalPublishRequest.java` | 评审结果发布请求体：`studentNo`、`courseId`、`stageNum` |
| `dto/TaskStatusResponse.java` | 任务状态响应体：`taskId`、`status`、`statusText`、`errorMsg`、`studentNo`、`stageNum` |
| `dto/EvalReportResponse.java` | 评审报告响应体：含 AI 原始分数/报告 + 教师修改后分数/报告 |
| `config/AiServiceConfig.java` | FastAPI 算法服务配置类，从 `ai-service.*` 读取地址和配额 |
| `config/RestTemplateConfig.java` | RestTemplate 配置，连接超时 5s，读取超时 300s |
| `service/EvalService.java` | 评测服务接口：`triggerEval`、`getTaskStatus`、`publishResult`、`getReport`、`getReportsByCourseAndStage` |
| `service/impl/EvalServiceImpl.java` | 评测服务实现（详见下方核心设计） |
| `controller/EvalController.java` | 评测控制器，5 个 REST 接口 |

### 改动文件

| 文件 | 改动要点 |
|:---|:---|
| `resources/application.yml` | 新增 `ai-service` 配置段：`base-url`、`submit-path`、`health-path`、`daily-quota` |

### 接口定义

| 接口 | 方法 | 路径 | 说明 |
|:---|:---|:---|:---|
| 触发 AI 评测 | `POST` | `/api/eval/trigger` | 教师触发全班/单人 AI 评测，投递异步任务给 FastAPI |
| 查询任务状态 | `GET` | `/api/task/status/{task_id}` | 前端轮询，返回 Redis 中任务状态机当前值 |
| 发布评审结果 | `POST` | `/api/eval/publish` | 教师确认下发，状态变更为 `3-已下发` |
| 获取评审报告 | `GET` | `/api/eval/report/{student_no}/{course_id}/{stage}` | 获取单个学生的 AI 评语（含教师修改后版本） |
| 获取课程报告列表 | `GET` | `/api/eval/reports/{course_id}/{stage}` | 获取课程某阶段所有学生的评测报告 |

### 核心设计

**教师端分布式锁**：
- Key: `neusoft:lock:teacher_eval:{student_no}:{course_id}:{stage_num}`
- 策略: `tryLock(wait=5s, lease=180s)`（考虑 AI+OCR 链路耗时，自动过期防死锁）
- 目的: 防止同组教师或重复点击触发同一条 AI 任务

**配额控制与降级**：
- Key: `neusoft:quota:deepseek:date:{yyyyMMdd}`（Redis INCR 计数器，TTL 24h）
- 每次触发前检查当日已用配额，超出阈值时写入 `neusoft:config:model_fallback = "local"`
- FastAPI 算法服务读取降级标识后自动路由到本地 Ollama 模型

**Redis 任务状态机**：
- Key: `neusoft:task:status:{task_id}`（Hash，TTL 24h）
- 状态流转: `10(等待)` → `20(OCR)` → `30(RAG)` → `40(LLM)` → `50(完成)`，`-1(失败)`
- FastAPI 异步执行期间逐级更新状态，前端轮询渲染多阶段进度条

**Redis 任务状态 → MySQL 业务状态映射**：
- Redis `10/20/30/40` → MySQL `1`（AI 评测中）
- Redis `50` → MySQL `2`（待发布，由 FastAPI 同步更新）
- Redis `-1` → MySQL 保持 `1` 或回退 `0`（由业务逻辑决定）

**批量触发流程**：
1. 查询该课程该阶段所有 `status=0` 的提交记录
2. 逐条获取 Redisson 分布式锁 → 校验状态 → 更新 MySQL 状态为 1
3. 生成 UUID `task_id` → 初始化 Redis 状态机 → 投递 HTTP POST 给 FastAPI
4. 返回已触发数量、task_id 列表、跳过的学生列表

### 测试方式

- 教师登录后发送 `POST /api/eval/trigger` 触发评测
- 使用 `GET /api/task/status/{task_id}` 轮询任务进度
- 检查 `t_submission_stage` 表 `status` 字段变化
- 使用 `POST /api/eval/publish` 下发评审结果，验证状态变为 3
- 使用 `GET /api/eval/report/{student_no}/{course_id}/{stage}` 获取报告

---

## 2026-06-16 — 模块 2.2.4 评语微调与分数管理模块 `review`

### 目标

根据开发文档 2.2.4 节实现评语微调与分数管理模块，教师可在 AI 评测完成后（status=2）在线修改 Markdown 评语和分数。

### 新增文件

| 文件路径 | 说明 |
|:---|:---|
| `dto/ReviewCommentRequest.java` | 教师修改评语请求体：`studentNo`、`courseId`、`stageNum`、`finalReportMarkdown` |
| `dto/ReviewScoreRequest.java` | 教师修改分数请求体：`studentNo`、`courseId`、`stageNum`、`teacherScore` |
| `service/ReviewService.java` | 评语微调服务接口：`saveComment`、`saveScore` |
| `service/impl/ReviewServiceImpl.java` | 评语微调服务实现：状态校验 + 直接覆盖写入 |
| `controller/ReviewController.java` | 评语微调控制器，2 个 REST 接口 |

### 接口定义

| 接口 | 方法 | 路径 | 说明 |
|:---|:---|:---|:---|
| 保存教师修改评语 | `PUT` | `/api/review/comment` | 直接覆盖 `t_submission_stage.final_report_markdown` |
| 保存教师最终分数 | `PUT` | `/api/review/score` | 直接覆盖 `t_submission_stage.teacher_score` |

### 核心设计

- 前提条件：提交记录状态必须为 `2`（AI 评测完成/待发布），否则拒绝修改
- 每次修改直接覆盖原值，不保留历史版本（可未来扩展 `t_review_history` 表）
- 分数校验范围：0-100

### 测试方式

- 确保提交记录 status=2，发送 `PUT /api/review/comment` 修改评语
- 发送 `PUT /api/review/score` 修改分数，验证 teacher_score 已更新
- 尝试对 status≠2 的记录修改，验证被拒绝

---

## 2026-06-16 — 模块 2.2.5 用户与课程管理模块 `user`

### 目标

根据开发文档 2.2.5 节实现用户与课程管理模块，教师查看课程学生列表、学生三阶段进度、期末总分查询。

### 新增文件

| 文件路径 | 说明 |
|:---|:---|
| `dto/CourseStudentResponse.java` | 课程学生信息响应：学号、姓名、班级、三阶段状态概览 |
| `dto/StudentProgressResponse.java` | 学生三阶段进度响应：含嵌套 `StageDetail` 内部类 |
| `dto/FinalScoreResponse.java` | 期末总分响应：finalScore、teacherFinalComment、gradeStatus |
| `service/UserQueryService.java` | 用户查询服务接口：`getCourseStudents`、`getStudentProgress`、`getFinalScore` |
| `service/impl/UserQueryServiceImpl.java` | 用户查询服务实现：多表联合查询 + 数据组装 |
| `controller/UserController.java` | 用户控制器，3 个 REST 接口 |

### 接口定义

| 接口 | 方法 | 路径 | 说明 |
|:---|:---|:---|:---|
| 获取课程学生列表 | `GET` | `/api/user/students/{course_id}` | JOIN t_student_course + t_student + t_submission_stage |
| 获取学生三阶段状态 | `GET` | `/api/user/progress/{student_no}/{course_id}` | 查询 t_submission_stage 三条记录 |
| 获取期末总分 | `GET` | `/api/user/final-score/{student_no}/{course_id}` | 查询 t_student_course |

### 核心设计

**课程学生列表**：
1. 先查 t_student_course 获取选课学号列表
2. 批量查 t_student 补全学生基本信息
3. 查 t_submission_stage 获取各阶段提交状态
4. 组装为 CourseStudentResponse 返回

**学生三阶段进度**：
- 查询 t_submission_stage 该学生该课程的全部记录（按 stageNum 排序）
- 返回每阶段的 status、aiScore、teacherScore、时间戳等

**期末总分访问控制**：
- 教师可查看所有状态
- 学生仅可查看 grade_status=1（已下发）的总分

### 测试方式

- 教师登录后访问 `GET /api/user/students/{courseId}` 查看全班学生和三阶段状态
- 访问 `GET /api/user/progress/{studentNo}/{courseId}` 查看单个学生详细进度
- 访问 `GET /api/user/final-score/{studentNo}/{courseId}` 查看期末总分
- 学生登录后尝试查看未发布的总分，验证被拒绝

---

## 2026-06-16 — 模块 4.3 评分标准上传接口

### 目标

根据开发文档 4.3 节实现评分标准上传接口，教师上传评分标准文档后存储到 MinIO，后续由 FastAPI 算法服务切片 + Embedding 写入 Milvus。

### 新增文件

| 文件路径 | 说明 |
|:---|:---|
| `dto/StandardUploadRequest.java` | 评分标准上传请求体：`courseId`、`stage`（0-通用/1-3阶段）、`file` |
| `dto/StandardInfoResponse.java` | 评分标准信息响应：`courseId`、`standardDocUrl`、`chunkCount`、`teacherNo` |
| `service/StandardService.java` | 评分标准服务接口：`uploadStandard`、`getStandardInfo` |
| `service/impl/StandardServiceImpl.java` | 评分标准服务实现：MinIO 上传 + 课程关联 + FastAPI 触发（桩） |
| `controller/StandardController.java` | 评分标准控制器，2 个 REST 接口 |

### 接口定义

| 接口 | 方法 | 路径 | 说明 |
|:---|:---|:---|:---|
| 上传评分标准文档 | `POST` | `/api/standard/upload` | multipart/form-data，支持 .docx / .pdf / .txt |
| 查询已上传标准 | `GET` | `/api/standard/list/{course_id}` | 返回文档路径和切片数 |

### 核心设计

**上传流程**：
1. 校验文件格式（.docx / .pdf / .txt）
2. 校验课程存在且属于当前教师
3. UUID 混淆路径后上传到 MinIO（`standards/{course_id}/{uuid}.ext`）
4. 更新 t_course_project.standard_doc_url
5. 触发 FastAPI 进行文档切片 + Embedding 写入 Milvus（桩实现，待 FastAPI 端完成）

**Milvus 集合设计（grading_standards）**：
- chunk_id: INT64 主键，自动递增
- vector: FLOAT_VECTOR(1024)，bge-large-zh-v1.5 生成
- course_id: VARCHAR(32)，标量索引
- stage: INT32，标量索引（0=通用 / 1-3=各阶段）
- content: VARCHAR(4000)，原始评分规则文本

**待实现**：
- FastAPI 端 `/ai/standard/process` 接口（文档解析 + 切片 + Embedding + Milvus 写入）
- Milvus Java SDK 集成（查询切片数量）

### 测试方式

- 教师登录后使用 `POST /api/standard/upload` 上传评分标准文档
- 验证 MinIO 中文件已存储，t_course_project.standard_doc_url 已更新
- 使用 `GET /api/standard/list/{courseId}` 查询评分标准信息

---

## 后续待实现模块

- FastAPI AI/OCR 算法服务模块（Python 端）
- 前端 Vue 3 模块

---

## 2026-06-16 — AI/OCR 算法服务模块（Python FastAPI）

### 目标

根据开发文档第 3 节实现 AI/OCR 算法服务模块，包含配置管理、数据模型、OCR 图文解析、RAG 评分标准检索、LLM 深度分析、结果持久化、评测编排管道、API 路由、评分标准处理、主应用入口共 10 个子模块。所有代码添加完整中文注释。

### 新增文件

| 文件路径 | 行数 | 说明 |
|:---|:---|:---|
| `app/config.py` | 98 | 全局配置模块 — MySQL/Redis/MinIO/Milvus/DeepSeek/Ollama 连接参数 |
| `app/models/schemas.py` | 126 | Pydantic 数据模型 — TaskStatus 枚举、请求/响应体、内部 DTO |
| `app/services/ocr_service.py` | 307 | OCR 图文解析子模块 — MinIO 下载 + .docx 解析 + PaddleOCR 识别 + .pdf 解析 |
| `app/services/rag_service.py` | 177 | RAG 评分标准检索子模块 — Embedding 向量化 + Milvus 相似度检索 |
| `app/services/llm_service.py` | 209 | LLM 深度分析子模块 — DeepSeek/Ollama 自适应切换 + 分数解析 |
| `app/services/persistence.py` | 249 | 结果持久化模块 — Redis 状态机 + MySQL 读写 |
| `app/services/eval_pipeline.py` | 129 | 评测编排管道 — 串联 OCR→RAG→LLM 全流程 |
| `app/services/standard_service.py` | 260 | 评分标准处理服务 — 文档切片 + Embedding + Milvus 写入 |
| `app/api/routes.py` | 77 | API 路由模块 — `/ai/eval/submit` 和 `/ai/health` 接口 |
| `app/main.py` | 90 | FastAPI 应用主入口 — 应用配置 + 生命周期管理 + Uvicorn 启动 |
| `app/api/__init__.py` | 2 | API 模块导出 |
| `app/services/__init__.py` | 7 | 服务模块导出 |
| `app/models/__init__.py` | 6 | 数据模型导出 |
| `requirements.txt` | 25 | Python 依赖清单 |

### 核心设计

**评测编排管道（eval_pipeline.py）**：
- 串联 OCR → RAG → LLM 全流程，通过 Redis 更新任务状态机（10→20→30→40→50）
- 异步执行：`asyncio.create_task()` 在后台运行，API 立即返回 task_id
- 异常安全：任意阶段失败时 Redis 状态设为 -1，MySQL 状态回退为 0

**OCR 图文解析（ocr_service.py）**：
- 从 MinIO 下载 .zip/.docx/.pdf 文件到临时目录
- 使用 python-docx 解析 .docx，提取段落文本 + 嵌入图片
- 使用 PaddleOCR 识别截图中的文字（置信度阈值 0.5）
- OCR 结果按图片出现顺序原位插入到对应段落位置
- 可选提取代码包 README 作为补充信息

**RAG 评分标准检索（rag_service.py）**：
- 使用 bge-large-zh-v1.5（1024 维）将报告文本向量化
- 在 Milvus 中执行 HNSW 向量检索 + 标量过滤（course_id + stage）
- 返回 Top-K 相关评分标准切片，拼接为 System Prompt 上下文

**LLM 深度分析（llm_service.py）**：
- 模型路由：读取 Redis 降级开关，决定使用 DeepSeek API 或 Ollama 本地模型
- DeepSeek 失败时自动降级到 Ollama
- 支持联合评审上下文（上一阶段教师终审评语）
- 正则解析 LLM 输出中的分数，支持多种格式

**评分标准处理（standard_service.py）**：
- 教师上传评分标准文档后，解析提取纯文本
- 按 500 字符切片（50 字符重叠），Embedding 向量化后写入 Milvus
- 覆盖更新：先删除该课程该阶段的旧数据，再插入新数据

### 接口定义

| 接口 | 方法 | 路径 | 说明 |
|:---|:---|:---|:---|
| 接收评测任务 | `POST` | `/ai/eval/submit` | 接收 `{task_id, student_no, course_id, stage}`，异步执行评测 |
| 健康检查 | `GET` | `/ai/health` | Java 端心跳探针，检测 FastAPI 服务存活 |

### 启动方式

```bash
# 开发模式（热重载）
cd ai_service && python -m app.main

# 生产模式
uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers 2
```

### 测试方式

- 启动 FastAPI 服务后，访问 `http://localhost:8000/docs` 查看 Swagger UI
- 使用 `POST /ai/eval/submit` 发送评测任务，检查 Redis 状态机变化
- 使用 `GET /ai/health` 检查服务存活状态
- 检查 MySQL `t_submission_stage` 表 `status` 和 `ai_score` 字段变化

### 技术依赖

| 包名 | 版本 | 用途 |
|:---|:---|:---|
| fastapi | 0.111.0 | 异步 Web 框架 |
| uvicorn[standard] | 0.30.1 | ASGI 服务器 |
| openai | 1.35.0 | DeepSeek API（OpenAI 兼容接口） |
| ollama | 0.2.1 | 本地私有化模型调用 |
| paddleocr | 2.8.1 | OCR 图文识别 |
| pymilvus | 2.4.4 | Milvus 向量数据库客户端 |
| sentence-transformers | 2.7.0 | Embedding 模型加载 |
| python-docx | 1.1.2 | .docx 文档解析 |
| redis[hiredis] | 5.0.8 | Redis 客户端 |
| pymysql | 1.1.1 | MySQL 客户端 |
| minio | 7.2.7 | MinIO 对象存储客户端 |

---

## 后续待实现模块

- 前端 Vue 3 模块
