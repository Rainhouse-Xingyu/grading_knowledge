# 双轨制 AI 评分系统 — 前端页面规范（供 Lovable 生成用）

> 生成日期：2026-06-22
> 后端 API 地址：`http://localhost:8080`（开发环境）
> 技术栈：Vue 3 + Element Plus + Pinia + Vue Router + Axios + v-md-editor + pdfjs-dist

---

## 一、页面路由结构

| 路由路径 | 页面 | 角色 | 说明 |
|:---|:---|:---|:---|
| `/login` | 登录页 | 公开 | CAS 登录 / 本地登录双标签 |
| `/student/dashboard` | 学生提交面板 | student | 文件上传、三阶段状态、PDF 预览 |
| `/teacher/workbench` | 教师工作台 | teacher | 全班列表、评审触发、评语微调、下发 |
| `/teacher/workbench?studentNo=xxx&stage=1` | 教师评阅详情 | teacher | 单人评分+评语修改 |

路由守卫：未登录（无 Token）强制跳转 `/login`。

---

## 二、页面 1：登录页 `/login`

### 布局
居中卡片布局，卡片内分两个标签页。

### 标签页 1：本地登录
- 用户名输入框（el-input）
- 密码输入框（el-input type="password"，8-64 位）
- 登录按钮
- 错误提示：账户锁定倒计时、密码错误

### 标签页 2：CAS 登录
- 跳转按钮「前往 CAS 统一身份认证」
- 点击后跳转到后端 CAS 回调地址
- 说明文字

### API 对接

| 操作 | 方法 | 路径 | 说明 |
|:---|:---|:---|:---|
| 本地登录 | `POST` | `/api/auth/login` | 请求体 `{username, password}`，返回 `{token, role, userInfo}` |
| CAS 回调 | `POST` | `/api/auth/cas/callback` | 请求体 `{ticket, service}`，返回同上 |
| 获取用户信息 | `GET` | `/api/auth/me` | 需 Token，返回 `{role, name, ...}` |
| 查询锁定状态 | `GET` | `/api/auth/lock-status?username=xxx` | 返回 `{locked, remainSeconds}` |

### Token 管理（Pinia store）
- 登录成功后将 Token 存入 `localStorage`
- Axios 拦截器自动注入 `Authorization: Bearer {token}`
- 401 响应时自动清除 Token 并跳转登录页
- Token 过期时间由后端 Redis 控制（TTL 2h）

### 角色路由
- `student` → 跳转 `/student/dashboard`
- `teacher` → 跳转 `/teacher/workbench`

---

## 三、页面 2：学生提交面板 `/student/dashboard`

### 全局布局
- 顶部导航栏：显示当前学生姓名 + 学号 + 退出登录按钮
- 主体区域：课程列表 → 展开三阶段卡片

### 功能区域

#### 2.1 课程选择
- 课程下拉选择（在 `GET /api/user/students/{course_id}` 中，学生端自行查自己课程）
- **接口**：`GET /api/user/progress/{student_no}/{course_id}` — 返回三阶段状态

#### 2.2 三阶段卡片（每阶段一张卡片）
每张卡片展示：

| 状态值 | 状态文本 | 学生可见操作 |
|:---|:---|:---|
| `0` | 已提交待评测 | 重新上传按钮 |
| `1` | AI 评测中 | 进度条显示「等待教师触发评测」 |
| `2` | AI 已完成/待发布 | 等待老师发布，不可见内容 |
| `3` | 已下发 | PDF 预览按钮 + 下载按钮 |

#### 2.3 文件上传（状态为 0 时显示）
- Element Plus `el-upload` 拖拽上传
- 上传两个文件：代码包（.zip，≤100MB）+ 报告（.docx/.pdf）
- 上传进度条（`el-progress`）
- 接口：`POST /api/file/upload`（multipart/form-data）

#### 2.4 PDF 预览（状态为 3 时显示）
- 使用 `pdfjs-dist` 渲染 PDF
- 接口：`GET /api/eval/report/{student_no}/{course_id}/{stage}` — 获取教师最终评语和分数
- 额外显示：AI 评分 + 教师最终评分对比

#### 2.5 退出登录
- `POST /api/auth/logout`
- 清除 localStorage 中 Token
- 跳转 `/login`

---

## 四、页面 3：教师工作台 `/teacher/workbench`（核心页面）

### 全局布局
- 顶部导航栏：教师姓名 + 退出登录
- 左侧：课程选择
- 主体：分为 "全班概况" 和 "评阅详情" 两个视图

### 3.1 课程选择
- 下拉选择课程
- 接口：暂缺，可从 `t_course_project` 表相关接口获取

### 3.2 全班作业列表（el-table）

| 列 | 说明 |
|:---|:---|
| 学号 | — |
| 姓名 | — |
| 阶段一状态 | `0` 未提交 / `1` 评测中 / `2` 待发布 / `3` 已下发，用不同颜色标签显示 |
| 阶段一 AI 分 | — |
| 阶段一教师分 | — |
| 阶段二状态 | 同上 |
| 阶段二 AI 分 | — |
| 阶段二教师分 | — |
| 阶段三状态 | 同上 |
| 阶段三 AI 分 | — |
| 阶段三教师分 | — |
| 操作 | 「评审」「评阅」按钮 |

**接口**：`GET /api/user/students/{course_id}` — 返回全班学生列表及各阶段状态

### 3.3 批量操作工具栏（表格上方）

| 操作 | 接口 | 说明 |
|:---|:---|:---|
| 一键批量评审 | `POST /api/eval/trigger` | 请求体：`{courseId, stageNum}`，触发该阶段所有未评学生 |
| 按阶段筛选 | — | 前端表格筛选 |

### 3.4 评阅详情视图（点击「评阅」按钮后进入）

#### 3.4.1 三阶段标签页
- `el-tabs` 切换阶段一/二/三

#### 3.4.2 评审操作区

**评审触发**（状态为 0/1 时）：
- 「开始 AI 评审」按钮
- 接口：`POST /api/eval/trigger` — `{courseId, studentNo, stageNum, isJointReview}`
- 弹窗确认是否开启联合评审（checkbox）

**进度轮询**（状态为 1 时）：
- 多阶段进度条，显示当前阶段
```
[等待中] → [OCR解析] → [标准检索] → [AI分析中] → [完成]
   10          20           30           40           50
```
- 前端轮询 `GET /api/task/status/{task_id}`（每 3 秒），渲染进度条
- 失败时显示红色状态和错误信息

**评语编辑**（状态为 2 时）：
- `v-md-editor`（`@kangc/v-md-editor`）Markdown 编辑器
- 左侧编辑区域，右侧实时预览
- 默认加载 AI 生成的评语（`ai_report_markdown`）
- 接口：`GET /api/eval/report/{student_no}/{course_id}/{stage}` → 获取当前报告

**分数修改**（状态为 2 时）：
- `el-input-number` 分数输入（0-100）
- 默认显示 AI 评分（`ai_score`）
- 教师输入后覆盖

**保存操作**：
- 「保存评语」按钮 → `PUT /api/review/comment` — `{studentNo, courseId, stageNum, finalReportMarkdown}`
- 「保存分数」按钮 → `PUT /api/review/score` — `{studentNo, courseId, stageNum, teacherScore}`

**发布操作**（状态为 2 时）：
- 「一键下发」按钮 → `POST /api/eval/publish` — `{studentNo, courseId, stageNum}`
- 学生端解锁，状态变更为 `3-已下发`

---

## 五、页面 4：PDF 报告导出

### 触发方式
- 学生端：状态 = 3（已下发）时显示「下载报告」按钮
- 接口：后端无独立 PDF 导出接口，当前设计为前端通过 `GET /api/eval/report/{student_no}/{course_id}/{stage}` 获取 JSON 数据后，由前端 PDF 库（`pdfjs-dist`）在线呈现，或通过浏览器打印功能导出

### 报告内容
- 课程名称
- 学生姓名 + 学号
- 阶段编号
- AI 评分
- 教师最终评分
- AI 原始评语（Markdown 渲染）
- 教师修改后评语（Markdown 渲染）

---

## 六、技术栈清单（Lovable 中需安装的 npm 包）

| 包名 | 版本 | 用途 | 安装命令 |
|:---|:---|:---|:---|
| vue | ^3.4 | 框架 | `npm install vue@^3.4` |
| vue-router | ^4.3 | 路由 | `npm install vue-router@^4.3` |
| pinia | ^2.2 | 状态管理 | `npm install pinia@^2.2` |
| element-plus | ^2.7 | UI 组件库 | `npm install element-plus@^2.7` |
| axios | ^1.7 | HTTP 请求 | `npm install axios@^1.7` |
| @kangc/v-md-editor | ^2.3 | Markdown 编辑器（教师端） | `npm install @kangc/v-md-editor@^2.3` |
| pdfjs-dist | ^4.0 | PDF 预览（学生端） | `npm install pdfjs-dist@^4.0` |
| @element-plus/icons-vue | — | 图标库 | `npm install @element-plus/icons-vue` |

---

## 七、Axios 配置

```typescript
// 全局配置要点
const http = axios.create({ baseURL: 'http://localhost:8080/api' })

// 请求拦截器：自动注入 Token
http.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 响应拦截器：401 跳转登录
http.interceptors.response.use(
  res => res.data,  // 直接返回 Result.data
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      router.push('/login')
    }
    return Promise.reject(err)
  }
)
```

后端统一返回格式：
```json
{ "code": 200, "message": "success", "data": { ... } }
```
前端 Axios 拦截器直接解出 `data` 字段。

---

## 八、TypeScript 类型定义范例

```typescript
// 用户信息
interface UserInfo {
  userNo: string      // 学号/工号
  name: string
  role: 'student' | 'teacher' | 'admin'
  deptName?: string
}

// 三阶段状态常量
type StageStatus = 0 | 1 | 2 | 3  // 已提交/评测中/待发布/已下发

// 阶段详情
interface StageDetail {
  stageNum: 1 | 2 | 3
  status: StageStatus
  aiScore?: number
  teacherScore?: number
  submitTime?: string
}

// 课程学生（教师工作台列表行）
interface CourseStudent {
  studentNo: string
  name: string
  className: string
  stages: StageDetail[]  // 三阶段
}

// 评测报告
interface EvalReport {
  aiScore: number
  aiReportMarkdown: string
  teacherScore?: number
  finalReportMarkdown?: string
  modelUsed: string
  status: StageStatus
}

// 任务状态（轮询用）
interface TaskStatus {
  taskId: string
  status: number        // 10/20/30/40/50/-1
  statusText: string
  errorMsg?: string
}
```

---

## 九、Vite 代理配置（开发环境解决跨域）

```typescript
// vite.config.ts
export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/ai': {
        target: 'http://localhost:8000',
        changeOrigin: true
      }
    }
  }
})
```
