<template>
  <div class="page-container trace-page">
    <!-- Search Section -->
    <el-card class="search-card" shadow="never">
      <div class="search-section">
        <div class="search-modes">
          <span class="mode-label">查询方式</span>
          <el-radio-group v-model="searchMode" size="default">
            <el-radio-button value="barcode">扫条码</el-radio-button>
            <el-radio-button value="cardNo">卡号</el-radio-button>
            <el-radio-button value="bundleNo">捆包号</el-radio-button>
            <el-radio-button value="resNo">资源号</el-radio-button>
            <el-radio-button value="contractNo">合同号</el-radio-button>
          </el-radio-group>
        </div>
        <div class="search-input">
          <el-input
            v-model="searchKeyword"
            :placeholder="searchPlaceholder"
            size="large"
            clearable
            style="width: 500px"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
            <template #append>
              <el-button type="primary" @click="handleSearch" :loading="searching">
                <el-icon><Search /></el-icon>追溯查询
              </el-button>
            </template>
          </el-input>
        </div>
      </div>
    </el-card>

    <!-- Results Section -->
    <template v-if="traceResult">
      <el-row :gutter="16">
        <!-- Current Item Info -->
        <el-col :span="10">
          <el-card class="info-card" shadow="never">
            <template #header>
              <div class="card-header">
                <span class="card-title">物料信息</span>
                <el-tag :type="traceResult.statusType" size="small" effect="light" round>{{ traceResult.statusText }}</el-tag>
              </div>
            </template>
            <el-descriptions :column="2" size="small" border>
              <el-descriptions-item label="条码/编号">{{ traceResult.barcode }}</el-descriptions-item>
              <el-descriptions-item label="规格">{{ traceResult.spec }}</el-descriptions-item>
              <el-descriptions-item label="材质">
                <span class="grade-cell">{{ traceResult.grade }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="产地">{{ traceResult.origin }}</el-descriptions-item>
              <el-descriptions-item label="重量(T)">{{ traceResult.weight }}</el-descriptions-item>
              <el-descriptions-item label="长度(mm)">{{ traceResult.length || '—' }}</el-descriptions-item>
              <el-descriptions-item label="合同号">{{ traceResult.contractNo }}</el-descriptions-item>
              <el-descriptions-item label="卡号">{{ traceResult.cardNo }}</el-descriptions-item>
              <el-descriptions-item label="资源号">{{ traceResult.resNo }}</el-descriptions-item>
              <el-descriptions-item label="仓库/库位">{{ traceResult.location }}</el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>

        <!-- Trace Timeline -->
        <el-col :span="14">
          <el-card class="timeline-card" shadow="never">
            <template #header>
              <span class="card-title">追溯链</span>
            </template>
            <el-timeline>
              <el-timeline-item
                v-for="step in traceTimeline"
                :key="step.id"
                :color="timelineColor(step.status)"
                :hollow="step.status === 'future'"
                :timestamp="step.time"
                placement="top"
                size="large"
              >
                <div class="timeline-content" :class="step.status">
                  <div class="timeline-header">
                    <span class="step-name">{{ step.processName }}</span>
                    <el-tag :type="stepTagType(step.status)" size="small" effect="light" round>{{ stepStatusLabel(step.status) }}</el-tag>
                  </div>
                  <div class="timeline-details">
                    <div class="detail-row" v-if="step.material">
                      <span class="detail-label">物料：</span>
                      <span>{{ step.material }}</span>
                    </div>
                    <div class="detail-row" v-if="step.weight">
                      <span class="detail-label">重量：</span>
                      <span>{{ step.weight }}T</span>
                    </div>
                    <div class="detail-row" v-if="step.scheduleNo">
                      <span class="detail-label">排产号：</span>
                      <span>{{ step.scheduleNo }}</span>
                    </div>
                    <div class="detail-row" v-if="step.operator">
                      <span class="detail-label">操作员：</span>
                      <span>{{ step.operator }}</span>
                    </div>
                    <div class="detail-row" v-if="step.remark">
                      <span class="detail-label">备注：</span>
                      <span>{{ step.remark }}</span>
                    </div>
                  </div>
                </div>
              </el-timeline-item>
            </el-timeline>
          </el-card>
        </el-col>
      </el-row>
    </template>

    <!-- Empty State -->
    <el-card v-else class="empty-card" shadow="never">
      <el-empty description="请输入条码/卡号/捆包号/资源号/合同号进行追溯查询">
        <template #image>
          <el-icon :size="80" color="#cbd5e1"><Search /></el-icon>
        </template>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { traceQuery, getTraceTimeline } from '@/api/trace'

interface TraceResult {
  barcode: string; spec: string; grade: string; origin: string
  weight: number; length: number; contractNo: string; cardNo: string
  resNo: string; location: string; statusText: string; statusType: string
}

interface TimelineStep {
  id: string; processName: string; status: 'completed' | 'current' | 'future'
  time: string; material: string; weight: number; scheduleNo: string
  operator: string; remark: string
}

const searchMode = ref('barcode')
const searchKeyword = ref('')
const searching = ref(false)
const traceResult = ref<TraceResult | null>(null)
const traceTimeline = ref<TimelineStep[]>([])

const searchPlaceholder = computed(() => {
  const map: Record<string, string> = {
    barcode: '请扫描或输入条码', cardNo: '请输入卡号(如: CK-2026-00451)',
    bundleNo: '请输入捆包号(如: BN-2026-001)', resNo: '请输入资源号(如: RS-BG-034521)',
    contractNo: '请输入合同号(如: HT2026-0318)',
  }
  return map[searchMode.value] || '请输入查询关键词'
})

function timelineColor(status: string) {
  const map: Record<string, string> = { completed: '#10b981', current: '#2563eb', future: '#cbd5e1' }
  return map[status] || '#cbd5e1'
}

function stepTagType(status: string) {
  const map: Record<string, string> = { completed: 'success', current: '', future: 'info' }
  return map[status] || 'info'
}

function stepStatusLabel(status: string) {
  const map: Record<string, string> = { completed: '已完成', current: '进行中', future: '待执行' }
  return map[status] || status
}

const mockResult: TraceResult = {
  barcode: 'BC-2026031901234', spec: '带钢 Q345B 4.0×305×C', grade: 'Q345B', origin: '宝钢',
  weight: 2.45, length: 6000, contractNo: 'HT2026-0318', cardNo: 'CK-2026-00460',
  resNo: 'RS-BG-034530', location: '半成品库 A-03-02', statusText: '半成品在库', statusType: 'success',
}

const mockTimeline: TimelineStep[] = [
  { id: '1', processName: '原料入库', status: 'completed', time: '2026-03-15 10:30', material: '热轧卷板 Q345B 4.0×1250×C (22.5T)', weight: 22.5, scheduleNo: '—', operator: '仓管员 陈七', remark: '宝钢来料，检验合格' },
  { id: '2', processName: '领料出库', status: 'completed', time: '2026-03-19 08:30', material: '热轧卷板 Q345B 4.0×1250×C', weight: 22.5, scheduleNo: 'SC2026-03001', operator: '张三', remark: '领料单 MI2026-0301' },
  { id: '3', processName: '纵剪分条', status: 'completed', time: '2026-03-19 09:00 ~ 12:00', material: '带钢 Q345B 4.0×305×C × 4条', weight: 21.96, scheduleNo: 'SC2026-03001', operator: '张三', remark: '纵剪线#1，模具 M-305-01，利用率 97.6%' },
  { id: '4', processName: '半成品入库', status: 'current', time: '2026-03-19 12:30', material: '带钢 Q345B 4.0×305×C', weight: 2.45, scheduleNo: 'SC2026-03001', operator: '仓管员 陈七', remark: '捆包号 BN-2026-001，库位 A-03-02' },
  { id: '5', processName: '制管', status: 'future', time: '计划 2026-03-21', material: '焊管 Q345B 4.0×Φ89×6000', weight: 2.35, scheduleNo: 'SC2026-03004', operator: '—', remark: '制管线#1，合同 HT2026-0318' },
  { id: '6', processName: '成品入库', status: 'future', time: '计划 2026-03-21', material: '焊管 Q345B 4.0×Φ89×6000', weight: 2.35, scheduleNo: '—', operator: '—', remark: '成品库A' },
]

async function handleSearch() {
  if (!searchKeyword.value.trim()) return
  searching.value = true
  try {
    const res = await traceQuery({ mode: searchMode.value, keyword: searchKeyword.value })
    traceResult.value = (res as Record<string, unknown>).item as TraceResult
    const tlRes = await getTraceTimeline(searchKeyword.value)
    traceTimeline.value = tlRes as TimelineStep[]
  } catch {
    traceResult.value = mockResult
    traceTimeline.value = mockTimeline
  } finally {
    searching.value = false
  }
}

onMounted(() => {
  searchKeyword.value = 'BC-2026031901234'
  handleSearch()
})
</script>

<style scoped lang="scss">
.trace-page {
  max-width: 1400px;
}

.search-card {
  margin-bottom: 16px;
  border-radius: 12px;
}

.search-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  padding: 12px 0;
}

.search-modes {
  display: flex;
  align-items: center;
  gap: 12px;
}

.mode-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
}

.info-card, .timeline-card {
  border-radius: 12px;
  margin-bottom: 16px;
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

.grade-cell {
  font-weight: 600;
  color: #2563eb;
}

.timeline-content {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 14px 18px;
  transition: all var(--transition-fast);

  &.completed {
    border-left: 3px solid #10b981;
  }

  &.current {
    border-left: 3px solid #2563eb;
    box-shadow: var(--shadow-sm);
    background: #f0f7ff;
  }

  &.future {
    border-left: 3px solid #e2e8f0;
    opacity: 0.7;
  }
}

.timeline-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.step-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.timeline-details {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.detail-row {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
}

.detail-label {
  color: var(--text-placeholder);
  font-weight: 500;
}

.empty-card {
  border-radius: 12px;
  padding: 40px 0;
}
</style>
