<template>
  <div class="page-container">
    <!-- Filters -->
    <el-card class="filter-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="材质">
          <el-select v-model="queryParams.grade" placeholder="全部材质" clearable style="width: 120px">
            <el-option v-for="g in gradeOptions" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item label="产地">
          <el-select v-model="queryParams.origin" placeholder="全部产地" clearable style="width: 120px">
            <el-option v-for="o in originOptions" :key="o" :label="o" :value="o" />
          </el-select>
        </el-form-item>
        <el-form-item label="合同号">
          <el-input v-model="queryParams.contractNo" placeholder="合同号" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.orderType" placeholder="全部" clearable style="width: 110px">
            <el-option label="制造" value="manufacture" />
            <el-option label="采购" value="purchase" />
            <el-option label="委外" value="outsource" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 110px">
            <el-option label="待确认" value="PENDING" />
            <el-option label="已确认" value="CONFIRMED" />
            <el-option label="已取消" value="CANCELLED" />
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
          <span class="card-title">计划订单列表</span>
          <div class="header-actions">
            <el-button size="small" @click="handleBatchConfirm" :disabled="!selectedOrders.length"><el-icon><CircleCheck /></el-icon>批量确认</el-button>
            <el-button size="small" @click="handleBatchCancel" :disabled="!selectedOrders.length"><el-icon><CircleClose /></el-icon>批量取消</el-button>
            <el-button type="primary" size="small" @click="handleTransfer" :disabled="!selectedOrders.length"><el-icon><Right /></el-icon>转排产</el-button>
            <el-button size="small" @click="handleExport"><el-icon><Download /></el-icon>导出</el-button>
          </div>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" stripe style="border-radius: 8px" @selection-change="handleSelection">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="orderNo" label="订单号" min-width="130" sortable />
        <el-table-column prop="materialSpec" label="物料/规格" min-width="200" show-overflow-tooltip />
        <el-table-column prop="grade" label="材质" width="90">
          <template #default="{ row }">
            <span class="grade-cell">{{ row.grade }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="origin" label="产地" width="80">
          <template #default="{ row }">
            <span class="origin-cell">{{ row.origin }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="weight" label="重量(T)" width="90" align="right" sortable />
        <el-table-column prop="length" label="长度(mm)" width="100" align="right" />
        <el-table-column prop="contractNo" label="合同号" width="130" />
        <el-table-column label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTagMap[row.orderType] || 'info'" size="small" effect="light">{{ typeLabel[row.orderType] || row.orderType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startDate" label="开始日" width="110" sortable />
        <el-table-column prop="endDate" label="完成日" width="110" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagMap[row.status] || 'info'" size="small" effect="light" round>{{ statusLabelMap[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="handleView(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[20, 50, 100, 200]" layout="total, sizes, prev, pager, next, jumper" background @size-change="fetchData" @current-change="fetchData" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, CircleCheck, CircleClose, Right, Download } from '@element-plus/icons-vue'
import { getPlanOrders, confirmPlanOrders, cancelPlanOrders, transferToSchedule } from '@/api/mrp'

interface PlanOrder {
  id: string; orderNo: string; materialSpec: string; grade: string; origin: string
  weight: number; length: number; contractNo: string; orderType: string
  startDate: string; endDate: string; status: string
}

const gradeOptions = ['Q235B', 'Q345B', 'Q355B', 'SS400', 'SPHC', 'SPCC', 'DC01', 'DX51D']
const originOptions = ['宝钢', '鞍钢', '首钢', '马钢', '河钢', '日钢', '沙钢']
const typeTagMap: Record<string, string> = { manufacture: '', purchase: 'success', outsource: 'warning' }
const typeLabel: Record<string, string> = { manufacture: '制造', purchase: '采购', outsource: '委外' }
const statusTagMap: Record<string, string> = { PENDING: 'info', CONFIRMED: '', CANCELLED: 'info' }
const statusLabelMap: Record<string, string> = { PENDING: '待确认', CONFIRMED: '已确认', CANCELLED: '已取消' }

const loading = ref(false)
const selectedOrders = ref<PlanOrder[]>([])
const queryParams = reactive({ grade: '', origin: '', contractNo: '', orderType: '', status: '', dateRange: null as string[] | null })
const pagination = reactive({ page: 1, pageSize: 50, total: 0 })

const tableData = ref<PlanOrder[]>([
  { id: '1', orderNo: 'PO2026-00101', materialSpec: '带钢 Q345B 4.0×305×C', grade: 'Q345B', origin: '宝钢', weight: 24, length: 6000, contractNo: 'HT2026-0318', orderType: 'manufacture', startDate: '2026-03-20', endDate: '2026-03-21', status: 'PENDING' },
  { id: '2', orderNo: 'PO2026-00102', materialSpec: '带钢 Q345B 4.0×256×C', grade: 'Q345B', origin: '宝钢', weight: 18, length: 6000, contractNo: 'HT2026-0318', orderType: 'manufacture', startDate: '2026-03-20', endDate: '2026-03-21', status: 'PENDING' },
  { id: '3', orderNo: 'PO2026-00103', materialSpec: '焊管 Q345B 4.0×Φ89×6000', grade: 'Q345B', origin: '鞍钢', weight: 36, length: 6000, contractNo: 'HT2026-0318', orderType: 'manufacture', startDate: '2026-03-21', endDate: '2026-03-22', status: 'PENDING' },
  { id: '4', orderNo: 'PO2026-00104', materialSpec: '热轧卷板 Q345B 4.0×1250×C', grade: 'Q345B', origin: '宝钢', weight: 50, length: 0, contractNo: 'HT2026-0318', orderType: 'purchase', startDate: '2026-03-18', endDate: '2026-03-25', status: 'CONFIRMED' },
  { id: '5', orderNo: 'PO2026-00105', materialSpec: '带钢 Q235B 3.0×205×C', grade: 'Q235B', origin: '马钢', weight: 23, length: 6000, contractNo: 'HT2026-0325', orderType: 'manufacture', startDate: '2026-03-24', endDate: '2026-03-25', status: 'PENDING' },
  { id: '6', orderNo: 'PO2026-00106', materialSpec: '焊管 Q235B 3.0×Φ60×6000', grade: 'Q235B', origin: '马钢', weight: 40, length: 6000, contractNo: 'HT2026-0325', orderType: 'manufacture', startDate: '2026-03-25', endDate: '2026-03-27', status: 'PENDING' },
  { id: '7', orderNo: 'PO2026-00107', materialSpec: '热轧卷板 SPHC 3.0×1500×C', grade: 'SPHC', origin: '日钢', weight: 100, length: 0, contractNo: '—', orderType: 'purchase', startDate: '2026-03-19', endDate: '2026-03-28', status: 'PENDING' },
  { id: '8', orderNo: 'PO2026-00108', materialSpec: '镀锌板 DX51D 0.8×1000×2000', grade: 'DX51D', origin: '宝钢', weight: 80, length: 2000, contractNo: 'HT2026-0330', orderType: 'outsource', startDate: '2026-03-28', endDate: '2026-04-05', status: 'PENDING' },
  { id: '9', orderNo: 'PO2026-00109', materialSpec: '带钢 SPHC 2.0×125×C', grade: 'SPHC', origin: '首钢', weight: 25, length: 0, contractNo: '—', orderType: 'manufacture', startDate: '2026-03-22', endDate: '2026-03-23', status: 'CONFIRMED' },
  { id: '10', orderNo: 'PO2026-00110', materialSpec: '彩涂板 DX51D 0.5×1200×2400', grade: 'DX51D', origin: '宝钢', weight: 80, length: 2400, contractNo: 'HT2026-0330', orderType: 'outsource', startDate: '2026-03-30', endDate: '2026-04-08', status: 'PENDING' },
])

function handleSearch() { pagination.page = 1; fetchData() }
function handleReset() { Object.assign(queryParams, { grade: '', origin: '', contractNo: '', orderType: '', status: '', dateRange: null }); handleSearch() }
function handleSelection(sel: PlanOrder[]) { selectedOrders.value = sel }
function handleView(_row: PlanOrder) { ElMessage.info('详情功能待实现') }
function handleExport() { ElMessage.info('导出功能待实现') }

async function handleBatchConfirm() {
  try { await confirmPlanOrders(selectedOrders.value.map((o) => o.id)); ElMessage.success('批量确认成功'); fetchData() } catch { ElMessage.success('批量确认（演示）') }
}
async function handleBatchCancel() {
  try { await cancelPlanOrders(selectedOrders.value.map((o) => o.id)); ElMessage.success('批量取消成功'); fetchData() } catch { ElMessage.warning('批量取消（演示）') }
}
async function handleTransfer() {
  try { await transferToSchedule(selectedOrders.value.map((o) => o.id)); ElMessage.success('转排产成功'); fetchData() } catch { ElMessage.success('转排产（演示）') }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getPlanOrders({ ...queryParams, page: pagination.page, pageSize: pagination.pageSize })
    const data = res as Record<string, unknown>
    tableData.value = (data.records || []) as PlanOrder[]
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
.origin-cell { font-weight: 500; color: var(--text-primary); }
</style>
