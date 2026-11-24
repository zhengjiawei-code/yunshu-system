<template>
  <div class="amend-page">
    <div class="amend-head">
      <div class="head-left">
        <el-button link :icon="ArrowLeft" @click="goBack">返回</el-button>
        <span class="amend-title">改单</span>
        <span class="amend-no">{{ waybill?.waybillNo || '—' }}</span>
        <el-tag v-if="waybill" size="small" effect="light" :type="statusTagType(waybill.status)">
          {{ statusText(waybill.status) }}
        </el-tag>
      </div>
      <span class="amend-hint">一期改单直接生效；金额由服务端重算</span>
    </div>

    <div v-loading="loading" class="amend-body">
      <template v-if="waybill">
        <waybill-form :model="form" :goods="goods" />

        <section class="card reason-card">
          <div class="sec-head">
            <span class="sec-title">改单原因</span>
            <span class="reason-hint">必填，≤200 字；仅提交实际改动字段，未改字段保持原值</span>
          </div>
          <el-input
            v-model="reason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请填写本次改单原因"
          />
        </section>
      </template>

      <el-empty v-else-if="!loading" description="无权访问或运单不存在">
        <el-button type="primary" @click="goBack">返回</el-button>
      </el-empty>
    </div>

    <div v-if="waybill" class="amend-footer">
      <el-button @click="goBack">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交改单</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import type { WaybillAmendPayload, WaybillGoodsRecord, WaybillRecord } from '@/types/operation'
import { amendWaybill, getWaybillDetail } from '@/api/operation'
import { canAmend, statusTagType, statusText } from '@/utils/operationDict'
import WaybillForm, { type WaybillFormModel } from './components/WaybillForm.vue'

defineOptions({ name: 'OperationWaybillAmend' })

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const waybill = ref<WaybillRecord | null>(null)
const form = reactive<WaybillFormModel>({})
const goods = ref<Array<Partial<WaybillGoodsRecord>>>([])
const reason = ref('')

/** 文本列：提交时只带与原始值不同的项；清空按 null 显式提交 */
const TEXT_KEYS = [
  'senderCompany', 'senderName', 'senderPhone', 'senderAddress',
  'receiverCompany', 'receiverName', 'receiverPhone',
  'receiverProvinceCode', 'receiverCityCode', 'receiverDistrictCode', 'receiverAddress',
  'payeeName', 'bankName', 'bankCardNo', 'remark'
] as const
/** 数值/码值列：同样只提交改动项，清空按 null 显式提交 */
const NUMBER_KEYS = [
  'payMethod', 'freightFee', 'deliveryFee', 'pickupFee', 'otherFee', 'receiptFee',
  'makeBillFee', 'insuranceFee', 'cashBack', 'oweBack',
  'prepaidFee', 'collectFee', 'monthlyFee', 'returnFee',
  'deliveryMethod', 'codAmount'
] as const

const originalText = ref<Record<string, string | null>>({})
const originalNumber = ref<Record<string, number | null>>({})
const originalGoods = ref('')

function textOf(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

function numberOf(value: unknown): number | null {
  if (value === null || value === undefined || value === '') return null
  const num = Number(value)
  return Number.isNaN(num) ? null : num
}

/** 货物明细整体作为一项提交：行内任一处变化即视为 goodsList 变更 */
function normalizedGoods() {
  return goods.value.map((row) => ({
    goodsName: row.goodsName?.trim() || '',
    goodsType: row.goodsType?.trim() || null,
    packageType: row.packageType?.trim() || null,
    quantity: Number(row.quantity ?? 0),
    weight: Number(row.weight ?? 0),
    volume: Number(row.volume ?? 0),
    goodsRemark: row.goodsRemark?.trim() || null
  }))
}

function goodsFingerprint(): string {
  return JSON.stringify(normalizedGoods())
}


function waybillId(): number {
  return Number(route.params.id)
}

async function loadDetail() {
  loading.value = true
  try {
    const result = await getWaybillDetail(waybillId())
    if (!canAmend(result.waybill.status)) {
      ElMessage.warning(`当前状态不可改单：${statusText(result.waybill.status)}`)
      router.replace(`/operation/waybill/${result.waybill.id}`)
      return
    }
    waybill.value = result.waybill
    Object.assign(form, result.waybill)
    goods.value = result.goods.map((item) => ({ ...item }))
    originalText.value = Object.fromEntries(
      TEXT_KEYS.map((key) => [key, textOf((form as Record<string, unknown>)[key])])
    )
    originalNumber.value = Object.fromEntries(
      NUMBER_KEYS.map((key) => [key, numberOf((form as Record<string, unknown>)[key])])
    )
    originalGoods.value = goodsFingerprint()
  } catch {
    waybill.value = null
  } finally {
    loading.value = false
  }
}

function goBack() {
  if (waybill.value) router.push(`/operation/waybill/${waybill.value.id}`)
  else router.push('/operation/waybill')
}

function validate(): string | undefined {
  if (!reason.value.trim()) return '请填写改单原因'
  if (reason.value.trim().length > 200) return '改单原因不能超过 200 字'
  return undefined
}

/** 只收集「实际改过」的字段：与加载时的原值比较，清空按 null 显式提交 */
function buildPayload(): { payload: WaybillAmendPayload; dirty: string[] } {
  const payload: Record<string, unknown> = {
    reason: reason.value.trim(),
    version: waybill.value?.updateTime || ''
  }
  const source = form as Record<string, unknown>
  const dirty: string[] = []
  for (const key of TEXT_KEYS) {
    const current = textOf(source[key])
    if (current !== originalText.value[key]) {
      payload[key] = current
      dirty.push(key)
    }
  }
  for (const key of NUMBER_KEYS) {
    const current = numberOf(source[key])
    if (current !== originalNumber.value[key]) {
      payload[key] = current
      dirty.push(key)
    }
  }
  if (goodsFingerprint() !== originalGoods.value) {
    payload.goodsList = normalizedGoods()
    dirty.push('goodsList')
  }
  return { payload: payload as unknown as WaybillAmendPayload, dirty }
}

async function submit() {
  const error = validate()
  if (error) {
    ElMessage.warning(error)
    return
  }
  if (!waybill.value?.id) return
  const { payload, dirty } = buildPayload()
  if (!dirty.length) {
    ElMessage.warning('未检测到任何修改')
    return
  }
  submitting.value = true
  try {
    await amendWaybill(waybill.value.id, payload)
    ElMessage.success('改单成功')
    router.replace(`/operation/waybill/${waybill.value.id}`)
  } catch {
    // 请求层已提示；409 时保留页面供刷新
  } finally {
    submitting.value = false
  }
}

onMounted(loadDetail)
</script>

<style scoped>
.amend-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 10px 12px 0;
  box-sizing: border-box;
}

.amend-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: none;
  padding-bottom: 8px;
}

.head-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.amend-title {
  font-size: 16px;
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.amend-no {
  color: var(--color-text-secondary);
  font-family: var(--font-family-number);
}

.amend-hint {
  color: var(--color-text-placeholder);
  font-size: 12px;
}

.amend-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
  padding-bottom: 8px;
}

.card {
  margin-top: 10px;
  padding: 12px 14px;
  border: 1px solid var(--color-border-light);
  border-radius: 6px;
  background: var(--color-bg-container);
}

.sec-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.sec-title {
  position: relative;
  padding-left: 9px;
  font-size: 14px;
  font-weight: var(--font-weight-medium);
}

.sec-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 2px;
  bottom: 2px;
  width: 3px;
  border-radius: 2px;
  background: var(--color-primary);
}

.reason-hint {
  color: var(--color-text-placeholder);
  font-size: 12px;
}

.amend-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex: none;
  padding: 10px 0;
  border-top: 1px solid var(--color-border-light);
  background: var(--color-bg-container);
}
</style>
