<template>
  <div class="page-container wizard">
    <!-- Steps -->
    <el-card class="steps-card" shadow="never">
      <el-steps :active="activeStep" align-center finish-status="success">
        <el-step title="选什么生产" description="勾选待排订单" />
        <el-step title="怎么生产" description="系统已自动配料" />
        <el-step title="排在哪 / 确认" description="系统已排好产线时间" />
      </el-steps>
    </el-card>

    <!-- ───────────── Step 1: 选订单 ───────────── -->
    <el-card v-show="activeStep === 0" class="body-card" shadow="never">
      <div class="source-row">
        <span class="lbl">来源</span>
        <el-radio-group v-model="source" size="default">
          <el-radio-button value="plan">计划订单</el-radio-button>
          <el-radio-button value="demand">需求订单</el-radio-button>
          <el-radio-button value="material">原料驱动</el-radio-button>
        </el-radio-group>
        <span class="hint">输入即查，无需点"查询"</span>
      </div>

      <div class="filter-row">
        <el-select v-model="filters.category" placeholder="品类" clearable style="width: 120px" size="small">
          <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select v-model="filters.process" placeholder="工艺" clearable style="width: 120px" size="small">
          <el-option v-for="p in processOptions" :key="p" :label="p" :value="p" />
        </el-select>
        <el-input v-model="filters.spec" placeholder="规格(输入即查)" clearable style="width: 170px" size="small">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filters.grade" placeholder="材质" clearable style="width: 110px" size="small">
          <el-option v-for="g in gradeOptions" :key="g" :label="g" :value="g" />
        </el-select>
        <el-input v-model="filters.contractNo" placeholder="合同号" clearable style="width: 150px" size="small" />
        <el-button size="small" @click="resetFilters"><el-icon><Refresh /></el-icon>重置</el-button>
      </div>

      <el-table
        ref="tableRef"
        :data="filteredCandidates"
        @selection-change="onSelectionChange"
        row-key="id"
        stripe
        size="small"
        style="border-radius: 8px"
      >
        <el-table-column type="selection" width="44" reserve-selection />
        <el-table-column prop="contractNo" label="合同号" width="130" />
        <el-table-column prop="customer" label="客户" width="100" show-overflow-tooltip />
        <el-table-column prop="spec" label="品名规格" min-width="170" show-overflow-tooltip />
        <el-table-column prop="grade" label="材质" width="80">
          <template #default="{ row }"><span class="grade-cell">{{ row.grade }}</span></template>
        </el-table-column>
        <el-table-column prop="qtyText" label="数量" width="90" align="right" />
        <el-table-column prop="weight" label="重量(T)" width="90" align="right" />
        <el-table-column prop="due" label="交期" width="100" align="center">
          <template #default="{ row }">
            <span :class="{ 'due-warn': row.dueRisk }">{{ row.due }}<span v-if="row.dueRisk"> ⚠</span></span>
          </template>
        </el-table-column>
        <el-table-column label="缺料" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="stockTag(row.stock).type" size="small" effect="light" round>{{ stockTag(row.stock).text }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div class="step-footer">
        <div class="summary">
          已选 <b>{{ selected.length }}</b> 行 · 合计 <b>{{ totalWeight }}</b> T
          <el-button text type="primary" size="small" @click="groupByContract">按合同分组选</el-button>
        </div>
        <el-button type="primary" :disabled="!selected.length" @click="goPlan">
          下一步<el-icon class="el-icon--right"><ArrowRight /></el-icon>
        </el-button>
      </div>
    </el-card>

    <!-- ───────────── Step 2: 配方案 ───────────── -->
    <el-card v-show="activeStep === 1" class="body-card" shadow="never">
      <el-alert type="success" :closable="false" show-icon style="margin-bottom: 14px">
        <template #title>
          系统已按"库存可用 + 材质匹配 + 合同窜料控制"自动配料，{{ planRows.length }} 单中
          <b>{{ okCount }}</b> 单达标<span v-if="warnCount">，<b class="warn-txt">{{ warnCount }}</b> 单需关注</span>
        </template>
      </el-alert>

      <div v-for="(row, idx) in planRows" :key="row.id" class="plan-block" :class="{ warn: row.warning }">
        <div class="plan-head">
          <span class="seq">{{ idx + 1 }}</span>
          <span class="plan-title">{{ row.spec }} · {{ row.grade }} · {{ row.contractNo }}</span>
          <el-tag :type="row.warning ? 'warning' : 'success'" size="small" effect="light" round>
            {{ row.warning || `成材率 ${row.yieldRate}% 达标` }}
          </el-tag>
        </div>
        <div class="plan-grid">
          <!-- 成品 -->
          <div class="col">
            <div class="col-title">成品（来自订单，已带出）</div>
            <div class="kv"><span>材质</span><b class="grade-cell">{{ row.grade }} ✓</b></div>
            <div class="kv"><span>规格</span><b>{{ row.spec }} ✓</b></div>
            <div class="kv"><span>工艺/模具</span><b>{{ row.process }} / {{ row.mold }}</b></div>
            <div class="kv"><span>需求</span><b>{{ row.demandQtyText }}</b></div>
          </div>
          <!-- 原料 -->
          <div class="col">
            <div class="col-title">投入原料（智能配料推荐）</div>
            <div class="kv"><span>原料</span><b>{{ row.material.spec }}</b></div>
            <div class="kv"><span>卷号/产地</span><b>{{ row.material.resNo }} · {{ row.material.origin }}</b></div>
            <div class="kv"><span>可用</span><b>{{ row.material.available }} T</b></div>
            <div class="kv">
              <span>投入</span>
              <el-input-number v-model="row.issueWeight" :min="0" :step="0.1" :precision="2" size="small" controls-position="right" style="width: 120px" @change="recalcFromIssue(row)" />
              <span class="unit">T</span>
            </div>
          </div>
          <!-- 产出 -->
          <div class="col">
            <div class="col-title">本次产出（实时反算）</div>
            <div class="kv">
              <span>产出量</span>
              <el-input-number v-model="row.outputQty" :min="0" :step="1" size="small" controls-position="right" style="width: 120px" @change="recalcFromOutput(row)" />
              <span class="unit">{{ row.unit }}</span>
            </div>
            <div class="kv"><span>产出重量</span><b>{{ row.outputWeight }} T</b></div>
            <div class="kv">
              <span>成材率</span>
              <b :class="row.yieldRate >= row.yieldBase ? 'ok-txt' : 'warn-txt'">{{ row.yieldRate }}% {{ row.yieldRate >= row.yieldBase ? '●优' : '▲低' }}</b>
            </div>
            <div class="kv"><span>损耗</span><b>{{ row.scrap }} T（边丝）</b></div>
          </div>
        </div>
      </div>

      <div class="step-footer">
        <el-button @click="activeStep = 0"><el-icon class="el-icon--left"><ArrowLeft /></el-icon>上一步</el-button>
        <div class="summary">
          <el-button size="small" @click="adoptAll">全部采用系统方案</el-button>
        </div>
        <el-button type="primary" @click="goSequence">
          下一步<el-icon class="el-icon--right"><ArrowRight /></el-icon>
        </el-button>
      </div>
    </el-card>

    <!-- ───────────── Step 3: 确认排产 ───────────── -->
    <el-card v-show="activeStep === 2" class="body-card" shadow="never">
      <div class="strategy-row">
        <span class="lbl">策略</span>
        <el-radio-group v-model="strategy" size="default" @change="rescore">
          <el-radio-button value="efficiency">效率优先</el-radio-button>
          <el-radio-button value="cost">成本优先</el-radio-button>
          <el-radio-button value="due">交期优先</el-radio-button>
          <el-radio-button value="balance">均衡</el-radio-button>
        </el-radio-group>
      </div>

      <div class="seq-box">
        <div class="seq-head">系统排产建议</div>
        <div class="seq-grid">
          <div class="kv"><span>产线</span><b>{{ sequence.line }}</b></div>
          <div class="kv"><span>开始</span><b>{{ sequence.start }}</b></div>
          <div class="kv"><span>结束</span><b>{{ sequence.end }}</b></div>
          <div class="kv"><span>模具</span><b>{{ sequence.mold }}（同壁厚连排，免换模）</b></div>
          <div class="kv"><span>班次</span><b>{{ sequence.shift }}</b></div>
          <div class="kv"><span>评分</span><b class="ok-txt">{{ sequence.score }} 分</b></div>
        </div>
      </div>

      <!-- 迷你甘特 -->
      <div class="mini-gantt">
        <div class="gantt-title">迷你甘特（排在哪一目了然）</div>
        <div v-for="g in ganttRows" :key="g.line" class="gantt-line">
          <span class="gantt-label">{{ g.line }}</span>
          <div class="gantt-track">
            <div
              v-for="(b, i) in g.bars"
              :key="i"
              class="gantt-bar"
              :class="b.type"
              :style="{ left: b.left + '%', width: b.width + '%' }"
            >{{ b.label }}</div>
          </div>
        </div>
      </div>

      <el-alert :type="sequence.impactType" :closable="false" show-icon style="margin-top: 14px">
        <template #title>影响预览：{{ sequence.impact }}</template>
      </el-alert>

      <div class="step-footer">
        <el-button @click="activeStep = 1"><el-icon class="el-icon--left"><ArrowLeft /></el-icon>上一步</el-button>
        <div class="summary">
          <el-button size="small" @click="simulate">What-If 模拟</el-button>
        </div>
        <el-button type="success" :loading="committing" @click="commit">
          <el-icon class="el-icon--left"><Check /></el-icon>确认排产
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox, ElTable } from 'element-plus'
import { Search, Refresh, ArrowRight, ArrowLeft, Check } from '@element-plus/icons-vue'

interface Candidate {
  id: string; contractNo: string; customer: string; spec: string; grade: string
  process: string; category: string; qty: number; unit: string; qtyText: string
  weight: number; due: string; dueRisk: boolean; stock: 'ok' | 'low' | 'none'
  mold: string; materialSpec: string; resNo: string; origin: string; available: number
  yieldBase: number
}

const activeStep = ref(0)
const source = ref<'plan' | 'demand' | 'material'>('plan')

const categoryOptions = ['管材', '型材', '板材', '开平', '分剪']
const processOptions = ['制管', '开平', '纵剪', '横剪', '折弯']
const gradeOptions = ['Q235B', 'Q345B', 'SPHC', 'SPCC', 'DX51D', 'SS400']

const filters = reactive({ category: '', process: '', spec: '', grade: '', contractNo: '' })
function resetFilters() { Object.assign(filters, { category: '', process: '', spec: '', grade: '', contractNo: '' }) }

const candidates = ref<Candidate[]>([
  { id: '1', contractNo: 'HT2026-0318', customer: '通源大兴', spec: '焊管 4.0×Φ89×6000', grade: 'Q345B', process: '制管', category: '管材', qty: 50, unit: '支', qtyText: '50 支', weight: 10.5, due: '03-22', dueRisk: false, stock: 'ok', mold: 'Φ89-01', materialSpec: '带钢 Q345B 4.0×305', resNo: 'R2026-0156', origin: '宝钢', available: 24, yieldBase: 93 },
  { id: '2', contractNo: 'HT2026-0318', customer: '通源大兴', spec: '焊管 4.2×Φ89×6000', grade: 'Q345B', process: '制管', category: '管材', qty: 30, unit: '支', qtyText: '30 支', weight: 8.47, due: '03-22', dueRisk: false, stock: 'ok', mold: 'Φ89-01', materialSpec: '带钢 Q345B 4.2×305', resNo: 'R2026-0157', origin: '宝钢', available: 18, yieldBase: 93 },
  { id: '3', contractNo: 'HT2026-0319', customer: '广汇钢构', spec: '开平 4.0×1500', grade: 'Q235B', process: '开平', category: '开平', qty: 20, unit: 'T', qtyText: '20 T', weight: 20, due: '03-25', dueRisk: false, stock: 'low', mold: '—', materialSpec: '热卷 Q235B 4.0×1500', resNo: 'R2026-0201', origin: '河钢', available: 14, yieldBase: 97 },
  { id: '4', contractNo: 'HT2026-0320', customer: '中铁建', spec: '横剪 6.0×1800×8000', grade: 'SS400', process: '横剪', category: '板材', qty: 68, unit: 'T', qtyText: '68 T', weight: 68, due: '03-21', dueRisk: true, stock: 'ok', mold: '—', materialSpec: '热卷 SS400 6.0×1800', resNo: 'R2026-0233', origin: '河钢', available: 80, yieldBase: 98 },
  { id: '5', contractNo: 'HT2026-0321', customer: '远大物流', spec: '纵剪 3.0×1500→305', grade: 'SPHC', process: '纵剪', category: '分剪', qty: 45, unit: 'T', qtyText: '45 T', weight: 45, due: '03-28', dueRisk: false, stock: 'none', mold: 'M-305-01', materialSpec: '热卷 SPHC 3.0×1500', resNo: 'R2026-0240', origin: '日钢', available: 0, yieldBase: 96 },
])

const filteredCandidates = computed(() =>
  candidates.value.filter(c =>
    (!filters.category || c.category === filters.category) &&
    (!filters.process || c.process === filters.process) &&
    (!filters.spec || c.spec.toLowerCase().includes(filters.spec.toLowerCase())) &&
    (!filters.grade || c.grade === filters.grade) &&
    (!filters.contractNo || c.contractNo.includes(filters.contractNo)),
  ),
)

function stockTag(s: string) {
  if (s === 'ok') return { type: 'success', text: '足' }
  if (s === 'low') return { type: 'warning', text: '缺' }
  return { type: 'danger', text: '无' }
}

const tableRef = ref<InstanceType<typeof ElTable>>()
const selected = ref<Candidate[]>([])
function onSelectionChange(rows: Candidate[]) { selected.value = rows }
const totalWeight = computed(() => selected.value.reduce((s, r) => s + r.weight, 0).toFixed(2))

function groupByContract() {
  if (!selected.value.length) { ElMessage.info('请先勾选一行作为分组基准'); return }
  const contract = selected.value[0].contractNo
  tableRef.value?.clearSelection()
  candidates.value.filter(c => c.contractNo === contract).forEach(c => tableRef.value?.toggleRowSelection(c, true))
  ElMessage.success(`已选中合同 ${contract} 的全部行`)
}

// ── Step 2 配方案 ──
interface PlanRow {
  id: string; contractNo: string; spec: string; grade: string; process: string; mold: string
  unit: string; demandQty: number; demandQtyText: string
  material: { spec: string; resNo: string; origin: string; available: number }
  issueWeight: number; outputQty: number; outputWeight: number
  yieldRate: number; yieldBase: number; scrap: number; warning: string
}
const planRows = ref<PlanRow[]>([])

function buildPlan() {
  planRows.value = selected.value.map(c => {
    const yieldRate = c.yieldBase + (c.stock === 'ok' ? 2 : 0)
    const outputWeight = c.weight
    const issueWeight = +(outputWeight / (yieldRate / 100)).toFixed(2)
    const scrap = +(issueWeight - outputWeight).toFixed(2)
    let warning = ''
    if (c.stock === 'none') warning = '原料无库存，需补料'
    else if (c.stock === 'low' && issueWeight > c.available) warning = '库存不足，需补料'
    else if (yieldRate < c.yieldBase) warning = '成材率偏低'
    return {
      id: c.id, contractNo: c.contractNo, spec: c.spec, grade: c.grade, process: c.process, mold: c.mold,
      unit: c.unit, demandQty: c.qty, demandQtyText: c.qtyText,
      material: { spec: c.materialSpec, resNo: c.resNo, origin: c.origin, available: c.available },
      issueWeight, outputQty: c.qty, outputWeight, yieldRate, yieldBase: c.yieldBase, scrap, warning,
    }
  })
}
const okCount = computed(() => planRows.value.filter(r => !r.warning).length)
const warnCount = computed(() => planRows.value.filter(r => r.warning).length)

function recalcFromIssue(row: PlanRow) {
  row.outputWeight = +(row.issueWeight * (row.yieldRate / 100)).toFixed(2)
  const perUnit = row.demandQty > 0 ? row.outputWeight / row.demandQty : 0
  void perUnit
  row.outputQty = Math.round(row.outputWeight / (row.outputWeight / row.demandQty || 1))
  row.scrap = +(row.issueWeight - row.outputWeight).toFixed(2)
}
function recalcFromOutput(row: PlanRow) {
  const perUnitWeight = row.demandQty > 0 ? (row.outputWeight || 1) / row.demandQty : 0
  row.outputWeight = +((perUnitWeight || (row.outputWeight / row.demandQty)) * row.outputQty).toFixed(2)
  row.issueWeight = +(row.outputWeight / (row.yieldRate / 100)).toFixed(2)
  row.scrap = +(row.issueWeight - row.outputWeight).toFixed(2)
}
function adoptAll() { buildPlan(); ElMessage.success('已采用系统配料方案') }

function goPlan() { buildPlan(); activeStep.value = 1 }

// ── Step 3 排产 ──
const strategy = ref<'efficiency' | 'cost' | 'due' | 'balance'>('efficiency')
const sequence = reactive({
  line: '制管线 #1', start: '03-21 08:00', end: '03-22 16:00', mold: 'Φ89-01',
  shift: '白班 ×2', score: 92, impact: '占用制管#1 白班 → HT..0320 后延 2h（仍满足交期）',
  impactType: 'warning' as 'success' | 'warning' | 'error',
})
function rescore() {
  const map = { efficiency: 92, cost: 88, due: 95, balance: 90 }
  sequence.score = map[strategy.value]
  if (strategy.value === 'due') { sequence.impact = '优先保交期，产能利用略降，无单后延'; sequence.impactType = 'success' }
  else if (strategy.value === 'cost') { sequence.impact = '合并换模降成本，HT..0320 后延 4h（仍满足交期）'; sequence.impactType = 'warning' }
  else { sequence.impact = '占用制管#1 白班 → HT..0320 后延 2h（仍满足交期）'; sequence.impactType = 'warning' }
}

const ganttRows = computed(() => [
  { line: '制管#1', bars: [
    { type: 'self', left: 5, width: 45, label: '本单' },
    { type: 'busy', left: 52, width: 25, label: '已排' },
  ] },
  { line: '制管#2', bars: [
    { type: 'busy', left: 5, width: 28, label: '已排' },
    { type: 'idle', left: 35, width: 40, label: '空闲' },
  ] },
])

function goSequence() { rescore(); activeStep.value = 2 }
function simulate() { ElMessage.info('进入 What-If 沙盒推演（演示）') }

const committing = ref(false)
async function commit() {
  committing.value = true
  setTimeout(async () => {
    committing.value = false
    await ElMessageBox.confirm(
      `已排产 ${planRows.value.length} 单 / ${totalWeight.value} T，已预占原料并生成领料预约。`,
      '排产成功',
      { confirmButtonText: '查看甘特', cancelButtonText: '继续排下一批', type: 'success' },
    ).then(() => {
      ElMessage.success('跳转排产甘特图（演示）')
      reset()
    }).catch(() => { reset() })
  }, 600)
}
function reset() {
  activeStep.value = 0
  tableRef.value?.clearSelection()
  selected.value = []
  planRows.value = []
}
</script>

<style scoped lang="scss">
.wizard { max-width: 1500px; }
.steps-card { margin-bottom: 16px; border-radius: 12px; }
.body-card { border-radius: 12px; }

.source-row, .filter-row, .strategy-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.source-row { margin-bottom: 14px; }
.filter-row { margin-bottom: 16px; }
.strategy-row { margin-bottom: 16px; }
.lbl { font-size: 13px; color: var(--text-secondary); font-weight: 600; }
.hint { font-size: 12px; color: var(--text-secondary); margin-left: auto; }
.grade-cell { font-weight: 600; color: #2563eb; }
.due-warn { color: #e6a23c; font-weight: 600; }

.step-footer {
  display: flex; align-items: center; justify-content: space-between;
  margin-top: 18px; padding-top: 14px; border-top: 1px solid var(--el-border-color-lighter);
}
.summary { font-size: 13px; color: var(--text-secondary); display: flex; align-items: center; gap: 10px; }
.summary b { color: #2563eb; }
.warn-txt { color: #e6a23c; }
.ok-txt { color: #16a34a; }

.plan-block {
  border: 1px solid var(--el-border-color-lighter); border-radius: 10px;
  padding: 12px 14px; margin-bottom: 12px; background: var(--el-fill-color-blank);
  &.warn { border-color: #f3d19e; background: #fdf6ec; }
}
.plan-head { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.seq {
  width: 22px; height: 22px; border-radius: 50%; background: #2563eb; color: #fff;
  font-size: 12px; display: inline-flex; align-items: center; justify-content: center;
}
.plan-title { font-weight: 600; font-size: 14px; flex: 1; }
.plan-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.col-title { font-size: 12px; color: var(--text-secondary); font-weight: 600; margin-bottom: 8px; padding-bottom: 6px; border-bottom: 1px dashed var(--el-border-color-lighter); }
.kv { display: flex; align-items: center; gap: 8px; font-size: 13px; margin-bottom: 7px; }
.kv > span:first-child { color: var(--text-secondary); min-width: 64px; }
.kv b { color: var(--text-primary); }
.unit { color: var(--text-secondary); font-size: 12px; }

.seq-box { border: 1px solid var(--el-border-color-lighter); border-radius: 10px; padding: 14px; margin-bottom: 14px; }
.seq-head { font-size: 13px; font-weight: 600; margin-bottom: 10px; color: var(--text-primary); }
.seq-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px 16px; }

.mini-gantt { border: 1px solid var(--el-border-color-lighter); border-radius: 10px; padding: 14px; }
.gantt-title { font-size: 13px; font-weight: 600; margin-bottom: 12px; color: var(--text-primary); }
.gantt-line { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.gantt-label { width: 60px; font-size: 12px; color: var(--text-secondary); }
.gantt-track { position: relative; flex: 1; height: 24px; background: var(--el-fill-color-light); border-radius: 4px; }
.gantt-bar {
  position: absolute; top: 2px; height: 20px; border-radius: 4px; font-size: 11px;
  color: #fff; display: flex; align-items: center; justify-content: center; overflow: hidden;
  &.self { background: #2563eb; }
  &.busy { background: #94a3b8; }
  &.idle { background: transparent; color: var(--text-secondary); border: 1px dashed var(--el-border-color); }
}
</style>
