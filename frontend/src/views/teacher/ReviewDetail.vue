<template>
  <AppLayout>
    <div style="padding:12px;">
      <el-button @click="$router.back()">返回</el-button>
      <h3>评阅详情 - {{ studentNo }} - 阶段 {{ stage }}</h3>

      <div style="display:flex;gap:12px;margin-top:12px">
        <div style="flex:1">
          <el-card>
            <div style="margin-bottom:8px">评语编辑器</div>
            <textarea v-model="markdown" style="width:100%;height:320px"></textarea>
          </el-card>
        </div>

        <div style="width:300px">
          <el-card>
            <div>AI 分: <strong>{{ report.aiScore ?? '-' }}</strong></div>
            <div style="margin-top:8px">教师分</div>
            <el-input-number v-model="teacherScore" :min="0" :max="100" :step="0.5" />
            <div style="margin-top:12px;display:flex;gap:8px">
              <el-button type="primary" @click="handleSave">保存评语</el-button>
              <el-button type="success" @click="handlePublish">保存并下发</el-button>
            </div>
          </el-card>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppLayout from '@/components/AppLayout.vue'
import { evalApi } from '@/api/eval'
import { reviewApi } from '@/api/review'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const studentNo = route.params.studentNo
const stage = Number(route.params.stage)
const report = ref({})
const markdown = ref('')
const teacherScore = ref(undefined)

onMounted(async () => {
  await fetchReport()
})

async function fetchReport() {
  try {
    const res = await evalApi.getReport(studentNo, route.query.courseId || '', stage)
    report.value = res || {}
    markdown.value = report.value.finalReportMarkdown || report.value.aiReportMarkdown || ''
    teacherScore.value = report.value.teacherScore
  } catch (e) {
    ElMessage.error('加载报告失败')
  }
}

async function handleSave() {
  try {
    await reviewApi.saveComment({ studentNo, courseId: route.query.courseId || '', stageNum: stage, finalReportMarkdown: markdown.value })
    if (teacherScore.value !== undefined && teacherScore.value !== report.value.teacherScore) {
      await reviewApi.saveScore({ studentNo, courseId: route.query.courseId || '', stageNum: stage, teacherScore: teacherScore.value })
    }
    ElMessage.success('保存成功')
    await fetchReport()
  } catch (e) { ElMessage.error('保存失败') }
}

async function handlePublish() {
  try {
    await handleSave()
    await evalApi.publishEval({ studentNo, courseId: route.query.courseId || '', stageNum: stage })
    ElMessage.success('已下发')
    router.back()
  } catch (e) { ElMessage.error('下发失败') }
}
</script>

<style scoped></style>
