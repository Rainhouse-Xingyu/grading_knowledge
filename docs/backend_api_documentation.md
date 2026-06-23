# 后端接口文档

> 统一响应格式：`Result<T>`
> ```json
> { "code": 200, "message": "success", "data": <T> }
> ```

---

## 1. AuthController — 认证授权

**类路径**: `com.neusoft.grading.controller.AuthController`
**基础路径**: `/api/auth`

### 1.1 本地用户名密码登录

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `POST /api/auth/login` → `AuthController.localLogin()` |
| **方法注释** | 本地用户名密码登录 |
| **入参** | `LocalLoginRequest` (JSON Body) |
| | `- username` (String, 必填) 用户名 |
| | `- password` (String, 必填) 密码 |
| **返回值** | `Result<LoginResponse>` |
| **返回值结构** | `LoginResponse`: `accessToken`(String), `role`(String: student/teacher), `name`(String), `userNo`(String) |

### 1.2 注册本地用户（仅管理员）

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `POST /api/auth/register` → `AuthController.localRegister()` |
| **方法注释** | 注册本地用户（仅管理员） |
| **入参** | `LocalRegisterRequest` (JSON Body) |
| | `- username` (String, 必填, 长度4-32) 用户名 |
| | `- password` (String, 必填, 长度8-64, 须含大小写字母和数字) 密码 |
| | `- role` (String, 必填, 枚举: student/teacher/admin) 角色 |
| | `- userNo` (String, 选填) 关联用户编号(student时填学号, teacher时填工号) |
| **返回值** | `Result<Void>` |

### 1.3 修改密码

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `POST /api/auth/change-password` → `AuthController.changePassword()` |
| **方法注释** | 修改密码 |
| **入参** | Header: `Authorization: Bearer <token>`, Body: `ChangePasswordRequest` (JSON) |
| | `- oldPassword` (String, 必填) 原密码 |
| | `- newPassword` (String, 必填, 长度8-64, 须含大小写字母和数字) 新密码 |
| **返回值** | `Result<Void>` |

### 1.4 查询账户锁定状态

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `GET /api/auth/lock-status` → `AuthController.lockStatus()` |
| **方法注释** | 查询账户锁定状态 |
| **入参** | Query: `username` (String, 必填) 用户名 |
| **返回值** | `Result<Long>` — data 为剩余锁定秒数 |

### 1.5 CAS 回调登录

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `POST /api/auth/cas/callback` → `AuthController.casCallback()` |
| **方法注释** | CAS 回调登录 |
| **入参** | `CasCallbackRequest` (JSON Body) |
| | `- ticket` (String) CAS 服务器返回的 ticket |
| | `- service` (String, 可选) 前端当前页面地址 |
| **返回值** | `Result<LoginResponse>` |
| **返回值结构** | `LoginResponse`: `accessToken`(String), `role`(String), `name`(String), `userNo`(String) |

### 1.6 登出

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `POST /api/auth/logout` → `AuthController.logout()` |
| **方法注释** | 登出 |
| **入参** | Header: `Authorization: Bearer <token>` |
| **返回值** | `Result<Map<String, String>>` — data 包含 `casLogoutUrl`(String) |

### 1.7 获取当前用户信息

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `GET /api/auth/me` → `AuthController.me()` |
| **方法注释** | 获取当前用户信息 |
| **入参** | Header: `Authorization: Bearer <token>` |
| **返回值** | `Result<UserInfoResponse>` |
| **返回值结构** | 见 `UserInfoResponse` (通用字段: role, userNo, name; 学生专属: gender, deptCode, deptName, majorCode, majorName, classCode, className; 教师专属: titleCode, titleName) |

---

## 2. UserController — 用户与课程管理

**类路径**: `com.neusoft.grading.controller.UserController`
**基础路径**: `/api/user`

### 2.1 获取课程学生列表

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `GET /api/user/students/{course_id}` → `UserController.getCourseStudents()` |
| **方法注释** | 获取课程学生列表 |
| **入参** | Path: `course_id` (String) 课程项目ID; Header: `Authorization: Bearer <token>` |
| **返回值** | `Result<List<CourseStudentResponse>>` |
| **返回值结构** | `CourseStudentResponse`: `studentNo`(String), `name`(String), `className`(String), `majorName`(String), `stage1Status`(Integer: null/0/1/2/3), `stage2Status`(Integer), `stage3Status`(Integer), `stage1TaskId`(String), `stage2TaskId`(String), `stage3TaskId`(String) |

### 2.2 获取学生三阶段进度

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `GET /api/user/progress/{student_no}/{course_id}` → `UserController.getStudentProgress()` |
| **方法注释** | 获取学生三阶段进度 |
| **入参** | Path: `student_no`(String), `course_id`(String); Header: `Authorization: Bearer <token>` |
| **返回值** | `Result<StudentProgressResponse>` |
| **返回值结构** | `StudentProgressResponse`: `studentNo`(String), `courseId`(String), `stages`(List\<StageDetail\>); `StageDetail`: `stageNum`(Integer), `status`(Integer: 0/1/2/3), `aiScore`(BigDecimal), `teacherScore`(BigDecimal), `submitTime`(LocalDateTime), `evalTriggerTime`(LocalDateTime), `reviewTime`(LocalDateTime), `isJointReview`(Integer) |

### 2.3 获取期末总分

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `GET /api/user/final-score/{student_no}/{course_id}` → `UserController.getFinalScore()` |
| **方法注释** | 获取期末总分 |
| **入参** | Path: `student_no`(String), `course_id`(String); Header: `Authorization: Bearer <token>` |
| **返回值** | `Result<FinalScoreResponse>` |
| **返回值结构** | `FinalScoreResponse`: `studentNo`(String), `courseId`(String), `finalScore`(BigDecimal), `teacherFinalComment`(String), `gradeStatus`(Integer: 0-未生成/1-已下发) |

---

## 3. CourseProjectController — 课程项目管理

**类路径**: `com.neusoft.grading.controller.CourseProjectController`
**基础路径**: `/api/course`

### 3.1 查询所有课程

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `GET /api/course` → `CourseProjectController.list()` |
| **方法注释** | 查询所有课程 |
| **入参** | 无 |
| **返回值** | `Result<List<CourseProject>>` |
| **返回值结构** | `CourseProject`: `courseId`(String), `courseName`(String), `teacherNo`(String), `semester`(String), `standardDocUrl`(String), `weightStage1`(BigDecimal, 默认0.20), `weightStage2`(BigDecimal, 默认0.30), `weightStage3`(BigDecimal, 默认0.50), `createTime`(LocalDateTime) |

### 3.2 根据ID查询课程

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `GET /api/course/{courseId}` → `CourseProjectController.getById()` |
| **方法注释** | 根据ID查询课程 |
| **入参** | Path: `courseId` (String) |
| **返回值** | `Result<CourseProject>` |

### 3.3 新增课程

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `POST /api/course` → `CourseProjectController.save()` |
| **方法注释** | 新增课程 |
| **入参** | `CourseProject` (JSON Body) |
| **返回值** | `Result<Boolean>` |

### 3.4 更新课程信息

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `PUT /api/course` → `CourseProjectController.update()` |
| **方法注释** | 更新课程信息 |
| **入参** | `CourseProject` (JSON Body) |
| **返回值** | `Result<Boolean>` |

### 3.5 删除课程

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `DELETE /api/course/{courseId}` → `CourseProjectController.delete()` |
| **方法注释** | 删除课程 |
| **入参** | Path: `courseId` (String) |
| **返回值** | `Result<Boolean>` |

---

## 4. StudentController — 学生管理

**类路径**: `com.neusoft.grading.controller.StudentController`
**基础路径**: `/api/student`

### 4.1 查询所有学生

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `GET /api/student` → `StudentController.list()` |
| **方法注释** | 查询所有学生 |
| **入参** | 无 |
| **返回值** | `Result<List<Student>>` |
| **返回值结构** | `Student`: `studentNo`(String), `name`(String), `gender`(String), `deptCode`(String), `deptName`(String), `majorCode`(String), `majorName`(String), `classCode`(String), `className`(String), `createTime`(LocalDateTime) |

### 4.2 根据学号查询学生

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `GET /api/student/{studentNo}` → `StudentController.getByNo()` |
| **方法注释** | 根据学号查询学生 |
| **入参** | Path: `studentNo` (String) |
| **返回值** | `Result<Student>` |

### 4.3 新增学生

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `POST /api/student` → `StudentController.save()` |
| **方法注释** | 新增学生 |
| **入参** | `Student` (JSON Body) |
| **返回值** | `Result<Boolean>` |

### 4.4 更新学生信息

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `PUT /api/student` → `StudentController.update()` |
| **方法注释** | 更新学生信息 |
| **入参** | `Student` (JSON Body) |
| **返回值** | `Result<Boolean>` |

### 4.5 删除学生

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `DELETE /api/student/{studentNo}` → `StudentController.delete()` |
| **方法注释** | 删除学生 |
| **入参** | Path: `studentNo` (String) |
| **返回值** | `Result<Boolean>` |

### 4.6 批量导入学生（Excel）并创建本地登录账号

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `POST /api/student/batch-import` → `StudentController.batchImport()` |
| **方法注释** | 批量导入学生（Excel）并创建本地登录账号 |
| **入参** | multipart/form-data: `file`(MultipartFile, 必填, .xlsx/.xls), `defaultPassword`(String, 选填, 默认"Neusoft@2026"), `courseId`(String, 选填) |
| **返回值** | `Result<BatchImportResult>` |
| **返回值结构** | `BatchImportResult`: `totalRows`(int), `successCount`(int), `failRows`(List\<FailRow\>); `FailRow`: `rowNum`(int), `studentNo`(String), `reason`(String) |

---

## 5. TeacherController — 教师管理

**类路径**: `com.neusoft.grading.controller.TeacherController`
**基础路径**: `/api/teacher`

### 5.1 查询所有教师

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `GET /api/teacher` → `TeacherController.list()` |
| **方法注释** | 查询所有教师 |
| **入参** | 无 |
| **返回值** | `Result<List<Teacher>>` |
| **返回值结构** | `Teacher`: `teacherNo`(String), `teacherName`(String), `deptCode`(String), `deptName`(String), `titleCode`(String), `titleName`(String), `createTime`(LocalDateTime) |

### 5.2 根据工号查询教师

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `GET /api/teacher/{teacherNo}` → `TeacherController.getByNo()` |
| **方法注释** | 根据工号查询教师 |
| **入参** | Path: `teacherNo` (String) |
| **返回值** | `Result<Teacher>` |

### 5.3 新增教师

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `POST /api/teacher` → `TeacherController.save()` |
| **方法注释** | 新增教师 |
| **入参** | `Teacher` (JSON Body) |
| **返回值** | `Result<Boolean>` |

### 5.4 更新教师信息

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `PUT /api/teacher` → `TeacherController.update()` |
| **方法注释** | 更新教师信息 |
| **入参** | `Teacher` (JSON Body) |
| **返回值** | `Result<Boolean>` |

### 5.5 删除教师

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `DELETE /api/teacher/{teacherNo}` → `TeacherController.delete()` |
| **方法注释** | 删除教师 |
| **入参** | Path: `teacherNo` (String) |
| **返回值** | `Result<Boolean>` |

---

## 6. StudentCourseController — 学生选课管理

**类路径**: `com.neusoft.grading.controller.StudentCourseController`
**基础路径**: `/api/student-course`

### 6.1 查询所有选课记录

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `GET /api/student-course` → `StudentCourseController.list()` |
| **方法注释** | 查询所有选课记录 |
| **入参** | 无 |
| **返回值** | `Result<List<StudentCourse>>` |
| **返回值结构** | `StudentCourse`: `id`(Long), `studentNo`(String), `courseId`(String), `finalScore`(BigDecimal), `teacherFinalComment`(String), `gradeStatus`(Integer: 0/1), `updateTime`(LocalDateTime) |

### 6.2 查询某课程的选课学生

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `GET /api/student-course/course/{courseId}` → `StudentCourseController.listByCourse()` |
| **方法注释** | 查询某课程的选课学生 |
| **入参** | Path: `courseId` (String) |
| **返回值** | `Result<List<StudentCourse>>` |

### 6.3 根据ID查询选课记录

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `GET /api/student-course/{id}` → `StudentCourseController.getById()` |
| **方法注释** | 根据ID查询选课记录 |
| **入参** | Path: `id` (Long) |
| **返回值** | `Result<StudentCourse>` |

### 6.4 新增选课记录

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `POST /api/student-course` → `StudentCourseController.save()` |
| **方法注释** | 新增选课记录 |
| **入参** | `StudentCourse` (JSON Body) |
| **返回值** | `Result<Boolean>` |

### 6.5 更新选课记录

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `PUT /api/student-course` → `StudentCourseController.update()` |
| **方法注释** | 更新选课记录 |
| **入参** | `StudentCourse` (JSON Body) |
| **返回值** | `Result<Boolean>` |

### 6.6 删除选课记录

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `DELETE /api/student-course/{id}` → `StudentCourseController.delete()` |
| **方法注释** | 删除选课记录 |
| **入参** | Path: `id` (Long) |
| **返回值** | `Result<Boolean>` |

---

## 7. SubmissionStageController — 提交评审管理

**类路径**: `com.neusoft.grading.controller.SubmissionStageController`
**基础路径**: `/api/submission`

### 7.1 查询所有提交记录

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `GET /api/submission` → `SubmissionStageController.list()` |
| **方法注释** | 查询所有提交记录 |
| **入参** | 无 |
| **返回值** | `Result<List<SubmissionStage>>` |
| **返回值结构** | `SubmissionStage`: `submissionId`(Long), `studentNo`(String), `courseId`(String), `stageNum`(Integer: 1/2/3), `codePackagePath`(String), `reportPath`(String), `isJointReview`(Integer: 0/1), `modelUsed`(String), `aiScore`(BigDecimal), `aiReportMarkdown`(String), `teacherScore`(BigDecimal), `finalReportMarkdown`(String), `status`(Integer: 0/1/2/3), `submitTime`(LocalDateTime), `evalTriggerTime`(LocalDateTime), `reviewTime`(LocalDateTime) |

### 7.2 查询学生某课程的提交记录

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `GET /api/submission/student/{studentNo}/course/{courseId}` → `SubmissionStageController.listByStudentAndCourse()` |
| **方法注释** | 查询学生某课程的提交记录 |
| **入参** | Path: `studentNo`(String), `courseId`(String) |
| **返回值** | `Result<List<SubmissionStage>>` |

### 7.3 根据ID查询提交记录

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `GET /api/submission/{submissionId}` → `SubmissionStageController.getById()` |
| **方法注释** | 根据ID查询提交记录 |
| **入参** | Path: `submissionId` (Long) |
| **返回值** | `Result<SubmissionStage>` |

### 7.4 新增提交记录

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `POST /api/submission` → `SubmissionStageController.save()` |
| **方法注释** | 新增提交记录 |
| **入参** | `SubmissionStage` (JSON Body) |
| **返回值** | `Result<Boolean>` |

### 7.5 更新提交记录

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `PUT /api/submission` → `SubmissionStageController.update()` |
| **方法注释** | 更新提交记录 |
| **入参** | `SubmissionStage` (JSON Body) |
| **返回值** | `Result<Boolean>` |

### 7.6 删除提交记录

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `DELETE /api/submission/{submissionId}` → `SubmissionStageController.delete()` |
| **方法注释** | 删除提交记录 |
| **入参** | Path: `submissionId` (Long) |
| **返回值** | `Result<Boolean>` |

---

## 8. FileController — 文件上传与管理

**类路径**: `com.neusoft.grading.controller.FileController`
**基础路径**: `/api/file`

### 8.1 学生上传文件

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `POST /api/file/upload` → `FileController.upload()` |
| **方法注释** | 学生上传文件（代码包 + 图文报告） |
| **入参** | multipart/form-data: `courseId`(String), `stageNum`(Integer: 1/2/3), `codePackage`(MultipartFile, .zip, 最大100MB), `report`(MultipartFile, .docx/.pdf); Header: `Authorization: Bearer <token>` (仅学生角色) |
| **返回值** | `Result<Map<String, Object>>` — data 包含 `submissionId`(Long), `message`(String) |

### 8.2 下载文件（流式中转）

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `GET /api/file/download/**` → `FileController.download()` |
| **方法注释** | 下载文件（流式中转） |
| **入参** | Path(通配): objectName (文件在 MinIO 中的混淆路径); Header: `Authorization: Bearer <token>` |
| **返回值** | 直接流式输出文件内容（void），非 JSON |

### 8.3 获取文件元信息

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `GET /api/file/info/**` → `FileController.info()` |
| **方法注释** | 获取文件元信息 |
| **入参** | Path(通配): objectName (文件在 MinIO 中的混淆路径) |
| **返回值** | `Result<FileInfoResponse>` |
| **返回值结构** | `FileInfoResponse`: `objectName`(String), `originalName`(String), `size`(long), `type`(String: code_package/report) |

---

## 9. EvalController — AI 评测触发与任务管理

**类路径**: `com.neusoft.grading.controller.EvalController`
**基础路径**: (跨路径：`/api/eval` 和 `/api/task`)

### 9.1 触发 AI 评测（教师专用）

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `POST /api/eval/trigger` → `EvalController.triggerEval()` |
| **方法注释** | 触发 AI 评测（教师专用）。studentNo 为空时批量触发全班，非空时仅触发单人 |
| **入参** | `EvalTriggerRequest` (JSON Body); Header: `Authorization: Bearer <token>` (仅教师角色) |
| | `- courseId` (String, 必填) 课程项目ID |
| | `- studentNo` (String, 选填) 学号，为空则批量触发全班 |
| | `- stageNum` (Integer, 必填, 1/2/3) 阶段编号 |
| | `- isJointReview` (Integer, 0/1) 是否联合评审，0-不联合，1-联合 |
| **返回值** | `Result<Map<String, Object>>` — data 包含 task_id 列表，前端通过 task_id 轮询进度 |

### 9.2 查询 AI 评测任务状态

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `GET /api/task/status/{task_id}` → `EvalController.getTaskStatus()` |
| **方法注释** | 查询 AI 评测任务状态（前端轮询，状态机: 10→20→30→40→50，-1为失败） |
| **入参** | Path: `task_id` (String) |
| **返回值** | `Result<TaskStatusResponse>` |
| **返回值结构** | `TaskStatusResponse`: `taskId`(String), `status`(Integer: 10/20/30/40/50/-1), `statusText`(String), `errorMsg`(String, status=-1时), `studentNo`(String), `stageNum`(Integer) |

### 9.3 发布评审结果（教师一键下发）

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `POST /api/eval/publish` → `EvalController.publishResult()` |
| **方法注释** | 发布评审结果（教师一键下发，状态从2变更为3，学生端可见） |
| **入参** | `EvalPublishRequest` (JSON Body); Header: `Authorization: Bearer <token>` (仅教师角色) |
| | `- studentNo` (String) 学号 |
| | `- courseId` (String) 课程项目ID |
| | `- stageNum` (Integer) 阶段编号 |
| **返回值** | `Result<Void>` |

### 9.4 获取单个学生评审报告

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `GET /api/eval/report/{student_no}/{course_id}/{stage}` → `EvalController.getReport()` |
| **方法注释** | 获取单个学生评审报告（status=2时教师查看AI原始报告；status=3时学生可查看最终版） |
| **入参** | Path: `student_no`(String), `course_id`(String), `stage`(Integer) |
| **返回值** | `Result<EvalReportResponse>` |
| **返回值结构** | `EvalReportResponse`: `submissionId`(Long), `studentNo`(String), `courseId`(String), `stageNum`(Integer), `aiScore`(BigDecimal), `aiReportMarkdown`(String), `teacherScore`(BigDecimal), `finalReportMarkdown`(String), `modelUsed`(String), `status`(Integer: 0/1/2/3) |

### 9.5 获取课程某阶段所有学生评测报告

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `GET /api/eval/reports/{course_id}/{stage}` → `EvalController.getReports()` |
| **方法注释** | 获取课程某阶段所有学生评测报告（教师工作台使用） |
| **入参** | Path: `course_id`(String), `stage`(Integer) |
| **返回值** | `Result<List<EvalReportResponse>>` |

---

## 10. ReviewController — 评语微调与分数管理

**类路径**: `com.neusoft.grading.controller.ReviewController`
**基础路径**: `/api/review`

### 10.1 保存教师修改评语

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `PUT /api/review/comment` → `ReviewController.saveComment()` |
| **方法注释** | 保存教师修改评语（直接覆盖 final_report_markdown 字段） |
| **入参** | `ReviewCommentRequest` (JSON Body); Header: `Authorization: Bearer <token>` (仅教师角色) |
| | `- studentNo` (String) 学号 |
| | `- courseId` (String) 课程项目ID |
| | `- stageNum` (Integer, 1/2/3) 阶段编号 |
| | `- finalReportMarkdown` (String) 教师修改后的 Markdown 评语（完整覆盖） |
| **返回值** | `Result<Void>` |

### 10.2 保存教师最终分数

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `PUT /api/review/score` → `ReviewController.saveScore()` |
| **方法注释** | 保存教师最终分数（直接覆盖 teacher_score 字段） |
| **入参** | `ReviewScoreRequest` (JSON Body); Header: `Authorization: Bearer <token>` (仅教师角色) |
| | `- studentNo` (String) 学号 |
| | `- courseId` (String) 课程项目ID |
| | `- stageNum` (Integer, 1/2/3) 阶段编号 |
| | `- teacherScore` (BigDecimal, 0-100) 教师最终评定分数 |
| **返回值** | `Result<Void>` |

---

## 11. StandardController — 评分标准管理

**类路径**: `com.neusoft.grading.controller.StandardController`
**基础路径**: `/api/standard`

### 11.1 上传评分标准文档

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `POST /api/standard/upload` → `StandardController.uploadStandard()` |
| **方法注释** | 上传评分标准文档（后端切片 + Embedding 写入 Milvus） |
| **入参** | multipart/form-data: `courseId`(String), `stage`(Integer: 0-通用/1/2/3), `file`(MultipartFile, .docx/.pdf/.txt); Header: `Authorization: Bearer <token>` (仅教师角色) |
| **返回值** | `Result<Void>` |

### 11.2 查询课程评分标准信息

| 项目 | 内容 |
|------|------|
| **全路径+方法** | `GET /api/standard/list/{course_id}` → `StandardController.getStandardInfo()` |
| **方法注释** | 查询课程评分标准信息 |
| **入参** | Path: `course_id`(String); Header: `Authorization: Bearer <token>` |
| **返回值** | `Result<StandardInfoResponse>` |
| **返回值结构** | `StandardInfoResponse`: `courseId`(String), `standardDocUrl`(String), `chunkCount`(Integer, Milvus 切片数), `teacherNo`(String) |

---

## 附录：统一响应格式 Result<T>

```json
{
  "code": 200,
  "message": "success",
  "data": <T>
}
```

| code | 含义 |
|------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未登录或 Token 无效 |
| 403 | 无权限 |
| 500 | 服务端错误 |

## 附录：业务状态码（SubmissionStage.status）

| 值 | 含义 |
|----|------|
| 0 | 已提交，待评测 |
| 1 | AI 评测中 |
| 2 | AI 评测完成/待教师发布 |
| 3 | 已下发（学生可见） |

## 附录：AI 评测任务状态机（TaskStatusResponse.status）

| 值 | 含义 |
|----|------|
| 10 | 等待队列中 |
| 20 | PaddleOCR 图文解析中 |
| 30 | Milvus 评分标准检索中 |
| 40 | DeepSeek 深度分析中 |
| 50 | 已完成 |
| -1 | 失败 |
