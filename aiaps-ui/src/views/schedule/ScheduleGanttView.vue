<template>
  <div class="page-container gantt-page">
    <!-- Toolbar -->
    <el-card class="toolbar-card" shadow="never">
      <div class="toolbar-row">
        <div class="toolbar-left">
          <el-radio-group v-model="viewMode" size="small">
            <el-radio-button value="line">产线</el-radio-button>
            <el-radio-button value="contract">合同</el-radio-button>
            <el-radio-button value="material">物料</el-radio-button>
          </el-radio-group>
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" size="small" style="width: 260px" value-format="YYYY-MM-DD" />
          <el-select v-model="filterLine" placeholder="产线" clearable size="small" style="width: 130px">
            <el-option v-for="l in lineOptions" :key="l" :label="l" :value="l" />
          </el-select>
          <el-select v-model="filterGrade" placeholder="材质" clearable size="small" style="width: 110px">
            <el-option v-for="g in gradeOptions" :key="g" :label="g" :value="g" />
          </el-select>
          <el-input v-model="filterContract" placeholder="合同号" clearable size="small" style="width: 140px" />
        </div>
        <div class="toolbar-right">
          <el-button type="primary" size="small" @click="handleAutoSchedule">
            <el-icon><MagicStick /></el-icon>自动排产
          </el-button>
          <el-button size="small" @click="handleInsert">
            <el-icon><Plus /></el-icon>插单
          </el-button>
          <el-button size="small" @click="handleLock" :disabled="!selectedTask">
            <el-icon><Lock /></el-icon>锁定
          </el-button>
          <el-button size="small" type="warning" @click="handleSimulate">
            <el-icon><VideoCamera /></el-icon>模拟推演
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- Gantt Container -->
    <el-card class="gantt-card" shadow="never">
      <!-- DHTMLX Gantt integration point -->
      <div id="gantt-container" :style="{ height: ganttHeight + 'px' }" class="gantt-container">
        <div class="gantt-placeholder">
          <el-icon :size="64" color="#cbd5e1"><Calendar /></el-icon>
          <h3>甘特图渲染区域</h3>
          <p>此处集成 DHTMLX Gantt 库进行排产甘特图渲染</p>
          <div class="gantt-demo-bars">
            <div v-for="(task, idx) in demoTasks" :key="idx" class="demo-bar" :style="{ left: task.left + '%', width: task.width + '%', backgroundColor: task.color }">
              <span class="demo-bar-text">{{ task.name }}</span>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <!-- Selected Task Detail Panel -->
    <el-card v-if="selectedTask" class="detail-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">排产任务详情</span>
          <el-button text type="primary" size="small" @click="selectedTask = null">关闭</el-button>
        </div>
      </template>
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="排产号">{{ selectedTask.scheduleNo }}</el-descriptions-item>
        <el-descriptions-item label="物料规格">{{ selectedTask.materialSpec }}</el-descriptions-item>
        <el-descriptions-item label="材质">
          <span class="grade-cell">{{ selectedTask.grade }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="产地">{{ selectedTask.origin }}</el-descriptions-item>
        <el-descriptions-item label="重量(T)">{{ selectedTask.weight }}</el-descriptions-item>
        <el-descriptions-item label="合同号">{{ selectedTask.contractNo }}</el-descriptions-item>
        <el-descriptions-item label="模具">{{ selectedTask.mold }}</el-descriptions-item>
        <el-descriptions-item label="流向">{{ selectedTask.flow }}</el-descriptions-item>
        <el-descriptions-item label="产线">{{ selectedTask.line }}</el-descriptions-item>
        <el-descriptions-item label="计划开始">{{ selectedTask.startDate }}</el-descriptions-item>
        <el-descriptions-item label="计划结束">{{ selectedTask.endDate }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="taskStatusType(selectedTask.status)" size="small" effect="light" round>{{ taskStatusLabel(selectedTask.status) }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Calendar, MagicStick, Plus, Lock, VideoCamera } from '@element-plus/icons-vue'
import { autoSchedule, insertOrder, lockSchedule, simulateSchedule, getScheduleGantt } from '@/api/schedule'

interface TaskDetail {
  scheduleNo: string; materialSpec: string; grade: string; origin: string
  weight: number; contractNo: string; mold: string; flow: string
  line: string; startDate: string; endDate: string; status: string
}

const viewMode = ref('line')
const dateRange = ref<string[] | null>(null)
const filterLine = ref('')
const filterGrade = ref('')
const filterContract = ref('')

const lineOptions = ['纵剪线 #1', '纵剪线 #2', '横剪线 #1', '制管线 #1', '制管线 #2', '矫平线 #1']
const gradeOptions = ['Q235B', 'Q345B', 'SPHC', 'SPCC', 'DX51D', 'SS400']

const ganttHeight = computed(() => Math.max(window.innerHeight - 320, 400))

const selectedTask = ref<TaskDetail | null>({
  scheduleNo: 'SC2026-03001', materialSpec: '带钢 Q345B 4.0×305×C', grade: 'Q345B', origin: '宝钢',
  weight: 24, contractNo: 'HT2026-0318', mold: 'M-305-01', flow: '纵剪→制管',
  line: '纵剪线 #1', startDate: '2026-03-20 08:00', endDate: '2026-03-20 16:00', status: 'CONFIRMED',
})

const demoTasks = ref([
  { name: 'Q345B 4.0×305 分剪', left: 5, width: 18, color: '#2563eb' },
  { name: 'Q345B 4.0×256 分剪', left: 25, width: 15, color: '#3b82f6' },
  { name: 'SPHC 3.0×1500 分剪', left: 8, width: 22, color: '#10b981' },
  { name: 'Q345B Φ89 制管', left: 35, width: 25, color: '#f59e0b' },
  { name: 'SS400 6.0×1800 横切', left: 12, width: 20, color: '#6366f1' },
  { name: 'DC01 1.2×1250 矫平', left: 42, width: 18, color: '#8b5cf6' },
])

function taskStatusType(status: string) {
  const map: Record<string, string> = { DRAFT: 'info', CONFIRMED: '', RELEASED: 'warning', IN_PROGRESS: 'success', COMPLETED: 'success', CANCELLED: 'info' }
  return map[status] || 'info'
}

function taskStatusLabel(status: string) {
  const map: Record<string, string> = { DRAFT: '草稿', CONFIRMED: '已确认', RELEASED: '已下达', IN_PROGRESS: '进行中', COMPLETED: '已完成', CANCELLED: '已取消' }
  return map[status] || status
}

async function handleAutoSchedule() {
  try {
    await autoSchedule({ viewMode: viewMode.value, dateRange: dateRange.value })
    ElMessage.success('自动排产完成')
  } catch { ElMessage.success('自动排产完成（演示）') }
}

async function handleInsert() {
  try {
    await insertOrder({})
    ElMessage.info('插单功能待实现')
  } catch { ElMessage.info('插单功能待实现') }
}

async function handleLock() {
  if (!selectedTask.value) return
  try {
    await lockSchedule([selectedTask.value.scheduleNo])
    ElMessage.success('已锁定排产任务')
  } catch { ElMessage.success('已锁定（演示）') }
}

async function handleSimulate() {
  try {
    await simulateSchedule({})
    ElMessage.info('模拟推演功能待实现')
  } catch { ElMessage.info('模拟推演功能待实现') }
}

onMounted(async () => {
  try {
    await getScheduleGantt({ viewMode: viewMode.value })
  } catch {
    /* use demo data */
  }
})
</script>

<style scoped lang="scss">
.gantt-page {
  max-width: 100%;
}

.toolbar-card {
  margin-bottom: 16px;
  border-radius: 12px;
}

.toolbar-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.toolbar-left, .toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.gantt-card {
  margin-bottom: 16px;
  border-radius: 12px;

  :deep(.el-card__body) {
    padding: 0;
  }
}

.gantt-container {
  background: #fafbfc;
  border-radius: 0 0 12px 12px;
  overflow: hidden;
}

.gantt-placeholder {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  position: relative;

  h3 {
    margin-top: 16px;
    font-size: 18px;
    color: #64748b;
  }

  p {
    margin-top: 8px;
    font-size: 13px;
  }
}

.gantt-demo-bars {
  position: absolute;
  bottom: 20%;
  left: 0;
  right: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 0 60px;
}

.demo-bar {
  position: relative;
  height: 28px;
  border-radius: 4px;
  opacity: 0.3;
  display: flex;
  align-items: center;
  padding: 0 8px;
}

.demo-bar-text {
  font-size: 11px;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
}

.detail-card {
  border-radius: 12px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.grade-cell {
  font-weight: 600;
  color: #2563eb;
}
</style>
