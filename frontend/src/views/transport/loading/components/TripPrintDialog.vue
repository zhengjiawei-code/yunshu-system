<template>
  <el-dialog
    :model-value="modelValue"
    title="打印装车单"
    width="980px"
    destroy-on-close
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
  >
    <div v-loading="loading" class="print-wrap">
      <template v-if="data">
        <div class="print-head">
          <div class="print-title">{{ data.header.tripNo }} 装车单</div>
          <div class="print-meta">
            <span>车次名称：{{ data.header.tripName || '—' }}</span>
            <span>始发：{{ data.header.originDeptName || '—' }}</span>
            <span>送达：{{ data.header.destNames || '—' }}</span>
            <span>车牌：{{ data.header.plateNo || '—' }}</span>
            <span>司机：{{ data.header.driverName || '—' }}</span>
            <span>计划发车：{{ formatDateTime(data.header.planDepartTime) }}</span>
          </div>
        </div>
        <el-table :data="data.rows" size="small" border max-height="440">
          <el-table-column type="index" label="序号" width="56" align="center" />
          <el-table-column prop="subTripNo" label="子车次" width="130" />
          <el-table-column label="运单号" min-width="190">
            <template #default="{ row }">
              <span class="mono">{{ row.waybillNo }}</span>
              <el-tag v-if="row.isChild === 1" size="small" type="info" class="child-tag">子单</el-tag>
              <div v-if="row.isChild === 1" class="parent-no">母单：{{ row.parentNo || '—' }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="destHubName" label="到达网点" min-width="130" />
          <el-table-column prop="customerName" label="客户名称" min-width="140" />
          <el-table-column prop="receiverName" label="收货人" min-width="96" />
          <el-table-column label="件数" width="78" align="right">
            <template #default="{ row }">{{ formatQuantity(row.quantity) }}</template>
          </el-table-column>
          <el-table-column label="重量kg" width="96" align="right">
            <template #default="{ row }">{{ formatQuantity(row.weight) }}</template>
          </el-table-column>
          <el-table-column label="体积m³" width="92" align="right">
            <template #default="{ row }">{{ formatQuantity(row.volume) }}</template>
          </el-table-column>
          <el-table-column label="运费" width="108" align="right">
            <template #default="{ row }">{{ formatMoney(row.totalFee) }}</template>
          </el-table-column>
        </el-table>
        <div class="print-totals">
          <span>合计 {{ data.totals.unitCount ?? 0 }} 个单元</span>
          <span>{{ formatQuantity(data.totals.quantity) }} 件</span>
          <span>{{ formatQuantity(data.totals.weight) }} kg</span>
          <span>{{ formatQuantity(data.totals.volume) }} m³</span>
          <span>运费 {{ formatMoney(data.totals.totalFee) }}</span>
        </div>
      </template>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button type="primary" :disabled="!data" @click="print">打印</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { getTripPrint } from '@/api/transport'
import type { TripPrintData, TripPrintRow } from '@/types/transport'
import { formatDateTime, formatMoney, formatQuantity } from '@/utils/operationDict'

const props = defineProps<{
  modelValue: boolean
  tripId: number
}>()

const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const loading = ref(false)
const data = ref<TripPrintData | null>(null)

async function load() {
  loading.value = true
  try {
    data.value = await getTripPrint(props.tripId)
  } catch {
    data.value = null
  } finally {
    loading.value = false
  }
}

function cell(value: unknown): string {
  if (value === undefined || value === null || value === '') return '—'
  return String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function rowHtml(row: TripPrintRow, index: number): string {
  return `<tr>
    <td>${index + 1}</td>
    <td>${cell(row.subTripNo)}</td>
    <td>${cell(row.waybillNo)}${row.isChild === 1 ? `<div class="sub">母单：${cell(row.parentNo)}</div>` : ''}</td>
    <td>${cell(row.destHubName)}</td>
    <td>${cell(row.customerName)}</td>
    <td>${cell(row.receiverName)}</td>
    <td class="num">${cell(row.quantity)}</td>
    <td class="num">${cell(row.weight)}</td>
    <td class="num">${cell(row.volume)}</td>
    <td class="num">${cell(row.totalFee)}</td>
  </tr>`
}

function print() {
  const value = data.value
  if (!value) return
  const win = window.open('', '_blank', 'width=1100,height=760')
  if (!win) return
  const totals = value.totals
  win.document.write(`<!DOCTYPE html><html lang="zh-CN"><head><meta charset="UTF-8" />
  <title>${cell(value.header.tripNo)} 装车单</title>
  <style>
    body{font-family:-apple-system,"PingFang SC",sans-serif;margin:18px;color:#1f2329;font-size:12px}
    h2{margin:0 0 8px;font-size:18px;text-align:center}
    .meta{display:flex;flex-wrap:wrap;gap:6px 18px;margin-bottom:10px;color:#4e5969}
    table{width:100%;border-collapse:collapse}
    th,td{border:1px solid #c9cdd4;padding:4px 6px;line-height:1.5;text-align:left}
    th{background:#f2f3f5;font-weight:600}
    .num{text-align:right}
    .sub{color:#86909c;font-size:10px}
    .totals{display:flex;gap:18px;margin-top:10px;justify-content:flex-end;font-weight:600}
  </style></head><body>
  <h2>${cell(value.header.tripNo)} 装车单</h2>
  <div class="meta">
    <span>车次名称：${cell(value.header.tripName)}</span>
    <span>始发：${cell(value.header.originDeptName)}</span>
    <span>送达：${cell(value.header.destNames)}</span>
    <span>车牌：${cell(value.header.plateNo)}</span>
    <span>司机：${cell(value.header.driverName)}</span>
    <span>计划发车：${cell(value.header.planDepartTime)}</span>
  </div>
  <table>
    <thead><tr><th>序号</th><th>子车次</th><th>运单号</th><th>到达网点</th><th>客户</th><th>收货人</th><th>件数</th><th>重量kg</th><th>体积m³</th><th>运费</th></tr></thead>
    <tbody>${value.rows.map(rowHtml).join('')}</tbody>
  </table>
  <div class="totals">
    <span>合计 ${totals.unitCount ?? 0} 个单元</span>
    <span>${cell(totals.quantity)} 件</span>
    <span>${cell(totals.weight)} kg</span>
    <span>${cell(totals.volume)} m³</span>
    <span>运费 ${cell(totals.totalFee)}</span>
  </div>
  </body></html>`)
  win.document.close()
  win.focus()
  win.print()
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) load()
  }
)
</script>

<style scoped>
.print-head {
  margin-bottom: 10px;
}

.print-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 6px;
}

.print-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 18px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-extra-small);
}

.child-tag {
  margin-left: 6px;
}

.parent-no {
  color: var(--color-text-placeholder);
  font-size: 11px;
}

.print-totals {
  display: flex;
  justify-content: flex-end;
  gap: 16px;
  margin-top: 10px;
  font-weight: 600;
}

.mono {
  font-family: var(--font-family-number);
}
</style>
