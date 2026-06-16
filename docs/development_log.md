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

## 后续待实现模块

- 2.2.3 AI 评测触发与任务管理模块 `eval`
- 2.2.4 评语微调与分数管理模块 `review`
- 2.2.5 用户与课程管理模块 `user`
- 4.3 评分标准上传接口（Milvus 相关）
