<template>
  <page-drawer
    :model-value="modelValue"
    :title="`操作日志 · ${tripNo || ''}`"
    :size="560"
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
  >
    <el-table v-loading="loading" :data="logs" size="small" border class="log-table" show-overflow-tooltip>
      <el-table-column label="时间" width="168">
        <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column prop="actionName" label="动作" width="104">
        <template #default="{ row }">{{ row.actionName || row.actionType }}</template>
      </el-table-column>
      <el-table-column prop="operatorName" label="操作人" width="88" />
      <el-table-column label="原因/摘要" min-width="180">
        <template #default="{ row }">
          <span class="log-reason">{{ reasonOf(row) }}</span>
        </template>
      </el-table-column>
    </el-table>
    <div class="log-hint">日志只增不改；到站/卸车与费用变更分别记录动作 8、12。</div>

    <template #footer>
      <el-button @click="close">关闭</el-button>
    </template>
  </page-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import PageDrawer from '@/components/base/PageDrawer.vue'
import { getTripLogs } from '@/api/transport'
import type { TripLog } from '@/types/transport'
import { formatDateTime } from '@/utils/operationDict'

const props = defineProps<{
  modelValue: boolean
  tripId: number
  tripNo?: string
}>()

const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const loading = ref(false)
const logs = ref<TripLog[]>([])

function reasonOf(row: TripLog): string {
  if (row.reason) return row.reason
  const snapshot = row.afterSnapshot || row.beforeSnapshot
  if (!snapshot) return '—'
  return snapshot.length > 160 ? `${snapshot.slice(0, 160)}…` : snapshot
}

async function load() {
  loading.value = true
  try {
    logs.value = await getTripLogs(props.tripId)
  } catch {
    logs.value = []
  } finally {
    loading.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) load()
  }
)
</script>

<style scoped>
.log-table :deep(.el-table__header th) {
  height: 44px;
}

.log-reason {
  color: var(--color-text-secondary);
  line-height: 1.6;
  word-break: break-all;
}

.log-hint {
  margin-top: 8px;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-extra-small);
}
</style>
