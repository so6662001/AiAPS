<template>
  <div class="page-container">
    <el-card class="filter-card" shadow="never">
      <el-form inline>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 280px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-tabs v-model="activeTab" class="analytics-tabs" @tab-change="handleTabChange">
      <!-- A1: Page Heat -->
      <el-tab-pane label="页面热力" name="pageHeat">
        <el-table :data="pageHeatData" stripe style="border-radius: 8px">
          <el-table-column prop="pagePath" label="页面路径" min-width="200" show-overflow-tooltip />
          <el-table-column prop="pageName" label="页面名称" width="140" />
          <el-table-column prop="pv" label="PV" width="100" align="right" sortable />
          <el-table-column prop="uv" label="UV" width="100" align="right" sortable />
          <el-table-column prop="avgDurationSeconds" label="平均停留(s)" width="130" align="right" sortable />
        </el-table>
      </el-tab-pane>

      <!-- A2: Feature Value -->
      <el-tab-pane label="功能价值" name="featureValue">
        <el-table :data="featureValueData" stripe style="border-radius: 8px">
          <el-table-column prop="pagePath" label="页面路径" min-width="200" show-overflow-tooltip />
          <el-table-column prop="pv" label="PV" width="90" align="right" />
          <el-table-column prop="avgDurationSeconds" label="停留(s)" width="100" align="right" />
          <el-table-column prop="actionCount" label="操作数" width="100" align="right" />
          <el-table-column prop="conversionRate" label="转化率(%)" width="110" align="right" />
          <el-table-column prop="userCoverage" label="覆盖率(%)" width="110" align="right" />
          <el-table-column prop="score" label="价值得分" width="110" align="right" sortable />
        </el-table>
      </el-tab-pane>

      <!-- A3: User Activity -->
      <el-tab-pane label="用户活跃" name="userActivity">
        <el-row :gutter="16" class="stat-row">
          <el-col :span="8">
            <el-card shadow="never" class="stat-card">
              <el-statistic title="DAU (日活)" :value="userActivityData.dau || 0" />
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never" class="stat-card">
              <el-statistic title="WAU (周活)" :value="userActivityData.wau || 0" />
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never" class="stat-card">
              <el-statistic title="MAU (月活)" :value="userActivityData.mau || 0" />
            </el-card>
          </el-col>
        </el-row>
        <el-card shadow="never" style="margin-top: 16px; border-radius: 12px">
          <template #header><span class="card-title">角色分布</span></template>
          <el-table :data="roleDistributionList" stripe style="border-radius: 8px">
            <el-table-column prop="role" label="角色" min-width="200" />
            <el-table-column prop="count" label="用户数" width="120" align="right" />
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- A6: API Efficiency -->
      <el-tab-pane label="接口效率" name="efficiency">
        <el-table :data="efficiencyData" stripe style="border-radius: 8px">
          <el-table-column prop="apiPath" label="接口路径" min-width="250" show-overflow-tooltip />
          <el-table-column prop="callCount" label="调用次数" width="110" align="right" sortable />
          <el-table-column prop="avgResponseMs" label="平均响应(ms)" width="140" align="right" sortable />
          <el-table-column prop="p95ResponseMs" label="P95响应(ms)" width="140" align="right" sortable>
            <template #default="{ row }">
              <span :class="{ 'text-danger': row.p95ResponseMs > 2000, 'text-warning': row.p95ResponseMs > 1000 }">
                {{ row.p95ResponseMs }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="errorCount" label="错误数" width="100" align="right" />
          <el-table-column prop="errorRate" label="错误率(%)" width="110" align="right" />
        </el-table>
      </el-tab-pane>

      <!-- Performance Overview -->
      <el-tab-pane label="性能概览" name="perfOverview">
        <el-row :gutter="16" class="stat-row">
          <el-col :span="6">
            <el-card shadow="never" class="stat-card">
              <el-statistic title="页面健康率" :value="perfOverview.pageHealthPct || 0" suffix="%" />
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="never" class="stat-card">
              <el-statistic title="API健康率" :value="perfOverview.apiHealthPct || 0" suffix="%" />
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="never" class="stat-card">
              <el-statistic title="页面样本数" :value="perfOverview.totalPageSamples || 0" />
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="never" class="stat-card">
              <el-statistic title="API样本数" :value="perfOverview.totalApiSamples || 0" />
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- Recent Alerts -->
      <el-tab-pane label="最近告警" name="recentAlerts">
        <el-table :data="recentAlertsData" stripe style="border-radius: 8px">
          <el-table-column prop="recordTime" label="时间" width="180" />
          <el-table-column label="级别" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.alertLevel === 'CRITICAL' ? 'danger' : 'warning'" size="small" round>
                {{ row.alertLevel }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sourceType" label="类型" width="100" />
          <el-table-column label="目标" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.pagePath || row.apiPath || '-' }}</template>
          </el-table-column>
          <el-table-column label="指标值" width="130" align="right">
            <template #default="{ row }">
              {{ row.sourceType === 'PAGE' ? (row.fcpMs + 'ms') : (row.responseMs + 'ms') }}
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- Alert Rules -->
      <el-tab-pane label="告警规则" name="alertRules">
        <div style="margin-bottom: 12px; text-align: right">
          <el-button type="primary" size="small" @click="showRuleDialog = true">新增规则</el-button>
        </div>
        <el-table :data="alertRulesData" stripe style="border-radius: 8px">
          <el-table-column prop="ruleName" label="规则名称" min-width="160" />
          <el-table-column prop="ruleType" label="规则类型" width="120" />
          <el-table-column prop="matchPath" label="匹配路径" min-width="180" show-overflow-tooltip />
          <el-table-column prop="warningThreshold" label="警告阈值" width="100" align="right" />
          <el-table-column prop="criticalThreshold" label="严重阈值" width="100" align="right" />
          <el-table-column prop="thresholdUnit" label="单位" width="80" />
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.isActive ? 'success' : 'info'" size="small">{{ row.isActive ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="notifyWecom" label="企微通知" width="100" align="center">
            <template #default="{ row }">{{ row.notifyWecom ? '是' : '否' }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- Overview Stats -->
      <el-tab-pane label="综合报表" name="summary">
        <el-empty description="综合报表开发中" />
      </el-tab-pane>
    </el-tabs>

    <!-- New Rule Dialog -->
    <el-dialog v-model="showRuleDialog" title="新增告警规则" width="500px">
      <el-form :model="ruleForm" label-width="100px">
        <el-form-item label="规则名称">
          <el-input v-model="ruleForm.ruleName" />
        </el-form-item>
        <el-form-item label="规则类型">
          <el-select v-model="ruleForm.ruleType" style="width: 100%">
            <el-option label="页面加载" value="PAGE_LOAD" />
            <el-option label="API慢响应" value="API_SLOW" />
            <el-option label="API错误" value="API_ERROR" />
            <el-option label="交互" value="INTERACTION" />
          </el-select>
        </el-form-item>
        <el-form-item label="匹配路径">
          <el-input v-model="ruleForm.matchPath" placeholder="留空表示全局" />
        </el-form-item>
        <el-form-item label="警告阈值">
          <el-input-number v-model="ruleForm.warningThreshold" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="严重阈值">
          <el-input-number v-model="ruleForm.criticalThreshold" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="阈值单位">
          <el-select v-model="ruleForm.thresholdUnit" style="width: 100%">
            <el-option label="毫秒(MS)" value="MS" />
            <el-option label="次数(COUNT)" value="COUNT" />
            <el-option label="百分比(PCT)" value="PCT" />
          </el-select>
        </el-form-item>
        <el-form-item label="窗口(分钟)">
          <el-input-number v-model="ruleForm.windowMinutes" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="最小样本数">
          <el-input-number v-model="ruleForm.minSampleCount" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="企微通知">
          <el-switch v-model="ruleForm.notifyWecom" />
        </el-form-item>
        <el-form-item v-if="ruleForm.notifyWecom" label="Webhook URL">
          <el-input v-model="ruleForm.notifyWebhookUrl" />
        </el-form-item>
        <el-form-item label="通知间隔(分)">
          <el-input-number v-model="ruleForm.notifyIntervalMin" :min="1" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRuleDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateRule">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getPageHeat, getFeatureValue, getUserActivity,
  getEfficiency, getPerformanceOverview,
  getRecentAlerts, getAlertRules, createAlertRule,
} from '@/api/analytics'

const activeTab = ref('pageHeat')
const dateRange = ref<string[]>([])
const showRuleDialog = ref(false)

const pageHeatData = ref<any[]>([])
const featureValueData = ref<any[]>([])
const userActivityData = ref<Record<string, any>>({})
const efficiencyData = ref<any[]>([])
const perfOverview = ref<Record<string, any>>({})
const recentAlertsData = ref<any[]>([])
const alertRulesData = ref<any[]>([])

const ruleForm = reactive({
  ruleName: '',
  ruleType: 'API_SLOW',
  matchPath: '',
  warningThreshold: 2000,
  criticalThreshold: 5000,
  thresholdUnit: 'MS',
  windowMinutes: 5,
  minSampleCount: 10,
  notifyWecom: false,
  notifyWebhookUrl: '',
  notifyIntervalMin: 30,
})

const roleDistributionList = computed(() => {
  const dist = userActivityData.value.roleDistribution as Record<string, number> | undefined
  if (!dist) return []
  return Object.entries(dist).map(([role, count]) => ({ role, count }))
})

function getParams() {
  const params: Record<string, string> = {}
  if (dateRange.value && dateRange.value.length === 2) {
    params.dateFrom = dateRange.value[0]
    params.dateTo = dateRange.value[1]
  }
  return params
}

async function loadPageHeat() {
  try {
    const res = await getPageHeat(getParams())
    pageHeatData.value = (res as any).data || res || []
  } catch { /* use empty */ }
}

async function loadFeatureValue() {
  try {
    const res = await getFeatureValue(getParams())
    featureValueData.value = (res as any).data || res || []
  } catch { /* use empty */ }
}

async function loadUserActivity() {
  try {
    const res = await getUserActivity(getParams())
    userActivityData.value = (res as any).data || res || {}
  } catch { /* use empty */ }
}

async function loadEfficiency() {
  try {
    const res = await getEfficiency(getParams())
    efficiencyData.value = (res as any).data || res || []
  } catch { /* use empty */ }
}

async function loadPerfOverview() {
  try {
    const res = await getPerformanceOverview()
    perfOverview.value = (res as any).data || res || {}
  } catch { /* use empty */ }
}

async function loadRecentAlerts() {
  try {
    const res = await getRecentAlerts({ limit: 20 })
    recentAlertsData.value = (res as any).data || res || []
  } catch { /* use empty */ }
}

async function loadAlertRules() {
  try {
    const res = await getAlertRules()
    alertRulesData.value = (res as any).data || res || []
  } catch { /* use empty */ }
}

function handleTabChange(tab: string) {
  const loaders: Record<string, () => void> = {
    pageHeat: loadPageHeat,
    featureValue: loadFeatureValue,
    userActivity: loadUserActivity,
    efficiency: loadEfficiency,
    perfOverview: loadPerfOverview,
    recentAlerts: loadRecentAlerts,
    alertRules: loadAlertRules,
  }
  loaders[tab]?.()
}

function handleQuery() {
  handleTabChange(activeTab.value)
}

async function handleCreateRule() {
  try {
    await createAlertRule({ ...ruleForm, isActive: true })
    ElMessage.success('规则创建成功')
    showRuleDialog.value = false
    loadAlertRules()
  } catch {
    ElMessage.error('创建失败')
  }
}

onMounted(() => {
  loadPageHeat()
})
</script>

<style scoped lang="scss">
.page-container {
  max-width: 1600px;
}

.filter-card {
  margin-bottom: 16px;
  border-radius: 12px;

  :deep(.el-card__body) {
    padding-bottom: 2px;
  }
}

.analytics-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 16px;
  }
}

.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  border-radius: 12px;
  text-align: center;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.text-danger {
  color: #ef4444;
  font-weight: 600;
}

.text-warning {
  color: #f59e0b;
  font-weight: 600;
}
</style>
