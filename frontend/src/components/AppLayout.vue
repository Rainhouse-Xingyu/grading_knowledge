<template>
  <el-container style="height:100vh">
    <el-header style="display:flex;justify-content:space-between;align-items:center">
      <div style="font-weight:600">双轨制 AI 评分系统</div>
      <div style="display:flex;gap:12px;align-items:center">
        <el-tag v-if="role">{{ role }}</el-tag>
        <div>{{ name }}</div>
        <el-button type="text" @click="handleLogout">退出</el-button>
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
</script>

<style scoped>
</style>
