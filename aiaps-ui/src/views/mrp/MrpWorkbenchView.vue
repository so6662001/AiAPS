<template>
  <div class="page-container">
    <!-- MRP Run Control -->
    <el-card class="control-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">
            <el-icon style="margin-right: 6px; vertical-align: -2px"><Cpu /></el-icon>MRP运行控制
          </span>
          <el-tag v-if="lastRunTime" size="small" effect="light" type="info">上次运行: {{ lastRunTime }}</el-tag>
        </div>
      </template>
      <div class="control-row">
        <div class="control-item">
          <span class="control-label">运行类型</span>
          <el-radio-group v-model="runConfig.type" size="default">
            <el-radio-button value="full">完整重算</el-radio-button>
            <el-radio-button value="net_change">净变更</el-radio-button>
            <el-radio-button value="selective">选择性</el-radio-button>
          </el-radio-group>
        </div>
        <div class="control-item">
          <span class="control-label">展望期(天)</span>
          <el-input-number v-model="runConfig.horizonDays" :min="7" :max="365" :step="7" style="width: 140px" />
        </div>
        <div class="control-item" style="margin-left: auto">
          <el-button type="primary" size="large" :loading="isRunning" @click="handleRunMrp" style="min-width: 140px">
            <el-icon v-if="!isRunning"><VideoPlay /></el-icon>
            {{ isRunning ? '运行中...' : '开始运行' }}
          </el-button>
        </div>
      </div>
      <el-progress v-if="isRunning" :percentage="runProgress" :stroke-width="4" :show-text="true" style="margin-top: 16px" :color="'#2563eb'" />
    </el-card>

    <!-- Results Tabs -->
    <el-card class="result-card" shadow="never">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- Plan Orders Tab -->
        <el-tab-pane label="计划订单" name="planOrders">
          <div class="tab-toolbar">
            <div class="tab-filters">
              <el-select v-model="planFilters.grade" placeholder="材质" clearable style="width: 120px" size="small">
                <el-option v-for="g in gradeOptions" :key="g" :label="g" :value="g" />
              </el-select>
              <el-select v-model="planFilters.origin" placeholder="产地" clearable style="width: 120px" size="small">
                <el-option v-for="o in originOptions" :key="o" :label="o" :value="o" />
              </el-select>
              <el-input v-model="planFilters.contractNo" placeholder="合同号" clearable style="width: 150px" size="small" />
              <el-select v-model="planFilters.status" placeholder="状态" clearable style="width: 120px" size="small">
                <el-option label="待确认" value="PENDING" />
                <el-option label="已确认" value="CONFIRMED" />
                <el-option label="已取消" value="CANCELLED" />
              </el-select>
              <el-button type="primary" size="small" @click="fetchPlanOrders"><el-icon><Search /></el-icon>查询</el-button>
            </div>
            <div class="tab-actions">
              <el-button size="small" @click="handleBatchConfirm" :disabled="!selectedOrders.length">
                <el-icon><CircleCheck /></el-icon>批量确认
              </el-button>
              <el-button size="small" @click="handleBatchCancel" :disabled="!selectedOrders.length">
                <el-icon><CircleClose /></el-icon>批量取消
              </el-button>
              <el-button type="primary" size="small" @click="handleTransferSchedule" :disabled="!selectedOrders.length">
                <el-icon><Right /></el-icon>转排产
              </el-button>
            </div>
          </div>
          <el-table :data="planOrderData" v-loading="planLoading" stripe style="border-radius: 8px" @selection-change="handleSelectionChange">
            <el-table-column type="selection" width="45" />
            <el-table-column prop="orderNo" label="订单号" min-width="130" />
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
            <el-table-column prop="weight" label="重量(T)" width="90" align="right" />
            <el-table-column prop="length" label="长度(mm)" width="100" align="right" />
            <el-table-column prop="contractNo" label="合同号" width="130" />
            <el-table-column label="类型" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="orderTypeTag(row.orderType)" size="small" effect="light">{{ orderTypeLabel(row.orderType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="startDate" label="开始日" width="110" />
            <el-table-column prop="endDate" label="完成日" width="110" />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="planStatusType(row.status)" size="small" effect="light" round>{{ planStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- Purchase Suggestions Tab -->
        <el-tab-pane label="采购建议" name="purchase">
          <el-table :data="purchaseData" v-loading="purchaseLoading" stripe style="border-radius: 8px">
            <el-table-column prop="materialSpec" label="物料/规格" min-width="200" show-overflow-tooltip />
            <el-table-column prop="grade" label="材质" width="90" />
            <el-table-column prop="origin" label="建议产地" width="100" />
            <el-table-column prop="requiredQty" label="需求量(T)" width="110" align="right" />
            <el-table-column prop="currentStock" label="当前库存(T)" width="120" align="right" />
            <el-table-column prop="suggestQty" label="建议采购(T)" width="120" align="right">
              <template #default="{ row }">
                <span style="color: #2563eb; font-weight: 600">{{ row.suggestQty }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="requiredDate" label="需求日期" width="120" />
            <el-table-column prop="suggestOrderDate" label="建议下单" width="120" />
            <el-table-column prop="leadTime" label="提前期(天)" width="110" align="center" />
          </el-table>
        </el-tab-pane>

        <!-- Exceptions Tab -->
        <el-tab-pane label="异常信息" name="exceptions">
          <el-table :data="exceptionData" v-loading="exceptionLoading" stripe style="border-radius: 8px">
            <el-table-column label="级别" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.level === 'error' ? 'danger' : 'warning'" size="small" effect="light">{{ row.level === 'error' ? '错误' : '警告' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="code" label="异常代码" width="130" />
            <el-table-column prop="message" label="异常描述" min-width="300" show-overflow-tooltip />
            <el-table-column prop="relatedOrder" label="相关单号" width="140" />
            <el-table-column prop="material" label="相关物料" width="180" show-overflow-tooltip />
            <el-table-column prop="suggestion" label="建议处理" min-width="200" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Cpu, VideoPlay, Search, CircleCheck, CircleClose, Right } from '@element-plus/icons-vue'
import { runMrp, getPlanOrders, getPurchaseSuggestions, getMrpExceptions, confirmPlanOrders, cancelPlanOrders, transferToSchedule } from '@/api/mrp'

interface PlanOrder {
  id: string; orderNo: string; materialSpec: string; grade: string; origin: string
  weight: number; length: number; contractNo: string; orderType: string
  startDate: string; endDate: string; status: string
}

interface PurchaseItem {
  materialSpec: string; grade: string; origin: string; requiredQty: number
  currentStock: number; suggestQty: number; requiredDate: string
  suggestOrderDate: string; leadTime: number
}

interface ExceptionItem {
  level: string; code: string; message: string
  relatedOrder: string; material: string; suggestion: string
}

const gradeOptions = ['Q235B', 'Q345B', 'Q355B', 'SS400', 'SPHC', 'SPCC', 'DC01', 'DX51D']
const originOptions = ['宝钢', '鞍钢', '首钢', '马钢', '河钢', '日钢', '沙钢']

const isRunning = ref(false)
const runProgress = ref(0)
const lastRunTime = ref('2026-03-19 08:30:00')
const activeTab = ref('planOrders')
const planLoading = ref(false)
const purchaseLoading = ref(false)
const exceptionLoading = ref(false)
const selectedOrders = ref<PlanOrder[]>([])

const runConfig = reactive({ type: 'full', horizonDays: 90 })
const planFilters = reactive({ grade: '', origin: '', contractNo: '', status: '' })

const planOrderData = ref<PlanOrder[]>([
  { id: '1', orderNo: 'PO2026-00101', materialSpec: '带钢 Q345B 4.0×305×C', grade: 'Q345B', origin: '宝钢', weight: 24, length: 6000, contractNo: 'HT2026-0318', orderType: 'manufacture', startDate: '2026-03-20', endDate: '2026-03-21', status: 'PENDING' },
  { id: '2', orderNo: 'PO2026-00102', materialSpec: '带钢 Q345B 4.0×256×C', grade: 'Q345B', origin: '宝钢', weight: 18, length: 6000, contractNo: 'HT2026-0318', orderType: 'manufacture', startDate: '2026-03-20', endDate: '2026-03-21', status: 'PENDING' },
  { id: '3', orderNo: 'PO2026-00103', materialSpec: '焊管 Q345B 4.0×Φ89×6000', grade: 'Q345B', origin: '鞍钢', weight: 36, length: 6000, contractNo: 'HT2026-0318', orderType: 'manufacture', startDate: '2026-03-21', endDate: '2026-03-22', status: 'PENDING' },
  { id: '4', orderNo: 'PO2026-00104', materialSpec: '热轧卷板 Q345B 4.0×1250×C', grade: 'Q345B', origin: '宝钢', weight: 50, length: 0, contractNo: 'HT2026-0318', orderType: 'purchase', startDate: '2026-03-18', endDate: '2026-03-25', status: 'CONFIRMED' },
  { id: '5', orderNo: 'PO2026-00105', materialSpec: '带钢 Q235B 3.0×205×C', grade: 'Q235B', origin: '马钢', weight: 23, length: 6000, contractNo: 'HT2026-0325', orderType: 'manufacture', startDate: '2026-03-24', endDate: '2026-03-25', status: 'PENDING' },
  { id: '6', orderNo: 'PO2026-00106', materialSpec: '焊管 Q235B 3.0×Φ60×6000', grade: 'Q235B', origin: '马钢', weight: 40, length: 6000, contractNo: 'HT2026-0325', orderType: 'manufacture', startDate: '2026-03-25', endDate: '2026-03-27', status: 'PENDING' },
  { id: '7', orderNo: 'PO2026-00107', materialSpec: '热轧卷板 SPHC 3.0×1500×C', grade: 'SPHC', origin: '日钢', weight: 100, length: 0, contractNo: '—', orderType: 'purchase', startDate: '2026-03-19', endDate: '2026-03-28', status: 'PENDING' },
  { id: '8', orderNo: 'PO2026-00108', materialSpec: '镀锌板 DX51D 0.8×1000×2000', grade: 'DX51D', origin: '宝钢', weight: 80, length: 2000, contractNo: 'HT2026-0330', orderType: 'outsource', startDate: '2026-03-28', endDate: '2026-04-05', status: 'PENDING' },
])

const purchaseData = ref<PurchaseItem[]>([
  { materialSpec: '热轧卷板 Q345B 4.0×1250×C', grade: 'Q345B', origin: '宝钢', requiredQty: 72, currentStock: 25, suggestQty: 50, requiredDate: '2026-03-20', suggestOrderDate: '2026-03-17', leadTime: 3 },
  { materialSpec: '热轧卷板 SPHC 3.0×1500×C', grade: 'SPHC', origin: '日钢', requiredQty: 100, currentStock: 15, suggestQty: 90, requiredDate: '2026-03-24', suggestOrderDate: '2026-03-19', leadTime: 5 },
  { materialSpec: '热轧卷板 Q235B 3.0×1250×C', grade: 'Q235B', origin: '马钢', requiredQty: 50, currentStock: 30, suggestQty: 25, requiredDate: '2026-03-24', suggestOrderDate: '2026-03-20', leadTime: 4 },
])

const exceptionData = ref<ExceptionItem[]>([
  { level: 'error', code: 'MRP-E001', message: '物料 Q345B 4.0×1250×C 库存不足，缺口 25T，无法满足需求 DM2026-0301 交期', relatedOrder: 'DM2026-0301', material: '热轧卷板 Q345B 4.0×1250×C', suggestion: '建议紧急采购或调整交期' },
  { level: 'warning', code: 'MRP-W001', message: '制管线#2 处于维护状态，焊管 Q345B 4.0×Φ89×6000 排产可能延迟', relatedOrder: 'PO2026-00103', material: '焊管 Q345B 4.0×Φ89×6000', suggestion: '调整至制管线#1 或等待维护完成' },
  { level: 'warning', code: 'MRP-W002', message: 'SPHC 2.0×125×C 安全库存预警，当前15T < 安全库存50T', relatedOrder: 'DM2026-0304', material: '带钢 SPHC 2.0×125×C', suggestion: '建议补充采购' },
])

function orderTypeTag(type: string) {
  const map: Record<string, string> = { manufacture: '', purchase: 'success', outsource: 'warning' }
  return map[type] || 'info'
}

function orderTypeLabel(type: string) {
  const map: Record<string, string> = { manufacture: '制造', purchase: '采购', outsource: '委外' }
  return map[type] || type
}

function planStatusType(status: string) {
  const map: Record<string, string> = { PENDING: 'info', CONFIRMED: '', CANCELLED: 'info' }
  return map[status] || 'info'
}

function planStatusLabel(status: string) {
  const map: Record<string, string> = { PENDING: '待确认', CONFIRMED: '已确认', CANCELLED: '已取消' }
  return map[status] || status
}

function handleSelectionChange(selection: PlanOrder[]) {
  selectedOrders.value = selection
}

async function handleRunMrp() {
  isRunning.value = true
  runProgress.value = 0
  const timer = setInterval(() => {
    runProgress.value = Math.min(runProgress.value + Math.random() * 15, 95)
  }, 500)
  try {
    await runMrp({ type: runConfig.type, horizonDays: runConfig.horizonDays })
    runProgress.value = 100
    lastRunTime.value = new Date().toLocaleString('zh-CN')
    ElMessage.success('MRP运行完成')
    fetchPlanOrders()
  } catch (err) {
    runProgress.value = 100
    ElMessage.error('MRP运行失败: ' + (err as Error).message)
  } finally {
    clearInterval(timer)
    setTimeout(() => { isRunning.value = false }, 800)
  }
}

async function handleBatchConfirm() {
  const ids = selectedOrders.value.map((o) => o.id)
  try {
    await confirmPlanOrders(ids)
    ElMessage.success(`已确认 ${ids.length} 条计划订单`)
    fetchPlanOrders()
  } catch {
    ElMessage.error('确认失败')
  }
}

async function handleBatchCancel() {
  const ids = selectedOrders.value.map((o) => o.id)
  try {
    await cancelPlanOrders(ids)
    ElMessage.success(`已取消 ${ids.length} 条计划订单`)
    fetchPlanOrders()
  } catch {
    ElMessage.warning('取消操作（演示）')
  }
}

async function handleTransferSchedule() {
  const ids = selectedOrders.value.map((o) => o.id)
  try {
    await transferToSchedule(ids)
    ElMessage.success(`已转排产 ${ids.length} 条计划订单`)
  } catch {
    ElMessage.success(`已转排产 ${ids.length} 条（演示）`)
  }
}

function handleTabChange(tab: string) {
  if (tab === 'planOrders') fetchPlanOrders()
  else if (tab === 'purchase') fetchPurchaseData()
  else if (tab === 'exceptions') fetchExceptions()
}

async function fetchPlanOrders() {
  planLoading.value = true
  try {
    const res = await getPlanOrders({ ...planFilters })
    const data = res as Record<string, unknown>
    planOrderData.value = (data.records || []) as PlanOrder[]
  } catch { /* use mock */ } finally { planLoading.value = false }
}

async function fetchPurchaseData() {
  purchaseLoading.value = true
  try {
    const res = await getPurchaseSuggestions({})
    purchaseData.value = (res as Record<string, unknown>).records as PurchaseItem[] || []
  } catch { /* use mock */ } finally { purchaseLoading.value = false }
}

async function fetchExceptions() {
  exceptionLoading.value = true
  try {
    const res = await getMrpExceptions({})
    exceptionData.value = (res as Record<string, unknown>).records as ExceptionItem[] || []
  } catch { /* use mock */ } finally { exceptionLoading.value = false }
}

onMounted(() => { fetchPlanOrders() })
</script>

<style scoped lang="scss">
.page-container {
  max-width: 1600px;
}

.control-card {
  margin-bottom: 16px;
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

.control-row {
  display: flex;
  align-items: flex-end;
  gap: 32px;
  flex-wrap: wrap;
}

.control-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.control-label {
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
}

.result-card {
  border-radius: 12px;
}

.tab-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 10px;
}

.tab-filters {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.tab-actions {
  display: flex;
  gap: 8px;
}

.grade-cell {
  font-weight: 600;
  color: #2563eb;
}

.origin-cell {
  font-weight: 500;
  color: var(--text-primary);
}
</style>
