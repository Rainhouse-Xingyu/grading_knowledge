<template>
  <el-tag :type="typeMap[status]?.type || 'info'" size="small">{{ typeMap[status]?.text || '未知' }}</el-tag>
</template>

<script setup>
const props = defineProps({ status: { type: Number, required: true } })
const typeMap = {
  0: { type: 'info', text: '待评测' },
  1: { type: 'warning', text: '评测中' },
  2: { type: 'primary', text: '待发布' },
  3: { type: 'success', text: '已下发' },
}
</script>

<style scoped></style>
