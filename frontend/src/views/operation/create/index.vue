<template>
  <div class="op-page">
    <div class="op-top">
      <div class="op-tabs">
        <button type="button" class="op-tab" :class="{ active: activeTab === 'create' }" @click="activeTab = 'create'">
          开单揽收
        </button>
        <button type="button" class="op-tab" :class="{ active: activeTab === 'pre' }" @click="switchTab('pre')">
          预开单揽收<span v-if="preTotal" class="tab-badge">{{ preTotal }}</span>
        </button>
      </div>
      <div class="op-context">
        <span class="ctx-item">
          开单网点 <b class="ctx-org">{{ originDeptName }}</b>
        </span>
        <span class="ctx-item">开单员 <b>{{ userStore.displayName }}</b></span>
        <span class="ctx-item">开单时间 <b>{{ nowText }}</b></span>
      </div>
    </div>

    <!-- Tab2：预开单只读列表（本阶段占位） -->
    <div v-if="activeTab === 'pre'" class="pre-card">
      <div class="pre-head">
        <div class="sec-title">预开单列表</div>
        <div class="pre-tools">
          <span class="placeholder-note">本阶段只读占位：接单 / 退回 / 确认揽收在下一阶段开放</span>
          <el-button size="small" @click="loadPreOrders">刷新</el-button>
        </div>
      </div>
      <el-table v-loading="preLoading" :data="preOrders" height="360" size="small" border class="pre-table">
        <el-table-column prop="preOrderNo" label="预开单号" width="170" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="preStatusType(row.status)">{{ preStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="destName" label="目的地" min-width="180" show-overflow-tooltip />
        <el-table-column prop="quantity" label="件数" width="90" align="right" />
        <el-table-column prop="weight" label="重量(kg)" width="110" align="right" />
        <el-table-column prop="volume" label="体积(m³)" width="110" align="right" />
        <el-table-column prop="expectPickupTime" label="期望提货时间" width="160" />
        <el-table-column prop="submitTime" label="提交时间" width="160" />
        <template #empty>暂无预开单</template>
      </el-table>
      <div class="pre-summary">
        合计：共 {{ preTotal }} 条 · 件数 {{ preSummary.quantity ?? 0 }} · 重量 {{ preSummary.weight ?? 0 }} kg · 体积
        {{ preSummary.volume ?? 0 }} m³
      </div>
    </div>

    <!-- Tab1：开单表单 -->
    <div v-show="activeTab === 'create'" ref="formAreaRef" class="op-form">
      <div v-if="errorList.length" class="form-alert">
        <b>请先修正以下问题：</b>{{ errorList.join('；') }}
      </div>

      <!-- 基本信息 -->
      <section class="card">
        <div class="sec-head">
          <div class="sec-title">基本信息</div>
          <div class="hub-tags">
            <span>始发网点 <b>{{ originHubName || selectedDeptName || '—' }}</b></span>
            <span>运达网点 <b>{{ selectedRoute?.destHubName || '—' }}</b></span>
          </div>
        </div>
        <div class="grid four">
          <label class="field"><span class="lbl">运单号</span><el-input model-value="保存后自动生成" disabled /></label>
          <label class="field"><span class="lbl">关联单号</span><el-input v-model="form.relatedNo" placeholder="客户/上游单据号" /></label>
          <label class="field"><span class="lbl">业务员</span><el-input v-model="form.salesName" placeholder="选填" /></label>
          <div class="field"><span class="lbl">业务类型</span>
            <el-radio-group v-model="form.bizType" class="seg">
              <el-radio-button :value="1">整车</el-radio-button>
              <el-radio-button :value="2">零担</el-radio-button>
            </el-radio-group>
          </div>
        </div>
        <div class="grid dest-row">
          <label ref="destFieldRef" class="field dest-field">
            <span class="lbl"><i class="star">*</i>目的地</span>
            <el-input
              v-model="form.destText"
              :class="{ 'is-empty': !form.destDistrictCode }"
              placeholder="输入区县/城市关键字，如 白云"
              clearable
              @input="onDestInput"
              @focus="onDestFocus"
              @clear="clearDestination"
            />
            <div v-if="destOpen" class="pop-panel dest-panel">
              <div v-if="destLoading" class="pop-empty">匹配中…</div>
              <template v-else-if="destOptions.length">
                <button
                  v-for="(item, index) in destOptions"
                  :key="item.routeKeys.join('-') + index"
                  type="button"
                  class="cand-item"
                  :class="{ disabled: item.noRoute }"
                  @mousedown.prevent="pickDestination(item)"
                >
                  <div class="cand-line1">
                    <span class="cand-chain">{{ item.noRoute ? item.destName : item.chainText }}</span>
                    <span v-if="item.recommended" class="cand-recommend">推荐</span>
                    <span v-if="item.noRoute" class="cand-none">暂无可用路由</span>
                  </div>
                  <div v-if="!item.noRoute" class="cand-line2">
                    <span class="cand-dest">{{ item.destName }}</span>
                    <span>目的网点 {{ item.destHubName || '—' }}</span>
                    <span class="badge" :class="item.endType === 1 ? 'badge-direct' : 'badge-out'">
                      {{ item.endType === 1 ? '直达' : '外转' }}
                    </span>
                    <span>{{ item.nodeCount }} 节点</span>
                    <span>{{ item.totalHours ?? '—' }}h</span>
                    <span>{{ item.totalMileage ?? '—' }}km</span>
                  </div>
                </button>
              </template>
              <div v-else class="pop-empty">未找到匹配的目的地</div>
            </div>
          </label>

          <div ref="routeFieldRef" class="field route-field">
            <span class="lbl"><i class="star">*</i>线路</span>
            <div class="route-box" :class="{ 'is-empty': !selectedRoute }">
              <template v-if="selectedRoute">
                <span class="route-chain" :title="selectedRoute.chainText">{{ selectedRoute.chainText }}</span>
                <span class="badge" :class="selectedRoute.endType === 1 ? 'badge-direct' : 'badge-out'">
                  {{ selectedRoute.endType === 1 ? '直达' : '外转' }}
                </span>
                <span class="route-meta">{{ selectedRoute.nodeCount }} 节点 · {{ selectedRoute.totalHours ?? '—' }}h ·
                  {{ selectedRoute.totalMileage ?? '—' }}km</span>
                <button
                  v-if="routeOptions.length > 1"
                  type="button"
                  class="switch-btn"
                  @click.stop="routeOpen = !routeOpen"
                >
                  切换({{ routeOptions.length }})
                </button>
              </template>
              <span v-else class="route-placeholder">选择目的地后自动带出线路</span>
              <div v-if="routeOpen && routeOptions.length" class="pop-panel route-panel">
                <button
                  v-for="item in routeOptions"
                  :key="item.routeKeys.join('-')"
                  type="button"
                  class="cand-item"
                  :class="{ active: isSameRoute(item) }"
                  @mousedown.prevent="selectRoute(item)"
                >
                  <div class="cand-line1">
                    <span class="cand-chain">{{ item.chainText }}</span>
                    <span v-if="item.recommended" class="cand-recommend">推荐</span>
                  </div>
                  <div class="cand-line2">
                    <span class="badge" :class="item.endType === 1 ? 'badge-direct' : 'badge-out'">
                      {{ item.endType === 1 ? '直达' : '外转' }}
                    </span>
                    <span>目的网点 {{ item.destHubName || '—' }}</span>
                    <span>{{ item.nodeCount }} 节点</span>
                    <span>{{ item.totalHours ?? '—' }}h</span>
                    <span>{{ item.totalMileage ?? '—' }}km</span>
                  </div>
                </button>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 发货方 / 收货方 -->
      <div class="grid two">
        <section class="card">
          <div class="sec-head"><div class="sec-title">发货方</div></div>
          <div class="grid two">
            <label class="field span2"><span class="lbl">发货单位</span>
              <el-input v-model="form.senderCompany" placeholder="选填" />
            </label>
            <label class="field"><span class="lbl"><i class="star">*</i>发货人</span>
              <el-input v-model="form.senderName" :class="{ 'is-empty': !form.senderName }" @input="onSenderInput" />
            </label>
            <label class="field"><span class="lbl"><i class="star">*</i>发货电话</span>
              <el-input v-model="form.senderPhone" :class="{ 'is-empty': !form.senderPhone }" @input="onSenderInput" />
            </label>
            <label class="field span2"><span class="lbl">客户名称</span>
              <div class="readonly-box">
                <template v-if="customerMatched"><span class="matched">{{ customerName }}</span><span class="matched-tag">已匹配</span></template>
                <span v-else class="muted">—</span>
              </div>
            </label>
            <label class="field span2"><span class="lbl"><i class="star">*</i>发货地址</span>
              <el-input v-model="form.senderAddress" :class="{ 'is-empty': !form.senderAddress }" />
            </label>
          </div>
        </section>
        <section class="card">
          <div class="sec-head"><div class="sec-title">收货方</div></div>
          <div class="grid two">
            <label class="field span2"><span class="lbl">收货单位</span>
              <el-input v-model="form.receiverCompany" placeholder="选填" />
            </label>
            <label class="field"><span class="lbl"><i class="star">*</i>收货人</span>
              <el-input v-model="form.receiverName" :class="{ 'is-empty': !form.receiverName }" />
            </label>
            <label class="field"><span class="lbl"><i class="star">*</i>收货电话</span>
              <el-input v-model="form.receiverPhone" :class="{ 'is-empty': !form.receiverPhone }" />
            </label>
            <div class="field span2">
              <span class="lbl">所在地区</span>
              <div class="region-selects">
                <el-select
                  v-model="form.receiverProvinceCode"
                  placeholder="省"
                  size="small"
                  clearable
                  @change="onReceiverProvinceChange"
                >
                  <el-option v-for="item in provinceOptions" :key="item.code" :label="item.name" :value="item.code" />
                </el-select>
                <el-select
                  v-model="form.receiverCityCode"
                  placeholder="市"
                  size="small"
                  clearable
                  :disabled="!form.receiverProvinceCode"
                  @change="onReceiverCityChange"
                >
                  <el-option v-for="item in cityOptions" :key="item.code" :label="item.name" :value="item.code" />
                </el-select>
                <el-select
                  v-model="form.receiverDistrictCode"
                  placeholder="区县"
                  size="small"
                  clearable
                  :disabled="!form.receiverCityCode"
                  @change="onReceiverDistrictChange"
                >
                  <el-option v-for="item in districtOptions" :key="item.code" :label="item.name" :value="item.code" />
                </el-select>
              </div>
            </div>
            <div v-if="receiverRegionHint" class="region-hint span2">{{ receiverRegionHint }}</div>
            <label class="field span2"><span class="lbl"><i class="star">*</i>收货地址</span>
              <el-input v-model="form.receiverAddress" :class="{ 'is-empty': !form.receiverAddress }" />
            </label>
          </div>
        </section>
      </div>

      <!-- 货物明细 -->
      <section class="card goods-card">
        <div class="sec-head">
          <div class="sec-title">货物明细</div>
          <div class="goods-sum">件数 <b>{{ goodsSummary.quantity }}</b> · 重量 <b>{{ goodsSummary.weight }}</b> kg · 体积
            <b>{{ goodsSummary.volume }}</b> m³</div>
        </div>
        <table class="goods-table">
          <thead>
            <tr>
              <th class="col-idx">序号</th>
              <th class="col-name">货物名称 <i class="star">*</i></th>
              <th class="col-type">货物类型</th>
              <th class="col-pack">包装类型</th>
              <th class="col-num col-qty">件数 <i class="star">*</i></th>
              <th class="col-num col-vol">体积(m³) <i class="star">*</i></th>
              <th class="col-num col-weight">重量(kg) <i class="star">*</i></th>
              <th class="col-remark">备注</th>
              <th class="col-op">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in form.goodsList" :key="index">
              <td class="center">{{ index + 1 }}</td>
              <td>
                <el-input v-model="row.goodsName" size="small" :class="{ 'is-empty': !row.goodsName }" />
              </td>
              <td><el-input v-model="row.goodsType" size="small" /></td>
              <td>
                <el-select v-model="row.packageType" size="small" clearable>
                  <el-option v-for="opt in PACKAGE_TYPES" :key="opt" :label="opt" :value="opt" />
                </el-select>
              </td>
              <td>
                <el-input-number
                  v-model="row.quantity"
                  size="small"
                  :min="0"
                  :precision="0"
                  :controls="false"
                  :class="{ 'is-empty': !row.quantity }"
                />
              </td>
              <td>
                <el-input-number
                  v-model="row.volume"
                  size="small"
                  :min="0"
                  :precision="3"
                  :controls="false"
                  :class="{ 'is-empty': !row.volume }"
                />
              </td>
              <td>
                <el-input-number
                  v-model="row.weight"
                  size="small"
                  :min="0"
                  :precision="3"
                  :controls="false"
                  :class="{ 'is-empty': !row.weight }"
                />
              </td>
              <td><el-input v-model="row.goodsRemark" size="small" /></td>
              <td class="center">
                <el-button link type="danger" size="small" @click="removeGoods(index)">删除</el-button>
              </td>
            </tr>
          </tbody>
        </table>
        <div class="goods-add">
          <el-button link type="primary" size="small" @click="addGoods">+ 添加一行</el-button>
        </div>
      </section>

      <!-- 费用项 / 支付与配送 -->
      <div class="grid two">
        <section class="card fee-card">
          <div class="sec-head">
            <div class="sec-title">费用项</div>
            <div class="fee-total">运费合计 <b>¥{{ money(totalCents) }}</b></div>
          </div>
          <div class="fee-grid">
            <label class="fee-cell"><span class="fee-lbl"><i class="star">*</i>运费</span>
              <el-input-number
                v-model="form.freightFee"
                size="small"
                :min="0"
                :precision="2"
                :controls="false"
                :class="{ 'is-empty': form.freightFee == null }"
              />
              <span class="std">标准 —</span>
            </label>
            <label class="fee-cell"><span class="fee-lbl">现返</span>
              <el-input-number v-model="form.cashBack" size="small" :min="0" :precision="2" :controls="false" />
            </label>
            <label class="fee-cell"><span class="fee-lbl">欠返</span>
              <el-input-number v-model="form.oweBack" size="small" :min="0" :precision="2" :controls="false" />
            </label>
            <label class="fee-cell"><span class="fee-lbl"><i v-if="deliveryFeeRequired" class="star">*</i>送货费</span>
              <el-input-number
                v-model="form.deliveryFee"
                size="small"
                :min="0"
                :precision="2"
                :controls="false"
                :class="{ 'is-empty': deliveryFeeRequired && form.deliveryFee == null }"
              />
              <span class="std">标准 —</span>
            </label>
            <label class="fee-cell"><span class="fee-lbl">声明价值</span>
              <el-input-number v-model="form.declaredValue" size="small" :min="0" :precision="2" :controls="false" />
            </label>
            <label class="fee-cell"><span class="fee-lbl">保费</span>
              <el-input-number v-model="form.insuranceFee" size="small" :min="0" :precision="2" :controls="false" />
            </label>
            <label class="fee-cell"><span class="fee-lbl">提货费</span>
              <el-input-number v-model="form.pickupFee" size="small" :min="0" :precision="2" :controls="false" />
              <span class="std">标准 —</span>
            </label>
            <label class="fee-cell"><span class="fee-lbl">其他费</span>
              <el-input-number v-model="form.otherFee" size="small" :min="0" :precision="2" :controls="false" />
            </label>
            <label class="fee-cell"><span class="fee-lbl">回单费</span>
              <el-input-number v-model="form.receiptFee" size="small" :min="0" :precision="2" :controls="false" />
            </label>
            <label class="fee-cell"><span class="fee-lbl">制单费</span>
              <el-input-number v-model="form.makeBillFee" size="small" :min="0" :precision="2" :controls="false" />
            </label>
          </div>
          <div class="fee-foot">
            <span>返款合计 <b>¥{{ money(rebateCents) }}</b></span>
            <span :class="{ negative: validCents < 0 }">有效运费 <b>¥{{ money(validCents) }}</b>{{ validCents < 0 ? '（负值）' : '' }}</span>
            <span class="fee-formula">返款合计=现返+欠返；有效运费=运费合计−返款合计</span>
          </div>
        </section>

        <section class="card pay-card">
          <div class="sec-head"><div class="sec-title">支付与配送</div></div>
          <div class="pay-block" :class="{ 'is-empty': form.payMethod == null }">
            <span class="lbl"><i class="star">*</i>支付方式</span>
            <el-radio-group v-model="form.payMethod" class="seg pay-seg" @change="applyPayMethod">
              <el-radio-button :value="1">现付</el-radio-button>
              <el-radio-button :value="2">到付</el-radio-button>
              <el-radio-button :value="3">月结</el-radio-button>
              <el-radio-button :value="4">回付</el-radio-button>
              <el-radio-button :value="5">多笔付</el-radio-button>
            </el-radio-group>
          </div>
          <div class="grid four pay-amounts">
            <label class="field"><span class="lbl">现付运费</span>
              <el-input-number v-model="form.prepaidFee" size="small" :min="0" :precision="2" :controls="false" :disabled="form.payMethod !== 5" />
            </label>
            <label class="field"><span class="lbl">到付运费</span>
              <el-input-number v-model="form.collectFee" size="small" :min="0" :precision="2" :controls="false" :disabled="form.payMethod !== 5" />
            </label>
            <label class="field"><span class="lbl">月结运费</span>
              <el-input-number v-model="form.monthlyFee" size="small" :min="0" :precision="2" :controls="false" :disabled="form.payMethod !== 5" />
            </label>
            <label class="field"><span class="lbl">回付运费</span>
              <el-input-number v-model="form.returnFee" size="small" :min="0" :precision="2" :controls="false" :disabled="form.payMethod !== 5" />
            </label>
          </div>
          <div v-if="form.payMethod === 5" class="diff-bar" :class="diffCents === 0 ? 'ok' : 'warn'">
            <template v-if="diffCents === 0">四项合计 ¥{{ money(paySumCents) }}，与运费合计一致</template>
            <template v-else>四项合计 ¥{{ money(paySumCents) }}，运费合计 ¥{{ money(totalCents) }}，差额 ¥{{ money(diffCents) }} 未分配</template>
          </div>
          <div class="pay-block" :class="{ 'is-empty': form.deliveryMethod == null }">
            <span class="lbl"><i class="star">*</i>送货方式</span>
            <el-radio-group v-model="form.deliveryMethod" class="seg">
              <el-radio-button :value="2">自提</el-radio-button>
              <el-radio-button :value="1">送货</el-radio-button>
              <el-radio-button :value="3">大车直送</el-radio-button>
            </el-radio-group>
          </div>
          <div class="grid two pay-extra">
            <div class="field"><span class="lbl">是否回单</span>
              <el-radio-group v-model="form.needReceipt" class="seg">
                <el-radio-button :value="0">否</el-radio-button>
                <el-radio-button :value="1">是</el-radio-button>
              </el-radio-group>
            </div>
            <label class="field"><span class="lbl"><i v-if="form.needReceipt === 1" class="star">*</i>回单份数</span>
              <el-input-number v-model="form.receiptCount" size="small" :min="0" :precision="0" :controls="false" :disabled="form.needReceipt !== 1" />
            </label>
            <div class="field"><span class="lbl">是否等通知</span>
              <el-radio-group v-model="form.waitNotice" class="seg">
                <el-radio-button :value="0">否</el-radio-button>
                <el-radio-button :value="1">是</el-radio-button>
              </el-radio-group>
            </div>
            <label class="field"><span class="lbl">备注</span><el-input v-model="form.remark" maxlength="100" /></label>
          </div>
        </section>
      </div>

      <!-- 增值服务 -->
      <section class="card cod-card">
        <div class="sec-head">
          <div class="sec-title">增值服务 · 代收款</div>
          <div class="cod-tip">代收货款 &gt; 0 时，收款人 / 开户行 / 银行卡号必填</div>
        </div>
        <div class="grid four">
          <label class="field"><span class="lbl">代收货款</span>
            <el-input-number v-model="form.codAmount" size="small" :min="0" :precision="2" :controls="false" />
          </label>
          <label class="field"><span class="lbl">收款人</span><el-input v-model="form.payeeName" /></label>
          <label class="field"><span class="lbl">开户行</span><el-input v-model="form.bankName" /></label>
          <label class="field"><span class="lbl">银行卡号</span><el-input v-model="form.bankCardNo" /></label>
        </div>
      </section>
    </div>

    <div v-if="activeTab === 'create'" class="op-bottom">
      <span class="bottom-tip">运费合计=运费+送货费+提货费+其他费+回单费+制单费+保费；支付分摊对象为运费合计，不含声明价值</span>
      <div class="bottom-actions">
        <el-button @click="resetForm">重置</el-button>
        <el-button :loading="saving" @click="save(false)">保存</el-button>
        <el-button type="primary" :loading="saving" @click="save(true)">保存并继续开单</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {
  createWaybill,
  getDestinations,
  getPreOrders,
  getRouteCandidates,
  matchCustomer
} from '@/api/operation'
import { getRegionChildren } from '@/api/system/region'
import type { RegionNode } from '@/types/region'
import type { PreOrder, RouteCandidate, WaybillSavePayload } from '@/types/operation'

interface GoodsRow {
  goodsName: string
  goodsType: string
  packageType: string
  quantity: number | undefined
  weight: number | undefined
  volume: number | undefined
  goodsRemark: string
}

interface FormState {
  originDeptId: number | undefined
  relatedNo: string
  salesId: number | undefined
  salesName: string
  bizType: number
  destText: string
  destDistrictCode: string
  senderName: string
  senderPhone: string
  senderCompany: string
  senderAddress: string
  receiverName: string
  receiverPhone: string
  receiverCompany: string
  receiverProvinceCode: string
  receiverCityCode: string
  receiverDistrictCode: string
  receiverAddress: string
  goodsList: GoodsRow[]
  payMethod: number | undefined
  freightFee: number | undefined
  deliveryFee: number | undefined
  pickupFee: number | undefined
  otherFee: number | undefined
  receiptFee: number | undefined
  makeBillFee: number | undefined
  insuranceFee: number | undefined
  declaredValue: number | undefined
  cashBack: number | undefined
  oweBack: number | undefined
  prepaidFee: number | undefined
  collectFee: number | undefined
  monthlyFee: number | undefined
  returnFee: number | undefined
  deliveryMethod: number | undefined
  codAmount: number | undefined
  payeeName: string
  bankName: string
  bankCardNo: string
  needReceipt: number
  receiptCount: number | undefined
  waitNotice: number
  remark: string
}

const userStore = useUserStore()
const route = useRoute()

const activeTab = ref<'create' | 'pre'>('create')
const formAreaRef = ref<HTMLElement>()
const destFieldRef = ref<HTMLElement>()
const routeFieldRef = ref<HTMLElement>()

const submitted = ref(false)
const saving = ref(false)
const errorList = ref<string[]>([])

const now = new Date()
const nowText = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}`

function pad(value: number) {
  return String(value).padStart(2, '0')
}

/** 包装类型预设（顺序固定；存文本，可清空，非必填） */
const PACKAGE_TYPES = ['袋装', '纸箱', '桶装', '托盘', '木箱', '编织袋', '卷装', '裸装', '其他']

function createGoodsRow(): GoodsRow {
  return {
    goodsName: '',
    goodsType: '',
    packageType: '',
    quantity: undefined,
    weight: undefined,
    volume: undefined,
    goodsRemark: ''
  }
}

function createForm(): FormState {
  return {
    originDeptId: undefined,
    relatedNo: '',
    salesId: undefined,
    salesName: '',
    bizType: 2,
    destText: '',
    destDistrictCode: '',
    senderName: '',
    senderPhone: '',
    senderCompany: '',
    senderAddress: '',
    receiverName: '',
    receiverPhone: '',
    receiverCompany: '',
    receiverProvinceCode: '',
    receiverCityCode: '',
    receiverDistrictCode: '',
    receiverAddress: '',
    goodsList: [createGoodsRow()],
    payMethod: undefined,
    freightFee: undefined,
    deliveryFee: undefined,
    pickupFee: undefined,
    otherFee: undefined,
    receiptFee: undefined,
    makeBillFee: undefined,
    insuranceFee: undefined,
    declaredValue: undefined,
    cashBack: undefined,
    oweBack: undefined,
    prepaidFee: undefined,
    collectFee: undefined,
    monthlyFee: undefined,
    returnFee: undefined,
    deliveryMethod: undefined,
    codAmount: undefined,
    payeeName: '',
    bankName: '',
    bankCardNo: '',
    needReceipt: 0,
    receiptCount: undefined,
    waitNotice: 0,
    remark: ''
  }
}

const form = reactive<FormState>(createForm())

/* ---------- 目的地与线路 ---------- */
const destOpen = ref(false)
const destLoading = ref(false)
const destOptions = ref<RouteCandidate[]>([])
const routeOpen = ref(false)
const routeOptions = ref<RouteCandidate[]>([])
const selectedRoute = ref<RouteCandidate | null>(null)
let destTimer: number | undefined

const selectedDeptName = computed(() => userStore.currentOrgName || '—')
const originDeptName = selectedDeptName
const originHubName = computed(() => selectedRoute.value?.nodes?.[0]?.fromDeptName)

/** 送货(1)/大车直送(3) 时送货费必填；自提(2) 与未选不要求 */
const deliveryFeeRequired = computed(
  () => form.deliveryMethod === 1 || form.deliveryMethod === 3
)

function resetRouteSelection() {
  selectedRoute.value = null
  routeOptions.value = []
  form.destDistrictCode = ''
  form.destText = ''
  destOptions.value = []
  if (formAreaRef.value) formAreaRef.value.scrollTop = 0
}

// 开单网点固定取当前机构（顶部机构切换后自动跟随），不缓存旧值
watch(
  () => userStore.currentOrgId,
  (id) => {
    form.originDeptId = id ?? undefined
    resetRouteSelection()
  },
  { immediate: true }
)

function onDestInput() {
  selectedRoute.value = null
  routeOptions.value = []
  form.destDistrictCode = ''
  destOpen.value = true
  if (destTimer) window.clearTimeout(destTimer)
  destTimer = window.setTimeout(searchDestinations, 250)
}

function onDestFocus() {
  if (form.destText.trim()) {
    destOpen.value = true
    searchDestinations()
  }
}

async function searchDestinations() {
  const keyword = form.destText.trim()
  if (!keyword) {
    destOptions.value = []
    return
  }
  destLoading.value = true
  try {
    destOptions.value = await getDestinations({ keyword, originDeptId: form.originDeptId })
  } catch {
    destOptions.value = []
  } finally {
    destLoading.value = false
  }
}

function pickDestination(item: RouteCandidate) {
  if (item.noRoute || !item.districtCode) {
    ElMessage.warning('该目的地暂无可用路由')
    return
  }
  form.destDistrictCode = item.districtCode
  form.destText = `${item.cityName || ''}${item.districtName || ''}`
  destOpen.value = false
  loadRoutes(item.districtCode)
}

async function loadRoutes(districtCode: string) {
  try {
    routeOptions.value = await getRouteCandidates({
      destDistrictCode: districtCode,
      originDeptId: form.originDeptId
    })
    selectedRoute.value = routeOptions.value.find((item) => item.recommended) || routeOptions.value[0] || null
    routeOpen.value = false
  } catch {
    routeOptions.value = []
    selectedRoute.value = null
  }
}

function clearDestination() {
  form.destDistrictCode = ''
  form.destText = ''
  destOptions.value = []
  routeOptions.value = []
  selectedRoute.value = null
  destOpen.value = false
}

function selectRoute(item: RouteCandidate) {
  selectedRoute.value = item
  routeOpen.value = false
}

function isSameRoute(item: RouteCandidate) {
  return (item.routeKeys || []).join(',') === (selectedRoute.value?.routeKeys || []).join(',')
}

/* ---------- 收货地区（省/市/区县，仅做地址文字提示，不强拦截） ---------- */
const provinceOptions = ref<RegionNode[]>([])
const cityOptions = ref<RegionNode[]>([])
const districtOptions = ref<RegionNode[]>([])
const receiverRegionHint = ref('')

async function loadProvinces() {
  try {
    provinceOptions.value = await getRegionChildren('0')
  } catch {
    provinceOptions.value = []
  }
}

async function onReceiverProvinceChange(code: string) {
  form.receiverCityCode = ''
  form.receiverDistrictCode = ''
  cityOptions.value = []
  districtOptions.value = []
  receiverRegionHint.value = ''
  if (code) {
    try {
      cityOptions.value = await getRegionChildren(code)
    } catch {
      cityOptions.value = []
    }
  }
}

async function onReceiverCityChange(code: string) {
  form.receiverDistrictCode = ''
  districtOptions.value = []
  receiverRegionHint.value = ''
  if (code) {
    try {
      districtOptions.value = await getRegionChildren(code)
    } catch {
      districtOptions.value = []
    }
  }
}

function onReceiverDistrictChange() {
  updateRegionHint()
}

function updateRegionHint() {
  const address = form.receiverAddress.trim()
  if (!form.receiverDistrictCode || !address) {
    receiverRegionHint.value = ''
    return
  }
  const district = districtOptions.value.find((item) => item.code === form.receiverDistrictCode)?.name || ''
  const city = cityOptions.value.find((item) => item.code === form.receiverCityCode)?.name || ''
  const matched = (district && address.includes(district)) || (city && address.includes(city))
  receiverRegionHint.value = matched
    ? ''
    : '收货地址未包含所选城市/区县名称，请确认（仅提示，不影响保存）'
}

watch(() => form.receiverAddress, updateRegionHint)

/* ---------- 客户匹配 ---------- */
const customerName = ref('')
const customerMatched = ref(false)
let customerTimer: number | undefined

function onSenderInput() {
  if (customerTimer) window.clearTimeout(customerTimer)
  customerTimer = window.setTimeout(runCustomerMatch, 250)
}

async function runCustomerMatch() {
  const senderName = form.senderName.trim()
  if (!senderName) {
    customerName.value = ''
    customerMatched.value = false
    return
  }
  try {
    const result = await matchCustomer({ senderName, senderPhone: form.senderPhone.trim() || undefined })
    customerMatched.value = !!result.matched
    customerName.value = result.matched && result.candidates.length ? result.candidates[0].customerName : ''
  } catch {
    customerMatched.value = false
    customerName.value = ''
  }
}

/* ---------- 金额（按分计算） ---------- */
function cents(value: number | undefined) {
  return Math.round(Number(value ?? 0) * 100)
}

function money(value: number) {
  return (value / 100).toFixed(2)
}

const totalCents = computed(() =>
  ['freightFee', 'deliveryFee', 'pickupFee', 'otherFee', 'receiptFee', 'makeBillFee', 'insuranceFee']
    .map((key) => cents(form[key as keyof FormState] as number | undefined))
    .reduce((sum, value) => sum + value, 0)
)
const rebateCents = computed(() => cents(form.cashBack) + cents(form.oweBack))
const validCents = computed(() => totalCents.value - rebateCents.value)
const paySumCents = computed(
  () => cents(form.prepaidFee) + cents(form.collectFee) + cents(form.monthlyFee) + cents(form.returnFee)
)
const diffCents = computed(() => totalCents.value - paySumCents.value)

watch(totalCents, (total) => {
  if (form.payMethod && form.payMethod >= 1 && form.payMethod <= 4) {
    form.prepaidFee = form.payMethod === 1 ? total / 100 : 0
    form.collectFee = form.payMethod === 2 ? total / 100 : 0
    form.monthlyFee = form.payMethod === 3 ? total / 100 : 0
    form.returnFee = form.payMethod === 4 ? total / 100 : 0
  }
})

function applyPayMethod(value: number | string | boolean | undefined) {
  const method = Number(value)
  if (!method) {
    form.prepaidFee = 0
    form.collectFee = 0
    form.monthlyFee = 0
    form.returnFee = 0
    return
  }
  if (method >= 1 && method <= 4) {
    form.prepaidFee = method === 1 ? totalCents.value / 100 : 0
    form.collectFee = method === 2 ? totalCents.value / 100 : 0
    form.monthlyFee = method === 3 ? totalCents.value / 100 : 0
    form.returnFee = method === 4 ? totalCents.value / 100 : 0
  } else {
    form.prepaidFee = form.prepaidFee ?? 0
    form.collectFee = form.collectFee ?? 0
    form.monthlyFee = form.monthlyFee ?? 0
    form.returnFee = form.returnFee ?? 0
  }
}

/* ---------- 货物 ---------- */
const goodsSummary = computed(() => {
  let quantity = 0
  let weight = 0
  let volume = 0
  form.goodsList.forEach((row) => {
    quantity += Number(row.quantity ?? 0)
    weight += Number(row.weight ?? 0)
    volume += Number(row.volume ?? 0)
  })
  return { quantity, weight: weight.toFixed(3), volume: volume.toFixed(3) }
})

function addGoods() {
  form.goodsList.push(createGoodsRow())
}

function removeGoods(index: number) {
  if (form.goodsList.length <= 1) {
    ElMessage.warning('至少保留一行货物')
    return
  }
  form.goodsList.splice(index, 1)
}

/* ---------- 预开单占位列表 ---------- */
const preLoading = ref(false)
const preOrders = ref<PreOrder[]>([])
const preTotal = ref(0)
const preSummary = ref<Record<string, number>>({})

async function loadPreOrders() {
  preLoading.value = true
  try {
    const result = await getPreOrders({ page: 1, pageSize: 10 })
    preOrders.value = result.data
    preTotal.value = result.total
    preSummary.value = result.summary || {}
  } finally {
    preLoading.value = false
  }
}

function switchTab(tab: 'create' | 'pre') {
  activeTab.value = tab
  if (tab === 'pre') loadPreOrders()
}

function preStatusText(status: number) {
  return ({ 10: '待接单', 20: '已接单', 30: '已揽收', 90: '已撤单' } as Record<number, string>)[status] || '—'
}

function preStatusType(status: number) {
  return ({ 10: 'warning', 20: 'info', 30: 'success', 90: 'danger' } as Record<number, string>)[status] || 'info'
}

/* ---------- 校验与保存 ---------- */
function collectErrors(): string[] {
  const errors: string[] = []
  if (!form.senderName.trim()) errors.push('发货人不能为空')
  if (!form.senderPhone.trim()) errors.push('发货电话不能为空')
  if (!form.senderAddress.trim()) errors.push('发货地址不能为空')
  if (!form.receiverName.trim()) errors.push('收货人不能为空')
  if (!form.receiverPhone.trim()) errors.push('收货电话不能为空')
  if (!form.receiverAddress.trim()) errors.push('收货地址不能为空')
  const receiverRegionCount = [form.receiverProvinceCode, form.receiverCityCode, form.receiverDistrictCode]
    .filter(Boolean).length
  if (receiverRegionCount > 0 && receiverRegionCount < 3) errors.push('收货省、市、区县需同时选择')
  if (!form.destDistrictCode) errors.push('请从候选列表中选择唯一目的地')
  if (!selectedRoute.value) errors.push('请选择线路')
  if (form.payMethod == null) errors.push('请选择支付方式')
  if (form.freightFee == null) {
    errors.push('运费不能为空')
  } else if (form.freightFee < 0) {
    errors.push('运费不能小于0')
  }
  if (form.deliveryMethod == null) {
    // 未选送货方式只报这一条，不叠加送货费报错
    errors.push('请选择送货方式')
  } else if (deliveryFeeRequired.value) {
    if (form.deliveryFee == null) {
      errors.push('送货方式为送货或大车直送时，送货费不能为空')
    } else if (form.deliveryFee < 0) {
      errors.push('送货费不能小于0')
    }
  }
  form.goodsList.forEach((row, index) => {
    const prefix = `第${index + 1}行货物`
    if (!row.goodsName.trim()) errors.push(`${prefix}名称不能为空`)
    if (!row.quantity || row.quantity < 1) errors.push(`${prefix}件数必须≥1`)
    if (!row.weight || row.weight <= 0) errors.push(`${prefix}重量必须>0`)
    if (!row.volume || row.volume <= 0) errors.push(`${prefix}体积必须>0`)
  })
  if (form.needReceipt === 1 && (!form.receiptCount || form.receiptCount < 1)) {
    errors.push('是否回单为是时，回单份数必须≥1')
  }
  if (cents(form.codAmount) > 0) {
    if (!form.payeeName.trim()) errors.push('代收货款大于0时，收款人不能为空')
    if (!form.bankName.trim()) errors.push('代收货款大于0时，开户行不能为空')
    if (!form.bankCardNo.trim()) errors.push('代收货款大于0时，银行卡号不能为空')
  }
  if (form.payMethod === 5 && diffCents.value !== 0) {
    errors.push(`多笔付四项合计与运费合计不一致，差额 ${money(diffCents.value)} 未分配`)
  }
  return errors
}

function buildPayload(): WaybillSavePayload {
  return {
    originDeptId: form.originDeptId,
    relatedNo: form.relatedNo.trim() || undefined,
    salesId: form.salesId,
    salesName: form.salesName.trim() || undefined,
    bizType: form.bizType,
    destDistrictCode: form.destDistrictCode,
    routeKeys: selectedRoute.value?.routeKeys || [],
    senderName: form.senderName.trim(),
    senderPhone: form.senderPhone.trim(),
    senderCompany: form.senderCompany.trim() || undefined,
    senderAddress: form.senderAddress.trim(),
    receiverName: form.receiverName.trim(),
    receiverPhone: form.receiverPhone.trim(),
    receiverCompany: form.receiverCompany.trim() || undefined,
    receiverProvinceCode: form.receiverProvinceCode || undefined,
    receiverCityCode: form.receiverCityCode || undefined,
    receiverDistrictCode: form.receiverDistrictCode || undefined,
    receiverAddress: form.receiverAddress.trim(),
    goodsList: form.goodsList.map((row) => ({
      goodsName: row.goodsName.trim(),
      goodsType: row.goodsType.trim() || undefined,
      packageType: row.packageType.trim() || undefined,
      quantity: Number(row.quantity ?? 0),
      weight: Number(row.weight ?? 0),
      volume: Number(row.volume ?? 0),
      goodsRemark: row.goodsRemark.trim() || undefined
    })),
    payMethod: form.payMethod,
    freightFee: form.freightFee == null ? undefined : Number(form.freightFee),
    deliveryFee: form.deliveryFee == null ? undefined : Number(form.deliveryFee),
    pickupFee: Number(form.pickupFee ?? 0),
    otherFee: Number(form.otherFee ?? 0),
    receiptFee: Number(form.receiptFee ?? 0),
    makeBillFee: Number(form.makeBillFee ?? 0),
    insuranceFee: Number(form.insuranceFee ?? 0),
    declaredValue: Number(form.declaredValue ?? 0),
    cashBack: Number(form.cashBack ?? 0),
    oweBack: Number(form.oweBack ?? 0),
    prepaidFee: Number(form.prepaidFee ?? 0),
    collectFee: Number(form.collectFee ?? 0),
    monthlyFee: Number(form.monthlyFee ?? 0),
    returnFee: Number(form.returnFee ?? 0),
    deliveryMethod: form.deliveryMethod,
    codAmount: Number(form.codAmount ?? 0),
    payeeName: form.payeeName.trim() || undefined,
    bankName: form.bankName.trim() || undefined,
    bankCardNo: form.bankCardNo.trim() || undefined,
    needReceipt: form.needReceipt,
    receiptCount: form.needReceipt === 1 ? Number(form.receiptCount ?? 0) : 0,
    waitNotice: form.waitNotice,
    remark: form.remark.trim() || undefined
  }
}

async function save(continueNext: boolean) {
  submitted.value = true
  errorList.value = collectErrors()
  if (errorList.value.length) {
    if (formAreaRef.value) formAreaRef.value.scrollTop = 0
    return
  }
  saving.value = true
  try {
    const result = await createWaybill(buildPayload())
    ElMessage.success(`运单 ${result.waybillNo} 已生成`)
    if (continueNext) {
      resetForm()
    } else {
      resetForm()
    }
  } finally {
    saving.value = false
  }
}

function resetForm() {
  const originDeptId = form.originDeptId
  Object.assign(form, createForm())
  form.originDeptId = originDeptId
  submitted.value = false
  errorList.value = []
  selectedRoute.value = null
  routeOptions.value = []
  destOptions.value = []
  cityOptions.value = []
  districtOptions.value = []
  receiverRegionHint.value = ''
  customerName.value = ''
  customerMatched.value = false
}

/* ---------- 初始化与点击外部关闭 ---------- */
function onDocumentClick(event: MouseEvent) {
  const target = event.target as Node
  if (destFieldRef.value && !destFieldRef.value.contains(target)) {
    destOpen.value = false
  }
  if (routeFieldRef.value && !routeFieldRef.value.contains(target)) {
    routeOpen.value = false
  }
}

onMounted(async () => {
  document.addEventListener('mousedown', onDocumentClick)
  loadProvinces()
  if (route.query.tab === 'pre') {
    switchTab('pre')
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocumentClick)
  if (destTimer) window.clearTimeout(destTimer)
  if (customerTimer) window.clearTimeout(customerTimer)
})
</script>

<style scoped>
.op-page {
  height: calc(100vh - var(--header-height));
  min-width: 980px;
  box-sizing: border-box;
  padding: 6px 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow: hidden;
  background: var(--color-bg-page);
  font-size: 12px;
}

.op-top {
  height: 34px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.op-tabs {
  display: flex;
  gap: 4px;
  align-items: center;
}

.op-tab {
  height: 28px;
  padding: 0 14px;
  border: 1px solid var(--color-border-base);
  border-radius: 5px;
  background: var(--color-white);
  color: var(--color-text-regular);
  font-size: 13px;
  cursor: pointer;
}

.op-tab.active {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #fff;
}

.tab-badge {
  display: inline-block;
  margin-left: 6px;
  min-width: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--color-warning-light-4);
  color: var(--color-warning-text);
  font-size: 10px;
  line-height: 15px;
}

.op-context {
  display: flex;
  align-items: center;
  gap: 16px;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.ctx-item b {
  color: var(--color-text-primary);
  font-weight: 600;
}

.ctx-item .muted {
  color: var(--color-text-placeholder);
  font-weight: 400;
}

.op-form {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding-right: 2px;
}

.card {
  background: var(--color-white);
  border: 1px solid var(--color-border-base);
  border-radius: 6px;
  padding: 5px 10px 7px;
}

.sec-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 4px;
}

.sec-title {
  position: relative;
  padding-left: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
  line-height: 18px;
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
  gap: 18px;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.hub-tags b {
  color: var(--color-primary-dark-1);
  font-weight: 600;
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

.grid.two > .card {
  min-width: 0;
}

.span2 {
  grid-column: span 2;
}

.field {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.lbl {
  flex: 0 0 62px;
  text-align: right;
  color: var(--color-text-regular);
  font-size: 12px;
  white-space: nowrap;
}

.star {
  color: var(--color-danger);
  font-style: normal;
  margin-right: 1px;
}

.field :deep(.el-input),
.field :deep(.el-input-number) {
  flex: 1;
  min-width: 0;
}

.op-page :deep(.el-input__wrapper) {
  min-height: 28px;
  padding: 1px 8px;
  background: #fbfcfd;
}

.op-page :deep(.el-input-number .el-input__inner) {
  text-align: left;
}

.op-page :deep(.is-empty .el-input__wrapper),
.op-page :deep(.el-input.is-empty .el-input__wrapper) {
  background: #fdf7f5;
  box-shadow: 0 0 0 1px #f0d9d4 inset;
}

.op-page :deep(.el-input.is-disabled .el-input__wrapper) {
  background: var(--color-bg-subtle);
}

.region-selects {
  flex: 1;
  min-width: 0;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
}

.region-selects :deep(.el-select__wrapper) {
  min-height: 28px;
  padding: 1px 8px;
  background: #fbfcfd;
}

.region-hint {
  padding-left: 68px;
  color: var(--color-warning-text);
  font-size: 11px;
  line-height: 16px;
}

.readonly-box {
  flex: 1;
  min-width: 0;
  height: 28px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 8px;
  border-radius: 4px;
  background: var(--color-bg-subtle);
  border: 1px dashed var(--color-border-base);
  color: var(--color-text-regular);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.readonly-box .matched {
  color: var(--color-text-primary);
  font-weight: 600;
}

.matched-tag {
  padding: 0 5px;
  border-radius: 3px;
  background: var(--color-success-light-5);
  color: var(--color-success-text);
  font-size: 10px;
}

.muted {
  color: var(--color-text-placeholder);
}

.seg :deep(.el-radio-button__inner) {
  padding: 5px 10px;
  font-size: 12px;
}

.dest-row {
  grid-template-columns: 1fr 3fr;
  margin-top: 5px;
}

.dest-field,
.route-field {
  position: relative;
  align-items: center;
}

.route-box {
  flex: 1;
  min-width: 0;
  height: 32px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 8px;
  border-radius: 4px;
  border: 1px solid var(--color-border-base);
  background: #fbfcfd;
  overflow: hidden;
}

.route-box.is-empty {
  background: #fdf7f5;
  border-color: #f0d9d4;
}

.route-chain {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-weight: 600;
  color: var(--color-primary-dark-1);
}

.route-meta {
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.route-placeholder {
  color: var(--color-text-placeholder);
}

.switch-btn {
  border: none;
  background: none;
  color: var(--color-primary);
  cursor: pointer;
  font-size: 12px;
  white-space: nowrap;
}

.badge {
  padding: 0 5px;
  border-radius: 3px;
  font-size: 10px;
  line-height: 16px;
  white-space: nowrap;
}

.badge-direct {
  background: var(--color-success-light-5);
  color: var(--color-success-text);
}

.badge-out {
  background: var(--color-warning-light-5);
  color: var(--color-warning-text);
}

.pop-panel {
  position: absolute;
  left: 68px;
  top: 31px;
  z-index: 30;
  background: var(--color-white);
  border: 1px solid var(--color-border-base);
  border-radius: 6px;
  box-shadow: var(--shadow-medium);
  padding: 4px;
}

.dest-panel {
  width: 760px;
  max-height: 392px;
  overflow-y: auto;
}

.route-panel {
  right: 0;
  left: auto;
  width: 620px;
  max-height: 300px;
  overflow-y: auto;
}

.cand-item {
  display: block;
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  border-radius: 4px;
  padding: 5px 8px;
  cursor: pointer;
}

.cand-item:hover,
.cand-item.active {
  background: var(--color-bg-active);
}

.cand-item.disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.cand-line1 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 700;
  color: var(--color-primary-dark-1);
}

.cand-chain {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.cand-recommend {
  padding: 0 5px;
  border-radius: 3px;
  background: var(--color-primary-light-5);
  color: var(--color-primary);
  font-size: 10px;
  font-weight: 500;
}

.cand-none {
  color: var(--color-danger);
  font-size: 11px;
  font-weight: 400;
}

.cand-line2 {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 2px;
  color: var(--color-text-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.cand-dest {
  color: #b26a1f;
  font-weight: 600;
}

.pop-empty {
  padding: 12px;
  color: var(--color-text-placeholder);
  text-align: center;
}

.goods-sum {
  color: var(--color-text-secondary);
}

.goods-sum b {
  color: var(--color-text-primary);
}

.goods-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.goods-table th {
  height: 24px;
  padding: 0 2px;
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
  font-weight: 500;
  font-size: 11px;
  white-space: nowrap;
  overflow: hidden;
  border: 1px solid var(--color-border-light);
}

.goods-table td {
  border: 1px solid var(--color-border-light);
  padding: 2px 4px;
}

.goods-table .col-idx {
  width: 3.5%;
}

.goods-table .col-name {
  width: 22%;
}

.goods-table .col-type {
  width: 10%;
}

.goods-table .col-pack {
  width: 10%;
}

.goods-table .col-num {
  width: 10%;
}

.goods-table .col-qty {
  width: 9%;
}

.goods-table .col-remark {
  width: 21.5%;
}

.goods-table .col-op {
  width: 4%;
}

.goods-table .center {
  text-align: center;
}

.goods-table :deep(.el-input__wrapper) {
  min-height: 24px;
  padding: 0 6px;
}

/* 数值控件默认 150px 宽，会把相邻列压住；改为跟随列宽 */
.goods-table :deep(.el-input-number) {
  width: 100%;
  min-width: 0;
}

.goods-table :deep(.el-select) {
  width: 100%;
}

.goods-table :deep(.el-select__wrapper) {
  min-height: 24px;
  padding: 0 6px;
}

.goods-add {
  margin-top: 3px;
}

.fee-total {
  color: var(--color-text-secondary);
}

.fee-total b {
  color: var(--color-danger);
  font-size: 14px;
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
}

.fee-lbl {
  flex: 0 0 54px;
  text-align: right;
  color: var(--color-text-regular);
  font-size: 12px;
}

.fee-cell :deep(.el-input-number) {
  flex: 1;
  min-width: 0;
}

.std {
  flex: 0 0 34px;
  font-size: 10px;
  color: var(--color-text-placeholder);
  border-bottom: 1px dashed var(--color-border-base);
  white-space: nowrap;
}

.fee-foot {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-top: 5px;
  padding-top: 4px;
  border-top: 1px dashed var(--color-border-light);
  color: var(--color-text-secondary);
}

.fee-foot b {
  color: var(--color-text-primary);
}

.fee-foot .negative b {
  color: var(--color-danger);
}

.fee-formula {
  margin-left: auto;
  font-size: 11px;
  color: var(--color-text-placeholder);
}

.pay-block {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 6px;
  border-radius: 4px;
  background: #f6f9fd;
  border-left: 3px solid var(--color-primary-light-5);
}

.pay-block.is-empty {
  background: #fdf7f5;
  border-left-color: #e2b3ab;
  box-shadow: 0 0 0 1px #f0d9d4;
}

.pay-seg {
  flex: 1;
}

.pay-amounts {
  margin: 4px 0 3px;
}

.diff-bar {
  margin: 2px 0 5px;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 11px;
}

.diff-bar.ok {
  background: var(--color-success-light-5);
  color: var(--color-success-text);
}

.diff-bar.warn {
  background: var(--color-warning-light-5);
  color: var(--color-warning-text);
  border-left: 3px solid var(--color-warning);
}

.pay-extra {
  margin-top: 4px;
}

.cod-tip {
  color: var(--color-text-placeholder);
  font-size: 11px;
}

.op-bottom {
  flex-shrink: 0;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 10px;
  background: var(--color-white);
  border: 1px solid var(--color-border-base);
  border-radius: 6px;
}

.bottom-tip {
  color: var(--color-text-placeholder);
  font-size: 11px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.bottom-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.form-alert {
  padding: 6px 10px;
  border-radius: 4px;
  background: #fbe7e3;
  border: 1px solid #d98b83;
  color: #8f4444;
  font-size: 12px;
}

.pre-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--color-white);
  border: 1px solid var(--color-border-base);
  border-radius: 6px;
  padding: 8px 10px;
}

.pre-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.pre-tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.placeholder-note {
  color: var(--color-warning-text);
  font-size: 12px;
}

.pre-table {
  flex: 0 0 382px;
}

.pre-summary {
  margin-top: 6px;
  color: var(--color-text-secondary);
}
</style>
