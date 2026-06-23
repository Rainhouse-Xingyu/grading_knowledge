<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2>登录</h2>
      <el-tabs v-model:active-name="activeTab">
        <el-tab-pane label="本地登录" name="local">
          <el-form :model="form" ref="formRef" class="login-form">
            <el-form-item prop="username">
              <el-input v-model="form.username" placeholder="用户名" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="form.password" placeholder="密码" show-password type="password" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loading" @click="handleLogin" style="width:100%">登录</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="CAS 统一认证" name="cas">
          <div>点击跳转到 CAS</div>
          <el-button type="primary" @click="gotoCas">前往 CAS 统一身份认证</el-button>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const activeTab = ref('local')
const formRef = ref(null)
const form = ref({ username: '', password: '' })

onMounted(async () => {
  const ticket = route.query.ticket
  if (ticket) {
    try {
      await auth.casCallback({ ticket, service: window.location.origin + '/login' })
      router.replace(auth.role === 'teacher' ? '/teacher/workbench' : '/student/dashboard')
    } catch (e) {
      ElMessage.error('CAS 认证失败')
    }
  }
})

async function handleLogin() {
  loading.value = true
  try {
    await auth.login({ username: form.value.username, password: form.value.password })
    router.push(auth.role === 'teacher' ? '/teacher/workbench' : '/student/dashboard')
  } catch (e) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}

function gotoCas() {
  // 后端会提供 CAS 跳转地址；这里直接打开 /api/auth/cas/login
  window.location.href = '/api/auth/cas/login'
}
</script>

<style scoped>
.login-page { display:flex; align-items:center; justify-content:center; height:100vh }
.login-card { width:420px }
.login-form { margin-top:12px }
</style>
