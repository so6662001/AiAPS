<template>
  <div class="page-container">
    <!-- Filters -->
    <el-card class="filter-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="方案编号">
          <el-input v-model="queryParams.nestingNo" placeholder="方案编号" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="原料规格">
          <el-input v-model="queryParams.sourceSpec" placeholder="原料规格" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="待审核" value="PENDING" />
            <el-option label="已审核" value="APPROVED" />
            <el-option label="已执行" value="EXECUTED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="handleReset"><el-icon><Refresh /></el-icon>重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Master Table -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">套裁/分剪方案</span>
          <el-button type="primary" size="small" @click="handleCreate"><el-icon><Plus /></el-icon>新建方案</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" stripe highlight-current-row style="border-radius: 8px" @row-click="handleRowClick">
        <el-table-column prop="nestingNo" label="方案编号" min-width="140" />
        <el-table-column prop="sourceSpec" label="原料规格" min-width="200" show-overflow-tooltip />
        <el-table-column prop="sourceGrade" label="材质" width="90">
          <template #default="{ row }"><span class="grade-cell">{{ row.sourceGrade }}</span></template>
        </el-table-column>
        <el-table-column prop="sourceWeight" label="原料重量(T)" width="120" align="right" />
        <el-table-column prop="stripCount" label="条带数" width="80" align="center" />
        <el-table-column prop="utilization" label="利用率" width="120">
          <template #default="{ row }">
            <el-progress :percentage="row.utilization" :stroke-width="8" :color="utilizationColor(row.utilization)" :show-text="true" :text-inside="false" />
          </template>
        </el-table-column>
        <el-table-column prop="scrapRate" label="废料率(%)" width="100" align="right" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="nestingStatusType(row.status)" size="small" effect="light" round>{{ nestingStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="150" />
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button text type="danger" size="small" @click.stop="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[20, 50]" layout="total, sizes, prev, pager, next" background @size-change="fetchData" @current-change="fetchData" />
      </div>
    </el-card>

    <!-- Detail Drawer -->
    <el-drawer v-model="drawerVisible" title="套裁明细" size="50%">
      <template v-if="selectedPlan">
        <el-descriptions :column="2" border size="small" class="detail-desc">
          <el-descriptions-item label="方案编号">{{ selectedPlan.nestingNo }}</el-descriptions-item>
          <el-descriptions-item label="原料规格">{{ selectedPlan.sourceSpec }}</el-descriptions-item>
          <el-descriptions-item label="材质">{{ selectedPlan.sourceGrade }}</el-descriptions-item>
          <el-descriptions-item label="原料重量(T)">{{ selectedPlan.sourceWeight }}</el-descriptions-item>
          <el-descriptions-item label="利用率">{{ selectedPlan.utilization }}%</el-descriptions-item>
          <el-descriptions-item label="废料率">{{ selectedPlan.scrapRate }}%</el-descriptions-item>
        </el-descriptions>
        <h4 class="detail-section-title">产出条带</h4>
        <el-table :data="detailLines" border size="small" style="border-radius: 6px">
          <el-table-column prop="seq" label="序号" width="60" align="center" />
          <el-table-column prop="stripWidth" label="宽度(mm)" width="100" align="right" />
          <el-table-column prop="stripWeight" label="重量(T)" width="100" align="right" />
          <el-table-column prop="contractNo" label="合同号" min-width="130" />
          <el-table-column prop="customer" label="客户" min-width="120" />
          <el-table-column prop="targetSpec" label="目标规格" min-width="160" show-overflow-tooltip />
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { getNestingList, getNestingDetail, deleteNesting } from '@/api/nesting'

interface NestingDetail {
  seq: number; stripWidth: number; stripWeight: number
  contractNo: string; customer: string; targetSpec: string
}

interface NestingItem {
  id: string; nestingNo: string; sourceSpec: string; sourceGrade: string
  sourceWeight: number; stripCount: number; utilization: number
  scrapRate: number; status: string; createTime: string
}

const loading = ref(false)
const drawerVisible = ref(false)
const selectedPlan = ref<NestingItem | null>(null)
const detailLines = ref<NestingDetail[]>([])
const queryParams = reactive({ nestingNo: '', sourceSpec: '', status: '' })
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })

const tableData = ref<NestingItem[]>([
  { id: '1', nestingNo: 'NS2026-001', sourceSpec: '热轧卷板 Q345B 4.0×1250×C', sourceGrade: 'Q345B', sourceWeight: 22.5, stripCount: 4, utilization: 97.6, scrapRate: 2.4, status: 'APPROVED', createTime: '2026-03-18 14:00' },
  { id: '2', nestingNo: 'NS2026-002', sourceSpec: '热轧卷板 Q235B 3.0×1500×C', sourceGrade: 'Q235B', sourceWeight: 18.0, stripCount: 5, utilization: 95.3, scrapRate: 4.7, status: 'EXECUTED', createTime: '2026-03-17 09:30' },
  { id: '3', nestingNo: 'NS2026-003', sourceSpec: '热轧卷板 SPHC 3.0×1250×C', sourceGrade: 'SPHC', sourceWeight: 20.0, stripCount: 3, utilization: 98.4, scrapRate: 1.6, status: 'PENDING', createTime: '2026-03-19 10:00' },
  { id: '4', nestingNo: 'NS2026-004', sourceSpec: '冷轧卷板 SPCC 1.2×1250×C', sourceGrade: 'SPCC', sourceWeight: 12.0, stripCount: 6, utilization: 94.8, scrapRate: 5.2, status: 'PENDING', createTime: '2026-03-19 11:30' },
])

const mockDetails: Record<string, NestingDetail[]> = {
  '1': [
    { seq: 1, stripWidth: 305, stripWeight: 5.5, contractNo: 'HT2026-0318', customer: '中建钢构', targetSpec: '带钢 Q345B 4.0×305×C' },
    { seq: 2, stripWidth: 256, stripWeight: 4.6, contractNo: 'HT2026-0318', customer: '中建钢构', targetSpec: '带钢 Q345B 4.0×256×C' },
    { seq: 3, stripWidth: 305, stripWeight: 5.5, contractNo: 'HT2026-0320', customer: '万科集团', targetSpec: '带钢 Q345B 4.0×305×C' },
    { seq: 4, stripWidth: 354, stripWeight: 6.3, contractNo: 'HT2026-0322', customer: '碧桂园', targetSpec: '带钢 Q345B 4.0×354×C' },
  ],
  '2': [
    { seq: 1, stripWidth: 205, stripWeight: 3.2, contractNo: 'HT2026-0325', customer: '远大住工', targetSpec: '带钢 Q235B 3.0×205×C' },
    { seq: 2, stripWidth: 205, stripWeight: 3.2, contractNo: 'HT2026-0325', customer: '远大住工', targetSpec: '带钢 Q235B 3.0×205×C' },
    { seq: 3, stripWidth: 305, stripWeight: 4.8, contractNo: 'HT2026-0328', customer: '恒大集团', targetSpec: '带钢 Q235B 3.0×305×C' },
    { seq: 4, stripWidth: 380, stripWeight: 3.4, contractNo: '—', customer: '—', targetSpec: '带钢 Q235B 3.0×380×C' },
    { seq: 5, stripWidth: 380, stripWeight: 3.4, contractNo: '—', customer: '—', targetSpec: '带钢 Q235B 3.0×380×C' },
  ],
}

function nestingStatusType(status: string) {
  const map: Record<string, string> = { PENDING: 'info', APPROVED: '', EXECUTED: 'success' }
  return map[status] || 'info'
}

function nestingStatusLabel(status: string) {
  const map: Record<string, string> = { PENDING: '待审核', APPROVED: '已审核', EXECUTED: '已执行' }
  return map[status] || status
}

function utilizationColor(rate: number) {
  if (rate >= 97) return '#10b981'
  if (rate >= 94) return '#2563eb'
  return '#f59e0b'
}

async function handleRowClick(row: NestingItem) {
  selectedPlan.value = row
  drawerVisible.value = true
  try {
    const res = await getNestingDetail(row.id)
    detailLines.value = res as NestingDetail[]
  } catch {
    detailLines.value = mockDetails[row.id] || []
  }
}

function handleSearch() { pagination.page = 1; fetchData() }
function handleReset() { Object.assign(queryParams, { nestingNo: '', sourceSpec: '', status: '' }); handleSearch() }
function handleCreate() { ElMessage.info('新建套裁方案功能待实现') }

async function handleDelete(row: NestingItem) {
  await ElMessageBox.confirm(`确认删除方案 "${row.nestingNo}" 吗？`, '提示', { type: 'warning' })
  try { await deleteNesting(row.id); ElMessage.success('删除成功'); fetchData() } catch { ElMessage.error('删除失败') }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getNestingList({ ...queryParams, page: pagination.page, pageSize: pagination.pageSize })
    const data = res as Record<string, unknown>
    tableData.value = (data.records || []) as NestingItem[]
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
.grade-cell { font-weight: 600; color: #2563eb; }
.detail-desc { margin-bottom: 20px; }
.detail-section-title { font-size: 14px; font-weight: 600; color: var(--text-primary); margin-bottom: 12px; }
</style>
