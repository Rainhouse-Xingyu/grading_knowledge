<template>
  <div class="score-display">
    <div class="ai">AI: <span class="value">{{ display(aiScore) }}</span></div>
    <div class="teacher">教师: <span class="value">{{ display(teacherScore) }}</span></div>
  </div>
</template>

<script setup>
const props = defineProps({ aiScore: { type: Number, required: false }, teacherScore: { type: Number, required: false } })
function display(v) { return v === undefined || v === null ? '-' : Number(v).toFixed(1) }
</script>

<style scoped>
.score-display { display:flex; gap:12px; align-items:center }
.ai { color:#409EFF }
.teacher { color:#E67E22 }
.value { font-weight:600 }
</style>
