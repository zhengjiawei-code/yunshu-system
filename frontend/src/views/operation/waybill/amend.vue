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
            <span class="reason-hint">必填，≤200 字；写入操作日志 action_type=6</span>
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
  if (!form.senderName?.trim()) return '发货人不能为空'
  if (!form.senderPhone?.trim()) return '发货电话不能为空'
  if (!form.senderAddress?.trim()) return '发货地址不能为空'
  if (!form.receiverName?.trim()) return '收货人不能为空'
  if (!form.receiverPhone?.trim()) return '收货电话不能为空'
  if (!form.receiverProvinceCode || !form.receiverCityCode || !form.receiverDistrictCode) {
    return '收货省、市、区县必须选择到区县级'
  }
  if (!form.receiverAddress?.trim()) return '收货地址不能为空'
  if (!form.payMethod) return '请选择支付方式'
  if (!form.deliveryMethod) return '请选择送货方式'
  if (form.freightFee === undefined || form.freightFee === null) return '运费不能为空'
  if ((form.deliveryMethod === 1 || form.deliveryMethod === 3) && (form.deliveryFee === undefined || form.deliveryFee === null)) {
    return '送货方式为送货或大车直送时，送货费不能为空'
  }
  if (!goods.value.length) return '请至少录入一行货物明细'
  for (let i = 0; i < goods.value.length; i++) {
    const row = goods.value[i]
    const prefix = `第${i + 1}行货物`
    if (!row.goodsName?.trim()) return `${prefix}名称不能为空`
    if (!row.quantity || row.quantity < 1) return `${prefix}件数必须为大于等于1的整数`
    if (!row.weight || Number(row.weight) <= 0) return `${prefix}重量必须大于0`
    if (!row.volume || Number(row.volume) <= 0) return `${prefix}体积必须大于0`
  }
  const cod = Number(form.codAmount || 0)
  if (cod > 0) {
    if (!form.payeeName?.trim()) return '代收货款大于0时，收款人不能为空'
    if (!form.bankName?.trim()) return '代收货款大于0时，开户行不能为空'
    if (!form.bankCardNo?.trim()) return '代收货款大于0时，银行卡号不能为空'
  }
  return undefined
}

function buildPayload(): WaybillAmendPayload {
  return {
    reason: reason.value.trim(),
    version: waybill.value?.updateTime || '',
    senderName: form.senderName?.trim() || '',
    senderPhone: form.senderPhone?.trim() || '',
    senderCompany: form.senderCompany?.trim() || undefined,
    senderAddress: form.senderAddress?.trim() || '',
    receiverName: form.receiverName?.trim() || '',
    receiverPhone: form.receiverPhone?.trim() || '',
    receiverCompany: form.receiverCompany?.trim() || undefined,
    receiverProvinceCode: form.receiverProvinceCode || '',
    receiverCityCode: form.receiverCityCode || '',
    receiverDistrictCode: form.receiverDistrictCode || '',
    receiverAddress: form.receiverAddress?.trim() || '',
    goodsList: goods.value.map((row) => ({
      goodsName: row.goodsName?.trim() || '',
      goodsType: row.goodsType?.trim() || undefined,
      packageType: row.packageType?.trim() || undefined,
      quantity: Number(row.quantity || 0),
      weight: Number(row.weight || 0),
      volume: Number(row.volume || 0),
      goodsRemark: row.goodsRemark?.trim() || undefined
    })),
    payMethod: Number(form.payMethod),
    freightFee: Number(form.freightFee || 0),
    deliveryFee: Number(form.deliveryFee || 0),
    pickupFee: Number(form.pickupFee || 0),
    otherFee: Number(form.otherFee || 0),
    receiptFee: Number(form.receiptFee || 0),
    makeBillFee: Number(form.makeBillFee || 0),
    insuranceFee: Number(form.insuranceFee || 0),
    cashBack: Number(form.cashBack || 0),
    oweBack: Number(form.oweBack || 0),
    deliveryMethod: Number(form.deliveryMethod),
    codAmount: Number(form.codAmount || 0),
    payeeName: form.payeeName?.trim() || undefined,
    bankName: form.bankName?.trim() || undefined,
    bankCardNo: form.bankCardNo?.trim() || undefined,
    remark: form.remark?.trim() || undefined
  }
}

async function submit() {
  const error = validate()
  if (error) {
    ElMessage.warning(error)
    return
  }
  if (!waybill.value?.id) return
  submitting.value = true
  try {
    await amendWaybill(waybill.value.id, buildPayload())
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
