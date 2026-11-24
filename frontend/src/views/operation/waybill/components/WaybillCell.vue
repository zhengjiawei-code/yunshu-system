<template>
  <span v-if="column.key === 'waybillNo'" class="waybill-no" @click="emit('open', row)">
    {{ row.waybillNo }}
    <span v-if="row.hasChildren" class="parent-badge">母</span>
  </span>

  <el-tag
    v-else-if="column.key === 'status'"
    size="small"
    effect="light"
    :type="statusTagType(row.status)"
  >
    {{ statusText(Number(value)) }}
  </el-tag>

  <span v-else-if="column.dict" class="dict">
    {{ dictText(column.dict, value === null || value === undefined ? undefined : Number(value)) }}
  </span>

  <span v-else-if="column.numeric === 'money'" class="num">
    {{ formatMoney(value) }}
  </span>

  <span v-else-if="column.numeric === 'number'" class="num">
    {{ formatQuantity(value) }}
  </span>

  <span v-else-if="column.key === 'bankCardNo'" class="ellipsis">
    {{ maskCard(value) }}
  </span>

  <span v-else-if="column.key === 'createTime'" class="nowrap">
    {{ formatDateTime(value === null || value === undefined ? undefined : String(value)) }}
  </span>

  <span v-else class="ellipsis" :title="textValue">{{ textValue }}</span>
</template>

<script setup lang="ts">
import type { WaybillRecord } from '@/types/operation'
import type { DictOption } from '@/utils/operationDict'
import {
  dictText,
  formatDateTime,
  formatMoney,
  formatQuantity,
  statusTagType,
  statusText
} from '@/utils/operationDict'

export interface WaybillCellColumn {
  key: string
  label: string
  numeric?: 'money' | 'number'
  dict?: DictOption[]
}

const props = defineProps<{
  column: WaybillCellColumn
  row: WaybillRecord
}>()

const emit = defineEmits<{ open: [row: WaybillRecord] }>()

const value = props.row?.[props.column.key as keyof WaybillRecord] as
  | string
  | number
  | null
  | undefined

const textValue = value === null || value === undefined || value === '' ? '—' : String(value)

/** 银行卡号展示脱敏：保留前 4 后 4 */
function maskCard(card?: string | number | null): string {
  const text = card === null || card === undefined ? '' : String(card)
  if (!text) return '—'
  if (text.length <= 8) return '****'
  return `${text.slice(0, 4)}****${text.slice(-4)}`
}
</script>

<style scoped>
.waybill-no {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-primary);
  cursor: pointer;
  font-family: var(--font-family-number);
  white-space: nowrap;
}

.waybill-no:hover {
  text-decoration: underline;
}

.parent-badge {
  flex: none;
  padding: 0 4px;
  border-radius: 3px;
  background: var(--color-primary-light-8);
  color: var(--color-primary);
  font-size: 10px;
  line-height: 15px;
}

.num {
  display: block;
  text-align: right;
  font-family: var(--font-family-number);
  font-variant-numeric: tabular-nums;
}

.nowrap {
  white-space: nowrap;
  font-family: var(--font-family-number);
}

.ellipsis {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dict {
  white-space: nowrap;
}
</style>
