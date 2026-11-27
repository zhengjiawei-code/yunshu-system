<template>
  <el-dialog
    :model-value="modelValue"
    title="运单详情（只读）"
    width="560px"
    destroy-on-close
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
  >
    <el-descriptions v-if="row" :column="2" border size="small">
      <el-descriptions-item label="运单号">
        <span class="mono">{{ row.waybillNo }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="单元类型">{{ unitTypeText }}</el-descriptions-item>
      <el-descriptions-item label="母单号">
        <span class="mono">{{ row.parentNo || '—' }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="运单状态">
        {{ statusText(row.status) }}
      </el-descriptions-item>
      <el-descriptions-item label="客户名称">{{ row.customerName || '—' }}</el-descriptions-item>
      <el-descriptions-item label="收货人">{{ row.receiverName || '—' }}</el-descriptions-item>
      <el-descriptions-item label="目的站">{{ row.destName || '—' }}</el-descriptions-item>
      <el-descriptions-item label="到达网点">{{ nextDeptName }}</el-descriptions-item>
      <el-descriptions-item label="件数">{{ formatQuantity(quantity) }}</el-descriptions-item>
      <el-descriptions-item label="重量(kg)">{{ formatQuantity(weight) }}</el-descriptions-item>
      <el-descriptions-item label="体积(m³)">{{ formatQuantity(volume) }}</el-descriptions-item>
      <el-descriptions-item label="运费合计">{{ formatMoney(totalFee) }}</el-descriptions-item>
      <el-descriptions-item label="货物名称">{{ row.goodsName || '—' }}</el-descriptions-item>
      <el-descriptions-item label="库存落入时间">{{ formatDateTime(time) }}</el-descriptions-item>
    </el-descriptions>
    <div class="readonly-hint">本页仅供查看；改单/作废请到运单管理操作。</div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { LoadedRow, PoolRow } from '@/types/transport'
import { formatDateTime, formatMoney, formatQuantity, statusText } from '@/utils/operationDict'

const props = defineProps<{
  modelValue: boolean
  row: PoolRow | LoadedRow | null
  source: 'pool' | 'loaded'
}>()

const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const unitTypeText = computed(() => {
  const row = props.row
  if (!row) return '—'
  if (props.source === 'pool') {
    const type = (row as PoolRow).unitType
    return type === 2 ? '母单余额' : type === 3 ? '子单' : '整票'
  }
  const loaded = row as LoadedRow
  return loaded.isChild === 1 ? '子单' : '整票'
})

const quantity = computed(() => {
  const row = props.row
  if (!row) return undefined
  return props.source === 'pool' ? (row as PoolRow).remainingQuantity : (row as LoadedRow).quantity
})

const weight = computed(() => {
  const row = props.row
  if (!row) return undefined
  return props.source === 'pool' ? (row as PoolRow).remainingWeight : (row as LoadedRow).weight
})

const volume = computed(() => {
  const row = props.row
  if (!row) return undefined
  return props.source === 'pool' ? (row as PoolRow).remainingVolume : (row as LoadedRow).volume
})

const totalFee = computed(() => props.row?.totalFee)
const time = computed(() => {
  const row = props.row
  if (!row) return undefined
  return props.source === 'pool' ? (row as PoolRow).inventoryTime : (row as LoadedRow).loadTime
})

const nextDeptName = computed(() => {
  const row = props.row
  if (!row) return '—'
  if (props.source === 'pool') return (row as PoolRow).nextDeptName || '—'
  return (row as LoadedRow).destDeptName || '—'
})
</script>

<style scoped>
.mono {
  font-family: var(--font-family-number);
}

.readonly-hint {
  margin-top: 12px;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-extra-small);
}
</style>
