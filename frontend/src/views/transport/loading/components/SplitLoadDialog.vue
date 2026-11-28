<template>
  <el-dialog
    :model-value="modelValue"
    title="拆单装车"
    width="560px"
    top="6vh"
    class="split-dialog-shell"
    :close-on-click-modal="false"
    destroy-on-close
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
  >
    <div v-if="row" v-loading="loading" class="split-dialog">
      <div class="split-head">
        <span class="split-head__no">{{ row.waybillNo }}</span>
        <span class="split-head__meta">{{ row.customerName || '—' }} · {{ row.destName || '—' }}</span>
      </div>

      <el-form label-width="104px" class="split-form">
        <el-form-item label="原运单号">
          <span class="mono">{{ row.waybillNo }}</span>
        </el-form-item>
        <el-form-item label="客户名称">
          <span>{{ row.customerName || '—' }}</span>
        </el-form-item>
        <el-form-item label="总件数">
          <span>{{ row.originalQuantity ?? '—' }} 件</span>
        </el-form-item>
        <el-form-item label="可装剩余">
          <span>{{ row.remainingQuantity ?? 0 }} 件</span>
        </el-form-item>
        <el-form-item label="本车件数" required>
          <div class="qty-line">
            <el-input-number
              v-model="qty"
              :min="1"
              :max="maxQty"
              :controls="false"
              size="small"
              class="qty-input"
            />
            <span class="qty-hint">可输入 1 ~ {{ maxQty }} 件 · 剩余 {{ Math.max(maxQty - (qty || 0), 0) }} 件</span>
            <el-button link type="primary" size="small" @click="qty = maxQty">全部余额装车</el-button>
          </div>
        </el-form-item>
      </el-form>

      <div class="split-preview">
        <div class="split-preview__title">拆分预览（以服务端计算为准）</div>
        <el-table :data="previewRows" size="small" border :empty-text="previewLoading ? '计算中…' : '输入件数后自动计算'">
          <el-table-column prop="label" label="项目" min-width="96" />
          <el-table-column prop="child" label="子单（本车）" align="right" min-width="108" />
          <el-table-column prop="remain" label="母单（回池）" align="right" min-width="108" />
          <el-table-column prop="total" label="拆前合计" align="right" min-width="108" />
        </el-table>
      </div>

      <div class="split-hint">
        确认后生成子单号 <b class="mono">{{ row.waybillNo }}-{{ nextSeqText }}</b>（递增，实际序号由服务端分配）并配入本车次；
        母单剩余件数留在待配载池，金额按件数比例拆分、尾差归母单。
      </div>
    </div>

    <template #footer>
      <el-button :disabled="submitting" @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submit">
        确认拆单并装车
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { previewTripSplit, splitLoadTripWaybill } from '@/api/transport'
import type { PoolRow, SplitResult } from '@/types/transport'
import { formatMoney, formatQuantity } from '@/utils/operationDict'

const props = defineProps<{
  modelValue: boolean
  tripId: number
  row: PoolRow | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  done: [result: SplitResult]
}>()

const qty = ref(1)
const loading = ref(false)
const previewLoading = ref(false)
const submitting = ref(false)
const preview = ref<SplitResult | null>(null)
let previewTimer: number | undefined
let previewToken = 0

const maxQty = computed(() => Math.max(props.row?.remainingQuantity ?? 0, 1))
const canSubmit = computed(() => {
  const value = qty.value
  return !!preview.value && value >= 1 && value <= maxQty.value && !previewLoading.value && !submitting.value
})
const nextSeqText = computed(() => {
  const childCount = props.row?.childCount ?? 0
  return String(Math.min(childCount + 1, 99)).padStart(2, '0')
})

const previewRows = computed(() => {
  const result = preview.value
  if (!result) return []
  const child = result.child
  const remain = result.parentRemaining
  return [
    {
      label: '件数',
      child: formatQuantity(child.quantity),
      remain: formatQuantity(remain.quantity),
      total: formatQuantity(child.quantity + remain.quantity)
    },
    {
      label: '重量(kg)',
      child: formatQuantity(child.weight),
      remain: formatQuantity(remain.weight),
      total: formatQuantity((child.weight || 0) + (remain.weight || 0))
    },
    {
      label: '体积(m³)',
      child: formatQuantity(child.volume),
      remain: formatQuantity(remain.volume),
      total: formatQuantity((child.volume || 0) + (remain.volume || 0))
    },
    {
      label: '运费合计',
      child: formatMoney(child.totalFee),
      remain: formatMoney(remain.totalFee),
      total: formatMoney((child.totalFee || 0) + (remain.totalFee || 0))
    },
    {
      label: '送货费',
      child: formatMoney(child.deliveryFee),
      remain: formatMoney(remain.deliveryFee),
      total: formatMoney((child.deliveryFee || 0) + (remain.deliveryFee || 0))
    },
    {
      label: '返款',
      child: formatMoney(child.rebateTotal),
      remain: formatMoney(remain.rebateTotal),
      total: formatMoney((child.rebateTotal || 0) + (remain.rebateTotal || 0))
    },
    {
      label: '有效运费',
      child: formatMoney(child.validFreight),
      remain: formatMoney(remain.validFreight),
      total: formatMoney((child.validFreight || 0) + (remain.validFreight || 0))
    }
  ]
})

function reset() {
  const remaining = props.row?.remainingQuantity ?? 0
  qty.value = Math.max(1, Math.min(Math.floor(remaining / 2) || 1, remaining || 1))
  preview.value = null
  schedulePreview()
}

function schedulePreview() {
  if (previewTimer) window.clearTimeout(previewTimer)
  preview.value = null
  const row = props.row
  const value = qty.value
  if (!row || !props.tripId || !value || value < 1 || value > maxQty.value) return
  const token = ++previewToken
  previewLoading.value = true
  previewTimer = window.setTimeout(async () => {
    try {
      const result = await previewTripSplit(props.tripId, row.waybillId, value)
      if (token !== previewToken) return
      preview.value = result
    } catch {
      if (token !== previewToken) return
      preview.value = null
    } finally {
      if (token === previewToken) previewLoading.value = false
    }
  }, 250)
}

async function submit() {
  if (!props.row || !canSubmit.value) return
  submitting.value = true
  try {
    const result = await splitLoadTripWaybill(props.tripId, props.row.waybillId, qty.value)
    ElMessage.success(`已拆单并配载：子单 ${result.childWaybillNo || ''}`)
    emit('done', result)
    close()
  } catch {
    // 请求层按响应体 code 提示，保留弹窗与输入
  } finally {
    submitting.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}

watch(
  () => [props.modelValue, props.row?.waybillId] as const,
  ([open]) => {
    if (open) {
      loading.value = false
      reset()
    } else if (previewTimer) {
      window.clearTimeout(previewTimer)
      previewTimer = undefined
    }
  },
  { immediate: true }
)

watch(qty, () => {
  if (!props.modelValue) return
  schedulePreview()
})
</script>

<style scoped>
:global(.split-dialog-shell .el-dialog__body) {
  max-height: 68vh;
  overflow-y: auto;
}

.split-dialog {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.split-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.split-head__no {
  font-family: var(--font-family-number);
  font-size: 15px;
  font-weight: 600;
}

.split-head__meta {
  color: var(--color-text-secondary);
  font-size: var(--font-size-extra-small);
}

.split-form :deep(.el-form-item) {
  margin-bottom: 8px;
}

.qty-line {
  display: flex;
  align-items: center;
  gap: 10px;
}

.qty-input {
  width: 110px;
}

.qty-hint {
  color: var(--color-text-placeholder);
  font-size: var(--font-size-extra-small);
}

.split-preview__title {
  margin-bottom: 6px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-extra-small);
}

.split-hint {
  padding: 8px 10px;
  border-radius: var(--radius-xs);
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
  font-size: var(--font-size-extra-small);
  line-height: 1.7;
}

.mono {
  font-family: var(--font-family-number);
}
</style>
