<template>
  <div>
    <el-progress :percentage="percent" :status="status === -1 ? 'exception' : 'active'" />
    <div style="margin-top:8px">{{ statusText }}</div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useEvalStore } from '@/stores/eval'
const props = defineProps({ taskId: { type: String, required: true }, pollingInterval: { type: Number, default: 3000 } })
const evalStore = useEvalStore()
const percent = ref(0)
const status = ref(10)
const statusText = ref('等待中')

function updateFromStore() {
  const info = evalStore.activeTasks[props.taskId]
  if (info) {
    percent.value = evalStore.getProgressPercent(info.status)
    status.value = info.status
    statusText.value = evalStore.getStatusText(info.status)
  }
}

onMounted(() => {
  evalStore.startPolling(props.taskId, props.pollingInterval)
  updateFromStore()
})

onUnmounted(() => {
  evalStore.stopPolling(props.taskId)
})

watch(() => evalStore.activeTasks, updateFromStore, { deep: true })
</script>

<style scoped></style>
