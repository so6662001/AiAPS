<template>
  <div class="page-container">
    <!-- Search Bar -->
    <el-card class="filter-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="关键词">
          <el-input v-model="queryParams.keyword" placeholder="物料编号/名称" clearable style="width: 200px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="品类">
          <el-select v-model="queryParams.category" placeholder="全部品类" clearable style="width: 160px">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 120px">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>查询
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Data Table -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">物料列表</span>
          <el-button type="primary" size="small" @click="handleAdd">
            <el-icon><Plus /></el-icon>新增物料
          </el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" stripe style="border-radius: 8px" @sort-change="handleSortChange">
        <el-table-column prop="PrdtNo" label="物料编号" min-width="140" sortable="custom" />
        <el-table-column prop="PrdtName" label="物料名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="category" label="品类" width="100" />
        <el-table-column prop="spec_desc" label="规格" min-width="180" show-overflow-tooltip />
        <el-table-column prop="thickness" label="厚度(mm)" width="100" align="right" />
        <el-table-column prop="width" label="宽度(mm)" width="100" align="right" />
        <el-table-column prop="unit" label="计量单位" width="90" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small" effect="light" round>
              {{ row.status === 'active' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button text type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <!-- Add/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑物料' : '新增物料'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="物料编号" prop="PrdtNo">
              <el-input v-model="formData.PrdtNo" placeholder="请输入物料编号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料名称" prop="PrdtName">
              <el-input v-model="formData.PrdtName" placeholder="请输入物料名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="品类" prop="category">
              <el-select v-model="formData.category" placeholder="请选择品类" style="width: 100%">
                <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计量单位" prop="unit">
              <el-select v-model="formData.unit" placeholder="请选择" style="width: 100%">
                <el-option label="吨(T)" value="T" />
                <el-option label="千克(kg)" value="kg" />
                <el-option label="米(m)" value="m" />
                <el-option label="根" value="根" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="规格" prop="spec_desc">
              <el-input v-model="formData.spec_desc" placeholder="如: 4.0×1250×C" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="厚度(mm)">
              <el-input-number v-model="formData.thickness" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="宽度(mm)">
              <el-input-number v-model="formData.width" :min="0" :precision="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch v-model="formData.status" active-value="active" inactive-value="inactive" active-text="启用" inactive-text="停用" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { getMaterialList, createMaterial, updateMaterial, deleteMaterial } from '@/api/material'

interface MaterialItem {
  id: string
  PrdtNo: string
  PrdtName: string
  category: string
  spec_desc: string
  thickness: number
  width: number
  unit: string
  status: string
}

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const editingId = ref('')
const formRef = ref<FormInstance>()

const categories = ['热轧卷', '冷轧卷', '中厚板', '镀锌板', '彩涂板', '焊管', '无缝管', '带钢']

const queryParams = reactive({ keyword: '', category: '', status: '' })
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })

const formData = reactive({
  PrdtNo: '', PrdtName: '', category: '', spec_desc: '',
  thickness: 0, width: 0, unit: 'T', status: 'active' as string,
})

const formRules = {
  PrdtNo: [{ required: true, message: '请输入物料编号', trigger: 'blur' }],
  PrdtName: [{ required: true, message: '请输入物料名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择品类', trigger: 'change' }],
}

const tableData = ref<MaterialItem[]>([
  { id: '1', PrdtNo: 'M20260001', PrdtName: '热轧卷板 Q345B', category: '热轧卷', spec_desc: '4.0×1250×C', thickness: 4.0, width: 1250, unit: 'T', status: 'active' },
  { id: '2', PrdtNo: 'M20260002', PrdtName: '热轧卷板 Q235B', category: '热轧卷', spec_desc: '3.0×1500×C', thickness: 3.0, width: 1500, unit: 'T', status: 'active' },
  { id: '3', PrdtNo: 'M20260003', PrdtName: '冷轧卷板 SPCC', category: '冷轧卷', spec_desc: '1.2×1250×C', thickness: 1.2, width: 1250, unit: 'T', status: 'active' },
  { id: '4', PrdtNo: 'M20260004', PrdtName: '镀锌板 DX51D', category: '镀锌板', spec_desc: '0.8×1000×C', thickness: 0.8, width: 1000, unit: 'T', status: 'active' },
  { id: '5', PrdtNo: 'M20260005', PrdtName: '中厚板 Q345B', category: '中厚板', spec_desc: '12.0×2000×6000', thickness: 12.0, width: 2000, unit: 'T', status: 'inactive' },
  { id: '6', PrdtNo: 'M20260006', PrdtName: '焊管 Q235B', category: '焊管', spec_desc: '2.5×Φ89×6000', thickness: 2.5, width: 89, unit: '根', status: 'active' },
  { id: '7', PrdtNo: 'M20260007', PrdtName: '带钢 SPHC', category: '带钢', spec_desc: '3.0×305×C', thickness: 3.0, width: 305, unit: 'T', status: 'active' },
  { id: '8', PrdtNo: 'M20260008', PrdtName: '热轧卷板 SS400', category: '热轧卷', spec_desc: '6.0×1800×C', thickness: 6.0, width: 1800, unit: 'T', status: 'active' },
])

function handleSearch() {
  pagination.page = 1
  fetchData()
}

function handleReset() {
  Object.assign(queryParams, { keyword: '', category: '', status: '' })
  handleSearch()
}

function handleSortChange(_sort: Record<string, unknown>) {
  fetchData()
}

function handleAdd() {
  editingId.value = ''
  Object.assign(formData, { PrdtNo: '', PrdtName: '', category: '', spec_desc: '', thickness: 0, width: 0, unit: 'T', status: 'active' })
  dialogVisible.value = true
}

function handleEdit(row: MaterialItem) {
  editingId.value = row.id
  Object.assign(formData, row)
  dialogVisible.value = true
}

async function handleDelete(row: MaterialItem) {
  await ElMessageBox.confirm(`确认删除物料 "${row.PrdtNo}" 吗？`, '提示', { type: 'warning' })
  try {
    await deleteMaterial(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    ElMessage.error('删除失败')
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  submitting.value = true
  try {
    if (editingId.value) {
      await updateMaterial(editingId.value, { ...formData })
    } else {
      await createMaterial({ ...formData })
    }
    ElMessage.success(editingId.value ? '更新成功' : '创建成功')
    dialogVisible.value = false
    fetchData()
  } catch {
    ElMessage.error('操作失败')
  } finally {
    submitting.value = false
  }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getMaterialList({ ...queryParams, page: pagination.page, pageSize: pagination.pageSize })
    const data = res as Record<string, unknown>
    tableData.value = (data.records || []) as MaterialItem[]
    pagination.total = (data.total || 0) as number
  } catch {
    pagination.total = tableData.value.length
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchData()
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

.table-card {
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

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
