<template>
  <el-container style="height:100vh">
    <el-header style="display:flex;justify-content:space-between;align-items:center">
        <div style="display:flex;gap:12px;align-items:center">
          <div style="font-weight:600">双轨制 AI 评分系统</div>
          <el-button type="text" @click="goHome">主页</el-button>
        </div>
        <div style="display:flex;gap:12px;align-items:center">
          <el-tag v-if="role">{{ role }}</el-tag>
          <div>{{ name }}</div>
          <el-popconfirm title="确认退出？" @confirm="handleLogout">
            <template #reference>
              <el-button type="text">退出</el-button>
            </template>
          </el-popconfirm>
        </div>
      </el-header>
    <el-main>
      <slot />
    </el-main>
  </el-container>
</template>

<script setup>
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'
const auth = useAuthStore()
const router = useRouter()
const role = auth.role
const name = auth.name

function handleLogout() {
  auth.logout()
  router.push('/login')
}

function goHome() {
  if (role === 'teacher') router.push('/teacher/workbench')
  else router.push('/student/dashboard')
}
</script>

<style scoped>
</style>
