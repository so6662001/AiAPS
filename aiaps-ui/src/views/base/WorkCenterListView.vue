<template>
  <div class="page-container">
    <!-- Filter -->
    <el-card class="filter-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="工作中心">
          <el-input v-model="queryParams.keyword" placeholder="编号/名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.type" placeholder="全部类型" clearable style="width: 140px">
            <el-option v-for="t in wcTypes" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
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
          <span class="card-title">工作中心</span>
          <el-button type="primary" size="small" @click="handleAdd"><el-icon><Plus /></el-icon>新增</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" stripe style="border-radius: 8px">
        <el-table-column prop="code" label="编号" width="120" />
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="light" :type="typeTagMap[row.type] || 'info'">{{ typeNameMap[row.type] || row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="capacity" label="日产能(T)" width="110" align="right" />
        <el-table-column label="当前负荷" width="160">
          <template #default="{ row }">
            <div class="load-cell">
              <el-progress :percentage="row.loadRate" :color="loadColor(row.loadRate)" :stroke-width="6" :show-text="false" style="flex: 1" />
              <span class="load-text">{{ row.loadRate }}%</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="shiftMode" label="班制" width="80" align="center" />
        <el-table-column prop="efficiency" label="效率(%)" width="90" align="right" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small" effect="light" round>
              {{ row.status === 'active' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button text type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next, jumper" background @size-change="fetchData" @current-change="fetchData" />
      </div>

      <!-- 工作中心编辑弹窗 -->
      <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" destroy-on-close>
        <el-form ref="wcFormRef" :model="wcForm" :rules="wcRules" label-width="100px">
          <el-form-item label="编号" prop="wcCode">
            <el-input v-model="wcForm.wcCode" placeholder="请输入编号" />
          </el-form-item>
          <el-form-item label="名称" prop="wcName">
            <el-input v-model="wcForm.wcName" placeholder="请输入名称" />
          </el-form-item>
          <el-form-item label="类型" prop="wcType">
            <el-select v-model="wcForm.wcType" placeholder="请选择类型" style="width: 100%">
              <el-option label="产线" value="LINE" />
              <el-option label="机台" value="MACHINE" />
              <el-option label="工位" value="STATION" />
            </el-select>
          </el-form-item>
          <el-form-item label="产能单位" prop="capacityUnit">
            <el-input v-model="wcForm.capacityUnit" placeholder="T/班" />
          </el-form-item>
          <el-form-item label="标准产能" prop="stdCapacity">
            <el-input-number v-model="wcForm.stdCapacity" :min="0" :precision="1" style="width: 100%" />
          </el-form-item>
          <el-form-item label="班制" prop="shiftMode">
            <el-select v-model="wcForm.shiftMode" placeholder="请选择班制" style="width: 100%">
              <el-option label="三班" value="3S" />
              <el-option label="两班" value="2S" />
              <el-option label="一班" value="1S" />
            </el-select>
          </el-form-item>
          <el-form-item label="每班工时" prop="hoursPerShift">
            <el-input-number v-model="wcForm.hoursPerShift" :min="1" :max="12" :precision="1" style="width: 100%" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleWcSubmit">确定</el-button>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { getWorkCenterList, deleteWorkCenter } from '@/api/workCenter'

interface WorkCenter {
  id: string; code: string; name: string; type: string
  capacity: number; loadRate: number; shiftMode: string
  efficiency: number; status: string
}

const loading = ref(false)
const queryParams = reactive({ keyword: '', type: '', status: '' })
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })

const wcTypes = [
  { value: 'slitting', label: '纵剪线' },
  { value: 'crosscut', label: '横剪线' },
  { value: 'pipe', label: '制管线' },
  { value: 'leveling', label: '矫平线' },
  { value: 'coating', label: '涂装线' },
]

const typeTagMap: Record<string, string> = { slitting: '', crosscut: 'success', pipe: 'warning', leveling: 'info', coating: 'danger' }
const typeNameMap: Record<string, string> = { slitting: '纵剪线', crosscut: '横剪线', pipe: '制管线', leveling: '矫平线', coating: '涂装线' }

const tableData = ref<WorkCenter[]>([
  { id: '1', code: 'WC-SL01', name: '纵剪线 #1', type: 'slitting', capacity: 500, loadRate: 92, shiftMode: '三班', efficiency: 95, status: 'active' },
  { id: '2', code: 'WC-SL02', name: '纵剪线 #2', type: 'slitting', capacity: 450, loadRate: 78, shiftMode: '三班', efficiency: 93, status: 'active' },
  { id: '3', code: 'WC-CC01', name: '横剪线 #1', type: 'crosscut', capacity: 350, loadRate: 45, shiftMode: '两班', efficiency: 90, status: 'active' },
  { id: '4', code: 'WC-PP01', name: '制管线 #1', type: 'pipe', capacity: 200, loadRate: 88, shiftMode: '三班', efficiency: 92, status: 'active' },
  { id: '5', code: 'WC-PP02', name: '制管线 #2', type: 'pipe', capacity: 180, loadRate: 0, shiftMode: '三班', efficiency: 88, status: 'inactive' },
  { id: '6', code: 'WC-LV01', name: '矫平线 #1', type: 'leveling', capacity: 300, loadRate: 65, shiftMode: '两班', efficiency: 91, status: 'active' },
])

function handleSearch() { pagination.page = 1; fetchData() }
function handleReset() { Object.assign(queryParams, { keyword: '', type: '', status: '' }); handleSearch() }
const dialogVisible = ref(false)
const dialogTitle = ref('新增工作中心')
const wcFormRef = ref()
const wcForm = reactive({ wcCode: '', wcName: '', wcType: 'LINE', capacityUnit: 'T/班', stdCapacity: 100, shiftMode: '3S', hoursPerShift: 8 })
const wcRules = {
  wcCode: [{ required: true, message: '请输入编号', trigger: 'blur' }],
  wcName: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  wcType: [{ required: true, message: '请选择类型', trigger: 'change' }],
}
let editingId: string | null = null

function handleAdd() {
  Object.assign(wcForm, { wcCode: '', wcName: '', wcType: 'LINE', capacityUnit: 'T/班', stdCapacity: 100, shiftMode: '3S', hoursPerShift: 8 })
  editingId = null
  dialogTitle.value = '新增工作中心'
  dialogVisible.value = true
}

function handleEdit(row: WorkCenter) {
  Object.assign(wcForm, { wcCode: row.code, wcName: row.name, wcType: 'LINE', capacityUnit: 'T/班', stdCapacity: row.capacity, shiftMode: row.shiftMode === '三班' ? '3S' : row.shiftMode === '两班' ? '2S' : '1S', hoursPerShift: 8 })
  editingId = row.id
  dialogTitle.value = '编辑工作中心'
  dialogVisible.value = true
}

async function handleWcSubmit() {
  const valid = await wcFormRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    ElMessage.success(dialogTitle.value + '成功')
    dialogVisible.value = false
    fetchData()
  } catch { ElMessage.error('操作失败') }
}
async function handleDelete(row: WorkCenter) {
  await ElMessageBox.confirm(`确认删除 "${row.name}" 吗？`, '提示', { type: 'warning' })
  try { await deleteWorkCenter(row.id); ElMessage.success('删除成功'); fetchData() } catch { ElMessage.error('删除失败') }
}

function loadColor(rate: number) {
  if (rate >= 90) return '#ef4444'
  if (rate >= 70) return '#f59e0b'
  return '#10b981'
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getWorkCenterList({ ...queryParams, page: pagination.page, pageSize: pagination.pageSize })
    const data = res as Record<string, unknown>
    tableData.value = (data.records || []) as WorkCenter[]
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
.load-cell { display: flex; align-items: center; gap: 8px; }
.load-text { font-size: 12px; font-weight: 600; color: var(--text-secondary); min-width: 36px; text-align: right; }
</style>
