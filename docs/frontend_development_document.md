# 双轨制 AI 评分系统 — 前端开发文档

> **版本**：V2.0 (JavaScript)
> **生成日期**：2026-06-23
> **框架**：Vue 3 + Vite + JavaScript
> **UI 库**：Element Plus
> **状态管理**：Pinia
> **路由**：Vue Router 4

---

## 目录

- [1. 项目概述](#1-项目概述)
- [2. 技术栈选型](#2-技术栈选型)
- [3. 项目目录结构](#3-项目目录结构)
- [4. 页面路由设计](#4-页面路由设计)
- [5. 状态管理（Pinia Store）](#5-状态管理pinia-store)
- [6. API 对接层](#6-api-对接层)
- [7. 页面详细设计](#7-页面详细设计)
- [8. 核心组件树](#8-核心组件树)
- [9. 数据结构定义](#9-数据结构定义)
- [10. Axios 拦截器配置](#10-axios-拦截器配置)
- [11. 开发规范](#11-开发规范)
- [12. 构建与部署](#12-构建与部署)
- [13. 接口索引表](#13-接口索引表)

---

## 1. 项目概述

### 1.1 系统背景

双轨制 AI 评分系统是一个面向高校实验课程的教学辅助平台，支持学生在线提交实验代码+报告，AI 自动评分，教师在线微调评语和分数，最终下发评分结果。

核心流程：
```
学生提交 → 教师触发AI评审 → OCR解析 → RAG检索标准 → LLM评分
                                                              ↓
学生查看 ← 教师下发结果 ← 教师微调评语/分数 ← AI完成/待发布
```

### 1.2 前端职责范围

- **学生端**：登录、文件上传、三阶段进度查看、PDF 报告预览与下载
- **教师端**：登录、全班作业列表、AI 评审触发、进度轮询、评语微调、分数覆盖、结果下发
- **通用**：认证授权、Token 管理、路由守卫

### 1.3 目标用户

- 学生（实验课程提交者）
- 教师（课程负责人、评审者）
- 管理员（系统管理，本次开发不涉及管理后台）

---

## 2. 技术栈选型

### 2.1 核心框架

| 依赖 | 版本 | 用途 |
|:---|:---|:---|
| **Vue** | `^3.4` | 核心框架，组合式 API + `<script setup>` |
| **Vite** | `^5.0` | 构建工具，开发服务器 HMR |
| **JavaScript** | ES2023+ | 无类型标注，纯 JS 编写 |

### 2.2 UI 与交互

| 依赖 | 版本 | 用途 |
|:---|:---|:---|
| **Element Plus** | `^2.7` | UI 组件库（表格 el-table、表单 el-form、上传 el-upload、进度条 el-progress、标签页 el-tabs、对话框 el-dialog、消息提示 ElMessage） |
| **@element-plus/icons-vue** | 最新 | Element Plus 图标库 |
| **vue-router** | `^4.3` | 前端路由，双角色路由守卫 |
| **pinia** | `^2.2` | 状态管理 |
| **axios** | `^1.7` | HTTP 客户端 |

### 2.3 开发工具

| 工具 | 版本 | 用途 |
|:---|:---|:---|
| **Node.js** | `18.x+` | 运行时 |
| **npm** / **yarn** | — | 包管理器 |
| **VSCode** | 最新 | 推荐 IDE |
| **Vue Language Features (Volar)** | 最新 | VSCode 插件，`<script setup>` 支持 |

---

## 3. 项目目录结构

```
frontend/
├── index.html                     # 入口 HTML
├── vite.config.js                 # Vite 配置（含代理）
├── package.json                   # 依赖清单
├── Dockerfile                     # 构建镜像（多阶段）
│
├── public/                        # 纯静态资源
│   └── favicon.ico
│
└── src/
    ├── main.js                    # 应用入口：注册 Element Plus、Router、Pinia
    ├── App.vue                    # 根组件（<router-view />）
    ├── style.css                  # 全局样式
    │
    ├── router/
    │   └── index.js               # 路由配置 + 路由守卫
    │
    ├── stores/                    # Pinia 状态管理
    │   ├── auth.js                # 用户会话（Token、角色、用户信息）
    │   └── eval.js                # 评审任务（进度轮询、task_id 管理）
    │
    ├── api/                       # API 对接层
    │   ├── request.js             # Axios 实例 + 拦截器
    │   ├── auth.js                # 认证相关 API
    │   ├── file.js                # 文件上传 API
    │   ├── eval.js                # 评审任务 API
    │   ├── review.js              # 评语/分数 API
    │   └── user.js                # 用户/课程查询 API
    │
    ├── components/                # 可复用组件
    │   ├── AppLayout.vue          # 全局布局（顶部导航 + 主体区插槽）
    │   ├── StageStatusBadge.vue   # 阶段状态标签（颜色映射）
    │   ├── TaskProgressBar.vue    # 多阶段任务进度条组件
    │   └── ScoreDisplay.vue       # 分数展示组件（AI分/教师分对比）
    │
    └── views/                     # 页面视图
        ├── login/
        │   └── LoginPage.vue      # 登录页
        ├── student/
        │   └── Dashboard.vue      # 学生提交面板
        └── teacher/
            ├── Workbench.vue      # 教师工作台（全班列表）
            └── ReviewDetail.vue   # 教师评阅详情（评语+分数）
```

> **与 TS 版本的关键差异**：无 `types/` 目录、无 `tsconfig` 文件、所有 `.ts` 后缀改为 `.js`、Vue 组件使用 `<script setup>`（去掉 `lang="ts"`）。

---

## 4. 页面路由设计

### 4.1 路由表

| 路径 | 页面组件 | 角色 | 元信息 |
|:---|:---|:---|:---|
| `/login` | `LoginPage.vue` | 公开 | `{ requiresAuth: false }` |
| `/student/dashboard` | `Dashboard.vue` | student | `{ requiresAuth: true, role: 'student' }` |
| `/teacher/workbench` | `Workbench.vue` | teacher | `{ requiresAuth: true, role: 'teacher' }` |
| `/teacher/review/:studentNo/:stage` | `ReviewDetail.vue` | teacher | `{ requiresAuth: true, role: 'teacher' }` |

### 4.2 路由守卫逻辑

```javascript
// router/index.js — 核心守卫逻辑
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/LoginPage.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/student/dashboard',
      name: 'StudentDashboard',
      component: () => import('@/views/student/Dashboard.vue'),
      meta: { requiresAuth: true, role: 'student' },
    },
    {
      path: '/teacher/workbench',
      name: 'TeacherWorkbench',
      component: () => import('@/views/teacher/Workbench.vue'),
      meta: { requiresAuth: true, role: 'teacher' },
    },
    {
      path: '/teacher/review/:studentNo/:stage',
      name: 'ReviewDetail',
      component: () => import('@/views/teacher/ReviewDetail.vue'),
      meta: { requiresAuth: true, role: 'teacher' },
    },
    {
      path: '/',
      redirect: '/login',
    },
  ],
})

router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()

  // 1. 公开页面（登录页）— 直接放行
  if (!to.meta.requiresAuth) {
    // 已登录用户访问登录页 → 重定向到角色对应主页
    if (authStore.isLoggedIn) {
      return next(authStore.role === 'teacher' ? '/teacher/workbench' : '/student/dashboard')
    }
    return next()
  }

  // 2. 需要认证 — 检查 Token
  if (!authStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    return next('/login')
  }

  // 3. 检查角色权限
  if (to.meta.role && to.meta.role !== authStore.role) {
    ElMessage.error('无权限访问')
    return next(authStore.role === 'teacher' ? '/teacher/workbench' : '/student/dashboard')
  }

  next()
})

export default router
```

### 4.3 初始化逻辑

`App.vue` 的 `onMounted` 中：

```javascript
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

onMounted(async () => {
  // 从 localStorage 恢复 Token
  authStore.restoreSession()
  // 如果 Token 存在，验证有效性并获取用户信息
  if (authStore.token) {
    try {
      await authStore.fetchUserInfo()
    } catch {
      // Token 过期，清除
      authStore.logout()
      router.push('/login')
    }
  }
})
```

---

## 5. 状态管理（Pinia Store）

### 5.1 认证 Store `stores/auth.js`

```javascript
// stores/auth.js
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  // ---- 状态 ----
  const token = ref(localStorage.getItem('token'))
  const userInfo = ref(null)  // { userNo, name, role, ... }

  // ---- 计算属性 ----
  const role = computed(() => userInfo.value?.role ?? null)
  const userNo = computed(() => userInfo.value?.userNo ?? null)
  const name = computed(() => userInfo.value?.name ?? null)
  const isLoggedIn = computed(() => !!token.value)

  // ---- 操作 ----
  function setSession(loginRes) {
    token.value = loginRes.token
    userInfo.value = loginRes.userInfo
    localStorage.setItem('token', loginRes.token)
  }

  function restoreSession() {
    const saved = localStorage.getItem('token')
    if (saved) token.value = saved
  }

  async function fetchUserInfo() {
    userInfo.value = await authApi.getUserInfo()
    return userInfo.value
  }

  async function login(credentials) {
    const res = await authApi.login(credentials)
    setSession(res)
  }

  async function casCallback(data) {
    const res = await authApi.casCallback(data)
    setSession(res)
  }

  function logout() {
    token.value = null
    userInfo.value = null
    localStorage.removeItem('token')
    authApi.logout().catch(() => {})
  }

  async function checkLockStatus(username) {
    return await authApi.checkLockStatus(username)
  }

  async function changePassword(data) {
    await authApi.changePassword(data)
  }

  return {
    token, userInfo, role, userNo, name, isLoggedIn,
    setSession, restoreSession, fetchUserInfo,
    login, casCallback, logout, checkLockStatus, changePassword,
  }
})
```

### 5.2 评审任务 Store `stores/eval.js`

```javascript
// stores/eval.js
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { evalApi } from '@/api/eval'

export const useEvalStore = defineStore('eval', () => {
  // ---- 状态 ----
  const activeTasks = ref({})        // { [taskId]: TaskStatusInfo }
  const pollingTimers = ref({})      // { [taskId]: intervalHandle }

  // ---- 工具函数 ----
  /**
   * 状态 → 百分比映射
   * @param {number} status
   * @returns {number}
   */
  function getProgressPercent(status) {
    const map = { 10: 20, 20: 40, 30: 60, 40: 80, 50: 100 }
    return map[status] ?? 0
  }

  function getStatusText(status) {
    const map = { 10: '等待中', 20: 'OCR解析中', 30: '标准检索中', 40: 'AI分析中', 50: '已完成', '-1': '失败' }
    return map[status] ?? '未知'
  }

  // ---- 操作 ----
  function updateTaskStatus(data) {
    activeTasks.value[data.taskId] = data
  }

  /**
   * 开始轮询任务状态，每 3s 一次
   * @param {string} taskId
   * @param {number} [interval=3000]
   */
  function startPolling(taskId, interval = 3000) {
    if (pollingTimers.value[taskId]) return

    const timer = setInterval(async () => {
      try {
        const data = await evalApi.getTaskStatus(taskId)
        updateTaskStatus(data)
        if (data.status === 50 || data.status === -1) {
          stopPolling(taskId)
        }
      } catch {
        stopPolling(taskId)
      }
    }, interval)

    pollingTimers.value[taskId] = timer
  }

  function stopPolling(taskId) {
    const timer = pollingTimers.value[taskId]
    if (timer) {
      clearInterval(timer)
      delete pollingTimers.value[taskId]
    }
  }

  function $reset() {
    Object.keys(pollingTimers.value).forEach(stopPolling)
    activeTasks.value = {}
  }

  return {
    activeTasks, pollingTimers,
    getProgressPercent, getStatusText,
    updateTaskStatus, startPolling, stopPolling, $reset,
  }
})
```

状态 → 百分比映射表：

| Redis 状态 | 阶段说明 | 进度百分比 |
|:---|:---|:---|
| `10` | 等待队列中 | 20% |
| `20` | PaddleOCR 图文解析中 | 40% |
| `30` | Milvus 评分标准检索中 | 60% |
| `40` | DeepSeek 深度分析中 | 80% |
| `50` | 完成 | 100% |
| `-1` | 失败 | — |

### 5.3 全局 Store 设计原则

- 每个 store 使用 `defineStore` + 组合式 API 风格
- 持久化：仅 Token 存入 `localStorage`，其余状态内存中管理
- 页面刷新时 `App.vue` 的 `onMounted` 恢复 Token 并重新获取用户信息
- 无类型标注，通过 JSDoc 注释辅助 IDE 提示

---

## 6. API 对接层

### 6.1 目录说明

```
src/api/
├── request.js    # Axios 实例：baseURL、拦截器配置
├── auth.js       # 认证 API
├── file.js       # 文件上传 API
├── eval.js       # 评审任务 API
├── review.js     # 评语/分数 API
└── user.js       # 用户/课程查询 API
```

### 6.2 统一返回格式

后端所有接口统一返回以下 JSON 结构：

```javascript
// response body
{
  "code": 200,        // 200=成功，其他=失败
  "message": "ok",    // 成功/错误消息
  "data": { ... }     // 业务数据
}
```

响应拦截器自动解包：当 `code === 200` 时，组件中 `await api.xxx()` 直接拿到 `data` 字段。

### 6.3 封装的 API 函数（按模块）

#### `api/auth.js`

```javascript
import http from './request'

export const authApi = {
  /** 本地账号密码登录 */
  login: (data) => http.post('/api/auth/login', data),

  /** CAS 统一认证回调 */
  casCallback: (data) => http.post('/api/auth/cas/callback', data),

  /** 获取当前用户信息（用于 Token 验证） */
  getUserInfo: () => http.get('/api/auth/me'),

  /** 登出 */
  logout: () => http.post('/api/auth/logout'),

  /** 检查账户锁定状态 */
  checkLockStatus: (username) =>
    http.get('/api/auth/lock-status', { params: { username } }),

  /** 修改密码 */
  changePassword: (data) => http.post('/api/auth/change-password', data),
}
```

#### `api/file.js`

```javascript
import http from './request'

export const fileApi = {
  /** 上传文件，支持进度回调 onUploadProgress */
  upload: (formData, onUploadProgress) =>
    http.post('/api/file/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress,
    }),

  /** 下载文件，返回 Blob */
  download: (fileId) =>
    http.get(`/api/file/download/${fileId}`, { responseType: 'blob' }),

  /** 获取文件元信息 */
  getFileInfo: (fileId) => http.get(`/api/file/info/${fileId}`),
}
```

#### `api/eval.js`

```javascript
import http from './request'

export const evalApi = {
  /**
   * 触发 AI 评审
   * @param {{ courseId, studentNo?, stageNum, isJointReview? }} data
   *   studentNo 为空时表示批量触发
   */
  triggerEval: (data) => http.post('/api/eval/trigger', data),

  /** 查询任务状态 */
  getTaskStatus: (taskId) => http.get(`/api/task/status/${taskId}`),

  /** 获取单个评审报告 */
  getReport: (studentNo, courseId, stage) =>
    http.get(`/api/eval/report/${studentNo}/${courseId}/${stage}`),

  /** 获取某课程某阶段全部报告 */
  getReportsByCourse: (courseId, stage) =>
    http.get(`/api/eval/reports/${courseId}/${stage}`),

  /** 下发评审结果给学生 */
  publishEval: (data) => http.post('/api/eval/publish', data),
}
```

#### `api/review.js`

```javascript
import http from './request'

export const reviewApi = {
  /** 保存/修改评语（Markdown 格式） */
  saveComment: (data) => http.put('/api/review/comment', data),

  /** 保存/修改教师评分 */
  saveScore: (data) => http.put('/api/review/score', data),
}
```

#### `api/user.js`

```javascript
import http from './request'

export const userApi = {
  /** 获取课程下全部学生及其阶段状态 */
  getCourseStudents: (courseId) =>
    http.get(`/api/user/students/${courseId}`),

  /** 获取单个学生的各阶段进度 */
  getStudentProgress: (studentNo, courseId) =>
    http.get(`/api/user/progress/${studentNo}/${courseId}`),

  /** 获取学生最终成绩 */
  getFinalScore: (studentNo, courseId) =>
    http.get(`/api/user/final-score/${studentNo}/${courseId}`),
}
```

---

## 7. 页面详细设计

### 7.1 登录页 `/login`

#### 7.1.1 布局结构

```
LoginPage.vue
└── 全屏渐变背景 + 白色居中卡片 (420px)
    ├── Logo 区：系统名称 + 副标题
    └── el-tabs
        ├── el-tab-pane (label="本地登录")
        │   ├── el-form
        │   │   ├── el-input (用户名，v-model.trim，prefix-icon=User)
        │   │   ├── el-input (密码，type="password"，prefix-icon=Lock，show-password)
        │   │   └── el-button (登录，:loading，全宽)
        │   └── el-alert (警告提示，锁定倒计时，type="warning")
        │
        └── el-tab-pane (label="CAS 统一认证")
            ├── 说明文字
            └── el-button ("前往 CAS 统一身份认证"，全宽)
```

#### 7.1.2 状态与交互

| 状态 | 界面表现 | 处理 |
|:---|:---|:---|
| 初始 | 登录按钮可用，无错误提示 | — |
| 登录中 | 按钮 loading + 禁用 | 防重复提交 |
| 登录失败(密码错) | `ElMessage.error` 提示 | 不清空密码，不暴露具体错误原因 |
| 账户锁定 | `el-alert` 显示剩余分钟数 | 禁用登录按钮，启动倒计时轮询 |
| CAS 登录 | 跳转后端 CAS 地址 | 监听 URL 中的 `ticket` 参数回调 |

#### 7.1.3 锁定状态处理

```javascript
// 登录失败时检查是否被锁定
const handleLogin = async () => {
  try {
    await authStore.login({ username: username.value, password: password.value })
    router.push(authStore.role === 'teacher' ? '/teacher/workbench' : '/student/dashboard')
  } catch (err) {
    if (err.message && err.message.includes('锁定')) {
      startLockCountdown(username.value)
    } else {
      ElMessage.error('用户名或密码错误')
    }
  }
}
```

#### 7.1.4 CAS 回调处理

```javascript
// LoginPage.vue 的 onMounted
onMounted(async () => {
  const ticket = route.query.ticket
  if (ticket) {
    try {
      await authStore.casCallback({ ticket, service: window.location.origin + '/login' })
      router.replace(authStore.role === 'teacher' ? '/teacher/workbench' : '/student/dashboard')
    } catch {
      ElMessage.error('CAS 认证失败，请重试')
    }
  }
})
```

---

### 7.2 学生提交面板 `/student/dashboard`

#### 7.2.1 布局结构

```
AppLayout.vue
├── 顶部导航栏
│   ├── 左侧: 系统名称 + 角色标签
│   └── 右侧: 用户名 + 退出按钮
│
└── 主内容区
    ├── 课程信息区
    │   ├── 课程名称（大标题）
    │   └── 三阶段权重标签
    │
    └── 三阶段卡片网格（3 列，响应式单列）
        ├── 阶段卡片 × 3（统一结构）
        │   ├── 卡片头：阶段标题 + StageStatusBadge
        │   ├── 卡片体：
        │   │   ├── [状态 0] el-upload 拖拽上传区
        │   │   ├── [状态 1] 等待图标 + 进度条
        │   │   ├── [状态 2] 完成图标 + 等待发布提示
        │   │   └── [状态 3] ScoreDisplay + 预览/下载按钮
        │   └── 卡片脚：提交时间
```

#### 7.2.2 阶段卡片状态机

| 状态值 | 学生可见文本 | 显示元素 | 说明 |
|:---|:---|:---|:---|
| `0` | 待评测 | 上传组件（支持重新上传覆盖） | 初始/可重传 |
| `1` | 评测中 | 等待图标 + 进度条 | 不可取消 |
| `2` | 待发布 | 勾选图标 + "等待发布" | 不可见分数 |
| `3` | 已下发 | AI 评分、教师评分、预览/下载按钮 | 完全解锁 |

#### 7.2.3 文件上传

```vue
<el-upload
  drag
  :http-request="handleUpload"
  :before-upload="beforeUpload"
  :show-file-list="false"
>
  <el-icon size="40"><UploadFilled /></el-icon>
  <div>拖拽文件到此处，或 <em>点击选择</em></div>
  <div class="upload-tip">支持格式：.zip / .docx / .pdf，≤ 100MB</div>
</el-upload>
```

**beforeUpload 校验规则**：
- 阶段一：仅 `.zip`（代码包），≤ 100MB
- 阶段二/三：`.zip`、`.docx`、`.pdf`，≤ 100MB

#### 7.2.4 状态驱动的上传

```javascript
// 自定义上传，支持进度
const handleUpload = async (stageNum, options) => {
  const formData = new FormData()
  formData.append('file', options.file)
  formData.append('courseId', courseId)
  formData.append('stageNum', String(stageNum))

  await fileApi.upload(formData, (e) => {
    if (e.total) uploadProgress.value = Math.round((e.loaded * 100) / e.total)
  })
  ElMessage.success('上传成功')
  await fetchProgress()  // 刷新状态
}
```

#### 7.2.5 PDF 预览

```javascript
// 状态 = 3 时可预览
const previewPdf = async (stageNum) => {
  // 1. 获取报告数据
  const report = await evalApi.getReport(studentNo, courseId, stageNum)
  // 2. 下载 PDF Blob
  const blob = await fileApi.download(report.reportFileId)
  // 3. 使用 pdfjs-dist 渲染到 Canvas（或直接打开 Blob URL）
  const url = URL.createObjectURL(blob)
  window.open(url)
}
```

---

### 7.3 教师工作台 `/teacher/workbench`

#### 7.3.1 布局结构

```
AppLayout.vue
├── 顶部导航栏
└── 主内容区 (flex)
    ├── 左侧边栏 (200px)
    │   ├── 标题 "课程列表"
    │   └── el-menu (vertical)
    │       └── 课程项 → 点击切换当前课程
    │
    └── 右侧主区域 (flex: 1)
        ├── 工具栏
        │   ├── el-radio-group (阶段一/二/三)
        │   ├── el-select (筛选状态)
        │   ├── el-button "刷新"
        │   └── el-button "一键批量评审"
        │
        ├── 批量任务结果提示 (el-alert)
        │
        └── 学生列表 (el-table)
            ├── 学号 | 姓名 | 班级
            ├── 阶段状态 (StageStatusBadge)
            ├── AI 评分 | 教师评分
            └── 操作列
                ├── [状态=0] "触发评审"
                ├── [状态=1] "评审中" (禁用)
                ├── [状态=2] "评阅" → 跳转 ReviewDetail
                └── [状态=3] "已下发" 标签
```

#### 7.3.2 批量评审弹窗

```vue
<el-dialog v-model="batchDialogVisible" title="批量评审确认" width="480px">
  <p>
    即将对 <b>{{ courseName }}</b> 的 <b>阶段{{ selectedStage }}</b> 中
    所有 <b>{{ pendingCount }}</b> 名待评审学生触发 AI 评测。
  </p>
  <el-checkbox v-model="isJointReview">
    开启联合评审（引入上一阶段教师终审评语）
  </el-checkbox>

  <template #footer>
    <el-button @click="batchDialogVisible = false">取消</el-button>
    <el-button type="primary" :loading="batchLoading" @click="confirmBatchEval">
      确认触发
    </el-button>
  </template>
</el-dialog>
```

#### 7.3.3 进度轮询

```javascript
// 触发评审后立即启动轮询
const handleTriggerEval = async (studentNo) => {
  const result = await evalApi.triggerEval({
    courseId: selectedCourseId.value,
    studentNo,       // undefined = 批量
    stageNum: selectedStage.value,
  })

  result.taskIds.forEach(taskId => {
    evalStore.startPolling(taskId)
  })

  // 更新本地状态
  if (studentNo) {
    const stu = students.value.find(s => s.studentNo === studentNo)
    if (stu) {
      const stage = stu.stages.find(s => s.stageNum === selectedStage.value)
      if (stage) stage.status = 1
    }
  }
}
```

#### 7.3.4 表格操作列逻辑

```javascript
// 根据当前阶段状态渲染不同操作按钮
const getStageStatus = (student) => {
  const stage = student.stages.find(s => s.stageNum === selectedStage.value)
  return stage?.status ?? 0
}

// 模板中根据状态渲染：
//   status === 0 → <el-button type="primary" @click="handleTriggerEval(row.studentNo)">触发评审</el-button>
//   status === 1 → <el-button disabled>评审中</el-button>
//   status === 2 → <el-button type="warning" @click="goToReview(row.studentNo)">评阅</el-button>
//   status === 3 → <el-tag type="success">已下发</el-tag>
```

---

### 7.4 教师评阅详情 `/teacher/review/:studentNo/:stage`

#### 7.4.1 布局结构

```
ReviewDetail.vue
├── 顶栏
│   ├── 返回按钮 (router.back)
│   ├── 学生学号
│   └── 操作按钮区
│       ├── "开始 AI 评审" (status=0)
│       ├── "保存评语" (status≥2)
│       └── "保存分数并下发" (status≥2)
│
├── el-tabs (阶段切换)
│
├── TaskProgressBar (status=1 时显示)
│
└── 评审内容区 (flex)
    ├── 左侧编辑器 (flex: 1)
    │   ├── 编辑器头部 ("评语编辑器" + 未保存标记)
    │   └── textarea Markdown 编辑区
    │
    └── 右侧分数面板 (260px)
        ├── AI 严格评分（大字显示）
        ├── 教师评分 (el-input-number, 0-100, 步长 0.5)
        ├── 使用模型 (el-tag)
        └── 联合评审标识 (el-tag)
```

#### 7.4.2 评语编辑

使用原生 `<textarea>` 进行 Markdown 编辑（`v-md-editor` 与 Vue 3 存在 peer dependency 冲突，暂以 textarea 替代）。后续可替换为 `@kangc/v-md-editor@next`。

```vue
<textarea
  v-model="markdownContent"
  class="markdown-editor"
  @input="isDirty = true"
  :disabled="report.status < 2"
/>
```

#### 7.4.3 保存下发流程

```javascript
const handlePublish = async () => {
  // 1. 先保存评语
  await reviewApi.saveComment({
    studentNo: studentNo.value,
    courseId: courseId.value,
    stageNum: activeStage.value,
    finalReportMarkdown: markdownContent.value,
  })

  // 2. 如果修改了分数，保存分数
  if (teacherScore.value !== undefined && teacherScore.value !== report.value?.teacherScore) {
    await reviewApi.saveScore({
      studentNo: studentNo.value,
      courseId: courseId.value,
      stageNum: activeStage.value,
      teacherScore: teacherScore.value,
    })
  }

  // 3. 下发
  await evalApi.publishEval({
    studentNo: studentNo.value,
    courseId: courseId.value,
    stageNum: activeStage.value,
  })

  ElMessage.success('已下发，学生端可查看')
  await fetchReport()  // 刷新本地状态
}
```

---

## 8. 核心组件树

### 8.1 全局组件

| 组件 | 所在目录 | 功能 | 复用范围 |
|:---|:---|:---|:---|
| `AppLayout.vue` | `components/` | 顶部导航栏 + 用户信息 + 退出确认 + 主体区 `<slot>` | 所有需登录页面 |
| `StageStatusBadge.vue` | `components/` | 状态值 → 彩色标签（info/warning/primary/success） | 学生端卡片、教师端表格 |
| `TaskProgressBar.vue` | `components/` | 步骤指示 + 进度条 + 轮询 + 错误提示 | 教师端评阅详情 |
| `ScoreDisplay.vue` | `components/` | AI 分（蓝色）vs 教师分（橙色）对比展示 | 学生端卡片、教师端评阅详情 |

### 8.2 StageStatusBadge 组件规格

```javascript
// props: { status: Number, size?: 'small' | 'default' }
// status 合法值：0 | 1 | 2 | 3

const statusMap = {
  0: { type: 'info',    text: '待评测' },
  1: { type: 'warning', text: '评测中' },
  2: { type: 'primary', text: '待发布' },
  3: { type: 'success', text: '已下发' },
}
```

### 8.3 TaskProgressBar 组件规格

```javascript
// props: { taskId: String, pollingInterval?: Number }
// 默认 pollingInterval = 3000 (ms)

const stepMap = [
  { value: 10, label: '等待中' },
  { value: 20, label: 'OCR解析' },
  { value: 30, label: '标准检索' },
  { value: 40, label: 'AI分析' },
  { value: 50, label: '完成' },
]
```

组件内部：`onMounted` 启动轮询，`onUnmounted` 清除轮询，自动感知终态（50/-1）停止。

### 8.4 ScoreDisplay 组件规格

```javascript
// props: { aiScore?: Number, teacherScore?: Number, size?: 'small' | 'large' }
// 默认 size = 'small'

// 显示规则：
//   aiScore 为 undefined → 显示 "-"
//   teacherScore 为 undefined → 显示 "-"
//   否则 → .toFixed(1) 显示一位小数
```

---

## 9. 数据结构定义

> 本章用 JSDoc 标注所有核心数据结构的字段和含义。
> 由于使用纯 JavaScript，组件内不强制类型校验，以下定义用于开发参考。

### 9.1 用户与认证

```javascript
/**
 * @typedef {Object} UserInfo
 * @property {string} userNo   - 学号/工号
 * @property {string} name     - 姓名
 * @property {'student'|'teacher'|'admin'} role
 * @property {string} [gender]
 * @property {string} [deptCode]
 * @property {string} [deptName]
 * @property {string} [majorCode]
 * @property {string} [majorName]
 * @property {string} [classCode]
 * @property {string} [className]
 */

/**
 * @typedef {Object} LoginResponse
 * @property {string} token
 * @property {UserInfo} userInfo
 */

/**
 * @typedef {Object} LockStatus
 * @property {boolean} locked
 * @property {number} remainSeconds
 */
```

### 9.2 阶段与提交

```javascript
/**
 * @typedef {0|1|2|3} StageStatus
 * 0-待评测  1-评测中  2-待发布  3-已下发
 */

/**
 * @typedef {Object} StageDetail
 * @property {1|2|3} stageNum
 * @property {StageStatus} status
 * @property {number} [aiScore]
 * @property {number} [teacherScore]
 * @property {string} [aiReportMarkdown]
 * @property {string} [finalReportMarkdown]
 * @property {string} [submitTime]
 * @property {string} [evalTriggerTime]
 * @property {string} [reviewTime]
 */

/**
 * @typedef {Object} CourseStudent
 * @property {string} studentNo
 * @property {string} name
 * @property {string} className
 * @property {StageDetail[]} stages      // 长度为 3 的阶段状态数组
 */

/**
 * @typedef {Object} StudentProgress
 * @property {string} studentNo
 * @property {string} courseId
 * @property {StageDetail[]} stages
 */

/**
 * @typedef {Object} FinalScore
 * @property {string} studentNo
 * @property {string} courseId
 * @property {number} [finalScore]
 * @property {string} [teacherFinalComment]
 * @property {0|1} gradeStatus            // 0-未生成 1-已下发可见
 */
```

### 9.3 评审任务

```javascript
/**
 * @typedef {Object} EvalTriggerResult
 * @property {number} triggeredCount
 * @property {string[]} taskIds
 * @property {string[]} skippedStudents
 */

/**
 * @typedef {Object} TaskStatusInfo
 * @property {string} taskId
 * @property {number} status              // 10/20/30/40/50/-1
 * @property {string} statusText
 * @property {string} [errorMsg]
 * @property {string} [studentNo]
 * @property {number} [stageNum]
 */

/**
 * @typedef {Object} EvalReport
 * @property {number} aiScore
 * @property {string} aiReportMarkdown
 * @property {number} [teacherScore]
 * @property {string} [finalReportMarkdown]
 * @property {string} [modelUsed]
 * @property {StageStatus} status
 * @property {boolean} [isJointReview]
 */
```

### 9.4 文件

```javascript
/**
 * @typedef {Object} FileInfo
 * @property {string} objectName
 * @property {string} originalName
 * @property {number} size
 * @property {string} type
 */
```

---

## 10. Axios 拦截器配置

### 10.1 实例创建

```javascript
// src/api/request.js
import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
})

export default http
```

### 10.2 请求拦截器

```javascript
http.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)
```

### 10.3 响应拦截器

```javascript
http.interceptors.response.use(
  (response) => {
    const res = response.data
    // 后端统一格式：{ code, message, data }
    if (res.code === 200) {
      return res.data  // 自动解包，组件中直接拿到 data
    }
    // 业务逻辑错误（非 200）
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message))
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      router.push('/login')
      ElMessage.warning('登录已过期，请重新登录')
    } else if (error.response?.status === 403) {
      ElMessage.error('无权限执行此操作')
    } else if (error.response?.status >= 500) {
      ElMessage.error('服务器错误，请稍后重试')
    }
    return Promise.reject(error)
  },
)
```

### 10.4 环境变量

```bash
# .env.development
VITE_API_BASE_URL=/api

# .env.production
VITE_API_BASE_URL=/api
```

---

## 11. 开发规范

### 11.1 命名规范

| 类型 | 规范 | 示例 |
|:---|:---|:---|
| 组件/页面文件 | PascalCase | `LoginPage.vue`、`TaskProgressBar.vue` |
| JavaScript 变量/函数 | camelCase | `isLoading`、`fetchReport`、`handleUpload` |
| 常量 | UPPER_SNAKE | `MAX_FILE_SIZE`、`POLL_INTERVAL` |
| 路由路径 | kebab-case | `/teacher/workbench`、`/teacher/review/:studentNo` |
| Pinia store | camelCase，use 前缀 | `useAuthStore`、`useEvalStore` |
| API 函数 | camelCase | `triggerEval`、`getTaskStatus` |
| CSS class | kebab-case | `.student-table`、`.stage-card` |

### 11.2 组件规范

- 每个 `.vue` 文件一个组件，使用 `<script setup>`
- Props 通过 `defineProps()` 定义，用 JSDoc 注释说明类型
- Emits 通过 `defineEmits()` 声明
- 组件边界职责清晰，超过 300 行考虑拆分
- 无需 `lang="ts"`、无需类型标注

### 11.3 状态管理原则

- 跨页面共享的状态 → Pinia store
- 页面内部临时状态 → `ref()` / `reactive()`
- 组件间传递 → `props` + `emits`，避免 provide/inject 滥用
- Token 持久化在 `localStorage`，其余状态不持久化

### 11.4 样式规范

- 使用 Element Plus 内置样式
- 组件内样式使用 `<style scoped>`
- 全局样式统一在 `src/style.css`
- 不建议使用 `:deep()` 选择器穿透；确实需要时谨慎使用

---

## 12. 构建与部署

### 12.1 开发环境

```bash
# 安装依赖
cd frontend && npm install

# 启动开发服务器（HMR + 代理到后端 localhost:8080）
npm run dev
# → http://localhost:5173
```

### 12.2 生产构建

```bash
npm run build
# → dist/ 目录输出静态文件
```

### 12.3 Vite 配置 (vite.config.js)

```javascript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
```

### 12.4 Docker 构建

```dockerfile
# frontend/Dockerfile (多阶段构建)
FROM node:18-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

### 12.5 Nginx 配置

```nginx
# deploy/nginx.conf
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    # Vue Router history 模式
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 后端 API 反向代理
    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_read_timeout 120s;
        client_max_body_size 100m;
    }

    # AI 服务代理
    location /ai/ {
        proxy_pass http://ai-service:8000;
        proxy_read_timeout 300s;
    }
}
```

---

## 13. 接口索引表

### 认证相关

| 方法 | 路径 | 请求体 | 响应体 | 页面 |
|:---|:---|:---|:---|:---|
| `POST` | `/api/auth/login` | `{username, password}` | `{token, userInfo}` | 登录页 |
| `POST` | `/api/auth/cas/callback` | `{ticket, service}` | `{token, userInfo}` | 登录页 |
| `GET` | `/api/auth/me` | — | `UserInfo` | 全局（Token 验证） |
| `POST` | `/api/auth/logout` | — | — | 所有页面 |
| `GET` | `/api/auth/lock-status` | `?username=` | `{locked, remainSeconds}` | 登录页 |

### 文件相关

| 方法 | 路径 | 请求体 | 响应体 | 页面 |
|:---|:---|:---|:---|:---|
| `POST` | `/api/file/upload` | MultipartFormData | `{submissionId}` | 学生提交面板 |
| `GET` | `/api/file/download/{fileId}` | — | Blob | 学生提交面板 |
| `GET` | `/api/file/info/{fileId}` | — | `FileInfo` | 学生提交面板 |

### 评审相关

| 方法 | 路径 | 请求体 | 响应体 | 页面 |
|:---|:---|:---|:---|:---|
| `POST` | `/api/eval/trigger` | `{courseId, studentNo?, stageNum, isJointReview?}` | `EvalTriggerResult` | 教师工作台 |
| `GET` | `/api/task/status/{taskId}` | — | `TaskStatusInfo` | 教师工作台（轮询） |
| `GET` | `/api/eval/report/{studentNo}/{courseId}/{stage}` | — | `EvalReport` | 评阅详情 |
| `GET` | `/api/eval/reports/{courseId}/{stage}` | — | `EvalReport[]` | 评阅详情 |
| `POST` | `/api/eval/publish` | `{studentNo, courseId, stageNum}` | — | 评阅详情 |

### 评阅相关

| 方法 | 路径 | 请求体 | 响应体 | 页面 |
|:---|:---|:---|:---|:---|
| `PUT` | `/api/review/comment` | `{studentNo, courseId, stageNum, finalReportMarkdown}` | — | 评阅详情 |
| `PUT` | `/api/review/score` | `{studentNo, courseId, stageNum, teacherScore}` | — | 评阅详情 |

### 用户查询相关

| 方法 | 路径 | 请求体 | 响应体 | 页面 |
|:---|:---|:---|:---|:---|
| `GET` | `/api/user/students/{courseId}` | — | `CourseStudent[]` | 教师工作台 |
| `GET` | `/api/user/progress/{studentNo}/{courseId}` | — | `StudentProgress` | 学生提交面板 |
| `GET` | `/api/user/final-score/{studentNo}/{courseId}` | — | `FinalScore` | 学生提交面板 |

---

## 附录 A：从 Lovable 生成后的对接检查清单

生成前端代码后，按以下清单逐项验证与后端对接的正确性：

- [ ] **Token 流程**：登录后 Token 存入 localStorage，后续请求自动注入
- [ ] **401 重定向**：Token 过期被后端拒绝时跳转登录页
- [ ] **学生/教师路由隔离**：学生不能访问教师页面，反之亦然
- [ ] **上传文件限制**：格式/大小在前端校验，`beforeUpload` 拒绝不合格文件
- [ ] **任务进度轮询**：3 秒间隔，状态 50 或 -1 时停止
- [ ] **评语编辑器**：AI 完成后的 Markdown 内容正确加载到编辑区
- [ ] **分数范围校验**：0-100，保留一位小数
- [ ] **状态机完整流转**：0→(教师触发)→1→(AI完成)→2→(教师下发)→3
- [ ] **PDF 预览**：已下发状态下可正常打开报告
- [ ] **批量操作反馈**：触发后显示成功数、跳过数
- [ ] **锁定状态提示**：连续失败 5 次后显示账户剩余锁定时间
- [ ] **API 基础路径**：开发环境 `/api`，生产环境 `/api`（Nginx 代理）
- [ ] **响应解包**：`{code, message, data}` 结构中直接使用 `data`

## 附录 B：JS 版 vs TS 版差异速查

| 项目 | TS 版 | JS 版 |
|:---|:---|:---|
| Vue 组件声明 | `<script setup lang="ts">` | `<script setup>` |
| 源文件后缀 | `.ts` / `.tsx` | `.js` |
| 类型定义 | `types/index.ts` (interface/type) | 无独立类型文件，用 JSDoc 注释 |
| 配置文件 | `vite.config.ts`、`tsconfig.json` | `vite.config.js`，无 tsconfig |
| Props 定义 | `defineProps<{...}>()` | `defineProps({ ... })` |
| API 函数 | `http.get<Type>(...)` 带泛型 | `http.get(...)` |
| 开发依赖 | typescript、@types/* 等 | 无额外类型依赖 |
