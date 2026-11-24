<template>
  <el-popover
    v-model:visible="visible"
    trigger="click"
    placement="bottom-start"
    :width="180"
    :disabled="!editable"
    @show="syncForm"
  >
    <template #reference>
      <span class="service-editor__value" :class="{ 'is-editable': editable }">{{ displayText }}</span>
    </template>
    <div class="service-editor__panel">
      <el-checkbox v-model="form.pickupEnabled" :disabled="saving" @change="onChange">自提</el-checkbox>
      <el-checkbox v-model="form.deliveryEnabled" :disabled="saving" @change="onChange">送货</el-checkbox>
      <span v-if="saving" class="service-editor__saving">保存中...</span>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { updateTransferServices } from '@/api/system/route'
import type { TransferRecord } from '@/types/route'

const props = defineProps<{
  row: TransferRecord
  editable: boolean
}>()

const emit = defineEmits<{
  updated: [value: { pickupEnabled: boolean; deliveryEnabled: boolean }]
}>()

const visible = ref(false)
const saving = ref(false)
const form = reactive({ pickupEnabled: true, deliveryEnabled: true })

const displayText = computed(() => {
  const pickup = props.row.pickupEnabled !== false
  const delivery = props.row.deliveryEnabled !== false
  if (pickup && delivery) return '自提+送货'
  if (pickup) return '自提'
  if (delivery) return '送货'
  return '未配置'
})

/** 打开面板时以当前行数据为准，未提交的改动不会保留 */
function syncForm() {
  form.pickupEnabled = props.row.pickupEnabled !== false
  form.deliveryEnabled = props.row.deliveryEnabled !== false
}

async function onChange() {
  if (saving.value) return
  const current = {
    pickupEnabled: props.row.pickupEnabled !== false,
    deliveryEnabled: props.row.deliveryEnabled !== false
  }
  // 点开面板后未改变勾选，关闭或点击都不发请求
  if (form.pickupEnabled === current.pickupEnabled && form.deliveryEnabled === current.deliveryEnabled) {
    return
  }
  if (!form.pickupEnabled && !form.deliveryEnabled) {
    ElMessage.warning('自提和送货至少提供一项')
    syncForm()
    return
  }
  const payload = { pickupEnabled: form.pickupEnabled, deliveryEnabled: form.deliveryEnabled }
  saving.value = true
  try {
    await updateTransferServices(props.row.id, payload)
    ElMessage.success('已保存')
    emit('updated', payload)
  } catch (error) {
    syncForm()
    const message = (error as { message?: string })?.message
    ElMessage.error(message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.service-editor__value {
  display: inline-block;
}

.service-editor__value.is-editable {
  cursor: pointer;
  border-bottom: 1px dashed var(--el-color-primary-light-5);
}

.service-editor__value.is-editable:hover {
  color: var(--el-color-primary);
}

.service-editor__panel {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.service-editor__saving {
  font-size: var(--font-size-extra-small);
  color: var(--color-text-secondary);
}
</style>
