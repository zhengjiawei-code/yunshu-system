<template>
  <div class="wf" :class="{ 'is-readonly': readonly }">
    <section class="card">
      <div class="sec-head">
        <span class="sec-title">基本信息</span>
      </div>

      <div class="grid four">
        <div class="field">
          <span class="lbl">关联单号</span>
          <span class="ro-value">{{ text(model.relatedNo) }}</span>
        </div>
        <div class="field">
          <span class="lbl">业务类型</span>
          <span class="ro-value">{{ dictText(BIZ_TYPE_OPTIONS, model.bizType) }}</span>
        </div>
        <div class="field">
          <span class="lbl">业务员</span>
          <span class="ro-value">{{ text(model.salesName) }}</span>
        </div>
        <div class="field">
          <span class="lbl">客户名称</span>
          <span class="ro-value">{{ text(model.customerName) }}</span>
        </div>
      </div>

      <div class="grid four mt4">
        <div class="field">
          <span class="lbl">目的地</span>
          <span class="ro-value" :title="model.destName">{{ text(model.destName) }}</span>
        </div>
        <div class="field">
          <span class="lbl">运达网点</span>
          <span class="ro-value" :title="model.destHubName">{{ text(model.destHubName) }}</span>
        </div>
        <div class="field span2">
          <span class="lbl">线路</span>
          <span class="ro-value" :title="model.routeName">{{ text(model.routeName) }}</span>
        </div>
      </div>

      <div class="route-strip">
        <span class="route-label">路由</span>
        <b>{{ text(model.originHubName) }}</b>
        <span class="route-arrow">→</span>
        <b>{{ text(model.destHubName) }}</b>
        <span v-if="!readonly && model.routeEndType" class="route-type">
          <span class="route-type-label">路由类型</span>
          <span class="route-badge">
            {{ dictText(ROUTE_END_TYPE_OPTIONS, model.routeEndType) }}
          </span>
        </span>
        <span class="route-meta">节点 {{ model.routeNodeCount ?? 0 }} 个</span>
      </div>
    </section>

    <section class="card">
      <div class="sec-head">
        <span class="sec-title">发货方 / 收货方</span>
      </div>
      <div class="grid two party-grid">
        <div class="party">
          <div class="party-title">发货方</div>
          <div class="party-inner">
            <div class="field">
              <span class="lbl">发货单位</span>
              <el-input v-if="!readonly" v-model="model.senderCompany" size="small" placeholder="选填" />
              <span v-else class="ro-value">{{ text(model.senderCompany) }}</span>
            </div>
            <div class="field">
              <span class="lbl">发货人</span>
              <el-input v-if="!readonly" v-model="model.senderName" size="small" />
              <span v-else class="ro-value">{{ text(model.senderName) }}</span>
            </div>
            <div class="field">
              <span class="lbl">发货电话</span>
              <el-input v-if="!readonly" v-model="model.senderPhone" size="small" />
              <span v-else class="ro-value">{{ text(model.senderPhone) }}</span>
            </div>
            <div class="field span2">
              <span class="lbl">发货地址</span>
              <el-input v-if="!readonly" v-model="model.senderAddress" size="small" />
              <span v-else class="ro-value" :title="model.senderAddress">{{ text(model.senderAddress) }}</span>
            </div>
          </div>
        </div>

        <div class="party">
          <div class="party-title">收货方</div>
          <div class="party-inner">
            <div class="field">
              <span class="lbl">收货单位</span>
              <el-input v-if="!readonly" v-model="model.receiverCompany" size="small" placeholder="选填" />
              <span v-else class="ro-value">{{ text(model.receiverCompany) }}</span>
            </div>
            <div class="field">
              <span class="lbl">收货人</span>
              <el-input v-if="!readonly" v-model="model.receiverName" size="small" />
              <span v-else class="ro-value">{{ text(model.receiverName) }}</span>
            </div>
            <div class="field">
              <span class="lbl">收货电话</span>
              <el-input v-if="!readonly" v-model="model.receiverPhone" size="small" />
              <span v-else class="ro-value">{{ text(model.receiverPhone) }}</span>
            </div>
            <div class="field span2">
              <span class="lbl">收货地区</span>
              <div v-if="readonly" class="ro-value">{{ regionText }}</div>
              <div v-else class="region-selects">
                <el-select
                  v-model="model.receiverProvinceCode"
                  placeholder="省"
                  size="small"
                  clearable
                  @change="onProvinceChange"
                >
                  <el-option v-for="item in provinceOptions" :key="item.code" :label="item.name" :value="item.code" />
                </el-select>
                <el-select
                  v-model="model.receiverCityCode"
                  placeholder="市"
                  size="small"
                  clearable
                  :disabled="!model.receiverProvinceCode"
                  @change="onCityChange"
                >
                  <el-option v-for="item in cityOptions" :key="item.code" :label="item.name" :value="item.code" />
                </el-select>
                <el-select
                  v-model="model.receiverDistrictCode"
                  placeholder="区县"
                  size="small"
                  clearable
                  :disabled="!model.receiverCityCode"
                >
                  <el-option v-for="item in districtOptions" :key="item.code" :label="item.name" :value="item.code" />
                </el-select>
              </div>
            </div>
            <div class="field span2">
              <span class="lbl">收货地址</span>
              <el-input v-if="!readonly" v-model="model.receiverAddress" size="small" />
              <span v-else class="ro-value" :title="model.receiverAddress">{{ text(model.receiverAddress) }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="card">
      <div class="sec-head">
        <span class="sec-title">货物明细</span>
        <span class="goods-sum">
          共 {{ goods.length }} 行 · 件数 {{ totals.quantity }} · 重量 {{ formatQuantity(totals.weight) }} kg ·
          体积 {{ formatQuantity(totals.volume) }} m³
        </span>
      </div>

      <el-table :data="goods" border size="small" class="goods-table" :row-style="{ height: '28px' }">
        <el-table-column type="index" label="序号" width="48" align="center" />
        <el-table-column label="货物名称" min-width="150">
          <template #default="{ row }">
            <el-input v-if="!readonly" v-model="row.goodsName" size="small" placeholder="选填" />
            <span v-else class="cell-text">{{ text(row.goodsName) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="货物类型" width="88">
          <template #default="{ row }">
            <el-input v-if="!readonly" v-model="row.goodsType" size="small" placeholder="选填" />
            <span v-else class="cell-text">{{ text(row.goodsType) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="包装类型" width="100">
          <template #default="{ row }">
            <el-select v-if="!readonly" v-model="row.packageType" size="small" clearable>
              <el-option v-for="item in PACKAGE_TYPES" :key="item" :label="item" :value="item" />
            </el-select>
            <span v-else class="cell-text">{{ text(row.packageType) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="件数" width="68" align="right">
          <template #default="{ row }">
            <el-input-number
              v-if="!readonly"
              v-model="row.quantity"
              size="small"
              :min="1"
              :precision="0"
              :controls="false"
            />
            <span v-else class="cell-num">{{ row.quantity ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="体积(m³)" width="84" align="right">
          <template #default="{ row }">
            <el-input-number
              v-if="!readonly"
              v-model="row.volume"
              size="small"
              :min="0"
              :precision="3"
              :controls="false"
            />
            <span v-else class="cell-num">{{ formatQuantity(row.volume) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="重量(kg)" width="84" align="right">
          <template #default="{ row }">
            <el-input-number
              v-if="!readonly"
              v-model="row.weight"
              size="small"
              :min="0"
              :precision="3"
              :controls="false"
            />
            <span v-else class="cell-num">{{ formatQuantity(row.weight) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="110">
          <template #default="{ row }">
            <el-input v-if="!readonly" v-model="row.goodsRemark" size="small" placeholder="选填" />
            <span v-else class="cell-text" :title="row.goodsRemark">{{ text(row.goodsRemark) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="!readonly" label="操作" width="56" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" :disabled="goods.length <= 1" @click="removeGoods($index)">
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>暂无货物明细</template>
      </el-table>

      <div v-if="!readonly" class="goods-add">
        <el-button size="small" @click="addGoods">+ 添加一行</el-button>
      </div>
    </section>

    <div class="grid two section-row">
      <section class="card">
        <div class="sec-head">
          <span class="sec-title">费用项</span>
          <span v-if="!readonly" class="fee-hint">声明价值不在改单范围；金额由服务端重算</span>
        </div>

        <div class="fee-groups">
          <div class="fee-group">
            <div class="fee-group-title">运输与返款</div>
            <div class="fee-grid">
              <div v-for="item in transportFeeFields" :key="item.key" class="fee-cell">
                <span class="fee-lbl">{{ item.label }}</span>
                <el-input-number
                  v-if="!readonly"
                  v-model="model[item.key]"
                  :min="0"
                  :precision="2"
                  :controls="false"
                  size="small"
                />
                <span v-else class="ro-value num">{{ formatMoney(model[item.key]) }}</span>
              </div>
            </div>
          </div>

          <div class="fee-group">
            <div class="fee-group-title">其他费用</div>
            <div class="fee-grid">
              <div v-for="item in otherFeeFields" :key="item.key" class="fee-cell">
                <span class="fee-lbl">{{ item.label }}</span>
                <el-input-number
                  v-if="!readonly && item.key !== 'declaredValue'"
                  v-model="model[item.key]"
                  :min="0"
                  :precision="2"
                  :controls="false"
                  size="small"
                />
                <span v-else class="ro-value num">{{ formatMoney(model[item.key]) }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="fee-foot">
          <span>运费合计 <b>{{ formatMoney(displayTotals.totalFee) }}</b></span>
          <span>返款合计 <b>{{ formatMoney(displayTotals.rebateTotal) }}</b></span>
          <span class="strong">有效运费 <b>{{ formatMoney(displayTotals.validFreight) }}</b></span>
        </div>
      </section>

      <section class="card">
        <div class="sec-head">
          <span class="sec-title">支付与配送</span>
        </div>

        <div class="field">
          <span class="lbl">支付方式</span>
          <el-select v-if="!readonly" v-model="model.payMethod" size="small" placeholder="请选择">
            <el-option v-for="item in PAY_METHOD_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <span v-else class="ro-value">{{ dictText(PAY_METHOD_OPTIONS, model.payMethod) }}</span>
        </div>

        <div class="grid four pay-amounts">
          <div v-for="item in payAllocations" :key="item.key" class="field">
            <span class="lbl">{{ item.label }}</span>
            <el-input-number
              v-if="!readonly && model.payMethod === 5"
              v-model="model[item.key]"
              :min="0"
              :precision="2"
              :controls="false"
              size="small"
            />
            <span v-else class="ro-value num">{{ formatMoney(model[item.key]) }}</span>
          </div>
        </div>

        <div v-if="model.payMethod === 5" class="pay-diff" :class="{ danger: Math.abs(payDiff) > 0.001 }">
          四项合计 {{ formatMoney(paySum) }}，与运费合计差额 {{ formatMoney(payDiff) }}
        </div>

        <div class="grid two pay-extra">
          <div class="field">
            <span class="lbl">送货方式</span>
            <el-select v-if="!readonly" v-model="model.deliveryMethod" size="small" placeholder="请选择">
              <el-option
                v-for="item in DELIVERY_METHOD_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <span v-else class="ro-value">{{ dictText(DELIVERY_METHOD_OPTIONS, model.deliveryMethod) }}</span>
          </div>
          <div class="field">
            <span class="lbl">是否回单</span>
            <span class="ro-value">{{ dictText(NEED_RECEIPT_OPTIONS, model.needReceipt) }}</span>
          </div>
          <div class="field">
            <span class="lbl">回单数量</span>
            <span class="ro-value num">{{ model.receiptCount ?? 0 }}</span>
          </div>
          <div class="field">
            <span class="lbl">是否等通知</span>
            <span class="ro-value">{{ dictText(NEED_RECEIPT_OPTIONS, model.waitNotice) }}</span>
          </div>
          <div class="field span2">
            <span class="lbl">备注</span>
            <el-input v-if="!readonly" v-model="model.remark" size="small" maxlength="500" placeholder="选填" />
            <span v-else class="ro-value" :title="model.remark">{{ text(model.remark) }}</span>
          </div>
        </div>
      </section>
    </div>

    <section class="card">
      <div class="sec-head">
        <span class="sec-title">代收货款</span>
      </div>
      <div class="grid four">
        <div class="field">
          <span class="lbl">代收货款</span>
          <el-input-number
            v-if="!readonly"
            v-model="model.codAmount"
            :min="0"
            :precision="2"
            :controls="false"
            size="small"
          />
          <span v-else class="ro-value num">{{ formatMoney(model.codAmount) }}</span>
        </div>
        <div class="field">
          <span class="lbl">收款人</span>
          <el-input v-if="!readonly" v-model="model.payeeName" size="small" placeholder="选填" />
          <span v-else class="ro-value">{{ text(model.payeeName) }}</span>
        </div>
        <div class="field">
          <span class="lbl">银行名称</span>
          <el-input v-if="!readonly" v-model="model.bankName" size="small" placeholder="选填" />
          <span v-else class="ro-value">{{ text(model.bankName) }}</span>
        </div>
        <div class="field">
          <span class="lbl">银行卡号</span>
          <el-input v-if="!readonly" v-model="model.bankCardNo" size="small" placeholder="选填" />
          <span v-else class="ro-value">{{ maskCard(model.bankCardNo) }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { WaybillGoodsRecord } from '@/types/operation'
import { getRegionChildren } from '@/api/system/region'
import type { RegionNode } from '@/types/region'
import {
  BIZ_TYPE_OPTIONS,
  DELIVERY_METHOD_OPTIONS,
  NEED_RECEIPT_OPTIONS,
  PACKAGE_TYPES,
  PAY_METHOD_OPTIONS,
  ROUTE_END_TYPE_OPTIONS,
  dictText,
  formatMoney,
  formatQuantity
} from '@/utils/operationDict'

export interface WaybillFormModel {
  waybillNo?: string
  relatedNo?: string
  salesName?: string
  bizType?: number
  destName?: string
  destHubName?: string
  originHubName?: string
  routeName?: string
  routeNodeCount?: number
  routeEndType?: number
  customerName?: string
  senderCompany?: string
  senderName?: string
  senderPhone?: string
  senderAddress?: string
  receiverCompany?: string
  receiverName?: string
  receiverPhone?: string
  receiverProvinceCode?: string
  receiverProvinceName?: string
  receiverCityCode?: string
  receiverCityName?: string
  receiverDistrictCode?: string
  receiverDistrictName?: string
  receiverAddress?: string
  goodsName?: string
  goodsType?: string
  packageType?: string
  quantity?: number
  weight?: number
  volume?: number
  goodsRemark?: string
  payMethod?: number
  freightFee?: number
  deliveryFee?: number
  pickupFee?: number
  otherFee?: number
  receiptFee?: number
  makeBillFee?: number
  insuranceFee?: number
  declaredValue?: number
  totalFee?: number
  cashBack?: number
  oweBack?: number
  rebateTotal?: number
  validFreight?: number
  prepaidFee?: number
  collectFee?: number
  monthlyFee?: number
  returnFee?: number
  deliveryMethod?: number
  codAmount?: number
  payeeName?: string
  bankName?: string
  bankCardNo?: string
  needReceipt?: number
  receiptCount?: number
  waitNotice?: number
  remark?: string
}

const props = withDefaults(
  defineProps<{
    model: WaybillFormModel
    goods: Array<Partial<WaybillGoodsRecord> & { goodsName?: string }>
    readonly?: boolean
  }>(),
  { readonly: false }
)

const readonly = computed(() => props.readonly)

const transportFeeFields = [
  { key: 'freightFee', label: '运费' },
  { key: 'deliveryFee', label: '送货费' },
  { key: 'pickupFee', label: '提货费' },
  { key: 'cashBack', label: '现返' },
  { key: 'oweBack', label: '欠返' }
] as const

const otherFeeFields = [
  { key: 'otherFee', label: '其他费' },
  { key: 'receiptFee', label: '回单费' },
  { key: 'makeBillFee', label: '制单费' },
  { key: 'insuranceFee', label: '保费' },
  { key: 'declaredValue', label: '声明价值' }
] as const

const payAllocations = [
  { key: 'prepaidFee', label: '现付' },
  { key: 'collectFee', label: '到付' },
  { key: 'monthlyFee', label: '月结' },
  { key: 'returnFee', label: '回付' }
] as const

const totals = computed(() => {
  let quantity = 0
  let weight = 0
  let volume = 0
  props.goods.forEach((row) => {
    quantity += Number(row.quantity || 0)
    weight += Number(row.weight || 0)
    volume += Number(row.volume || 0)
  })
  return {
    quantity,
    weight: Math.round(weight * 1000) / 1000,
    volume: Math.round(volume * 1000) / 1000
  }
})

const calcTotal = computed(() => {
  const keys = [
    'freightFee',
    'deliveryFee',
    'pickupFee',
    'cashBack',
    'oweBack',
    'otherFee',
    'receiptFee',
    'makeBillFee',
    'insuranceFee'
  ] as const
  return round2(keys.reduce((sum, key) => sum + Number(props.model[key] || 0), 0))
})

const calcRebate = computed(() => round2(Number(props.model.cashBack || 0) + Number(props.model.oweBack || 0)))

const displayTotals = computed(() => ({
  totalFee: props.readonly ? Number(props.model.totalFee ?? calcTotal.value) : calcTotal.value,
  rebateTotal: props.readonly ? Number(props.model.rebateTotal ?? calcRebate.value) : calcRebate.value,
  validFreight: props.readonly
    ? Number(props.model.validFreight ?? round2(calcTotal.value - calcRebate.value))
    : round2(calcTotal.value - calcRebate.value)
}))

const paySum = computed(() =>
  round2(
    Number(props.model.prepaidFee || 0) +
      Number(props.model.collectFee || 0) +
      Number(props.model.monthlyFee || 0) +
      Number(props.model.returnFee || 0)
  )
)

const payDiff = computed(() => round2(displayTotals.value.totalFee - paySum.value))

function round2(value: number): number {
  return Math.round(value * 100) / 100
}

function text(value?: string | number | null): string {
  return value === undefined || value === null || value === '' ? '—' : String(value)
}

function maskCard(card?: string | null): string {
  const value = card?.trim()
  if (!value) return '—'
  if (value.length <= 8) return '****'
  return `${value.slice(0, 4)}****${value.slice(-4)}`
}

const regionText = computed(() => {
  const textValue = [props.model.receiverProvinceName, props.model.receiverCityName, props.model.receiverDistrictName]
    .filter(Boolean)
    .join(' / ')
  return textValue || '—'
})

/* ---------- 地区级联（仅改单可编辑时加载） ---------- */
const provinceOptions = ref<RegionNode[]>([])
const cityOptions = ref<RegionNode[]>([])
const districtOptions = ref<RegionNode[]>([])

async function onProvinceChange(code?: string) {
  props.model.receiverCityCode = ''
  props.model.receiverDistrictCode = ''
  props.model.receiverCityName = ''
  props.model.receiverDistrictName = ''
  cityOptions.value = []
  districtOptions.value = []
  if (!code) return
  try {
    cityOptions.value = await getRegionChildren(code)
  } catch {
    cityOptions.value = []
  }
}

async function onCityChange(code?: string) {
  props.model.receiverDistrictCode = ''
  props.model.receiverDistrictName = ''
  districtOptions.value = []
  if (!code) return
  try {
    districtOptions.value = await getRegionChildren(code)
  } catch {
    districtOptions.value = []
  }
}

function addGoods() {
  props.goods.push({ goodsName: '', goodsType: '', packageType: '', quantity: 1, weight: 0, volume: 0, goodsRemark: '' })
}

function removeGoods(index: number) {
  if (props.goods.length <= 1) return
  props.goods.splice(index, 1)
}

onMounted(async () => {
  if (readonly.value) return
  try {
    provinceOptions.value = await getRegionChildren('0')
    if (props.model.receiverProvinceCode) {
      cityOptions.value = await getRegionChildren(props.model.receiverProvinceCode)
    }
    if (props.model.receiverCityCode) {
      districtOptions.value = await getRegionChildren(props.model.receiverCityCode)
    }
  } catch {
    provinceOptions.value = []
  }
})
</script>

<style scoped>
.wf {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.card {
  padding: 5px 10px 6px;
  border: 1px solid var(--color-border-base);
  border-radius: 6px;
  background: var(--color-white, #fff);
}

.sec-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 18px;
  margin-bottom: 3px;
}

.sec-title {
  position: relative;
  padding-left: 8px;
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
  color: var(--color-text-primary);
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

.grid {
  display: grid;
  gap: 5px 10px;
}

.grid.two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.grid.four {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.mt4 {
  margin-top: 4px;
}

.span2 {
  grid-column: span 2;
}

.field {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  height: 28px;
}

.lbl {
  flex: 0 0 62px;
  text-align: right;
  color: var(--color-text-regular);
  font-size: 12px;
  white-space: nowrap;
}

.field :deep(.el-input),
.field :deep(.el-input-number),
.field :deep(.el-select) {
  flex: 1;
  min-width: 0;
}

.field :deep(.el-input__wrapper),
.field :deep(.el-select__wrapper) {
  min-height: 28px;
  padding: 1px 8px;
}

.ro-value {
  flex: 1;
  min-width: 0;
  height: 28px;
  line-height: 26px;
  padding: 0 8px;
  border: 1px solid var(--color-border-light);
  border-radius: 4px;
  background: var(--color-bg-subtle);
  color: var(--color-text-primary);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 只读态：标签 + 文本，不用表单控件撑行高 */
.is-readonly .field,
.is-readonly .fee-cell {
  height: 26px;
}

.is-readonly .ro-value {
  height: 24px;
  line-height: 24px;
  padding: 0 6px;
  border-color: transparent;
  border-radius: 3px;
  background: var(--color-bg-subtle);
}

.ro-value.num {
  text-align: right;
  font-family: var(--font-family-number);
  font-variant-numeric: tabular-nums;
}

.route-strip {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 24px;
  margin-top: 4px;
  padding: 0 8px;
  border-radius: 4px;
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
  font-size: 12px;
  overflow: hidden;
  white-space: nowrap;
}

.route-label {
  flex: none;
  color: var(--color-text-placeholder);
}

.route-strip b {
  color: var(--color-primary-dark-1, var(--color-primary));
  font-weight: 600;
}

.route-arrow {
  color: var(--color-text-placeholder);
}

.route-meta {
  margin-left: auto;
  color: var(--color-text-placeholder);
}

/* 改单页保留路由类型徽标；详情页（只读）不渲染 */
.route-type {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: none;
}

.route-type-label {
  color: var(--color-text-placeholder);
}

.route-badge {
  flex: none;
  padding: 0 6px;
  border: 1px solid var(--color-primary-light-5);
  border-radius: 3px;
  color: var(--color-primary);
  line-height: 16px;
}

.party-grid {
  align-items: stretch;
}

.party {
  min-width: 0;
  padding: 6px 8px;
  border: 1px solid var(--color-border-light);
  border-radius: 4px;
}

.party-title {
  height: 18px;
  line-height: 18px;
  margin-bottom: 3px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.party-inner {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 8px;
}

.party-inner .field {
  height: 28px;
}

.party-inner .lbl {
  flex-basis: 56px;
}

.party-inner .span2 {
  grid-column: span 2;
}

.region-selects {
  flex: 1;
  min-width: 0;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 4px;
}

.region-selects :deep(.el-select__wrapper) {
  min-height: 28px;
  padding: 1px 6px;
}

.goods-sum {
  color: var(--color-text-secondary);
  font-size: 11px;
  font-family: var(--font-family-number);
}

.goods-table :deep(.el-table__header th) {
  height: 26px;
  padding: 0;
  background: var(--color-bg-subtle);
  font-size: 11px;
  font-weight: 500;
}

.goods-table :deep(.el-table__body td) {
  padding: 1px 4px;
}

.goods-table :deep(.el-input__wrapper),
.goods-table :deep(.el-select__wrapper) {
  min-height: 24px;
  padding: 0 6px;
}

.goods-table :deep(.el-input-number) {
  width: 100%;
  min-width: 0;
}

.goods-table :deep(.el-select) {
  width: 100%;
}

.cell-text {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
}

.cell-num {
  display: block;
  text-align: right;
  font-family: var(--font-family-number);
  font-variant-numeric: tabular-nums;
  font-size: 12px;
}

.goods-add {
  margin-top: 3px;
}

.fee-hint {
  color: var(--color-text-placeholder);
  font-size: 11px;
}

.fee-groups {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.fee-group {
  min-width: 0;
}

.fee-group-title {
  height: 16px;
  line-height: 16px;
  color: var(--color-text-placeholder);
  font-size: 11px;
}

.fee-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 4px 10px;
}

.fee-cell {
  display: flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  height: 28px;
}

.fee-lbl {
  flex: 0 0 54px;
  text-align: right;
  color: var(--color-text-regular);
  font-size: 12px;
  white-space: nowrap;
}

.fee-cell :deep(.el-input-number) {
  flex: 1;
  min-width: 0;
}

.fee-cell :deep(.el-input__wrapper) {
  min-height: 28px;
  padding: 1px 6px;
}

.fee-foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 26px;
  margin-top: auto;
  padding-top: 6px;
  border-top: 1px dashed var(--color-border-light);
  color: var(--color-text-secondary);
  font-size: 12px;
}

.fee-foot b {
  color: var(--color-text-primary);
  font-family: var(--font-family-number);
  font-variant-numeric: tabular-nums;
}

.fee-foot .strong b {
  color: var(--color-primary);
}

.pay-amounts {
  margin-top: 4px;
}

.pay-amounts .lbl {
  flex-basis: 40px;
}

.pay-extra {
  margin-top: 4px;
}

.section-row {
  align-items: stretch;
}

.section-row > .card {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.pay-diff {
  margin-top: 3px;
  color: var(--color-text-secondary);
  font-size: 11px;
  font-family: var(--font-family-number);
}

.pay-diff.danger {
  color: var(--color-danger);
}
</style>
