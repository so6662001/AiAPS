<template>
  <el-dialog
    v-model="visible"
    title="隐私政策更新通知"
    width="520px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
    align-center
  >
    <div class="ack-body">
      <p class="ack-intro">
        AiAPS 隐私政策已更新至 <strong>{{ versionNo }}</strong> 版本。
      </p>

      <div v-if="adminSigned" class="admin-status signed">
        贵企业管理员已确认授权。
      </div>
      <div v-else class="admin-status unsigned">
        贵企业管理员尚未确认新版协议，请联系管理员确认后继续使用。
      </div>

      <div v-if="changeSummary" class="change-block">
        <p class="change-label">主要变更：</p>
        <p class="change-text">{{ changeSummary }}</p>
      </div>

      <p class="behavior-note">
        您的操作行为数据（页面访问、按钮点击等）将用于产品功能改进，不涉及业务数据内容。
      </p>

      <el-checkbox v-model="acknowledged" :disabled="!adminSigned" class="ack-checkbox">
        我已知悉
      </el-checkbox>
    </div>

    <template #footer>
      <el-button
        type="primary"
        :disabled="!acknowledged || !adminSigned"
        @click="handleConfirm"
      >
        确认
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

const props = defineProps<{
  modelValue: boolean
  versionNo?: string
  changeSummary?: string
  adminSigned?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'confirmed': []
}>()

const acknowledged = ref(false)

const visible = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit('update:modelValue', val),
})

const handleConfirm = () => {
  emit('confirmed')
  visible.value = false
}
</script>

<style scoped>
.ack-body {
  padding: 8px 0;
}
.ack-intro {
  font-size: 15px;
  color: #334155;
  margin: 0 0 16px;
  line-height: 1.6;
}
.admin-status {
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 14px;
  margin-bottom: 16px;
}
.admin-status.signed {
  background: #f0fdf4;
  color: #15803d;
  border: 1px solid #bbf7d0;
}
.admin-status.unsigned {
  background: #fef2f2;
  color: #b91c1c;
  border: 1px solid #fecaca;
}
.change-block {
  margin-bottom: 16px;
}
.change-label {
  font-weight: 600;
  font-size: 14px;
  color: #475569;
  margin: 0 0 4px;
}
.change-text {
  color: #64748b;
  font-size: 14px;
  line-height: 1.6;
  margin: 0;
}
.behavior-note {
  color: #94a3b8;
  font-size: 13px;
  margin: 0 0 16px;
  line-height: 1.6;
}
.ack-checkbox :deep(.el-checkbox__label) {
  font-weight: 600;
  color: #1e293b;
}
</style>
