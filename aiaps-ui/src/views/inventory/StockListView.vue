<template>
  <div class="page-container">
    <!-- Filters -->
    <el-card class="filter-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="品类">
          <el-select v-model="queryParams.category" placeholder="全部品类" clearable style="width: 130px">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="材质">
          <el-select v-model="queryParams.PATName" placeholder="全部材质" clearable style="width: 110px">
            <el-option v-for="g in gradeOptions" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item label="产地">
          <el-select v-model="queryParams.PAName" placeholder="全部产地" clearable style="width: 110px">
            <el-option v-for="o in originOptions" :key="o" :label="o" :value="o" />
          </el-select>
        </el-form-item>
        <el-form-item label="合同号">
          <el-input v-model="queryParams.contractNo" placeholder="合同号" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="仓库">
          <el-select v-model="queryParams.warehouse" placeholder="全部仓库" clearable style="width: 130px">
            <el-option v-for="w in warehouses" :key="w" :label="w" :value="w" />
          </el-select>
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
          <span class="card-title">库存查询</span>
          <el-button size="small" @click="handleExport"><el-icon><Download /></el-icon>导出</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" stripe highlight-current-row style="border-radius: 8px" @row-click="handleRowClick">
        <el-table-column prop="materialSpec" label="物料/规格" min-width="200" show-overflow-tooltip />
        <el-table-column prop="PATName" label="材质" width="90">
          <template #default="{ row }"><span class="grade-cell">{{ row.PATName }}</span></template>
        </el-table-column>
        <el-table-column prop="PAName" label="产地" width="80" />
        <el-table-column prop="length" label="长度(mm)" width="100" align="right" />
        <el-table-column prop="CardNo" label="卡号" width="120" />
        <el-table-column prop="ResNo" label="资源号" width="130" />
        <el-table-column prop="stockQty" label="在库量" width="80" align="right" />
        <el-table-column prop="stockWeight" label="在库重量(T)" width="110" align="right">
          <template #default="{ row }"><span class="weight-cell">{{ row.stockWeight }}</span></template>
        </el-table-column>
        <el-table-column prop="reservedQty" label="已预留" width="80" align="right" />
        <el-table-column prop="availableQty" label="可用量" width="80" align="right">
          <template #default="{ row }">
            <span :class="{ 'low-stock': row.availableQty < 5 }">{{ row.availableQty }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="contractNo" label="合同号" width="130" />
        <el-table-column prop="warehouse" label="仓库" width="100" />
        <el-table-column prop="CardRemark" label="备注1" min-width="120" show-overflow-tooltip />
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next, jumper" background @size-change="fetchData" @current-change="fetchData" />
      </div>
    </el-card>

    <!-- Binds Drawer -->
    <el-drawer v-model="drawerVisible" title="捆包列表" size="45%">
      <template v-if="selectedStock">
        <el-descriptions :column="2" border size="small" class="bind-desc">
          <el-descriptions-item label="物料/规格">{{ selectedStock.materialSpec }}</el-descriptions-item>
          <el-descriptions-item label="材质">{{ selectedStock.PATName }}</el-descriptions-item>
          <el-descriptions-item label="卡号">{{ selectedStock.CardNo }}</el-descriptions-item>
          <el-descriptions-item label="资源号">{{ selectedStock.ResNo }}</el-descriptions-item>
        </el-descriptions>
        <h4 class="section-title">捆包明细</h4>
        <el-table :data="bindList" border size="small" style="border-radius: 6px" v-loading="bindLoading">
          <el-table-column prop="bundleNo" label="捆包号" min-width="140" />
          <el-table-column prop="weight" label="重量(T)" width="100" align="right" />
          <el-table-column prop="pcs" label="支数" width="70" align="right" />
          <el-table-column prop="length" label="长度(mm)" width="100" align="right" />
          <el-table-column prop="location" label="库位" width="100" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === '在库' ? 'success' : 'warning'" size="small" effect="light" round>{{ row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Download } from '@element-plus/icons-vue'
import { getStockList, getStockBinds } from '@/api/inventory'

interface StockItem {
  id: string; materialSpec: string; PATName: string; PAName: string
  length: number; CardNo: string; ResNo: string; stockQty: number
  stockWeight: number; reservedQty: number; availableQty: number
  contractNo: string; warehouse: string; CardRemark: string
}

interface BindItem {
  bundleNo: string; weight: number; pcs: number
  length: number; location: string; status: string
}

const categories = ['热轧卷', '冷轧卷', '中厚板', '镀锌板', '带钢', '焊管']
const gradeOptions = ['Q235B', 'Q345B', 'SPHC', 'SPCC', 'DC01', 'DX51D', 'SS400']
const originOptions = ['宝钢', '鞍钢', '首钢', '马钢', '河钢', '日钢', '沙钢']
const warehouses = ['原料库A', '原料库B', '成品库A', '成品库B', '半成品库']

const loading = ref(false)
const bindLoading = ref(false)
const drawerVisible = ref(false)
const selectedStock = ref<StockItem | null>(null)
const bindList = ref<BindItem[]>([])
const queryParams = reactive({ category: '', PATName: '', PAName: '', contractNo: '', warehouse: '' })
const pagination = reactive({ page: 1, pageSize: 50, total: 0 })

const tableData = ref<StockItem[]>([
  { id: '1', materialSpec: '热轧卷板 Q345B 4.0×1250×C', PATName: 'Q345B', PAName: '宝钢', length: 0, CardNo: 'CK-2026-00451', ResNo: 'RS-BG-034521', stockQty: 3, stockWeight: 67.5, reservedQty: 2, availableQty: 1, contractNo: '—', warehouse: '原料库A', CardRemark: '热轧一级品' },
  { id: '2', materialSpec: '热轧卷板 SPHC 3.0×1500×C', PATName: 'SPHC', PAName: '日钢', length: 0, CardNo: 'CK-2026-00452', ResNo: 'RS-RG-078912', stockQty: 1, stockWeight: 15.0, reservedQty: 0, availableQty: 1, contractNo: '—', warehouse: '原料库A', CardRemark: '' },
  { id: '3', materialSpec: '带钢 Q345B 4.0×305×C', PATName: 'Q345B', PAName: '宝钢', length: 6000, CardNo: 'CK-2026-00460', ResNo: 'RS-BG-034530', stockQty: 50, stockWeight: 12.3, reservedQty: 20, availableQty: 30, contractNo: 'HT2026-0318', warehouse: '半成品库', CardRemark: '纵剪成品' },
  { id: '4', materialSpec: '焊管 Q345B 4.0×Φ89×6000', PATName: 'Q345B', PAName: '鞍钢', length: 6000, CardNo: 'CK-2026-00470', ResNo: 'RS-AG-015678', stockQty: 80, stockWeight: 28.8, reservedQty: 0, availableQty: 80, contractNo: 'HT2026-0318', warehouse: '成品库A', CardRemark: '一等品' },
  { id: '5', materialSpec: '冷轧卷板 SPCC 1.2×1250×C', PATName: 'SPCC', PAName: '宝钢', length: 0, CardNo: 'CK-2026-00480', ResNo: 'RS-BG-034540', stockQty: 2, stockWeight: 24.0, reservedQty: 1, availableQty: 1, contractNo: '—', warehouse: '原料库B', CardRemark: '' },
  { id: '6', materialSpec: '带钢 SPHC 2.0×125×C', PATName: 'SPHC', PAName: '首钢', length: 0, CardNo: 'CK-2026-00485', ResNo: 'RS-SG-098765', stockQty: 100, stockWeight: 15.0, reservedQty: 60, availableQty: 40, contractNo: '—', warehouse: '半成品库', CardRemark: '安全库存物料' },
])

const mockBinds: BindItem[] = [
  { bundleNo: 'BN-2026-001', weight: 2.45, pcs: 10, length: 6000, location: 'A-03-02', status: '在库' },
  { bundleNo: 'BN-2026-002', weight: 2.45, pcs: 10, length: 6000, location: 'A-03-03', status: '在库' },
  { bundleNo: 'BN-2026-003', weight: 2.45, pcs: 10, length: 6000, location: 'A-03-04', status: '预留' },
  { bundleNo: 'BN-2026-004', weight: 2.45, pcs: 10, length: 6000, location: 'A-03-05', status: '在库' },
  { bundleNo: 'BN-2026-005', weight: 2.50, pcs: 10, length: 6000, location: 'A-04-01', status: '预留' },
]

async function handleRowClick(row: StockItem) {
  selectedStock.value = row
  drawerVisible.value = true
  bindLoading.value = true
  try {
    const res = await getStockBinds(row.id)
    bindList.value = res as BindItem[]
  } catch {
    bindList.value = mockBinds
  } finally {
    bindLoading.value = false
  }
}

function handleSearch() { pagination.page = 1; fetchData() }
function handleReset() { Object.assign(queryParams, { category: '', PATName: '', PAName: '', contractNo: '', warehouse: '' }); handleSearch() }
function handleExport() { ElMessage.info('导出功能待实现') }

async function fetchData() {
  loading.value = true
  try {
    const res = await getStockList({ ...queryParams, page: pagination.page, pageSize: pagination.pageSize })
    const data = res as Record<string, unknown>
    tableData.value = (data.records || []) as StockItem[]
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
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
.grade-cell { font-weight: 600; color: #2563eb; }
.weight-cell { font-weight: 600; }
.low-stock { color: #ef4444; font-weight: 600; }
.bind-desc { margin-bottom: 20px; }
.section-title { font-size: 14px; font-weight: 600; color: var(--text-primary); margin-bottom: 12px; }
</style>
