<template>
  <div class="page-container">
    <!-- Filters -->
    <el-card class="filter-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="产线">
          <el-select v-model="queryParams.line" placeholder="全部产线" clearable style="width: 140px">
            <el-option v-for="l in lineOptions" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
        <el-form-item label="材质">
          <el-select v-model="queryParams.grade" placeholder="全部材质" clearable style="width: 110px">
            <el-option v-for="g in gradeOptions" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item label="合同号">
          <el-input v-model="queryParams.contractNo" placeholder="合同号" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="queryParams.dateRange" type="daterange" range-separator="至" start-placeholder="开始" end-placeholder="结束" style="width: 260px" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="handleReset"><el-icon><Refresh /></el-icon>重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Table -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">排产列表</span>
          <div class="header-actions">
            <el-button size="small" @click="handleExport"><el-icon><Download /></el-icon>导出</el-button>
          </div>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" stripe style="border-radius: 8px">
        <el-table-column prop="scheduleNo" label="排产号" min-width="130" sortable />
        <el-table-column prop="materialSpec" label="物料/规格" min-width="200" show-overflow-tooltip />
        <el-table-column prop="grade" label="材质" width="90">
          <template #default="{ row }"><span class="grade-cell">{{ row.grade }}</span></template>
        </el-table-column>
        <el-table-column prop="origin" label="产地" width="80" />
        <el-table-column prop="length" label="长度(mm)" width="100" align="right" />
        <el-table-column prop="weight" label="重量(T)" width="90" align="right" />
        <el-table-column prop="contractNo" label="合同号" width="130" />
        <el-table-column prop="line" label="产线" width="110" />
        <el-table-column prop="startDate" label="计划开始" width="140" sortable />
        <el-table-column prop="endDate" label="计划结束" width="140" />
        <el-table-column prop="mold" label="模具" width="100" />
        <el-table-column prop="flow" label="流向" width="120" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-tag :type="statusTypeMap[row.status] || 'info'" size="small" effect="light" round>{{ statusLabelMap[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="handleView(row)">详情</el-button>
            <el-button v-if="row.status === 'DRAFT'" text type="success" size="small" @click="handleConfirm(row)">确认</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next, jumper" background @size-change="fetchData" @current-change="fetchData" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Download } from '@element-plus/icons-vue'
import { getScheduleList } from '@/api/schedule'

interface ScheduleItem {
  id: string; scheduleNo: string; materialSpec: string; grade: string; origin: string
  length: number; weight: number; contractNo: string; line: string
  startDate: string; endDate: string; mold: string; flow: string; status: string
}

const lineOptions = ['纵剪线 #1', '纵剪线 #2', '横剪线 #1', '制管线 #1', '制管线 #2', '矫平线 #1']
const gradeOptions = ['Q235B', 'Q345B', 'SPHC', 'SPCC', 'DX51D', 'SS400']
const statusOptions = [
  { value: 'DRAFT', label: '草稿' }, { value: 'CONFIRMED', label: '已确认' },
  { value: 'RELEASED', label: '已下达' }, { value: 'IN_PROGRESS', label: '进行中' },
  { value: 'COMPLETED', label: '已完成' }, { value: 'CANCELLED', label: '已取消' },
]

const statusTypeMap: Record<string, string> = {
  DRAFT: 'info', CONFIRMED: '', RELEASED: 'warning', IN_PROGRESS: 'success', COMPLETED: 'success', CANCELLED: 'info',
}
const statusLabelMap: Record<string, string> = {
  DRAFT: '草稿', CONFIRMED: '已确认', RELEASED: '已下达', IN_PROGRESS: '进行中', COMPLETED: '已完成', CANCELLED: '已取消',
}

const loading = ref(false)
const queryParams = reactive({ line: '', grade: '', contractNo: '', status: '', dateRange: null as string[] | null })
const pagination = reactive({ page: 1, pageSize: 50, total: 0 })

const tableData = ref<ScheduleItem[]>([
  { id: '1', scheduleNo: 'SC2026-03001', materialSpec: '带钢 Q345B 4.0×305×C', grade: 'Q345B', origin: '宝钢', length: 6000, weight: 24, contractNo: 'HT2026-0318', line: '纵剪线 #1', startDate: '2026-03-20 08:00', endDate: '2026-03-20 16:00', mold: 'M-305-01', flow: '纵剪→制管', status: 'CONFIRMED' },
  { id: '2', scheduleNo: 'SC2026-03002', materialSpec: '带钢 Q345B 4.0×256×C', grade: 'Q345B', origin: '宝钢', length: 6000, weight: 18, contractNo: 'HT2026-0318', line: '纵剪线 #1', startDate: '2026-03-20 16:00', endDate: '2026-03-21 04:00', mold: 'M-256-02', flow: '纵剪→制管', status: 'CONFIRMED' },
  { id: '3', scheduleNo: 'SC2026-03003', materialSpec: '带钢 SPHC 3.0×1500×C', grade: 'SPHC', origin: '日钢', length: 0, weight: 45, contractNo: '—', line: '纵剪线 #2', startDate: '2026-03-20 08:00', endDate: '2026-03-21 08:00', mold: 'M-1500-01', flow: '纵剪→入库', status: 'RELEASED' },
  { id: '4', scheduleNo: 'SC2026-03004', materialSpec: '焊管 Q345B 4.0×Φ89×6000', grade: 'Q345B', origin: '鞍钢', length: 6000, weight: 36, contractNo: 'HT2026-0318', line: '制管线 #1', startDate: '2026-03-21 08:00', endDate: '2026-03-22 16:00', mold: 'Φ89-01', flow: '制管→入库', status: 'DRAFT' },
  { id: '5', scheduleNo: 'SC2026-03005', materialSpec: '钢板 SS400 6.0×1800×8000', grade: 'SS400', origin: '河钢', length: 8000, weight: 68, contractNo: 'HT2026-0320', line: '横剪线 #1', startDate: '2026-03-20 08:00', endDate: '2026-03-20 20:00', mold: '—', flow: '横剪→入库', status: 'IN_PROGRESS' },
  { id: '6', scheduleNo: 'SC2026-03006', materialSpec: '冷轧板 DC01 1.2×1250×2500', grade: 'DC01', origin: '宝钢', length: 2500, weight: 12, contractNo: 'HT2026-0312', line: '矫平线 #1', startDate: '2026-03-19 08:00', endDate: '2026-03-19 16:00', mold: '—', flow: '矫平→入库', status: 'COMPLETED' },
])

function handleSearch() { pagination.page = 1; fetchData() }
function handleReset() { Object.assign(queryParams, { line: '', grade: '', contractNo: '', status: '', dateRange: null }); handleSearch() }
function handleView(_row: ScheduleItem) { ElMessage.info('详情功能待实现') }
function handleConfirm(_row: ScheduleItem) { ElMessage.success('已确认排产单（演示）') }
function handleExport() { ElMessage.info('导出功能待实现') }

async function fetchData() {
  loading.value = true
  try {
    const res = await getScheduleList({ ...queryParams, page: pagination.page, pageSize: pagination.pageSize })
    const data = res as Record<string, unknown>
    tableData.value = (data.records || []) as ScheduleItem[]
    pagination.total = (data.total || 0) as number
  } catch { pagination.total = tableData.value.length } finally { loading.value = false }
}

onMounted(() => { fetchData() })
</script>

<style scoped lang="scss">
.page-container { max-width: 1600px; }
.filter-card { margin-bottom: 16px; border-radius: 12px; :deep(.el-card__body) { padding-bottom: 2px; } }
.table-card { border-radius: 12px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.card-title { font-size: 15px; font-weight: 600; color: var(--text-primary); }
.header-actions { display: flex; gap: 8px; }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
.grade-cell { font-weight: 600; color: #2563eb; }
</style>
