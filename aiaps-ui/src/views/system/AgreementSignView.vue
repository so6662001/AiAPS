<template>
  <div class="agreement-container">
    <div class="agreement-card">
      <div class="agreement-header">
        <h1 class="title">AiAPS 数据处理协议</h1>
        <p class="version-tag">{{ versionNo }}</p>
      </div>

      <el-card class="change-summary" shadow="never">
        <template #header>
          <span class="summary-title">变更摘要</span>
        </template>
        <p class="summary-text">{{ changeSummary || '首次签署，请阅读完整协议后确认。' }}</p>
      </el-card>

      <el-card class="agreement-content" shadow="never">
        <template #header>
          <span class="content-title">协议正文</span>
        </template>
        <div class="scrollable-text">
          <p>第一条 定义</p>
          <p>本协议中，"平台"指AiAPS智能排产系统及相关服务；"企业数据"指企业通过平台产生或上传的生产经营数据；"个人信息"指与已识别或可识别的自然人有关的各种信息。</p>
          <p>&nbsp;</p>
          <p>第二条 数据采集范围</p>
          <p>平台采集的数据包括但不限于：排产计划数据、库存数据、BOM数据、生产报工数据、用户操作行为数据。所有数据仅用于本协议约定的目的。</p>
          <p>&nbsp;</p>
          <p>第三条 数据使用目的</p>
          <p>平台采集的数据将用于以下目的（需经企业管理员授权）：算法训练优化、区域加工指数发布、行业基准对标、供需匹配服务、原料行情分析、产品功能改进、客户成功服务。</p>
          <p>&nbsp;</p>
          <p>第四条 数据安全措施</p>
          <p>平台采取加密存储、HTTPS传输、访问控制等技术措施保障数据安全。聚合数据需至少5家企业才可生成，确保不可反推至单个企业。</p>
          <p>&nbsp;</p>
          <p>第五条 企业权利</p>
          <p>企业管理员可随时查看、调整数据授权范围。企业有权申请数据导出和删除。</p>
        </div>
      </el-card>

      <el-card class="auth-scope" shadow="never">
        <template #header>
          <span class="scope-title">数据使用授权</span>
        </template>

        <div class="scope-section">
          <p class="scope-label">基础授权（使用系统必须同意）：</p>
          <el-checkbox v-model="scope.authProductImprove" disabled>产品功能改进 — 用于优化系统功能和用户体验</el-checkbox>
          <el-checkbox v-model="scope.authCustomerSuccess" disabled>客户成功服务 — 用于主动发现使用问题并提供帮助</el-checkbox>
        </div>

        <el-divider />

        <div class="scope-section">
          <p class="scope-label">可选授权（您可以选择性开启）：</p>
          <el-checkbox v-model="scope.authAlgoTraining">算法训练 — 用于优化排产/MRP/套料算法（数据脱敏）</el-checkbox>
          <el-checkbox v-model="scope.authRegionIndex">区域加工指数 — 用于发布行业趋势报告（≥5家聚合，不可反推）</el-checkbox>
          <el-checkbox v-model="scope.authBenchmark">行业基准对标 — 您的效率指标与行业平均匿名对比</el-checkbox>
          <el-checkbox v-model="scope.authSupplyMatch">供需匹配服务 — 产能空闲时接收委外加工需求推荐</el-checkbox>
          <el-checkbox v-model="scope.authPriceAnalysis">原料行情分析 — 参与区域原料价格趋势聚合</el-checkbox>
        </div>
      </el-card>

      <div class="confirm-section">
        <el-checkbox v-model="confirmed" class="confirm-checkbox">
          我已阅读并理解上述协议，代表企业同意签署
        </el-checkbox>
      </div>

      <div class="action-bar">
        <el-button type="primary" size="large" :disabled="!confirmed" :loading="signing" @click="handleSign">
          确认签署
        </el-button>
        <el-button size="large" @click="handleDownload">下载协议PDF</el-button>
        <el-button size="large" type="info" text @click="handleSkip">暂不签署</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { signAgreement } from '@/api/agreement'

const router = useRouter()
const versionNo = ref('V1.0')
const changeSummary = ref('')
const confirmed = ref(false)
const signing = ref(false)

const scope = reactive({
  authProductImprove: true,
  authCustomerSuccess: true,
  authAlgoTraining: true,
  authRegionIndex: true,
  authBenchmark: false,
  authSupplyMatch: false,
  authPriceAnalysis: false,
})

const handleSign = async () => {
  signing.value = true
  try {
    await signAgreement({
      enterpriseId: 1,
      enterpriseName: '示例企业',
      signerUserId: localStorage.getItem('username') || '',
      signerName: localStorage.getItem('username') || '',
      signerRole: 'ENTERPRISE_ADMIN',
      versionId: 1,
      agreementType: 'DATA_PROCESS',
      versionNo: versionNo.value,
      ...scope,
    })
    ElMessage.success('签署成功')
    router.push('/')
  } catch {
    ElMessage.error('签署失败，请重试')
  } finally {
    signing.value = false
  }
}

const handleDownload = () => {
  ElMessage.info('协议PDF下载功能将在OSS对接后启用')
}

const handleSkip = () => {
  ElMessageBox.confirm('暂不签署将无法使用系统，确定要离开吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    router.push('/login')
  }).catch(() => {})
}
</script>

<style scoped>
.agreement-container {
  min-height: 100vh;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  background: #f0f2f5;
  padding: 40px 16px;
}
.agreement-card {
  width: 800px;
  max-width: 100%;
}
.agreement-header {
  text-align: center;
  margin-bottom: 24px;
}
.title {
  font-size: 28px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 8px;
}
.version-tag {
  color: #64748b;
  font-size: 14px;
  margin: 0;
}
.change-summary {
  margin-bottom: 16px;
  border-radius: 12px;
}
.summary-title {
  font-weight: 600;
  color: #d97706;
}
.summary-text {
  color: #475569;
  line-height: 1.8;
  margin: 0;
}
.agreement-content {
  margin-bottom: 16px;
  border-radius: 12px;
}
.content-title {
  font-weight: 600;
  color: #334155;
}
.scrollable-text {
  max-height: 300px;
  overflow-y: auto;
  color: #475569;
  line-height: 1.8;
  font-size: 14px;
}
.auth-scope {
  margin-bottom: 24px;
  border-radius: 12px;
}
.scope-title {
  font-weight: 600;
  color: #334155;
}
.scope-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.scope-label {
  font-weight: 500;
  color: #64748b;
  font-size: 13px;
  margin: 0;
}
.confirm-section {
  margin-bottom: 24px;
  padding: 0 4px;
}
.confirm-checkbox :deep(.el-checkbox__label) {
  font-weight: 600;
  color: #1e293b;
}
.action-bar {
  display: flex;
  gap: 12px;
  justify-content: center;
  padding-bottom: 40px;
}
</style>
