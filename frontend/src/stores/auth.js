import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token'))
  const userInfo = ref(null)

  const role = computed(() => userInfo.value?.role ?? null)
  const userNo = computed(() => userInfo.value?.userNo ?? null)
  const name = computed(() => userInfo.value?.name ?? null)
  const isLoggedIn = computed(() => !!token.value)

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
