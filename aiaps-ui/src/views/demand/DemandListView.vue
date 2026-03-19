<template>
  <div class="page-container">
    <!-- Filter -->
    <el-card class="filter-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="需求来源">
          <el-select v-model="queryParams.source" placeholder="全部来源" clearable style="width: 130px">
            <el-option label="MTO(订单)" value="MTO" />
            <el-option label="MTS(备库)" value="MTS" />
            <el-option label="SSK(安全库存)" value="SSK" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户">
          <el-input v-model="queryParams.customer" placeholder="客户名称" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="合同号">
          <el-input v-model="queryParams.contractNo" placeholder="合同号" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 120px">
            <el-option label="待确认" value="PENDING" />
            <el-option label="已确认" value="CONFIRMED" />
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="交期">
          <el-date-picker v-model="queryParams.dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" style="width: 260px" value-format="YYYY-MM-DD" />
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
          <span class="card-title">需求列表</span>
          <el-button type="primary" size="small" @click="handleAdd"><el-icon><Plus /></el-icon>新增需求</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" stripe row-key="id" style="border-radius: 8px" @expand-change="handleExpand">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-content">
              <h4 class="expand-title">需求明细 — {{ row.demandNo }}</h4>
              <el-table :data="row.lines || []" border size="small" style="border-radius: 6px">
                <el-table-column prop="materialSpec" label="物料/规格" min-width="180" />
                <el-table-column prop="PATName" label="材质" width="100" />
                <el-table-column prop="PAName" label="产地" width="100" />
                <el-table-column prop="length" label="长度(mm)" width="100" align="right" />
                <el-table-column prop="qty" label="数量" width="80" align="right" />
                <el-table-column prop="weight" label="重量(T)" width="100" align="right" />
                <el-table-column label="进度" width="180">
                  <template #default="{ row: line }">
                    <el-progress :percentage="line.progress" :stroke-width="8" :color="progressColor(line.progress)" />
                  </template>
                </el-table-column>
                <el-table-column prop="contractNo" label="合同号" width="140" />
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="demandNo" label="需求单号" min-width="140" />
        <el-table-column label="来源" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="sourceTagType(row.source)" size="small" effect="light">{{ row.source }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="customer" label="客户" min-width="140" show-overflow-tooltip />
        <el-table-column prop="contractNo" label="合同号" min-width="140" />
        <el-table-column prop="dueDate" label="交期" width="120" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="demandStatusType(row.status)" size="small" effect="light" round>{{ demandStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalWeight" label="总重(T)" width="100" align="right" />
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button text type="danger" size="small" @click="handleDelete(row)">删除</el-button>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { getDemandList, getDemandLines, deleteDemand } from '@/api/demand'

interface DemandLine {
  materialSpec: string; PATName: string; PAName: string
  length: number; qty: number; weight: number; progress: number; contractNo: string
}

interface DemandItem {
  id: string; demandNo: string; source: string; customer: string
  contractNo: string; dueDate: string; status: string; totalWeight: number
  lines?: DemandLine[]
}

const loading = ref(false)
const queryParams = reactive({ source: '', customer: '', contractNo: '', status: '', dateRange: null as string[] | null })
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })

const tableData = ref<DemandItem[]>([
  {
    id: '1', demandNo: 'DM2026-0301', source: 'MTO', customer: '中建钢构华南公司', contractNo: 'HT2026-0318', dueDate: '2026-03-22', status: 'IN_PROGRESS', totalWeight: 120,
    lines: [
      { materialSpec: '带钢 Q345B 4.0×305×C', PATName: 'Q345B', PAName: '宝钢', length: 6000, qty: 200, weight: 48, progress: 65, contractNo: 'HT2026-0318' },
      { materialSpec: '带钢 Q345B 4.0×256×C', PATName: 'Q345B', PAName: '宝钢', length: 6000, qty: 150, weight: 36, progress: 40, contractNo: 'HT2026-0318' },
      { materialSpec: '焊管 Q345B 4.0×Φ89×6000', PATName: 'Q345B', PAName: '鞍钢', length: 6000, qty: 100, weight: 36, progress: 20, contractNo: 'HT2026-0318' },
    ],
  },
  {
    id: '2', demandNo: 'DM2026-0302', source: 'MTO', customer: '远大住工集团', contractNo: 'HT2026-0325', dueDate: '2026-04-05', status: 'CONFIRMED', totalWeight: 85,
    lines: [
      { materialSpec: '带钢 Q235B 3.0×205×C', PATName: 'Q235B', PAName: '马钢', length: 6000, qty: 300, weight: 45, progress: 0, contractNo: 'HT2026-0325' },
      { materialSpec: '焊管 Q235B 3.0×Φ60×6000', PATName: 'Q235B', PAName: '马钢', length: 6000, qty: 200, weight: 40, progress: 0, contractNo: 'HT2026-0325' },
    ],
  },
  {
    id: '3', demandNo: 'DM2026-0303', source: 'MTS', customer: '—', contractNo: '—', dueDate: '2026-03-31', status: 'PENDING', totalWeight: 200,
    lines: [
      { materialSpec: '热轧卷板 SPHC 3.0×1500×C', PATName: 'SPHC', PAName: '日钢', length: 0, qty: 1, weight: 100, progress: 0, contractNo: '—' },
      { materialSpec: '热轧卷板 Q235B 2.5×1250×C', PATName: 'Q235B', PAName: '沙钢', length: 0, qty: 1, weight: 100, progress: 0, contractNo: '—' },
    ],
  },
  {
    id: '4', demandNo: 'DM2026-0304', source: 'SSK', customer: '—', contractNo: '—', dueDate: '2026-03-28', status: 'CONFIRMED', totalWeight: 50,
    lines: [
      { materialSpec: '带钢 SPHC 2.0×125×C', PATName: 'SPHC', PAName: '首钢', length: 0, qty: 500, weight: 50, progress: 10, contractNo: '—' },
    ],
  },
  {
    id: '5', demandNo: 'DM2026-0305', source: 'MTO', customer: '华润万家建材', contractNo: 'HT2026-0330', dueDate: '2026-04-15', status: 'CONFIRMED', totalWeight: 160,
    lines: [
      { materialSpec: '镀锌板 DX51D 0.8×1000×2000', PATName: 'DX51D', PAName: '宝钢', length: 2000, qty: 500, weight: 80, progress: 0, contractNo: 'HT2026-0330' },
      { materialSpec: '彩涂板 DX51D 0.5×1200×2400', PATName: 'DX51D', PAName: '宝钢', length: 2400, qty: 400, weight: 80, progress: 0, contractNo: 'HT2026-0330' },
    ],
  },
])

function sourceTagType(source: string) {
  const map: Record<string, string> = { MTO: '', MTS: 'success', SSK: 'warning' }
  return map[source] || 'info'
}

function demandStatusType(status: string) {
  const map: Record<string, string> = { PENDING: 'info', CONFIRMED: '', IN_PROGRESS: 'success', COMPLETED: 'success', CANCELLED: 'info' }
  return map[status] || 'info'
}

function demandStatusLabel(status: string) {
  const map: Record<string, string> = { PENDING: '待确认', CONFIRMED: '已确认', IN_PROGRESS: '进行中', COMPLETED: '已完成', CANCELLED: '已取消' }
  return map[status] || status
}

function progressColor(pct: number) {
  if (pct >= 80) return '#10b981'
  if (pct >= 40) return '#2563eb'
  return '#f59e0b'
}

function handleSearch() { pagination.page = 1; fetchData() }
function handleReset() { Object.assign(queryParams, { source: '', customer: '', contractNo: '', status: '', dateRange: null }); handleSearch() }
function handleAdd() { ElMessage.info('新增需求功能待实现') }
function handleEdit(_row: DemandItem) { ElMessage.info('编辑需求功能待实现') }

async function handleDelete(row: DemandItem) {
  await ElMessageBox.confirm(`确认删除 "${row.demandNo}" 吗？`, '提示', { type: 'warning' })
  try { await deleteDemand(row.id); ElMessage.success('删除成功'); fetchData() } catch { ElMessage.error('删除失败') }
}

async function handleExpand(row: DemandItem, expanded: DemandItem[]) {
  if (expanded.includes(row) && !row.lines?.length) {
    try {
      const res = await getDemandLines(row.id)
      row.lines = res as DemandLine[]
    } catch { /* use mock lines */ }
  }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getDemandList({ ...queryParams, page: pagination.page, pageSize: pagination.pageSize })
    const data = res as Record<string, unknown>
    tableData.value = (data.records || []) as DemandItem[]
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
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
.expand-content { padding: 12px 48px 12px 64px; }
.expand-title { font-size: 13px; font-weight: 600; color: var(--text-secondary); margin-bottom: 10px; }
</style>
