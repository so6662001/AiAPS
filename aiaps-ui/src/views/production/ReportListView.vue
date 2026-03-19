<template>
  <div class="page-container">
    <!-- Filters -->
    <el-card class="filter-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="排产号">
          <el-input v-model="queryParams.scheduleNo" placeholder="排产号" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="产线">
          <el-select v-model="queryParams.line" placeholder="全部产线" clearable style="width: 140px">
            <el-option v-for="l in lineOptions" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
        <el-form-item label="班次">
          <el-select v-model="queryParams.shift" placeholder="全部班次" clearable style="width: 120px">
            <el-option label="早班" value="morning" />
            <el-option label="中班" value="afternoon" />
            <el-option label="晚班" value="night" />
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

    <!-- Summary Stats -->
    <el-row :gutter="16" class="summary-row">
      <el-col :span="6">
        <el-card class="summary-card" shadow="never">
          <el-statistic title="今日产量(T)" :value="summaryStats.todayOutput" :precision="1" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="summary-card" shadow="never">
          <el-statistic title="今日报工数" :value="summaryStats.todayCount" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="summary-card" shadow="never">
          <el-statistic title="平均成材率(%)" :value="summaryStats.avgYield" :precision="1" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="summary-card" shadow="never">
          <el-statistic title="计划完成率(%)" :value="summaryStats.planRate" :precision="1" />
        </el-card>
      </el-col>
    </el-row>

    <!-- Table -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">报工记录</span>
          <div class="header-actions">
            <el-button type="primary" size="small" @click="handleCreate"><el-icon><Plus /></el-icon>新增报工</el-button>
            <el-button size="small" @click="handleExport"><el-icon><Download /></el-icon>导出</el-button>
          </div>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" stripe style="border-radius: 8px">
        <el-table-column prop="reportNo" label="报工单号" min-width="130" />
        <el-table-column prop="scheduleNo" label="排产号" width="130" />
        <el-table-column prop="line" label="产线" width="110" />
        <el-table-column prop="materialSpec" label="物料/规格" min-width="200" show-overflow-tooltip />
        <el-table-column prop="planQty" label="计划量(T)" width="100" align="right" />
        <el-table-column prop="actualQty" label="实际量(T)" width="100" align="right">
          <template #default="{ row }"><span class="weight-cell">{{ row.actualQty }}</span></template>
        </el-table-column>
        <el-table-column prop="scrapQty" label="废品量(T)" width="100" align="right" />
        <el-table-column label="成材率" width="110">
          <template #default="{ row }">
            <span :class="yieldClass(row.yieldRate)">{{ row.yieldRate }}%</span>
          </template>
        </el-table-column>
        <el-table-column prop="shift" label="班次" width="70" align="center">
          <template #default="{ row }">{{ shiftLabel(row.shift) }}</template>
        </el-table-column>
        <el-table-column prop="operator" label="操作员" width="90" />
        <el-table-column prop="reportTime" label="报工时间" width="150" />
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
import { Search, Refresh, Plus, Download } from '@element-plus/icons-vue'
import { getReportList } from '@/api/production'

interface ReportItem {
  id: string; reportNo: string; scheduleNo: string; line: string
  materialSpec: string; planQty: number; actualQty: number; scrapQty: number
  yieldRate: number; shift: string; operator: string; reportTime: string
}

const lineOptions = ['纵剪线 #1', '纵剪线 #2', '横剪线 #1', '制管线 #1', '矫平线 #1']

const summaryStats = reactive({
  todayOutput: 486.5, todayCount: 18, avgYield: 97.2, planRate: 94.5,
})

const loading = ref(false)
const queryParams = reactive({ scheduleNo: '', line: '', shift: '', dateRange: null as string[] | null })
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })

const tableData = ref<ReportItem[]>([
  { id: '1', reportNo: 'RP2026-031901', scheduleNo: 'SC2026-03001', line: '纵剪线 #1', materialSpec: '带钢 Q345B 4.0×305×C', planQty: 24, actualQty: 23.8, scrapQty: 0.5, yieldRate: 97.9, shift: 'morning', operator: '张三', reportTime: '2026-03-19 12:00' },
  { id: '2', reportNo: 'RP2026-031902', scheduleNo: 'SC2026-03002', line: '纵剪线 #1', materialSpec: '带钢 Q345B 4.0×256×C', planQty: 18, actualQty: 17.6, scrapQty: 0.3, yieldRate: 98.3, shift: 'morning', operator: '张三', reportTime: '2026-03-19 12:00' },
  { id: '3', reportNo: 'RP2026-031903', scheduleNo: 'SC2026-03003', line: '纵剪线 #2', materialSpec: '带钢 SPHC 3.0×1500×C', planQty: 45, actualQty: 44.2, scrapQty: 1.0, yieldRate: 97.8, shift: 'morning', operator: '李四', reportTime: '2026-03-19 14:00' },
  { id: '4', reportNo: 'RP2026-031904', scheduleNo: 'SC2026-03005', line: '横剪线 #1', materialSpec: '钢板 SS400 6.0×1800×8000', planQty: 68, actualQty: 65.5, scrapQty: 2.5, yieldRate: 96.3, shift: 'afternoon', operator: '王五', reportTime: '2026-03-19 16:30' },
  { id: '5', reportNo: 'RP2026-031905', scheduleNo: 'SC2026-03006', line: '矫平线 #1', materialSpec: '冷轧板 DC01 1.2×1250×2500', planQty: 12, actualQty: 11.8, scrapQty: 0.1, yieldRate: 99.2, shift: 'morning', operator: '赵六', reportTime: '2026-03-19 11:30' },
])

function shiftLabel(shift: string) {
  const map: Record<string, string> = { morning: '早', afternoon: '中', night: '晚' }
  return map[shift] || shift
}

function yieldClass(rate: number) {
  if (rate >= 98) return 'yield-high'
  if (rate >= 95) return 'yield-mid'
  return 'yield-low'
}

function handleSearch() { pagination.page = 1; fetchData() }
function handleReset() { Object.assign(queryParams, { scheduleNo: '', line: '', shift: '', dateRange: null }); handleSearch() }
function handleCreate() { ElMessage.info('新增报工功能待实现') }
function handleExport() { ElMessage.info('导出功能待实现') }

async function fetchData() {
  loading.value = true
  try {
    const res = await getReportList({ ...queryParams, page: pagination.page, pageSize: pagination.pageSize })
    const data = res as Record<string, unknown>
    tableData.value = (data.records || []) as ReportItem[]
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

.summary-row { margin-bottom: 16px; }
.summary-card {
  border-radius: 12px;
  text-align: center;
  :deep(.el-statistic__head) { font-size: 13px; color: var(--text-secondary); }
  :deep(.el-statistic__content) { font-size: 24px; font-weight: 700; }
}

.weight-cell { font-weight: 600; }
.yield-high { color: #10b981; font-weight: 600; }
.yield-mid { color: #2563eb; font-weight: 600; }
.yield-low { color: #ef4444; font-weight: 600; }
</style>
