<template>
  <div class="multi-nesting-page">
    <!-- 顶部模式选择 -->
    <el-card class="mode-card" shadow="never">
      <div class="mode-bar">
        <div class="mode-left">
          <span class="mode-label">套料模式</span>
          <el-radio-group v-model="nestingMode" @change="handleModeChange">
            <el-radio-button value="SLIT">纵剪合并</el-radio-button>
            <el-radio-button value="LEVEL">开平合并</el-radio-button>
            <el-radio-button value="CUT">剪切合并</el-radio-button>
          </el-radio-group>
          <el-select v-model="filterGrade" placeholder="材质" clearable style="width: 120px; margin-left: 12px;">
            <el-option label="Q235B" value="Q235B" />
            <el-option label="Q345B" value="Q345B" />
            <el-option label="SPCC" value="SPCC" />
            <el-option label="DC01" value="DC01" />
          </el-select>
          <el-select v-model="filterThickness" placeholder="厚度" clearable style="width: 100px; margin-left: 8px;">
            <el-option label="2.0" value="2.0" />
            <el-option label="2.5" value="2.5" />
            <el-option label="3.0" value="3.0" />
            <el-option label="4.0" value="4.0" />
            <el-option label="6.0" value="6.0" />
            <el-option label="8.0" value="8.0" />
          </el-select>
        </div>
        <div class="mode-right">
          <el-button type="primary" :icon="Search" @click="loadPoolData">加载待合并需求</el-button>
          <el-button :icon="Refresh" @click="resetFilter">重置</el-button>
        </div>
      </div>
    </el-card>

    <div class="nesting-body">
      <!-- 左侧: 需求池 -->
      <div class="pool-panel">
        <el-card shadow="never">
          <template #header>
            <div class="panel-header">
              <span class="panel-title">
                <el-icon><Collection /></el-icon> 需求池
                <el-tag type="info" size="small" style="margin-left: 8px;">{{ poolGroups.length }} 组</el-tag>
              </span>
              <el-button size="small" type="primary" plain @click="handleAutoCollect">从MRP自动收集</el-button>
            </div>
          </template>

          <!-- 合并组列表 -->
          <div v-for="(group, gi) in poolGroups" :key="gi" class="pool-group">
            <div class="group-header" @click="group.expanded = !group.expanded">
              <el-icon><CaretRight v-if="!group.expanded" /><CaretBottom v-else /></el-icon>
              <span class="group-key">{{ group.groupKey }}</span>
              <el-tag size="small">{{ group.items.length }} 条需求</el-tag>
              <el-tag size="small" type="warning">{{ group.totalWeight }}T</el-tag>
              <el-tag v-if="group.contractCount > 1" size="small" type="danger">{{ group.contractCount }} 合同</el-tag>
            </div>
            <div v-show="group.expanded" class="group-body">
              <el-table :data="group.items" size="small" border stripe>
                <el-table-column type="selection" width="35" />
                <el-table-column label="合同" prop="contractNo" width="90" />
                <el-table-column label="客户" prop="customerName" width="80" />
                <el-table-column label="规格" width="100">
                  <template #default="{ row }">
                    <span v-if="nestingMode === 'SLIT'">宽{{ row.width }}mm</span>
                    <span v-else-if="nestingMode === 'LEVEL'">{{ row.cutLength }}mm 定尺</span>
                    <span v-else>{{ row.cutWidth }}×{{ row.cutLength }}mm</span>
                  </template>
                </el-table-column>
                <el-table-column label="材质" prop="patName" width="65" />
                <el-table-column label="重量(T)" prop="requiredWeight" width="75" align="right" />
                <el-table-column label="数量" prop="requiredQty" width="55" align="right" />
                <el-table-column label="交期" width="85">
                  <template #default="{ row }">
                    <span :class="{ 'text-danger': isUrgent(row.requiredDate) }">{{ formatDate(row.requiredDate) }}</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>

          <el-empty v-if="poolGroups.length === 0" description="暂无待合并需求，请先加载" :image-size="60" />
        </el-card>
      </div>

      <!-- 中间: 母卷/母板匹配 -->
      <div class="source-panel">
        <el-card shadow="never">
          <template #header>
            <div class="panel-header">
              <span class="panel-title"><el-icon><Coin /></el-icon> 可用母卷/母板</span>
            </div>
          </template>

          <el-table :data="sourceCoils" size="small" border stripe highlight-current-row @current-change="handleCoilSelect">
            <el-table-column type="index" width="35" />
            <el-table-column label="卷号/批号" prop="resNo" width="110" />
            <el-table-column label="规格" width="100">
              <template #default="{ row }">{{ row.thickness }}×{{ row.width }}</template>
            </el-table-column>
            <el-table-column label="材质" prop="patName" width="65" />
            <el-table-column label="产地" prop="paName" width="55" />
            <el-table-column label="重量(T)" prop="weight" width="75" align="right" />
            <el-table-column label="可切出" width="120">
              <template #default="{ row }">
                <span class="cut-preview">{{ row.cutPreview }}</span>
              </template>
            </el-table-column>
          </el-table>

          <div style="margin-top: 12px; text-align: center;">
            <el-button type="primary" size="default" :icon="MagicStick" :disabled="!selectedCoil" @click="handleOptimize">
              自动优化套料方案
            </el-button>
          </div>
        </el-card>
      </div>
    </div>

    <!-- 底部: 套料方案预览 -->
    <el-card v-if="nestingResult" class="result-card" shadow="never">
      <template #header>
        <div class="panel-header">
          <span class="panel-title">
            <el-icon><TrophyBase /></el-icon> 套料方案预览
            <el-tag type="success" size="small" style="margin-left: 8px;">利用率 {{ nestingResult.utilizationPct }}%</el-tag>
            <el-tag size="small" style="margin-left: 4px;">{{ nestingResult.contractCount }} 合同合并</el-tag>
          </span>
          <div>
            <el-button type="success" :icon="Check" @click="handleConfirm">确认方案 → 转排产</el-button>
            <el-button :icon="RefreshRight" @click="handleReoptimize">重新优化</el-button>
          </div>
        </div>
      </template>

      <el-row :gutter="16">
        <!-- 左: 方案图形化展示 -->
        <el-col :span="14">
          <div class="visual-title">
            <span v-if="nestingMode === 'SLIT'">纵剪方案 (母卷 {{ nestingResult.sourceWidth }}mm)</span>
            <span v-else-if="nestingMode === 'LEVEL'">开平切割序列 (母卷 {{ nestingResult.sourceLength }}m)</span>
            <span v-else>二维排版 (母板 {{ nestingResult.sourceWidth }}×{{ nestingResult.sourceLength }}mm)</span>
          </div>

          <!-- 纵剪可视化: 横条分割 -->
          <div v-if="nestingMode === 'SLIT'" class="slit-visual">
            <div class="slit-coil">
              <div v-for="(strip, si) in nestingResult.strips" :key="si"
                   class="slit-strip"
                   :style="{ width: (strip.width / nestingResult.sourceWidth * 100) + '%', background: getContractColor(strip.contractNo) }"
                   :title="strip.contractNo + ' ' + strip.width + 'mm ' + strip.weight + 'T'">
                <div class="strip-label">{{ strip.width }}</div>
                <div class="strip-contract">{{ strip.contractNo || '余料' }}</div>
              </div>
            </div>
            <div class="slit-legend">
              <span v-for="(c, ci) in nestingResult.contracts" :key="ci" class="legend-item">
                <span class="legend-dot" :style="{ background: getContractColor(c.contractNo) }"></span>
                {{ c.contractNo }} {{ c.customerName }}
              </span>
            </div>
          </div>

          <!-- 开平可视化: 横向序列 -->
          <div v-if="nestingMode === 'LEVEL'" class="level-visual">
            <div class="level-coil">
              <div v-for="(sheet, si) in nestingResult.sheets" :key="si"
                   class="level-sheet"
                   :style="{ width: (sheet.length / nestingResult.totalLength * 100) + '%', background: getContractColor(sheet.contractNo) }">
                <div class="sheet-label">{{ sheet.length / 1000 }}m</div>
                <div class="sheet-contract">{{ sheet.contractNo }}</div>
                <div class="sheet-count">×{{ sheet.count }}</div>
              </div>
            </div>
          </div>

          <!-- 剪切二维可视化 -->
          <div v-if="nestingMode === 'CUT'" class="cut-visual">
            <div class="cut-board" :style="{ aspectRatio: nestingResult.sourceWidth / nestingResult.sourceLength }">
              <div v-for="(piece, pi) in nestingResult.pieces" :key="pi"
                   class="cut-piece"
                   :style="{
                     left: (piece.posX / nestingResult.sourceWidth * 100) + '%',
                     top: (piece.posY / nestingResult.sourceLength * 100) + '%',
                     width: ((piece.isRotated ? piece.pieceLength : piece.pieceWidth) / nestingResult.sourceWidth * 100) + '%',
                     height: ((piece.isRotated ? piece.pieceWidth : piece.pieceLength) / nestingResult.sourceLength * 100) + '%',
                     background: getContractColor(piece.contractNo),
                   }"
                   :title="piece.contractNo + ' ' + piece.pieceWidth + '×' + piece.pieceLength">
                <span class="piece-label">{{ piece.pieceWidth }}×{{ piece.pieceLength }}</span>
              </div>
            </div>
          </div>
        </el-col>

        <!-- 右: 产出分配表 -->
        <el-col :span="10">
          <div class="visual-title">产出分配与成本分摊</div>
          <el-table :data="nestingResult.allocations" size="small" border stripe show-summary :summary-method="getSummary">
            <el-table-column label="合同" prop="contractNo" width="80" />
            <el-table-column label="客户" prop="customerName" width="70" />
            <el-table-column label="规格" prop="specDesc" width="90" />
            <el-table-column label="数量" prop="qty" width="50" align="right" />
            <el-table-column label="重量(T)" prop="weight" width="75" align="right" />
            <el-table-column label="占比" width="60" align="right">
              <template #default="{ row }">{{ row.pct }}%</template>
            </el-table-column>
            <el-table-column label="分摊成本" prop="cost" width="85" align="right">
              <template #default="{ row }">¥{{ row.cost }}</template>
            </el-table-column>
          </el-table>

          <div class="result-summary">
            <div class="summary-row">
              <span>母卷/母板</span>
              <span>{{ nestingResult.sourceSpec }} {{ nestingResult.sourceGrade }} {{ nestingResult.sourceWeight }}T</span>
            </div>
            <div class="summary-row">
              <span>利用率</span>
              <span :class="nestingResult.utilizationPct >= 90 ? 'text-success' : 'text-warning'">
                {{ nestingResult.utilizationPct }}%
              </span>
            </div>
            <div class="summary-row">
              <span>余料/废料</span>
              <span>{{ nestingResult.wasteWeight }}T</span>
            </div>
            <div class="summary-row">
              <span>成本分摊方式</span>
              <el-radio-group v-model="costSplitMethod" size="small">
                <el-radio-button value="WEIGHT">按重量</el-radio-button>
                <el-radio-button value="QTY">按数量</el-radio-button>
                <el-radio-button value="AREA">按面积</el-radio-button>
              </el-radio-group>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, MagicStick, Check, RefreshRight, Collection, Coin, TrophyBase, CaretRight, CaretBottom } from '@element-plus/icons-vue'

const nestingMode = ref('SLIT')
const filterGrade = ref('')
const filterThickness = ref('')
const costSplitMethod = ref('WEIGHT')
const selectedCoil = ref<any>(null)

interface PoolItem {
  poolId: number; contractNo: string; customerName: string; patName: string;
  thickness: number; width: number; cutLength: number; cutWidth: number;
  requiredQty: number; requiredWeight: number; requiredDate: string;
}
interface PoolGroup {
  groupKey: string; items: PoolItem[]; totalWeight: number;
  contractCount: number; expanded: boolean;
}

const poolGroups = ref<PoolGroup[]>([])

const sourceCoils = ref([
  { resNo: 'C-0500', thickness: 6.0, width: 1500, patName: 'Q235B', paName: '鞍钢', weight: 30.0, cutPreview: '可切6m×35+4m×28张' },
  { resNo: 'C-0501', thickness: 6.0, width: 1500, patName: 'Q235B', paName: '唐钢', weight: 25.0, cutPreview: '可切6m×29+4m×23张' },
  { resNo: 'C-0510', thickness: 2.5, width: 1500, patName: 'Q235B', paName: '鞍钢', weight: 22.0, cutPreview: '300+600+400 一分三' },
])

const nestingResult = ref<any>(null)

const handleModeChange = () => {
  nestingResult.value = null
  poolGroups.value = []
}

const resetFilter = () => {
  filterGrade.value = ''
  filterThickness.value = ''
  poolGroups.value = []
  nestingResult.value = null
}

const loadPoolData = () => {
  if (nestingMode.value === 'LEVEL') {
    poolGroups.value = [{
      groupKey: 'LEVEL|6.0|Q235B|1500',
      expanded: true,
      totalWeight: 28,
      contractCount: 3,
      items: [
        { poolId: 1, contractNo: 'HT-001', customerName: '客户A', patName: 'Q235B', thickness: 6.0, width: 1500, cutLength: 6000, cutWidth: 0, requiredQty: 35, requiredWeight: 15, requiredDate: '2026-04-01' },
        { poolId: 2, contractNo: 'HT-002', customerName: '客户B', patName: 'Q235B', thickness: 6.0, width: 1500, cutLength: 4000, cutWidth: 0, requiredQty: 28, requiredWeight: 8, requiredDate: '2026-04-05' },
        { poolId: 3, contractNo: 'HT-003', customerName: '客户C', patName: 'Q235B', thickness: 6.0, width: 1500, cutLength: 8000, cutWidth: 0, requiredQty: 9, requiredWeight: 5, requiredDate: '2026-04-10' },
      ]
    }]
  } else if (nestingMode.value === 'SLIT') {
    poolGroups.value = [{
      groupKey: 'SLIT|2.5|Q235B|1500',
      expanded: true,
      totalWeight: 33,
      contractCount: 3,
      items: [
        { poolId: 10, contractNo: 'HT-007', customerName: '客户D', patName: 'Q235B', thickness: 2.5, width: 300, cutLength: 0, cutWidth: 0, requiredQty: 1, requiredWeight: 10, requiredDate: '2026-04-02' },
        { poolId: 11, contractNo: 'HT-008', customerName: '客户E', patName: 'Q235B', thickness: 2.5, width: 600, cutLength: 0, cutWidth: 0, requiredQty: 2, requiredWeight: 15, requiredDate: '2026-04-06' },
        { poolId: 12, contractNo: '(内部)', customerName: '自用', patName: 'Q235B', thickness: 2.5, width: 400, cutLength: 0, cutWidth: 0, requiredQty: 1, requiredWeight: 8, requiredDate: '2026-04-15' },
      ]
    }]
  } else {
    poolGroups.value = [{
      groupKey: 'CUT|6.0|Q235B',
      expanded: true,
      totalWeight: 2.5,
      contractCount: 3,
      items: [
        { poolId: 20, contractNo: 'HT-004', customerName: '客户F', patName: 'Q235B', thickness: 6.0, width: 300, cutLength: 500, cutWidth: 300, requiredQty: 20, requiredWeight: 1.4, requiredDate: '2026-04-03' },
        { poolId: 21, contractNo: 'HT-005', customerName: '客户G', patName: 'Q235B', thickness: 6.0, width: 200, cutLength: 800, cutWidth: 200, requiredQty: 15, requiredWeight: 1.1, requiredDate: '2026-04-07' },
        { poolId: 22, contractNo: 'HT-006', customerName: '客户H', patName: 'Q235B', thickness: 6.0, width: 400, cutLength: 400, cutWidth: 400, requiredQty: 10, requiredWeight: 0.6, requiredDate: '2026-04-12' },
      ]
    }]
  }
  ElMessage.success(`已加载 ${poolGroups.value[0]?.items.length || 0} 条待合并需求`)
}

const handleAutoCollect = () => {
  ElMessage.info('正在从MRP计划订单中自动收集套料类需求...')
  setTimeout(() => { loadPoolData() }, 500)
}

const handleCoilSelect = (row: any) => { selectedCoil.value = row }

const contractColors: Record<string, string> = {}
const colorPalette = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#06b6d4', '#84cc16']
let colorIdx = 0
const getContractColor = (contractNo: string) => {
  if (!contractNo) return '#cbd5e1'
  if (!contractColors[contractNo]) {
    contractColors[contractNo] = colorPalette[colorIdx % colorPalette.length]
    colorIdx++
  }
  return contractColors[contractNo]
}

const handleOptimize = () => {
  if (nestingMode.value === 'SLIT') {
    nestingResult.value = {
      sourceWidth: 1500, sourceLength: 0, sourceSpec: '2.5×1500', sourceGrade: 'Q235B', sourceWeight: 22.0,
      utilizationPct: 93.3, wasteWeight: 1.47, contractCount: 3,
      strips: [
        { width: 300, contractNo: 'HT-007', weight: 4.4 },
        { width: 600, contractNo: 'HT-008', weight: 8.8 },
        { width: 400, contractNo: '(内部)', weight: 5.87 },
        { width: 194, contractNo: null, weight: 1.47 },
      ],
      contracts: [
        { contractNo: 'HT-007', customerName: '客户D' },
        { contractNo: 'HT-008', customerName: '客户E' },
        { contractNo: '(内部)', customerName: '自用' },
      ],
      allocations: [
        { contractNo: 'HT-007', customerName: '客户D', specDesc: '2.5×300', qty: 1, weight: 4.40, pct: 21.4, cost: 18480 },
        { contractNo: 'HT-008', customerName: '客户E', specDesc: '2.5×600', qty: 2, weight: 8.80, pct: 42.9, cost: 36960 },
        { contractNo: '(内部)', customerName: '自用', specDesc: '2.5×400', qty: 1, weight: 5.87, pct: 28.6, cost: 24654 },
        { contractNo: '—', customerName: '余料', specDesc: '边丝+余', qty: 0, weight: 1.47, pct: 7.1, cost: 0 },
      ],
    }
  } else if (nestingMode.value === 'LEVEL') {
    const totalLen = 6000 * 35 + 4000 * 28 + 8000 * 9
    nestingResult.value = {
      sourceWidth: 1500, sourceLength: 420, sourceSpec: '6.0×1500', sourceGrade: 'Q235B', sourceWeight: 30.0,
      utilizationPct: 92.9, wasteWeight: 2.14, contractCount: 3, totalLength: totalLen,
      sheets: [
        { length: 6000, contractNo: 'HT-001', count: 35 },
        { length: 4000, contractNo: 'HT-002', count: 28 },
        { length: 8000, contractNo: 'HT-003', count: 9 },
      ],
      contracts: [
        { contractNo: 'HT-001', customerName: '客户A' },
        { contractNo: 'HT-002', customerName: '客户B' },
        { contractNo: 'HT-003', customerName: '客户C' },
      ],
      allocations: [
        { contractNo: 'HT-001', customerName: '客户A', specDesc: '6.0×1500×6m', qty: 35, weight: 14.85, pct: 53.3, cost: 62370 },
        { contractNo: 'HT-002', customerName: '客户B', specDesc: '6.0×1500×4m', qty: 28, weight: 7.92, pct: 28.4, cost: 33264 },
        { contractNo: 'HT-003', customerName: '客户C', specDesc: '6.0×1500×8m', qty: 9, weight: 5.09, pct: 18.3, cost: 21378 },
        { contractNo: '—', customerName: '余料', specDesc: '短尺', qty: 0, weight: 2.14, pct: 0, cost: 0 },
      ],
    }
  } else {
    nestingResult.value = {
      sourceWidth: 1500, sourceLength: 6000, sourceSpec: '6.0×1500×6000', sourceGrade: 'Q235B', sourceWeight: 0.424,
      utilizationPct: 82.3, wasteWeight: 0.075, contractCount: 3,
      pieces: [
        { posX: 0, posY: 0, pieceWidth: 300, pieceLength: 500, contractNo: 'HT-004', isRotated: false },
        { posX: 300, posY: 0, pieceWidth: 300, pieceLength: 500, contractNo: 'HT-004', isRotated: false },
        { posX: 600, posY: 0, pieceWidth: 200, pieceLength: 800, contractNo: 'HT-005', isRotated: false },
        { posX: 800, posY: 0, pieceWidth: 200, pieceLength: 800, contractNo: 'HT-005', isRotated: false },
        { posX: 1000, posY: 0, pieceWidth: 400, pieceLength: 400, contractNo: 'HT-006', isRotated: false },
        { posX: 0, posY: 500, pieceWidth: 300, pieceLength: 500, contractNo: 'HT-004', isRotated: false },
        { posX: 300, posY: 500, pieceWidth: 400, pieceLength: 400, contractNo: 'HT-006', isRotated: false },
        { posX: 700, posY: 800, pieceWidth: 200, pieceLength: 800, contractNo: 'HT-005', isRotated: true },
      ],
      contracts: [
        { contractNo: 'HT-004', customerName: '客户F' },
        { contractNo: 'HT-005', customerName: '客户G' },
        { contractNo: 'HT-006', customerName: '客户H' },
      ],
      allocations: [
        { contractNo: 'HT-004', customerName: '客户F', specDesc: '300×500', qty: 3, weight: 0.21, pct: 49.4, cost: 890 },
        { contractNo: 'HT-005', customerName: '客户G', specDesc: '200×800', qty: 3, weight: 0.18, pct: 41.4, cost: 745 },
        { contractNo: 'HT-006', customerName: '客户H', specDesc: '400×400', qty: 2, weight: 0.06, pct: 9.2, cost: 165 },
      ],
    }
  }
  ElMessage.success('套料方案优化完成')
}

const handleReoptimize = () => {
  nestingResult.value = null
  setTimeout(() => handleOptimize(), 300)
}

const handleConfirm = () => {
  ElMessageBox.confirm(
    `确认套料方案并转排产？\n合并 ${nestingResult.value.contractCount} 个合同，利用率 ${nestingResult.value.utilizationPct}%`,
    '确认套料方案', { type: 'success' }
  ).then(() => {
    ElMessage.success('套料方案已确认，排产单已自动创建')
    nestingResult.value = null
  }).catch(() => {})
}

const getSummary = ({ columns, data }: any) => {
  const sums: string[] = []
  columns.forEach((col: any, index: number) => {
    if (index === 0) { sums[index] = '合计'; return }
    if (col.property === 'weight') {
      sums[index] = data.reduce((s: number, r: any) => s + (r.weight || 0), 0).toFixed(2)
    } else if (col.property === 'cost') {
      sums[index] = '¥' + data.reduce((s: number, r: any) => s + (r.cost || 0), 0).toLocaleString()
    } else {
      sums[index] = ''
    }
  })
  return sums
}

const isUrgent = (d: string) => d && new Date(d) < new Date(Date.now() + 7 * 86400000)
const formatDate = (d: string) => d ? d.substring(5, 10) : ''
</script>

<style scoped>
.multi-nesting-page { display: flex; flex-direction: column; gap: 12px; }
.mode-card { border-radius: 12px; }
.mode-bar { display: flex; justify-content: space-between; align-items: center; }
.mode-left { display: flex; align-items: center; gap: 8px; }
.mode-right { display: flex; gap: 8px; }
.mode-label { font-weight: 600; color: #1e293b; margin-right: 8px; }
.nesting-body { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.panel-header { display: flex; justify-content: space-between; align-items: center; }
.panel-title { display: flex; align-items: center; gap: 6px; font-weight: 600; color: #1e293b; }
.pool-group { margin-bottom: 12px; border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden; }
.group-header { display: flex; align-items: center; gap: 8px; padding: 10px 12px; background: #f8fafc; cursor: pointer; font-size: 13px; }
.group-header:hover { background: #f1f5f9; }
.group-key { font-family: 'Courier New', monospace; font-weight: 600; color: #2563eb; }
.group-body { padding: 8px; }
.cut-preview { font-size: 12px; color: #64748b; }
.result-card { border-radius: 12px; border-top: 3px solid #2563eb; }
.visual-title { font-weight: 600; color: #1e293b; margin-bottom: 12px; font-size: 14px; }

/* 纵剪可视化 */
.slit-visual { margin-bottom: 16px; }
.slit-coil { display: flex; height: 80px; border: 2px solid #334155; border-radius: 8px; overflow: hidden; }
.slit-strip { display: flex; flex-direction: column; justify-content: center; align-items: center; color: #fff; font-size: 12px; font-weight: 600; border-right: 2px dashed rgba(255,255,255,0.4); min-width: 30px; }
.slit-strip:last-child { border-right: none; }
.strip-label { font-size: 14px; }
.strip-contract { font-size: 10px; opacity: 0.85; }
.slit-legend { display: flex; gap: 16px; margin-top: 8px; font-size: 12px; }
.legend-item { display: flex; align-items: center; gap: 4px; }
.legend-dot { width: 10px; height: 10px; border-radius: 2px; }

/* 开平可视化 */
.level-visual { margin-bottom: 16px; }
.level-coil { display: flex; height: 70px; border: 2px solid #334155; border-radius: 8px; overflow: hidden; }
.level-sheet { display: flex; flex-direction: column; justify-content: center; align-items: center; color: #fff; font-size: 11px; font-weight: 600; border-right: 1px solid rgba(255,255,255,0.3); }
.sheet-label { font-size: 13px; }
.sheet-contract { font-size: 9px; opacity: 0.8; }
.sheet-count { font-size: 10px; opacity: 0.7; }

/* 剪切二维可视化 */
.cut-visual { margin-bottom: 16px; }
.cut-board { position: relative; width: 100%; min-height: 200px; border: 2px solid #334155; border-radius: 8px; background: #f8fafc; }
.cut-piece { position: absolute; display: flex; justify-content: center; align-items: center; border: 1px solid rgba(255,255,255,0.5); border-radius: 2px; }
.piece-label { color: #fff; font-size: 9px; font-weight: 600; text-shadow: 0 1px 2px rgba(0,0,0,0.3); }

.result-summary { margin-top: 16px; padding: 12px; background: #f8fafc; border-radius: 8px; }
.summary-row { display: flex; justify-content: space-between; padding: 6px 0; font-size: 13px; border-bottom: 1px dashed #e2e8f0; }
.summary-row:last-child { border-bottom: none; }
.text-success { color: #10b981; font-weight: 700; }
.text-warning { color: #f59e0b; font-weight: 700; }
.text-danger { color: #ef4444; }
</style>
