<template>
  <AppLayout>
    <div class="workbench" style="display:flex;gap:12px">
      <el-aside width="220px">
        <h3>课程列表</h3>
        <el-menu @select="selectCourse" :default-active="selectedCourseId">
          <el-menu-item v-for="c in courses" :key="c.courseId" :index="c.courseId">{{ c.courseName }}</el-menu-item>
        </el-menu>
      </el-aside>

      <div style="flex:1">
        <div style="display:flex;gap:12px;align-items:center;margin-bottom:12px">
          <el-radio-group v-model="selectedStage">
            <el-radio-button label="1">阶段1</el-radio-button>
            <el-radio-button label="2">阶段2</el-radio-button>
            <el-radio-button label="3">阶段3</el-radio-button>
          </el-radio-group>
          <el-button type="primary" @click="refresh">刷新</el-button>
          <el-button @click="triggerBatch" type="warning">一键批量评审</el-button>
        </div>

        <el-table :data="students" style="width:100%">
          <el-table-column prop="studentNo" label="学号" width="140" />
          <el-table-column prop="name" label="姓名" width="140" />
          <el-table-column label="阶段状态" width="120">
            <template #default="{ row }">
              <StageStatusBadge :status="getStageStatus(row)" />
            </template>
          </el-table-column>
          <el-table-column label="AI分/教师分">
            <template #default="{ row }">
              <ScoreDisplay :aiScore="row.aiScore" :teacherScore="row.teacherScore" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220">
            <template #default="{ row }">
              <el-button v-if="getStageStatus(row)===0" size="small" type="primary" @click="triggerEval(row.studentNo)">触发评审</el-button>
              <el-button v-else-if="getStageStatus(row)===1" size="small" disabled>评审中</el-button>
              <el-button v-else-if="getStageStatus(row)===2" size="small" type="warning" @click="goReview(row.studentNo)">评阅</el-button>
              <el-tag v-else type="success">已下发</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import StageStatusBadge from '@/components/StageStatusBadge.vue'
import ScoreDisplay from '@/components/ScoreDisplay.vue'
import http from '@/api/request'
import { userApi } from '@/api/user'
import { evalApi } from '@/api/eval'
import { useEvalStore } from '@/stores/eval'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const courses = ref([])
const selectedCourseId = ref('')
const students = ref([])
const selectedStage = ref('1')
const evalStore = useEvalStore()
const router = useRouter()

onMounted(async () => {
  try {
    const res = await http.get('/api/course')
    courses.value = res || []
    if (courses.value.length) selectedCourseId.value = courses.value[0].courseId
    await loadStudents()
  } catch (e) {
    // ignore
  }
})

async function loadStudents() {
  if (!selectedCourseId.value) return
  try {
    students.value = await userApi.getCourseStudents(selectedCourseId.value)
  } catch (e) {
    students.value = []
  }
}

function selectCourse(id) { selectedCourseId.value = id; loadStudents() }

function getStageStatus(row) {
  const stage = row.stages?.find(s => String(s.stageNum) === String(selectedStage.value))
  return stage?.status ?? 0
}

async function triggerEval(studentNo) {
  try {
    const res = await evalApi.triggerEval({ courseId: selectedCourseId.value, studentNo, stageNum: Number(selectedStage.value) })
    (res.taskIds || []).forEach(id => evalStore.startPolling(id))
    ElMessage.success('已触发评审')
    await loadStudents()
  } catch (e) { ElMessage.error('触发失败') }
}

async function triggerBatch() {
  try {
    const res = await evalApi.triggerEval({ courseId: selectedCourseId.value, stageNum: Number(selectedStage.value) })
    (res.taskIds || []).forEach(id => evalStore.startPolling(id))
    ElMessage.success('批量触发已提交')
    await loadStudents()
  } catch (e) { ElMessage.error('批量触发失败') }
}

function goReview(studentNo) { router.push(`/teacher/review/${studentNo}/${selectedStage.value}`) }

async function refresh() { await loadStudents() }
</script>

<style scoped>
.workbench { padding:12px }
.stage-card { margin-bottom:12px }
</style>
