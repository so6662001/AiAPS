<template>
  <div class="page-container">
    <!-- Contract Selector -->
    <el-card class="selector-card" shadow="never">
      <el-form inline>
        <el-form-item label="合同号">
          <el-select v-model="selectedContract" placeholder="选择或搜索合同号" filterable clearable style="width: 260px" @change="handleContractChange">
            <el-option v-for="c in contractList" :key="c.contractNo" :label="`${c.contractNo} — ${c.customer}`" :value="c.contractNo" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery"><el-icon><Search /></el-icon>查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <template v-if="contractData">
      <!-- Summary Card -->
      <el-row :gutter="16" class="summary-row">
        <el-col :span="6">
          <el-card class="summary-info-card" shadow="never">
            <div class="summary-field">
              <span class="summary-label">合同号</span>
              <span class="summary-value">{{ contractData.contractNo }}</span>
            </div>
            <div class="summary-field">
              <span class="summary-label">客户</span>
              <span class="summary-value">{{ contractData.customer }}</span>
            </div>
            <div class="summary-field">
              <span class="summary-label">合同量(T)</span>
              <span class="summary-value weight-value">{{ contractData.totalWeight }}</span>
            </div>
            <div class="summary-field">
              <span class="summary-label">预计完工</span>
              <span class="summary-value">{{ contractData.estimatedDate }}</span>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="progress-circle-card" shadow="never">
            <el-progress type="circle" :percentage="contractData.overallProgress" :width="140" :stroke-width="10" :color="progressColor(contractData.overallProgress)">
              <template #default="{ percentage }">
                <div class="circle-inner">
                  <span class="circle-pct">{{ percentage }}%</span>
                  <span class="circle-label">整体进度</span>
                </div>
              </template>
            </el-progress>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card class="chain-card" shadow="never">
            <template #header>
              <span class="card-title">生产链路概览</span>
            </template>
            <div class="production-chain">
              <div v-for="(stage, idx) in productionStages" :key="stage.name" class="chain-stage">
                <div class="stage-block" :style="{ borderColor: stage.color }">
                  <span class="stage-name">{{ stage.name }}</span>
                  <span class="stage-pct" :style="{ color: stage.color }">{{ stage.progress }}%</span>
                  <span class="stage-weight">{{ stage.completedWeight }}/{{ stage.totalWeight }}T</span>
                </div>
                <div v-if="idx < productionStages.length - 1" class="chain-arrow">
                  <svg width="24" height="16" viewBox="0 0 24 16"><path d="M0 8 L20 8 M16 4 L20 8 L16 12" stroke="#cbd5e1" stroke-width="2" fill="none" /></svg>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- Detail Table -->
      <el-card class="table-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span class="card-title">产品明细</span>
            <el-button size="small" @click="handleExport"><el-icon><Download /></el-icon>导出</el-button>
          </div>
        </template>
        <el-table :data="detailData" stripe style="border-radius: 8px">
          <el-table-column prop="product" label="产品" min-width="200" show-overflow-tooltip />
          <el-table-column prop="grade" label="材质" width="90">
            <template #default="{ row }"><span class="grade-cell">{{ row.grade }}</span></template>
          </el-table-column>
          <el-table-column prop="length" label="长度(mm)" width="100" align="right" />
          <el-table-column prop="contractQty" label="合同量(T)" width="110" align="right" />
          <el-table-column prop="producedQty" label="已产量(T)" width="110" align="right">
            <template #default="{ row }"><span class="weight-cell">{{ row.producedQty }}</span></template>
          </el-table-column>
          <el-table-column label="进度" width="180">
            <template #default="{ row }">
              <el-progress :percentage="row.progress" :stroke-width="10" :color="progressColor(row.progress)" />
            </template>
          </el-table-column>
          <el-table-column prop="dueDate" label="交期" width="120" />
          <el-table-column prop="estimatedDate" label="预计完工" width="120" />
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="detailStatusType(row.status)" size="small" effect="light" round>{{ detailStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <!-- Empty State -->
    <el-card v-else class="empty-card" shadow="never">
      <el-empty description="请选择合同号查看进度" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Download } from '@element-plus/icons-vue'
import { getContractProgress, getContractList } from '@/api/report'

interface ContractInfo {
  contractNo: string; customer: string; totalWeight: number
  overallProgress: number; estimatedDate: string
}

interface DetailItem {
  product: string; grade: string; length: number; contractQty: number
  producedQty: number; progress: number; dueDate: string
  estimatedDate: string; status: string
}

interface StageInfo {
  name: string; color: string; progress: number
  completedWeight: number; totalWeight: number
}

const selectedContract = ref('HT2026-0318')
const contractList = ref([
  { contractNo: 'HT2026-0318', customer: '中建钢构华南公司' },
  { contractNo: 'HT2026-0325', customer: '远大住工集团' },
  { contractNo: 'HT2026-0330', customer: '华润万家建材' },
  { contractNo: 'HT2026-0320', customer: '万科集团' },
  { contractNo: 'HT2026-0312', customer: '碧桂园控股' },
])

const contractData = ref<ContractInfo | null>({
  contractNo: 'HT2026-0318', customer: '中建钢构华南公司', totalWeight: 120, overallProgress: 45, estimatedDate: '2026-03-25',
})

const productionStages = reactive<StageInfo[]>([
  { name: '原料采购', color: '#6366f1', progress: 100, completedWeight: 50, totalWeight: 50 },
  { name: '纵剪分条', color: '#2563eb', progress: 65, completedWeight: 42, totalWeight: 64.8 },
  { name: '制管成型', color: '#f59e0b', progress: 20, completedWeight: 7.2, totalWeight: 36 },
  { name: '成品入库', color: '#10b981', progress: 0, completedWeight: 0, totalWeight: 120 },
])

const detailData = ref<DetailItem[]>([
  { product: '带钢 Q345B 4.0×305×C', grade: 'Q345B', length: 6000, contractQty: 48, producedQty: 31.2, progress: 65, dueDate: '2026-03-22', estimatedDate: '2026-03-21', status: 'IN_PROGRESS' },
  { product: '带钢 Q345B 4.0×256×C', grade: 'Q345B', length: 6000, contractQty: 36, producedQty: 14.4, progress: 40, dueDate: '2026-03-22', estimatedDate: '2026-03-22', status: 'IN_PROGRESS' },
  { product: '焊管 Q345B 4.0×Φ89×6000', grade: 'Q345B', length: 6000, contractQty: 36, producedQty: 7.2, progress: 20, dueDate: '2026-03-22', estimatedDate: '2026-03-25', status: 'DELAYED' },
])

function progressColor(pct: number) {
  if (pct >= 80) return '#10b981'
  if (pct >= 40) return '#2563eb'
  return '#f59e0b'
}

function detailStatusType(status: string) {
  const map: Record<string, string> = { NOT_STARTED: 'info', IN_PROGRESS: '', COMPLETED: 'success', DELAYED: 'danger' }
  return map[status] || 'info'
}

function detailStatusLabel(status: string) {
  const map: Record<string, string> = { NOT_STARTED: '未开始', IN_PROGRESS: '进行中', COMPLETED: '已完成', DELAYED: '延期' }
  return map[status] || status
}

function handleContractChange(_val: string) {
  handleQuery()
}

async function handleQuery() {
  if (!selectedContract.value) { contractData.value = null; return }
  try {
    const res = await getContractProgress({ contractNo: selectedContract.value })
    const data = res as Record<string, unknown>
    contractData.value = data.contract as ContractInfo
    detailData.value = (data.details || []) as DetailItem[]
  } catch {
    /* use mock data */
  }
}

function handleExport() { ElMessage.info('导出功能待实现') }

onMounted(async () => {
  try {
    const res = await getContractList({})
    contractList.value = (res as Record<string, unknown>).records as typeof contractList.value || contractList.value
  } catch { /* use mock */ }
  handleQuery()
})
</script>

<style scoped lang="scss">
.page-container { max-width: 1600px; }

.selector-card {
  margin-bottom: 16px;
  border-radius: 12px;
  :deep(.el-card__body) { padding-bottom: 2px; }
}

.summary-row { margin-bottom: 16px; }

.summary-info-card {
  border-radius: 12px;
  height: 100%;
}

.summary-field {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid var(--border-color-light);

  &:last-child { border-bottom: none; }
}

.summary-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.summary-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.weight-value {
  color: #2563eb;
  font-size: 16px;
}

.progress-circle-card {
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.circle-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.circle-pct {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
}

.circle-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.chain-card {
  border-radius: 12px;
  height: 100%;
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

.production-chain {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0;
  padding: 10px 0;
}

.chain-stage {
  display: flex;
  align-items: center;
}

.stage-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 16px;
  border: 2px solid;
  border-radius: 10px;
  background: #fff;
  min-width: 100px;
}

.stage-name {
  font-size: 12px;
  color: var(--text-secondary);
  font-weight: 500;
}

.stage-pct {
  font-size: 20px;
  font-weight: 700;
  margin: 4px 0;
}

.stage-weight {
  font-size: 11px;
  color: var(--text-placeholder);
}

.chain-arrow {
  padding: 0 6px;
}

.table-card { border-radius: 12px; }
.grade-cell { font-weight: 600; color: #2563eb; }
.weight-cell { font-weight: 600; }
.empty-card { border-radius: 12px; padding: 40px 0; }
</style>
