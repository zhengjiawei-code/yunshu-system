<template>
  <div class="wf">
    <section class="card">
      <div class="sec-head">
        <span class="sec-title">基本信息</span>
        <div class="hub-tags">
          <span class="hub-tag">始发：{{ model.originHubName || '—' }}</span>
          <span class="hub-arrow">→</span>
          <span class="hub-tag">运达：{{ model.destHubName || '—' }}</span>
          <span v-if="model.routeEndType" class="route-badge">
            {{ dictText(ROUTE_END_TYPE_OPTIONS, model.routeEndType) }}
          </span>
        </div>
      </div>

      <div class="grid four">
        <label class="field">
          <span class="lbl">关联单号</span>
          <el-input v-model="model.relatedNo" :disabled="readonly" placeholder="—" />
        </label>
        <label class="field">
          <span class="lbl">业务类型</span>
          <el-select v-model="model.bizType" :disabled="readonly" placeholder="请选择">
            <el-option v-for="item in BIZ_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </label>
        <label class="field">
          <span class="lbl">业务员</span>
          <el-input v-model="model.salesName" :disabled="readonly" placeholder="—" />
        </label>
        <label class="field">
          <span class="lbl">客户名称</span>
          <el-input v-model="model.customerName" :disabled="true" placeholder="—" />
        </label>
      </div>

      <div class="grid four">
        <label class="field">
          <span class="lbl">目的地</span>
          <el-input v-model="model.destName" :disabled="true" />
        </label>
        <label class="field">
          <span class="lbl">运达网点</span>
          <el-input v-model="model.destHubName" :disabled="true" />
        </label>
        <label class="field span2">
          <span class="lbl">线路</span>
          <el-input v-model="model.routeName" :disabled="true" />
        </label>
      </div>
      <div class="route-meta">
        路由节点 {{ model.routeNodeCount ?? 0 }} 个
        <span class="dot">·</span>
        末段类型：{{ dictText(ROUTE_END_TYPE_OPTIONS, model.routeEndType) }}
        <span class="dot">·</span>
        目的地/线路为开单派生字段，改单不可修改
      </div>
    </section>

    <section class="card">
      <div class="sec-head">
        <span class="sec-title">发货方 / 收货方</span>
      </div>
      <div class="grid two party-grid">
        <div class="party">
          <div class="party-title">发货方</div>
          <label class="field">
            <span class="lbl">发货单位</span>
            <el-input v-model="model.senderCompany" :disabled="readonly" placeholder="选填" />
          </label>
          <label class="field">
            <span class="lbl"><i v-if="!readonly" class="star">*</i>发货人</span>
            <el-input v-model="model.senderName" :disabled="readonly" />
          </label>
          <label class="field">
            <span class="lbl"><i v-if="!readonly" class="star">*</i>发货电话</span>
            <el-input v-model="model.senderPhone" :disabled="readonly" />
          </label>
          <label class="field">
            <span class="lbl"><i v-if="!readonly" class="star">*</i>发货地址</span>
            <el-input v-model="model.senderAddress" :disabled="readonly" />
          </label>
        </div>

        <div class="party">
          <div class="party-title">收货方</div>
          <label class="field">
            <span class="lbl">收货单位</span>
            <el-input v-model="model.receiverCompany" :disabled="readonly" placeholder="选填" />
          </label>
          <label class="field">
            <span class="lbl"><i v-if="!readonly" class="star">*</i>收货人</span>
            <el-input v-model="model.receiverName" :disabled="readonly" />
          </label>
          <label class="field">
            <span class="lbl"><i v-if="!readonly" class="star">*</i>收货电话</span>
            <el-input v-model="model.receiverPhone" :disabled="readonly" />
          </label>
          <div class="field">
            <span class="lbl"><i v-if="!readonly" class="star">*</i>收货地区</span>
            <div v-if="readonly" class="region-readonly">
              {{ regionText }}
            </div>
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
          <label class="field">
            <span class="lbl"><i v-if="!readonly" class="star">*</i>收货地址</span>
            <el-input v-model="model.receiverAddress" :disabled="readonly" />
          </label>
        </div>
      </div>
    </section>

    <section class="card">
      <div class="sec-head">
        <span class="sec-title">货物明细</span>
        <div class="goods-sum">
          共 {{ goods.length }} 行 · 件数 {{ totals.quantity }} · 重量 {{ formatQuantity(totals.weight) }} kg ·
          体积 {{ formatQuantity(totals.volume) }} m³
        </div>
      </div>

      <el-table :data="goods" border size="small" class="goods-table" show-overflow-tooltip>
        <el-table-column type="index" label="序号" width="56" align="center" />
        <el-table-column label="货物名称" min-width="140">
          <template #default="{ row }">
            <el-input v-if="!readonly" v-model="row.goodsName" size="small" placeholder="必填" />
            <span v-else>{{ row.goodsName || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="货物类型" min-width="110">
          <template #default="{ row }">
            <el-input v-if="!readonly" v-model="row.goodsType" size="small" placeholder="选填" />
            <span v-else>{{ row.goodsType || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="包装类型" width="120">
          <template #default="{ row }">
            <el-select v-if="!readonly" v-model="row.packageType" size="small" clearable placeholder="选填">
              <el-option v-for="item in PACKAGE_TYPES" :key="item" :label="item" :value="item" />
            </el-select>
            <span v-else>{{ row.packageType || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="件数" width="100" align="right">
          <template #default="{ row }">
            <el-input-number
              v-if="!readonly"
              v-model="row.quantity"
              size="small"
              :min="1"
              :precision="0"
              :controls="false"
            />
            <span v-else>{{ row.quantity ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="体积(m³)" width="120" align="right">
          <template #default="{ row }">
            <el-input-number
              v-if="!readonly"
              v-model="row.volume"
              size="small"
              :min="0"
              :precision="3"
              :controls="false"
            />
            <span v-else>{{ formatQuantity(row.volume) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="重量(kg)" width="120" align="right">
          <template #default="{ row }">
            <el-input-number
              v-if="!readonly"
              v-model="row.weight"
              size="small"
              :min="0"
              :precision="3"
              :controls="false"
            />
            <span v-else>{{ formatQuantity(row.weight) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="160">
          <template #default="{ row }">
            <el-input v-if="!readonly" v-model="row.goodsRemark" size="small" placeholder="选填" />
            <span v-else>{{ row.goodsRemark || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="!readonly" label="操作" width="70" align="center">
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

    <section class="card">
      <div class="sec-head">
        <span class="sec-title">费用项</span>
      </div>
      <div class="fee-grid">
        <label v-for="item in feeFields" :key="item.key" class="fee-cell">
          <span class="lbl">{{ item.label }}</span>
          <el-input-number
            v-model="model[item.key]"
            :min="0"
            :precision="2"
            :controls="false"
            :disabled="readonly || item.key === 'declaredValue'"
            size="small"
          />
        </label>
        <div class="fee-cell derived">
          <span class="lbl">运费合计</span>
          <span class="derived-value">{{ formatMoney(displayTotals.totalFee) }}</span>
        </div>
        <div class="fee-cell derived">
          <span class="lbl">返款合计</span>
          <span class="derived-value">{{ formatMoney(displayTotals.rebateTotal) }}</span>
        </div>
        <div class="fee-cell derived">
          <span class="lbl">有效运费</span>
          <span class="derived-value strong">{{ formatMoney(displayTotals.validFreight) }}</span>
        </div>
      </div>
      <div v-if="!readonly" class="fee-hint">声明价值不在改单范围；金额由服务端按费用项重算，多笔付需四项合计等于运费合计。</div>
    </section>

    <section class="card">
      <div class="sec-head">
        <span class="sec-title">支付与配送</span>
      </div>
      <div class="grid four">
        <label class="field">
          <span class="lbl"><i v-if="!readonly" class="star">*</i>支付方式</span>
          <el-select v-model="model.payMethod" :disabled="readonly" placeholder="请选择">
            <el-option v-for="item in PAY_METHOD_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </label>
        <label class="field">
          <span class="lbl"><i v-if="!readonly" class="star">*</i>送货方式</span>
          <el-select v-model="model.deliveryMethod" :disabled="readonly" placeholder="请选择">
            <el-option
              v-for="item in DELIVERY_METHOD_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </label>
        <label class="field">
          <span class="lbl">是否回单</span>
          <el-select v-model="model.needReceipt" :disabled="true">
            <el-option v-for="item in NEED_RECEIPT_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </label>
        <label class="field">
          <span class="lbl">回单数量</span>
          <el-input-number v-model="model.receiptCount" :min="0" :precision="0" :controls="false" :disabled="true" />
        </label>
        <label class="field">
          <span class="lbl">是否等通知</span>
          <el-select v-model="model.waitNotice" :disabled="true">
            <el-option v-for="item in NEED_RECEIPT_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </label>
      </div>

      <div v-if="model.payMethod === 5" class="pay-amounts">
        <label v-for="item in payAllocations" :key="item.key" class="fee-cell">
          <span class="lbl">{{ item.label }}</span>
          <el-input-number
            v-model="model[item.key]"
            :min="0"
            :precision="2"
            :controls="false"
            :disabled="readonly"
            size="small"
          />
        </label>
        <div class="pay-diff" :class="{ danger: Math.abs(payDiff) > 0.001 }">
          四项合计 {{ formatMoney(paySum) }}，与运费合计差额 {{ formatMoney(payDiff) }}
        </div>
      </div>

      <div class="grid two">
        <label class="field span2">
          <span class="lbl">备注</span>
          <el-input
            v-model="model.remark"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            :disabled="readonly"
          />
        </label>
      </div>
    </section>

    <section class="card">
      <div class="sec-head">
        <span class="sec-title">代收货款</span>
      </div>
      <div class="grid four">
        <label class="field">
          <span class="lbl">代收货款</span>
          <el-input-number
            v-model="model.codAmount"
            :min="0"
            :precision="2"
            :controls="false"
            :disabled="readonly"
          />
        </label>
        <label class="field">
          <span class="lbl">收款人</span>
          <el-input v-model="model.payeeName" :disabled="readonly" placeholder="代收&gt;0 必填" />
        </label>
        <label class="field">
          <span class="lbl">银行名称</span>
          <el-input v-model="model.bankName" :disabled="readonly" placeholder="代收&gt;0 必填" />
        </label>
        <label class="field">
          <span class="lbl">银行卡号</span>
          <el-input v-model="model.bankCardNo" :disabled="readonly" placeholder="代收&gt;0 必填" />
        </label>
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

const feeFields = [
  { key: 'freightFee', label: '运费' },
  { key: 'cashBack', label: '现返' },
  { key: 'oweBack', label: '欠返' },
  { key: 'deliveryFee', label: '送货费' },
  { key: 'declaredValue', label: '声明价值' },
  { key: 'insuranceFee', label: '保费' },
  { key: 'pickupFee', label: '提货费' },
  { key: 'otherFee', label: '其他费' },
  { key: 'receiptFee', label: '回单费' },
  { key: 'makeBillFee', label: '制单费' }
] as const

const payAllocations = [
  { key: 'prepaidFee', label: '现付运费' },
  { key: 'collectFee', label: '到付运费' },
  { key: 'monthlyFee', label: '月结运费' },
  { key: 'returnFee', label: '回付运费' }
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
    'cashBack',
    'oweBack',
    'deliveryFee',
    'insuranceFee',
    'pickupFee',
    'otherFee',
    'receiptFee',
    'makeBillFee'
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

const regionText = computed(() => {
  const text = [props.model.receiverProvinceName, props.model.receiverCityName, props.model.receiverDistrictName]
    .filter(Boolean)
    .join(' / ')
  return text || '—'
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
  gap: 10px;
}

.card {
  padding: 12px 14px;
  border: 1px solid var(--color-border-light);
  border-radius: 6px;
  background: var(--color-bg-container);
}

.sec-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.sec-title {
  position: relative;
  padding-left: 9px;
  font-size: 14px;
  font-weight: var(--font-weight-medium);
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

.hub-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.hub-tag {
  padding: 2px 8px;
  border-radius: 10px;
  background: var(--color-bg-subtle);
}

.hub-arrow {
  color: var(--color-text-placeholder);
}

.route-badge {
  padding: 1px 7px;
  border: 1px solid var(--color-primary-light-5);
  border-radius: 3px;
  color: var(--color-primary);
}

.grid {
  display: grid;
  gap: 10px 14px;
}

.grid.four {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.grid.two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.span2 {
  grid-column: span 2;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.lbl {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.star {
  color: var(--color-danger);
  font-style: normal;
  margin-right: 2px;
}

.route-meta {
  margin-top: 8px;
  color: var(--color-text-placeholder);
  font-size: 12px;
}

.dot {
  margin: 0 6px;
}

.party-grid {
  align-items: start;
}

.party {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 4px;
  border: 1px solid var(--color-border-light);
}

.party-title {
  font-size: 13px;
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.region-selects {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
}

.region-readonly {
  min-height: 30px;
  padding: 5px 10px;
  border-radius: 4px;
  background: var(--color-bg-subtle);
  color: var(--color-text-primary);
}

.goods-sum {
  color: var(--color-text-secondary);
  font-size: 12px;
  font-family: var(--font-family-number);
}

.goods-table :deep(.el-input-number) {
  width: 100%;
}

.goods-add {
  margin-top: 8px;
}

.fee-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px 14px;
}

.fee-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.fee-cell.derived .derived-value {
  min-height: 30px;
  padding: 5px 10px;
  border-radius: 4px;
  background: var(--color-bg-subtle);
  font-family: var(--font-family-number);
  text-align: right;
}

.derived-value.strong {
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
}

.fee-hint {
  margin-top: 8px;
  color: var(--color-text-placeholder);
  font-size: 12px;
}

.pay-amounts {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px 14px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--color-border-light);
}

.pay-diff {
  grid-column: span 4;
  color: var(--color-text-secondary);
  font-size: 12px;
  font-family: var(--font-family-number);
}

.pay-diff.danger {
  color: var(--color-danger);
}

@media (max-width: 1280px) {
  .grid.four,
  .fee-grid,
  .pay-amounts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .pay-diff {
    grid-column: span 2;
  }
}
</style>
