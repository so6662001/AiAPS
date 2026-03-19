<template>
  <div class="dashboard-page">
    <!-- Stat Cards Row -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6" v-for="stat in statCards" :key="stat.title">
        <el-card class="stat-card" shadow="never" :style="{ borderLeftColor: stat.color }">
          <el-statistic :title="stat.title" :value="stat.value" :precision="stat.precision" :suffix="stat.suffix">
            <template #prefix>
              <el-icon :style="{ color: stat.color }"><component :is="stat.icon" /></el-icon>
            </template>
          </el-statistic>
          <div class="stat-footer">
            <span class="stat-trend" :class="stat.trendUp ? 'up' : 'down'">
              {{ stat.trendUp ? '↑' : '↓' }} {{ stat.trendValue }}
            </span>
            <span class="stat-label">较昨日</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Middle Row -->
    <el-row :gutter="16" class="middle-row">
      <el-col :span="14">
        <el-card class="section-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">各产线实时状态</span>
              <el-button text type="primary" size="small">查看全部</el-button>
            </div>
          </template>
          <el-table :data="productionLines" stripe :header-cell-style="{ background: '#f8fafc' }" style="border-radius: 8px">
            <el-table-column prop="name" label="产线名称" min-width="120" />
            <el-table-column label="负荷率" min-width="180">
              <template #default="{ row }">
                <div class="load-cell">
                  <el-progress
                    :percentage="row.loadRate"
                    :color="getLoadColor(row.loadRate)"
                    :stroke-width="8"
                    :show-text="false"
                    style="flex: 1"
                  />
                  <span class="load-text">{{ row.loadRate }}%</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="currentTask" label="当前任务" min-width="140" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag
                  :type="statusTagType(row.status)"
                  size="small"
                  effect="light"
                  round
                >
                  {{ statusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card class="section-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">产能负荷热力图</span>
              <el-radio-group v-model="heatmapRange" size="small">
                <el-radio-button value="week">本周</el-radio-button>
                <el-radio-button value="month">本月</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <!-- ECharts integration point -->
          <div id="heatmap-container" class="chart-placeholder">
            <div class="placeholder-inner">
              <el-icon :size="48" color="#cbd5e1"><TrendCharts /></el-icon>
              <p>ECharts 热力图渲染区域</p>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Bottom Row: Urgent Items -->
    <el-card class="section-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">紧急事项</span>
          <el-badge :value="urgentItems.length" type="danger" />
        </div>
      </template>
      <div class="urgent-list">
        <div
          v-for="item in urgentItems"
          :key="item.id"
          class="urgent-item"
        >
          <span class="urgent-dot" :class="item.level" />
          <div class="urgent-content">
            <span class="urgent-title">{{ item.title }}</span>
            <span class="urgent-time">{{ item.time }}</span>
          </div>
          <el-button text type="primary" size="small">处理</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { TrendCharts, Goods, Timer, CircleCheck, ShoppingCart } from '@element-plus/icons-vue'
import { getDashboardStats, getProductionLineStatus, getUrgentItems } from '@/api/dashboard'

const heatmapRange = ref('week')

const statCards = ref([
  { title: '今日产量', value: 1286, suffix: 'T', precision: 0, color: '#2563eb', icon: Goods, trendUp: true, trendValue: '12.5%' },
  { title: '本月累计', value: 28450, suffix: 'T', precision: 0, color: '#10b981', icon: Timer, trendUp: true, trendValue: '8.3%' },
  { title: '交期达成率', value: 96.8, suffix: '%', precision: 1, color: '#f59e0b', icon: CircleCheck, trendUp: false, trendValue: '0.5%' },
  { title: '在线订单', value: 342, suffix: '单', precision: 0, color: '#6366f1', icon: ShoppingCart, trendUp: true, trendValue: '15' },
])

const productionLines = ref([
  { id: '1', name: '纵剪线 #1', loadRate: 92, currentTask: 'Q345B-4.0×1250 分剪', status: 'running' },
  { id: '2', name: '纵剪线 #2', loadRate: 78, currentTask: 'SPHC-3.0×1500 分剪', status: 'running' },
  { id: '3', name: '横剪线 #1', loadRate: 45, currentTask: 'SS400-6.0×1800 横切', status: 'running' },
  { id: '4', name: '制管线 #1', loadRate: 88, currentTask: 'Q235B-2.5×Φ89 制管', status: 'running' },
  { id: '5', name: '制管线 #2', loadRate: 0, currentTask: '—', status: 'maintenance' },
  { id: '6', name: '矫平线 #1', loadRate: 65, currentTask: 'DC01-1.2×1250 矫平', status: 'running' },
])

const urgentItems = ref([
  { id: '1', title: '合同 HT2026-0318 交期预警：Q345B 4.0×1250×C 剩余 48T 未排产，距交期仅剩3天', level: 'high', time: '10分钟前' },
  { id: '2', title: '纵剪线#2 模具磨损预警：已累计剪切 1,200T，建议更换', level: 'high', time: '25分钟前' },
  { id: '3', title: '库存预警：SPHC-2.0×1250 安全库存不足，当前可用 15T / 安全库存 50T', level: 'medium', time: '1小时前' },
  { id: '4', title: '制管线#2 计划停机维护提醒，预计恢复时间 2026-03-20 08:00', level: 'medium', time: '2小时前' },
  { id: '5', title: 'MRP运行异常：3条需求无法满足交期要求，请检查产能', level: 'high', time: '3小时前' },
])

function getLoadColor(rate: number) {
  if (rate >= 90) return '#ef4444'
  if (rate >= 70) return '#f59e0b'
  return '#10b981'
}

function statusTagType(status: string) {
  const map: Record<string, string> = { running: 'success', idle: 'info', maintenance: 'warning', error: 'danger' }
  return map[status] || 'info'
}

function statusLabel(status: string) {
  const map: Record<string, string> = { running: '运行中', idle: '空闲', maintenance: '维护', error: '故障' }
  return map[status] || status
}

onMounted(async () => {
  try {
    const [statsRes, linesRes, urgentRes] = await Promise.all([
      getDashboardStats(),
      getProductionLineStatus(),
      getUrgentItems(),
    ])
    if (statsRes) Object.assign(statCards.value, statsRes)
    if (linesRes) productionLines.value = linesRes as typeof productionLines.value
    if (urgentRes) urgentItems.value = urgentRes as typeof urgentItems.value
  } catch {
    /* use mock data */
  }
})
</script>

<style scoped lang="scss">
.dashboard-page {
  max-width: 1600px;
}

.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  border-radius: 12px;
  border-left: 4px solid;
  transition: box-shadow var(--transition-fast);

  &:hover {
    box-shadow: var(--shadow-md);
  }

  :deep(.el-statistic__head) {
    font-size: 13px;
    color: var(--text-secondary);
  }

  :deep(.el-statistic__content) {
    font-size: 28px;
    font-weight: 700;
    color: var(--text-primary);
  }
}

.stat-footer {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.stat-trend {
  font-weight: 600;
  &.up { color: #10b981; }
  &.down { color: #ef4444; }
}

.stat-label {
  color: var(--text-placeholder);
}

.middle-row {
  margin-bottom: 16px;
}

.section-card {
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

.load-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.load-text {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  min-width: 36px;
  text-align: right;
}

.chart-placeholder {
  height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px dashed #e2e8f0;
}

.placeholder-inner {
  text-align: center;
  color: #94a3b8;

  p {
    margin-top: 12px;
    font-size: 13px;
  }
}

.urgent-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.urgent-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border-color-light);

  &:last-child {
    border-bottom: none;
  }
}

.urgent-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;

  &.high { background: #ef4444; box-shadow: 0 0 6px rgba(239, 68, 68, 0.4); }
  &.medium { background: #f59e0b; box-shadow: 0 0 6px rgba(245, 158, 11, 0.4); }
}

.urgent-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.urgent-title {
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.5;
}

.urgent-time {
  font-size: 12px;
  color: var(--text-placeholder);
}
</style>
