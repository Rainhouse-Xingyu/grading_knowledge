import { defineStore } from 'pinia'
import { ref } from 'vue'
import { evalApi } from '@/api/eval'

export const useEvalStore = defineStore('eval', () => {
  const activeTasks = ref({})
  const pollingTimers = ref({})

  function getProgressPercent(status) {
    const map = { 10: 20, 20: 40, 30: 60, 40: 80, 50: 100 }
    return map[status] ?? 0
  }

  function getStatusText(status) {
    const map = { 10: '等待中', 20: 'OCR解析中', 30: '标准检索中', 40: 'AI分析中', 50: '已完成', '-1': '失败' }
    return map[status] ?? '未知'
  }

  function updateTaskStatus(data) {
    activeTasks.value[data.taskId] = data
  }

  function startPolling(taskId, interval = 3000) {
    if (pollingTimers.value[taskId]) return

    const timer = setInterval(async () => {
      try {
        const data = await evalApi.getTaskStatus(taskId)
        updateTaskStatus(data)
        if (data.status === 50 || data.status === -1) stopPolling(taskId)
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
