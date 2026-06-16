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

## 后续待实现模块

- 2.2.5 用户与课程管理模块 `user`
- 4.3 评分标准上传接口（Milvus 相关）
