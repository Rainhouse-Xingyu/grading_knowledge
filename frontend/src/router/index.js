import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/login/LoginPage.vue'), meta: { requiresAuth: false } },
  { path: '/student/dashboard', name: 'StudentDashboard', component: () => import('@/views/student/Dashboard.vue'), meta: { requiresAuth: true, role: 'student' } },
  { path: '/teacher/workbench', name: 'TeacherWorkbench', component: () => import('@/views/teacher/Workbench.vue'), meta: { requiresAuth: true, role: 'teacher' } },
  { path: '/teacher/review/:studentNo/:stage', name: 'ReviewDetail', component: () => import('@/views/teacher/ReviewDetail.vue'), meta: { requiresAuth: true, role: 'teacher' } },
  { path: '/', redirect: '/login' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()

  if (!to.meta.requiresAuth) {
    if (authStore.isLoggedIn) {
      return next(authStore.role === 'teacher' ? '/teacher/workbench' : '/student/dashboard')
    }
    return next()
  }

  if (!authStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    return next('/login')
  }

  if (to.meta.role && to.meta.role !== authStore.role) {
    ElMessage.error('无权限访问')
    return next(authStore.role === 'teacher' ? '/teacher/workbench' : '/student/dashboard')
  }

  next()
})

export default router
