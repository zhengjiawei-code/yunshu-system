<template>
  <el-dialog
    :model-value="visible"
    :title="`子单清单 · ${data?.parentNo || ''}`"
    width="900px"
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-loading="loading" class="children-body">
      <div class="totals">
        <div class="total-card">
          <span class="total-label">母单合计</span>
          <span class="total-value">
            件数 {{ data?.parentTotals.quantity ?? 0 }} · 重量 {{ data?.parentTotals.weight ?? 0 }} kg ·
            体积 {{ data?.parentTotals.volume ?? 0 }} m³
          </span>
        </div>
        <div class="total-card" :class="{ mismatch: mismatch }">
          <span class="total-label">子单合计</span>
          <span class="total-value">
            件数 {{ data?.childTotals.quantity ?? 0 }} · 重量 {{ data?.childTotals.weight ?? 0 }} kg ·
            体积 {{ data?.childTotals.volume ?? 0 }} m³
          </span>
          <span v-if="mismatch" class="mismatch-tag">与母单不一致</span>
        </div>
      </div>

      <el-table :data="data?.children || []" border size="small" height="380" show-overflow-tooltip>
        <el-table-column prop="childSeq" label="子单序号" width="90" align="center" />
        <el-table-column prop="waybillNo" label="子单号" width="180" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="light" :type="statusTagType(row.status)">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="件数" width="80" align="right" />
        <el-table-column label="重量(kg)" width="110" align="right">
          <template #default="{ row }">{{ formatQuantity(row.weight) }}</template>
        </el-table-column>
        <el-table-column label="体积(m³)" width="110" align="right">
          <template #default="{ row }">{{ formatQuantity(row.volume) }}</template>
        </el-table-column>
        <el-table-column label="开单时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <template #empty>暂无有效子单</template>
      </el-table>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { getWaybillChildren } from '@/api/operation'
import type { WaybillChildren } from '@/types/operation'
import { formatDateTime, formatQuantity, statusTagType, statusText } from '@/utils/operationDict'

const props = defineProps<{
  visible: boolean
  waybillId?: number
}>()

const emit = defineEmits<{ 'update:visible': [value: boolean] }>()

const loading = ref(false)
const data = ref<WaybillChildren | null>(null)

const mismatch = computed(() => {
  if (!data.value) return false
  const parent = data.value.parentTotals
  const child = data.value.childTotals
  return (
    parent.quantity !== child.quantity ||
    Number(parent.weight) !== Number(child.weight) ||
    Number(parent.volume) !== Number(child.volume)
  )
})

watch(
  () => [props.visible, props.waybillId],
  async () => {
    if (!props.visible || !props.waybillId) return
    loading.value = true
    try {
      data.value = await getWaybillChildren(props.waybillId)
    } catch {
      data.value = null
    } finally {
      loading.value = false
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.children-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.totals {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.total-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 4px;
  background: var(--color-bg-subtle);
  border: 1px solid var(--color-border-light);
}

.total-card.mismatch {
  background: #fdf7f5;
  border-color: #e2b3ab;
}

.total-label {
  flex: none;
  color: var(--color-text-secondary);
}

.total-value {
  color: var(--color-text-primary);
  font-family: var(--font-family-number);
}

.mismatch-tag {
  margin-left: auto;
  color: var(--color-danger);
  font-size: 12px;
}
</style>
