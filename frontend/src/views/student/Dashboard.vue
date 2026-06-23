<template>
  <AppLayout>
    <div class="student-dashboard">
      <h2>{{ courseName || '课程' }}</h2>
      <div class="stages">
        <el-card v-for="s in stages" :key="s.stageNum" class="stage-card">
          <div style="display:flex;justify-content:space-between;align-items:center">
            <div>阶段 {{ s.stageNum }}</div>
            <StageStatusBadge :status="s.status" />
          </div>
          <div style="margin-top:12px">
            <div v-if="s.status === 0">
              <el-upload drag :before-upload="(f)=>beforeUpload(f,s.stageNum)" :http-request="(o)=>handleUpload(s.stageNum,o)" :show-file-list="false">
                <div class="el-upload__text">拖拽上传或点击选择</div>
                <div class="el-upload__tip">支持 .zip/.docx/.pdf，≤100MB</div>
              </el-upload>
            </div>
            <div v-else-if="s.status === 1">
              <el-progress :percentage="progressMap[s.stageNum] || 0" />
            </div>
            <div v-else>
              <ScoreDisplay :aiScore="s.aiScore" :teacherScore="s.teacherScore" />
            </div>
          </div>
        </el-card>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import StageStatusBadge from '@/components/StageStatusBadge.vue'
import ScoreDisplay from '@/components/ScoreDisplay.vue'
import { userApi } from '@/api/user'
import { fileApi } from '@/api/file'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const courseId = ref(null)
const courseName = ref('示例课程')
const stages = ref([
  { stageNum: 1, status: 0 },
  { stageNum: 2, status: 0 },
  { stageNum: 3, status: 0 },
])
const progressMap = ref({})

onMounted(async () => {
  // 尝试通过 userApi 获取学生进度（需提供 studentNo 与 courseId）
  try {
    if (auth.userNo && courseId.value) {
      const res = await userApi.getStudentProgress(auth.userNo, courseId.value)
      stages.value = res.stages || stages.value
    }
  } catch (e) {
    // ignore for stub
  }
})

function beforeUpload(file, stageNum) {
  const ok = file.size <= 100 * 1024 * 1024
  if (!ok) ElMessage.error('文件过大，最大 100MB')
  return ok
}

async function handleUpload(stageNum, options) {
  const fd = new FormData()
  fd.append('courseId', courseId.value || '')
  fd.append('stageNum', String(stageNum))
  fd.append('codePackage', options.file)
  try {
    await fileApi.upload(fd, (e) => {
      if (e.total) progressMap.value[stageNum] = Math.round((e.loaded * 100) / e.total)
    })
    ElMessage.success('上传成功')
    // 刷新进度（省略）
  } catch (e) {
    ElMessage.error('上传失败')
  }
}
</script>

<style scoped>
.stages { display:flex; gap:16px }
.stage-card { width:30% }
</style>
