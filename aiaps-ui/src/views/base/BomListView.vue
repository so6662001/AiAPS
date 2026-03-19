<template>
  <div class="page-container">
    <!-- Filter -->
    <el-card class="filter-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="产品编号">
          <el-input v-model="queryParams.productNo" placeholder="请输入产品编号" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="产品名称">
          <el-input v-model="queryParams.productName" placeholder="请输入产品名称" clearable style="width: 180px" />
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

    <!-- BOM List Table -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">BOM列表</span>
          <el-button type="primary" size="small" @click="handleAdd"><el-icon><Plus /></el-icon>新增BOM</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" stripe row-key="id" style="border-radius: 8px" @expand-change="handleExpand">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-content">
              <h4 class="expand-title">BOM明细 — {{ row.productName }}</h4>
              <el-table :data="row.children || []" border size="small" style="border-radius: 6px">
                <el-table-column prop="seq" label="序号" width="60" align="center" />
                <el-table-column prop="materialNo" label="子件编号" min-width="120" />
                <el-table-column prop="materialName" label="子件名称" min-width="160" />
                <el-table-column prop="spec" label="规格" min-width="140" />
                <el-table-column prop="qty" label="用量" width="80" align="right" />
                <el-table-column prop="unit" label="单位" width="70" align="center" />
                <el-table-column prop="lossRate" label="损耗率(%)" width="100" align="right" />
                <el-table-column prop="process" label="工序" width="100" />
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="bomNo" label="BOM编号" min-width="130" />
        <el-table-column prop="productNo" label="产品编号" min-width="120" />
        <el-table-column prop="productName" label="产品名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="version" label="版本" width="80" align="center" />
        <el-table-column prop="componentCount" label="子件数" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small" effect="light" round>
              {{ row.status === 'active' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="160" />
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
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { getBomList, getBomTree, deleteBom } from '@/api/bom'

interface BomChild {
  seq: number
  materialNo: string
  materialName: string
  spec: string
  qty: number
  unit: string
  lossRate: number
  process: string
}

interface BomItem {
  id: string
  bomNo: string
  productNo: string
  productName: string
  version: string
  componentCount: number
  status: string
  updateTime: string
  children?: BomChild[]
}

const loading = ref(false)
const queryParams = reactive({ productNo: '', productName: '', status: '' })
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })

const tableData = ref<BomItem[]>([
  {
    id: '1', bomNo: 'BOM-2026001', productNo: 'M20260006', productName: '焊管 Q235B 2.5×Φ89×6000',
    version: 'V1.0', componentCount: 3, status: 'active', updateTime: '2026-03-15 10:30',
    children: [
      { seq: 1, materialNo: 'M20260007', materialName: '带钢 SPHC 3.0×305', spec: '3.0×305×C', qty: 1.05, unit: 'T', lossRate: 3.0, process: '分剪' },
      { seq: 2, materialNo: 'M20260001', materialName: '热轧卷板 Q345B', spec: '4.0×1250×C', qty: 1.0, unit: 'T', lossRate: 2.5, process: '开卷' },
      { seq: 3, materialNo: 'AUX-001', materialName: '焊丝 ER50-6', spec: 'Φ1.2', qty: 0.02, unit: 'kg', lossRate: 5.0, process: '制管' },
    ],
  },
  {
    id: '2', bomNo: 'BOM-2026002', productNo: 'M20260007', productName: '带钢 SPHC 3.0×305',
    version: 'V1.0', componentCount: 1, status: 'active', updateTime: '2026-03-14 14:20',
    children: [
      { seq: 1, materialNo: 'M20260002', materialName: '热轧卷板 Q235B', spec: '3.0×1500×C', qty: 1.02, unit: 'T', lossRate: 2.0, process: '纵剪' },
    ],
  },
  {
    id: '3', bomNo: 'BOM-2026003', productNo: 'P-PLATE-001', productName: '中厚板成品 Q345B 12×2000×6000',
    version: 'V2.0', componentCount: 1, status: 'active', updateTime: '2026-03-12 09:00',
    children: [
      { seq: 1, materialNo: 'M20260005', materialName: '中厚板 Q345B', spec: '12.0×2000×6000', qty: 1.01, unit: 'T', lossRate: 1.0, process: '矫平' },
    ],
  },
])

function handleSearch() {
  pagination.page = 1
  fetchData()
}

function handleReset() {
  Object.assign(queryParams, { productNo: '', productName: '', status: '' })
  handleSearch()
}

async function handleExpand(row: BomItem, expanded: BomItem[]) {
  if (expanded.includes(row) && !row.children?.length) {
    try {
      const res = await getBomTree(row.id)
      row.children = res as BomChild[]
    } catch {
      /* use pre-loaded mock children */
    }
  }
}

function handleAdd() {
  ElMessage.info('新增BOM功能待实现')
}

function handleEdit(_row: BomItem) {
  ElMessage.info('编辑BOM功能待实现')
}

async function handleDelete(row: BomItem) {
  await ElMessageBox.confirm(`确认删除BOM "${row.bomNo}" 吗？`, '提示', { type: 'warning' })
  try {
    await deleteBom(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    ElMessage.error('删除失败')
  }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getBomList({ ...queryParams, page: pagination.page, pageSize: pagination.pageSize })
    const data = res as Record<string, unknown>
    tableData.value = (data.records || []) as BomItem[]
    pagination.total = (data.total || 0) as number
  } catch {
    pagination.total = tableData.value.length
  } finally {
    loading.value = false
  }
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
.expand-content { padding: 12px 48px 12px 64px; }
.expand-title { font-size: 13px; font-weight: 600; color: var(--text-secondary); margin-bottom: 10px; }
</style>
