<template>
  <div class="page-container">
    <!-- Filters -->
    <el-card class="filter-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="领料单号">
          <el-input v-model="queryParams.issueNo" placeholder="领料单号" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="排产号">
          <el-input v-model="queryParams.scheduleNo" placeholder="排产号" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 130px">
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

    <!-- Status Flow -->
    <el-card class="flow-card" shadow="never">
      <div class="status-flow">
        <div v-for="(step, idx) in statusFlow" :key="step.value" class="flow-step">
          <div class="flow-node" :class="{ active: step.count > 0 }" :style="{ borderColor: step.color }">
            <span class="flow-count" :style="{ color: step.color }">{{ step.count }}</span>
            <span class="flow-label">{{ step.label }}</span>
          </div>
          <div v-if="idx < statusFlow.length - 1" class="flow-arrow">→</div>
        </div>
      </div>
    </el-card>

    <!-- Table -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">领料单列表</span>
          <el-button type="primary" size="small" @click="handleCreate"><el-icon><Plus /></el-icon>新增领料</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" stripe style="border-radius: 8px">
        <el-table-column prop="issueNo" label="领料单号" min-width="140" />
        <el-table-column prop="scheduleNo" label="排产号" width="130" />
        <el-table-column prop="materialSpec" label="物料/规格" min-width="200" show-overflow-tooltip />
        <el-table-column prop="grade" label="材质" width="80" />
        <el-table-column prop="requestWeight" label="申请重量(T)" width="110" align="right" />
        <el-table-column prop="actualWeight" label="实发重量(T)" width="110" align="right" />
        <el-table-column prop="applicant" label="申请人" width="90" />
        <el-table-column prop="requestTime" label="申请时间" width="150" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="issueStatusType(row.status)" size="small" effect="light" round>{{ issueStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'REQUESTED'" text type="primary" size="small" @click="handleApprove(row)">审批</el-button>
            <el-button v-if="row.status === 'APPROVED'" text type="success" size="small" @click="handleIssue(row)">发料</el-button>
            <el-button v-if="row.status === 'ISSUED'" text type="warning" size="small" @click="handleDeliver(row)">签收</el-button>
            <el-button text type="primary" size="small" @click="handleView(row)">详情</el-button>
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
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { getMaterialIssueList, approveMaterialIssue, issueMaterial, deliverMaterial } from '@/api/inventory'

interface IssueItem {
  id: string; issueNo: string; scheduleNo: string; materialSpec: string
  grade: string; requestWeight: number; actualWeight: number
  applicant: string; requestTime: string; status: string
}

const statusOptions = [
  { value: 'REQUESTED', label: '已申请' }, { value: 'APPROVED', label: '已审批' },
  { value: 'ISSUED', label: '已发料' }, { value: 'DELIVERED', label: '已签收' },
]

const statusFlow = reactive([
  { value: 'REQUESTED', label: '已申请', color: '#2563eb', count: 5 },
  { value: 'APPROVED', label: '已审批', color: '#6366f1', count: 3 },
  { value: 'ISSUED', label: '已发料', color: '#f59e0b', count: 2 },
  { value: 'DELIVERED', label: '已签收', color: '#10b981', count: 12 },
])

const loading = ref(false)
const queryParams = reactive({ issueNo: '', scheduleNo: '', status: '', dateRange: null as string[] | null })
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })

const tableData = ref<IssueItem[]>([
  { id: '1', issueNo: 'MI2026-0301', scheduleNo: 'SC2026-03001', materialSpec: '热轧卷板 Q345B 4.0×1250×C', grade: 'Q345B', requestWeight: 22.5, actualWeight: 0, applicant: '张三', requestTime: '2026-03-19 08:30', status: 'REQUESTED' },
  { id: '2', issueNo: 'MI2026-0302', scheduleNo: 'SC2026-03003', materialSpec: '热轧卷板 SPHC 3.0×1500×C', grade: 'SPHC', requestWeight: 18.0, actualWeight: 0, applicant: '李四', requestTime: '2026-03-19 09:00', status: 'APPROVED' },
  { id: '3', issueNo: 'MI2026-0303', scheduleNo: 'SC2026-03005', materialSpec: '热轧卷板 SS400 6.0×1800×C', grade: 'SS400', requestWeight: 25.0, actualWeight: 24.8, applicant: '王五', requestTime: '2026-03-18 14:00', status: 'ISSUED' },
  { id: '4', issueNo: 'MI2026-0298', scheduleNo: 'SC2026-02045', materialSpec: '冷轧卷板 SPCC 1.2×1250×C', grade: 'SPCC', requestWeight: 12.0, actualWeight: 12.0, applicant: '赵六', requestTime: '2026-03-17 10:00', status: 'DELIVERED' },
  { id: '5', issueNo: 'MI2026-0297', scheduleNo: 'SC2026-02044', materialSpec: '热轧卷板 Q235B 3.0×1250×C', grade: 'Q235B', requestWeight: 20.0, actualWeight: 20.0, applicant: '张三', requestTime: '2026-03-17 08:30', status: 'DELIVERED' },
])

function issueStatusType(status: string) {
  const map: Record<string, string> = { REQUESTED: '', APPROVED: 'warning', ISSUED: 'warning', DELIVERED: 'success' }
  return map[status] || 'info'
}

function issueStatusLabel(status: string) {
  const map: Record<string, string> = { REQUESTED: '已申请', APPROVED: '已审批', ISSUED: '已发料', DELIVERED: '已签收' }
  return map[status] || status
}

function handleSearch() { pagination.page = 1; fetchData() }
function handleReset() { Object.assign(queryParams, { issueNo: '', scheduleNo: '', status: '', dateRange: null }); handleSearch() }
function handleCreate() { ElMessage.info('新增领料功能待实现') }
function handleView(_row: IssueItem) { ElMessage.info('详情功能待实现') }

async function handleApprove(row: IssueItem) {
  try { await approveMaterialIssue(row.id); row.status = 'APPROVED'; ElMessage.success('审批通过') } catch { ElMessage.error('审批失败') }
}

async function handleIssue(row: IssueItem) {
  try { await issueMaterial(row.id); row.status = 'ISSUED'; ElMessage.success('发料成功') } catch { ElMessage.error('发料失败') }
}

async function handleDeliver(row: IssueItem) {
  try { await deliverMaterial(row.id); row.status = 'DELIVERED'; ElMessage.success('签收完成') } catch { ElMessage.error('签收失败') }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getMaterialIssueList({ ...queryParams, page: pagination.page, pageSize: pagination.pageSize })
    const data = res as Record<string, unknown>
    tableData.value = (data.records || []) as IssueItem[]
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

.flow-card {
  margin-bottom: 16px;
  border-radius: 12px;
}

.status-flow {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.flow-step {
  display: flex;
  align-items: center;
  gap: 8px;
}

.flow-node {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 24px;
  border: 2px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  min-width: 100px;
  transition: all var(--transition-fast);

  &.active {
    background: #f8fafc;
    box-shadow: var(--shadow-sm);
  }
}

.flow-count {
  font-size: 24px;
  font-weight: 700;
}

.flow-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.flow-arrow {
  color: #cbd5e1;
  font-size: 20px;
}
</style>
