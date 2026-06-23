# 前端开发日志

日期：2026-06-23

概要：在 `frontend/` 下初始化 Vue 3 + Vite（JavaScript）项目骨架，包含路由、Pinia、Axios API 层、Element Plus 基础依赖，以及 Dockerfile 与 Vite 配置。按照项目的前端开发文档对目录结构与接口做了初步对齐，但未生成页面视图文件（views 下的组件仍为占位）。

已完成项：

- 新建项目骨架：`frontend/package.json`、`index.html`、`vite.config.js`、`.gitignore`、`Dockerfile`
- 添加核心入口与样式：`src/main.js`、`src/App.vue`、`src/style.css`
- 路由：`src/router/index.js`（含路由守卫）
- API 层：`src/api/request.js`、`auth.js`、`file.js`、`eval.js`、`review.js`、`user.js`
- 状态管理：`src/stores/auth.js`、`src/stores/eval.js`

后续计划（建议）：

1. 生成并实现核心页面视图：`views/login/LoginPage.vue`、`views/student/Dashboard.vue`、`views/teacher/Workbench.vue`、`views/teacher/ReviewDetail.vue`
2. 实现共享组件：`components/AppLayout.vue`、`StageStatusBadge.vue`、`TaskProgressBar.vue`、`ScoreDisplay.vue`
3. 本地安装依赖并运行：

```bash
cd frontend
npm install
npm run dev
```

4. 与后端联调基础接口（在本地后端启动的情况下）：登录、获取课程与学生列表、文件上传与下载、触发评审并轮询任务状态

记录人：自动化脚手架生成（按文档要求）
