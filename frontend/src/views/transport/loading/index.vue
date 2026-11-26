<template>
  <div class="page-container">
    <div class="page-content">
      <div class="trip-filter-bar">
        <div class="filter-item">
          <span class="filter-label">车次号</span>
          <el-input
            v-model="filters.tripNo"
            clearable
            placeholder="请输入"
            class="filter-input"
            @keyup.enter="handleQuery"
            @clear="handleQuery"
          />
        </div>
        <div class="filter-item">
          <span class="filter-label">车次名称</span>
          <el-input
            v-model="filters.tripName"
            clearable
            placeholder="请输入"
            class="filter-input"
            @keyup.enter="handleQuery"
            @clear="handleQuery"
          />
        </div>
        <div class="filter-item">
          <span class="filter-label">车次状态</span>
          <el-select
            v-model="filters.status"
            multiple
            collapse-tags
            collapse-tags-tooltip
            clearable
            placeholder="全部"
            class="filter-select"
            @change="handleQuery"
          >
            <el-option
              v-for="item in TRIP_STATUS_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </div>
        <div class="filter-item">
          <span class="filter-label">始发网点</span>
          <el-select
            v-model="filters.originDeptId"
            filterable
            clearable
            placeholder="全部"
            class="filter-select"
            @change="handleQuery"
          >
            <el-option
              v-for="item in deptOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </div>
        <div class="filter-item">
          <span class="filter-label">目的网点</span>
          <el-select
            v-model="filters.destDeptId"
            filterable
            clearable
            placeholder="全部"
            class="filter-select"
            @change="handleQuery"
          >
            <el-option
              v-for="item in deptOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </div>
        <div class="filter-item">
          <span class="filter-label">计划发车</span>
          <el-date-picker
            v-model="filters.planDepartRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            format="YYYY-MM-DD"
            range-separator="~"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            class="filter-date"
            @change="handleQuery"
          />
        </div>
        <div class="filter-actions">
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>
      </div>

      <div class="action-bar">
        <div class="action-left">
          <el-button type="primary" @click="openCreate">创建车次</el-button>
          <el-button :disabled="selectedRows.length !== 1" @click="openEditSelected">
            修改车次
          </el-button>
        </div>

        <div class="action-right">
          <el-button class="tool-export" :icon="Download" :loading="exportSubmitting" @click="handleExport">
            导出
          </el-button>

          <el-popover v-if="exportTasks.length" placement="bottom-end" :width="420" trigger="click">
            <template #reference>
              <el-badge :value="activeExportCount" :hidden="!activeExportCount" :max="9">
                <el-button :icon="Tickets" aria-label="导出任务" />
              </el-badge>
            </template>
            <div class="export-panel">
              <div class="export-panel__head">导出任务</div>
              <div v-for="task in exportTasks" :key="task.id" class="export-task">
                <div class="export-task__main">
                  <span class="export-task__status" :class="exportStatusClass(task)">
                    {{ exportStatusText(task) }}
                  </span>
                  <span class="export-task__time">{{ formatDateTime(task.createTime) }}</span>
                </div>
                <div class="export-task__ops">
                  <el-button
                    v-if="task.status === 20"
                    link
                    type="primary"
                    size="small"
                    @click="downloadTask(task)"
                  >
                    下载
                  </el-button>
                  <el-button
                    v-else-if="task.status === 30 || task.status === 40"
                    link
                    type="primary"
                    size="small"
                    @click="handleExport"
                  >
                    重新导出
                  </el-button>
                  <span v-else class="export-task__progress">
                    {{ task.processedRows || 0 }} / {{ task.totalRows || '—' }} 行
                  </span>
                </div>
              </div>
            </div>
          </el-popover>

          <column-setting-trigger
            :schema="schema"
            :states="states"
            :loading="columnLoading"
            :saving="columnSaving"
            @update:states="onStatesUpdate"
            @save="handleColumnSave"
            @reset="handleColumnReset"
          />

          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            class="table-pager"
            size="small"
            background
            :total="total"
            :page-sizes="[20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            @current-change="loadPage"
            @size-change="handleSizeChange"
          />
        </div>
      </div>

      <div class="trip-table-wrap">
        <smart-table
          ref="tableRef"
          v-loading="loading"
          :data="rows"
          row-key="id"
          height="100%"
          border
          scrollbar-always-on
          show-overflow-tooltip
          show-summary
          :summary-method="summaryMethod"
          persist-key="transport:loading"
          :column-order="columnOrder"
          :column-hidden="hiddenKeys"
          :column-sortable="sortableMap"
          class="trip-table"
          :empty-text="emptyText"
          @sort-change="handleSortChange"
          @selection-change="onSelectionChange"
        >
          <el-table-column type="selection" width="44" fixed="left" reserve-selection />
          <el-table-column
            type="index"
            label="序号"
            width="60"
            align="center"
            fixed="left"
            :index="indexMethod"
          />

          <el-table-column
            v-for="def in visibleColumns"
            :key="def.key"
            :prop="def.prop"
            :label="def.label"
            :min-width="def.minWidth || 90"
            :align="def.align"
            :fixed="def.key === 'tripNo' ? 'left' : undefined"
          >
            <template #default="{ row }">
              <status-tag
                v-if="def.kind === 'status'"
                :type="tripStatusTagType(row.tripStatus)"
                :text="tripStatusText(row.tripStatus)"
              />
              <el-button
                v-else-if="def.kind === 'link'"
                link
                type="primary"
                size="small"
                class="link-cell"
                @click="goDetail(row)"
              >
                {{ row.tripNo }}
              </el-button>
              <span v-else>{{ cellText(def, row) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="150" fixed="right" align="center">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button link type="primary" size="small" @click="goDetail(row)">
                  {{ canLoad(row) ? '配载' : '详情' }}
                </el-button>
                <el-button
                  v-if="row.tripStatus === 20"
                  link
                  type="primary"
                  size="small"
                  @click="openAction('depart', row)"
                >
                  发车
                </el-button>
                <el-button
                  v-if="row.tripStatus === 10 || row.tripStatus === 20"
                  link
                  type="danger"
                  size="small"
                  @click="openAction('close', row)"
                >
                  关闭
                </el-button>
                <el-tooltip
                  v-if="row.tripStatus === 30"
                  :disabled="canCancelDepart(row)"
                  content="已有子车次到站，不可取消发车"
                  placement="top"
                >
                  <span class="btn-wrap">
                    <el-button
                      link
                      type="primary"
                      size="small"
                      :disabled="!canCancelDepart(row)"
                      @click="openAction('cancel', row)"
                    >
                      取消发车
                    </el-button>
                  </span>
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
        </smart-table>
      </div>
    </div>

    <trip-form-drawer
      v-model="drawer.visible"
      :mode="drawer.mode"
      :trip-id="drawer.tripId"
      @saved="onTripSaved"
    />

    <trip-action-dialog
      v-model="action.visible"
      :mode="action.mode"
      :trip="action.trip"
      @done="loadPage"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Download, Tickets } from '@element-plus/icons-vue'
import SmartTable from '@/components/base/SmartTable.vue'
import StatusTag from '@/components/base/StatusTag.vue'
import ColumnSettingTrigger from '@/components/base/ColumnSettingTrigger.vue'
import type { ColumnDef, ColumnItemState } from '@/composables/useColumnConfig'
import { useColumnConfig } from '@/composables/useColumnConfig'
import { createExportTask, downloadExportTask, getExportTask } from '@/api/operation'
import { getTripDetail, getTripOptions, getTripPage } from '@/api/transport'
import type { ExportTask } from '@/types/operation'
import type { TripDest, TripPageSummary, TripRecord } from '@/types/transport'
import {
  TRIP_STATUS_OPTIONS,
  allocMethodText,
  tripCategoryText,
  tripStatusTagType,
  tripStatusText
} from '@/utils/transportDict'
import { formatDateTime, formatMoney, formatQuantity } from '@/utils/operationDict'
import TripFormDrawer from './components/TripFormDrawer.vue'
import TripActionDialog from './components/TripActionDialog.vue'

defineOptions({ name: 'TransportLoading' })

type TripCellKind =
  | 'text'
  | 'status'
  | 'link'
  | 'datetime'
  | 'money'
  | 'quantity'
  | 'destNames'
  | 'loadRate'
  | 'category'
  | 'alloc'

interface TripColumn extends ColumnDef {
  kind?: TripCellKind
  /** 合计行取值键（后端 summary 字段） */
  summaryKey?: string
}

const router = useRouter()

/** 后端支持排序的字段（TripService#applySort 白名单） */
const SORTABLE_PROPS = new Set([
  'tripNo',
  'tripName',
  'tripStatus',
  'planDepartTime',
  'departTime',
  'planArriveTime',
  'actualArriveTime',
  'plateNo',
  'driverName',
  'totalFreight',
  'grossProfit',
  'createTime'
])

/** 默认可见 20 列（6.2）；宽度仅作为自适应下限，实际列宽由 colWidth 规则计算 */
const schema = reactive<TripColumn[]>([
  { key: 'tripNo', label: '车次编号', prop: 'tripNo', minWidth: 150, kind: 'link', filterType: 'none', locked: true },
  { key: 'tripStatus', label: '车次状态', prop: 'tripStatus', minWidth: 96, kind: 'status', filterType: 'none' },
  { key: 'tripName', label: '车次名称', prop: 'tripName', minWidth: 140, kind: 'text', filterType: 'none' },
  { key: 'originDeptName', label: '始发网点', prop: 'originDeptName', minWidth: 130, kind: 'text', filterType: 'none' },
  { key: 'destNames', label: '目的网点', prop: 'destNames', minWidth: 200, kind: 'destNames', filterType: 'none', sortable: false },
  { key: 'planDepartTime', label: '计划发车时间', prop: 'planDepartTime', minWidth: 150, kind: 'datetime', filterType: 'none' },
  { key: 'departTime', label: '发车时间', prop: 'departTime', minWidth: 150, kind: 'datetime', filterType: 'none' },
  { key: 'planArriveTime', label: '计划到站时间', prop: 'planArriveTime', minWidth: 150, kind: 'datetime', filterType: 'none' },
  { key: 'actualArriveTime', label: '实际到站时间', prop: 'actualArriveTime', minWidth: 150, kind: 'datetime', filterType: 'none' },
  { key: 'plateNo', label: '车牌号', prop: 'plateNo', minWidth: 110, kind: 'text', filterType: 'none' },
  { key: 'driverName', label: '司机', prop: 'driverName', minWidth: 90, kind: 'text', filterType: 'none' },
  { key: 'vehicleType', label: '车辆型号', prop: 'vehicleType', minWidth: 110, kind: 'text', filterType: 'none' },
  { key: 'totalQuantity', label: '总数量', prop: 'totalQuantity', minWidth: 80, kind: 'quantity', align: 'right', filterType: 'none', sortable: false, summaryKey: 'quantity' },
  { key: 'totalWeight', label: '总重量(kg)', prop: 'totalWeight', minWidth: 100, kind: 'quantity', align: 'right', filterType: 'none', sortable: false, summaryKey: 'weight' },
  { key: 'totalVolume', label: '总体积(m³)', prop: 'totalVolume', minWidth: 100, kind: 'quantity', align: 'right', filterType: 'none', sortable: false, summaryKey: 'volume' },
  { key: 'loadRate', label: '装载率', prop: 'loadRateWeight', minWidth: 130, kind: 'loadRate', align: 'right', filterType: 'none', sortable: false },
  { key: 'totalFreight', label: '总运费', prop: 'totalFreight', minWidth: 110, kind: 'money', align: 'right', filterType: 'none', summaryKey: 'totalFreight' },
  { key: 'totalValidFreight', label: '总有效运费', prop: 'totalValidFreight', minWidth: 120, kind: 'money', align: 'right', filterType: 'none', sortable: false, summaryKey: 'totalValidFreight' },
  { key: 'grossProfit', label: '毛利', prop: 'grossProfit', minWidth: 110, kind: 'money', align: 'right', filterType: 'none', summaryKey: 'grossProfit' },
  { key: 'createTime', label: '创建时间', prop: 'createTime', minWidth: 150, kind: 'datetime', filterType: 'none' },

  /* 隐藏列池（列设置可勾回） */
  { key: 'driverPhone', label: '司机电话', prop: 'driverPhone', minWidth: 120, kind: 'text', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'vehicleLoad', label: '车辆载重(kg)', prop: 'vehicleLoad', minWidth: 110, kind: 'quantity', align: 'right', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'vehicleVolume', label: '车辆容积(m³)', prop: 'vehicleVolume', minWidth: 110, kind: 'quantity', align: 'right', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'loadRateVolume', label: '体积装载率', prop: 'loadRateVolume', minWidth: 100, kind: 'loadRate', align: 'right', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'totalPrepaidFreight', label: '现付运费收入', prop: 'totalPrepaidFreight', minWidth: 120, kind: 'money', align: 'right', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'totalCollectFreight', label: '到付运费收入', prop: 'totalCollectFreight', minWidth: 120, kind: 'money', align: 'right', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'totalReturnFreight', label: '回付运费收入', prop: 'totalReturnFreight', minWidth: 120, kind: 'money', align: 'right', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'totalMonthlyFreight', label: '月结运费收入', prop: 'totalMonthlyFreight', minWidth: 120, kind: 'money', align: 'right', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'totalRebate', label: '总返款', prop: 'totalRebate', minWidth: 110, kind: 'money', align: 'right', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'allocMethod', label: '全车分摊方式', prop: 'allocMethod', minWidth: 120, kind: 'alloc', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'prepaidTransportFee', label: '现付运输费', prop: 'prepaidTransportFee', minWidth: 110, kind: 'money', align: 'right', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'returnTransportFee', label: '回付运输费', prop: 'returnTransportFee', minWidth: 110, kind: 'money', align: 'right', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'arrivalTransportFee', label: '到站付运输费', prop: 'arrivalTransportFee', minWidth: 120, kind: 'money', align: 'right', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'prepaidOilCardFee', label: '现付油卡费', prop: 'prepaidOilCardFee', minWidth: 110, kind: 'money', align: 'right', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'wholeWeighingFee', label: '整车过磅费', prop: 'wholeWeighingFee', minWidth: 110, kind: 'money', align: 'right', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'wholeInsuranceFee', label: '整车保险费', prop: 'wholeInsuranceFee', minWidth: 110, kind: 'money', align: 'right', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'bulkLoadFee', label: '分拨装车费', prop: 'bulkLoadFee', minWidth: 110, kind: 'money', align: 'right', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'bulkUnloadFee', label: '分拨卸车费', prop: 'bulkUnloadFee', minWidth: 110, kind: 'money', align: 'right', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'bulkOtherFee', label: '分拨其他费', prop: 'bulkOtherFee', minWidth: 110, kind: 'money', align: 'right', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'arrivalUnloadFee', label: '到站卸车费', prop: 'arrivalUnloadFee', minWidth: 110, kind: 'money', align: 'right', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'arrivalOtherFee', label: '到站其他费', prop: 'arrivalOtherFee', minWidth: 110, kind: 'money', align: 'right', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'totalCostFee', label: '总成本费用', prop: 'totalCostFee', minWidth: 110, kind: 'money', align: 'right', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'totalPickupCost', label: '总提货费', prop: 'totalPickupCost', minWidth: 100, kind: 'money', align: 'right', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'totalDeliveryFee', label: '总派送费', prop: 'totalDeliveryFee', minWidth: 100, kind: 'money', align: 'right', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'totalTransferFee', label: '总外转费', prop: 'totalTransferFee', minWidth: 100, kind: 'money', align: 'right', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'createByName', label: '创建人', prop: 'createByName', minWidth: 90, kind: 'text', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'updateTime', label: '更新时间', prop: 'updateTime', minWidth: 150, kind: 'datetime', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'updateByName', label: '更新人', prop: 'updateByName', minWidth: 90, kind: 'text', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'closeReason', label: '关闭原因', prop: 'closeReason', minWidth: 200, kind: 'text', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'closeTime', label: '关闭时间', prop: 'closeTime', minWidth: 150, kind: 'datetime', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'closeByName', label: '关闭人', prop: 'closeByName', minWidth: 90, kind: 'text', filterType: 'none', sortable: false, defaultHidden: true },
  { key: 'remark', label: '备注', prop: 'remark', minWidth: 200, kind: 'text', filterType: 'none', sortable: false, defaultHidden: true }
])

const {
  states,
  loading: columnLoading,
  saving: columnSaving,
  isVisible,
  columnOrder,
  hiddenKeys,
  load: loadColumnConfig,
  save: saveColumnConfig,
  reset: resetColumnConfig
} = useColumnConfig('transport.loading', schema)

/** 仅后端白名单字段可排序，避免列设置误开排序后静默按创建时间排序 */
const sortableMap = computed(() => {
  const result: Record<string, boolean> = {}
  states.value.forEach((item) => {
    const def = schema.find((entry) => entry.key === item.key)
    const key = def?.prop || def?.elKey || item.key
    result[key] = item.sortable && (!def?.prop || SORTABLE_PROPS.has(def.prop))
  })
  return result
})

const tableRef = ref<InstanceType<typeof SmartTable>>()
const loading = ref(false)
const rows = ref<TripRecord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const summary = ref<Partial<TripPageSummary>>({})
const selectedRows = ref<TripRecord[]>([])
const sortState = reactive<{ sortBy?: string; sortOrder?: 'asc' | 'desc' }>({
  sortBy: undefined,
  sortOrder: undefined
})

const filters = reactive<{
  tripNo: string
  tripName: string
  status: number[]
  originDeptId: number | undefined
  destDeptId: number | undefined
  planDepartRange: string[]
}>({
  tripNo: '',
  tripName: '',
  status: [],
  originDeptId: undefined,
  destDeptId: undefined,
  planDepartRange: []
})

const deptOptions = ref<Array<{ id: number; name: string }>>([])

/** 目的网点/子车次状态按车次懒加载并缓存，列表接口本身不返回目的网点名称 */
interface DestInfo {
  names: string[]
  dests: TripDest[]
}
const destInfo = reactive<Record<number, DestInfo>>({})
const destLoading = new Set<number>()
let destToken = 0

const emptyText = computed(() => (loading.value ? ' ' : '暂无符合条件的车次'))

const visibleColumns = computed(() => schema.filter((item) => isVisible(item.key)))

const drawer = reactive<{
  visible: boolean
  mode: 'create' | 'edit'
  tripId?: number
}>({ visible: false, mode: 'create', tripId: undefined })

const action = reactive<{
  visible: boolean
  mode: 'depart' | 'cancel' | 'close'
  trip: TripRecord | null
}>({ visible: false, mode: 'depart', trip: null })

/* ------------------------------ 列渲染 ------------------------------ */

function cellText(def: TripColumn, row: TripRecord): string {
  const value = row[def.prop as keyof TripRecord]
  switch (def.kind) {
    case 'datetime':
      return formatDateTime(value as string | undefined)
    case 'money':
      return formatMoney(value as number | undefined)
    case 'quantity':
      return formatQuantity(value as number | undefined)
    case 'destNames':
      return destNameOf(row)
    case 'loadRate':
      return loadRateText(row, def.prop === 'loadRateVolume')
    case 'category':
      return tripCategoryText(row.tripCategory)
    case 'alloc':
      return allocMethodText(row.allocMethod)
    default:
      return value === undefined || value === null || value === '' ? '—' : String(value)
  }
}

function destNameOf(row: TripRecord): string {
  const names = destInfo[row.id]?.names
  return names && names.length ? names.join('、') : '—'
}

function loadRateText(row: TripRecord, volume: boolean): string {
  const value = volume ? row.loadRateVolume : row.loadRateWeight
  const capacity = volume ? row.vehicleVolume : row.vehicleLoad
  if (!capacity) return '—'
  if (value === undefined || value === null) return '—'
  return `${formatQuantity(value)}%`
}

function canLoad(row: TripRecord): boolean {
  return row.tripStatus === 10 || row.tripStatus === 20
}

function canCancelDepart(row: TripRecord): boolean {
  if (row.tripStatus !== 30) return false
  const info = destInfo[row.id]
  if (!info || !info.dests.length) return false
  return info.dests.every((item) => item.subStatus < 30)
}

function indexMethod(index: number): number {
  return (page.value - 1) * pageSize.value + index + 1
}

/* ------------------------------ 查询 ------------------------------ */

function buildFilterParams(): Record<string, string> {
  const params: Record<string, string> = {}
  if (filters.tripNo.trim()) params.tripNo = filters.tripNo.trim()
  if (filters.tripName.trim()) params.tripName = filters.tripName.trim()
  if (filters.status.length) params.status = filters.status.join(',')
  if (filters.originDeptId) params.originDeptId = String(filters.originDeptId)
  if (filters.destDeptId) params.destDeptId = String(filters.destDeptId)
  const [start, end] = filters.planDepartRange || []
  if (start) params.planDepartStart = start
  if (end) params.planDepartEnd = end
  return params
}

function buildQuery(): Record<string, unknown> {
  return {
    ...buildFilterParams(),
    page: page.value,
    pageSize: pageSize.value,
    sortBy: sortState.sortBy,
    sortOrder: sortState.sortOrder
  }
}

async function loadPage() {
  loading.value = true
  try {
    const result = await getTripPage(buildQuery())
    rows.value = result.records || []
    total.value = result.total || 0
    summary.value = result.summary || {}
    selectedRows.value = []
    tableRef.value?.getTable()?.clearSelection?.()
    hydrateDests(rows.value)
  } catch {
    rows.value = []
    total.value = 0
    summary.value = {}
  } finally {
    loading.value = false
  }
}

/** 目的网点与子车次状态：列表接口不返回，按行拉详情并缓存 */
async function hydrateDests(list: TripRecord[]) {
  const token = ++destToken
  const targets = list.filter((row) => !destInfo[row.id] && !destLoading.has(row.id))
  targets.forEach((row) => destLoading.add(row.id))
  await Promise.allSettled(
    targets.map(async (row) => {
      try {
        const detail = await getTripDetail(row.id)
        if (token !== destToken) return
        destInfo[row.id] = {
          names: detail.dests.map((item) => item.destDeptName).filter(Boolean),
          dests: detail.dests
        }
      } catch {
        // 单行详情失败不影响列表，目的网点显示 —
      } finally {
        destLoading.delete(row.id)
      }
    })
  )
}

function handleSizeChange() {
  page.value = 1
  loadPage()
}

function handleQuery() {
  page.value = 1
  loadPage()
}

function resetFilters() {
  filters.tripNo = ''
  filters.tripName = ''
  filters.status = []
  filters.originDeptId = undefined
  filters.destDeptId = undefined
  filters.planDepartRange = []
  sortState.sortBy = undefined
  sortState.sortOrder = undefined
  tableRef.value?.getTable()?.clearSort?.()
  page.value = 1
  loadPage()
}

function handleSortChange({
  prop,
  order
}: {
  prop?: string
  order?: 'ascending' | 'descending' | null
}) {
  if (!order || !prop) {
    sortState.sortBy = undefined
    sortState.sortOrder = undefined
  } else {
    sortState.sortBy = prop
    sortState.sortOrder = order === 'ascending' ? 'asc' : 'desc'
  }
  page.value = 1
  loadPage()
}

function onSelectionChange(selection: TripRecord[]) {
  selectedRows.value = selection
}

/** 合计行：仅汇总后端 summary 提供且列标注过的字段 */
function summaryMethod({ columns }: { columns: Array<{ label: string }> }) {
  return columns.map((column) => {
    if (!column.label) return ''
    if (column.label === '序号') return '合计'
    const def = schema.find((item) => item.label === column.label)
    if (!def?.summaryKey) return ''
    const value = summary.value[def.summaryKey as keyof TripPageSummary]
    if (value === undefined || value === null) return '—'
    if (def.summaryKey === 'quantity' || def.summaryKey === 'weight' || def.summaryKey === 'volume') {
      return formatQuantity(value)
    }
    return formatMoney(value)
  })
}

/* ------------------------------ 行操作 ------------------------------ */

function goDetail(row: TripRecord) {
  router.push({ path: '/transport/loading/detail', query: { id: row.id } })
}

function openAction(mode: 'depart' | 'cancel' | 'close', row: TripRecord) {
  action.mode = mode
  action.trip = row
  action.visible = true
}

/* ------------------------------ 抽屉 ------------------------------ */

function openCreate() {
  drawer.mode = 'create'
  drawer.tripId = undefined
  drawer.visible = true
}

function openEditSelected() {
  if (selectedRows.value.length !== 1) return
  const row = selectedRows.value[0]
  if (row.tripStatus !== 10 && row.tripStatus !== 20) {
    ElMessage.warning('仅车次计划/装车配载状态可修改')
    return
  }
  drawer.mode = 'edit'
  drawer.tripId = row.id
  drawer.visible = true
}

function onTripSaved(payload: { id: number }) {
  if (drawer.mode === 'create') {
    router.push({ path: '/transport/loading/detail', query: { id: payload.id } })
    return
  }
  loadPage()
}

/* ------------------------------ 列设置 ------------------------------ */

function onStatesUpdate(next: ColumnItemState[]) {
  states.value = next
}

async function handleColumnSave() {
  await saveColumnConfig()
  loadPage()
}

async function handleColumnReset() {
  await resetColumnConfig()
  tableRef.value?.resetColumnWidths()
  page.value = 1
  loadPage()
}

/* ------------------------------ 异步导出（taskType=trip） ------------------------------ */

/** 导出后端支持的列 key，避免把页面独有列传给导出被忽略 */
const EXPORT_KEYS = new Set([
  'tripNo',
  'tripName',
  'tripStatus',
  'originDeptName',
  'destNames',
  'plateNo',
  'driverName',
  'planDepartTime',
  'departTime',
  'totalWaybillCount',
  'totalQuantity',
  'totalWeight',
  'totalVolume',
  'totalFreight',
  'totalRebate',
  'totalCostFee',
  'grossProfit',
  'createTime'
])

const exportTasks = ref<ExportTask[]>([])
const exportSubmitting = ref(false)
let exportTimer: number | undefined

const activeExportCount = computed(
  () => exportTasks.value.filter((item) => item.status === 0 || item.status === 10).length
)

async function handleExport() {
  if (exportSubmitting.value) return
  exportSubmitting.value = true
  try {
    const columns = visibleColumns.value
      .map((item) => item.key)
      .filter((key) => EXPORT_KEYS.has(key))
    const result = await createExportTask({
      taskType: 'trip',
      filters: buildFilterParams(),
      sort: { sortBy: sortState.sortBy, sortOrder: sortState.sortOrder },
      columns
    })
    ElMessage.success('已提交，可在导出任务中查看')
    await refreshExportTask(result.taskId)
    startExportPolling()
  } catch {
    // 请求层按响应体 code 提示
  } finally {
    exportSubmitting.value = false
  }
}

async function refreshExportTask(id: number) {
  try {
    const task = await getExportTask(id)
    const index = exportTasks.value.findIndex((item) => item.id === id)
    if (index >= 0) exportTasks.value[index] = task
    else exportTasks.value.unshift(task)
  } catch {
    // 单次轮询失败不打断列表
  }
}

function startExportPolling() {
  if (exportTimer) return
  exportTimer = window.setInterval(async () => {
    if (document.hidden) return
    const active = exportTasks.value.filter((item) => item.status === 0 || item.status === 10)
    for (const task of active) {
      await refreshExportTask(task.id)
    }
    if (!exportTasks.value.some((item) => item.status === 0 || item.status === 10)) {
      stopExportPolling()
    }
  }, 3000)
}

function stopExportPolling() {
  if (exportTimer) {
    clearInterval(exportTimer)
    exportTimer = undefined
  }
}

function exportStatusText(task: ExportTask): string {
  switch (task.status) {
    case 0:
      return '排队中'
    case 10:
      return `生成中（已处理 ${task.processedRows || 0} 行）`
    case 20:
      return '已完成'
    case 30:
      return `失败：${task.failReason || '未知原因'}`
    case 40:
      return '已过期'
    default:
      return '未知状态'
  }
}

function exportStatusClass(task: ExportTask): string {
  if (task.status === 20) return 'is-success'
  if (task.status === 30) return 'is-danger'
  if (task.status === 40) return 'is-muted'
  return 'is-running'
}

async function downloadTask(task: ExportTask) {
  try {
    const blob = await downloadExportTask(task.id)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = task.fileName || `车次列表_${task.id}.xlsx`
    link.click()
    URL.revokeObjectURL(url)
  } catch {
    // 请求层已提示（403/410）
  }
}

onMounted(async () => {
  await loadColumnConfig()
  try {
    const options = await getTripOptions('dept')
    deptOptions.value = options
      .filter((item) => !!item.name)
      .map((item) => ({ id: item.id, name: item.name as string }))
  } catch {
    // 下拉失败仍可用文本筛选
  }
  loadPage()
})

onBeforeUnmount(() => {
  stopExportPolling()
})
</script>

<style scoped>
.trip-filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 16px;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px dashed var(--color-border-light);
}

.filter-item {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.filter-label {
  flex: none;
  color: var(--color-text-secondary);
  font-size: var(--font-size-extra-small);
  white-space: nowrap;
}

.filter-input {
  width: 150px;
}

.filter-select {
  width: 150px;
}

.filter-date {
  width: 250px;
}

.filter-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-left: auto;
}

.trip-table-wrap {
  flex: 1;
  min-height: 0;
}

.trip-table :deep(.el-table__header th) {
  height: 48px;
  padding: 2px 0;
}

.trip-table :deep(.el-table__body td) {
  height: 42px;
  padding: 0;
}

.trip-table :deep(.el-table__footer-wrapper) {
  position: sticky;
  bottom: 0;
  z-index: 2;
}

.trip-table :deep(.el-table__footer td) {
  height: 36px;
  background: var(--color-bg-subtle);
}

.link-cell {
  font-family: var(--font-family-number);
}

.row-actions {
  display: flex;
  justify-content: center;
  flex-wrap: nowrap;
  gap: 0;
}

.row-actions :deep(.el-button) {
  flex-shrink: 0;
}

.row-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.btn-wrap {
  display: inline-flex;
}

.table-pager {
  margin-left: 4px;
}

.export-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.export-panel__head {
  padding-bottom: 6px;
  border-bottom: 1px solid var(--color-border-light);
  color: var(--color-text-secondary);
  font-size: var(--font-size-extra-small);
}

.export-task {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 2px;
  border-bottom: 1px dashed var(--color-border-light);
}

.export-task:last-child {
  border-bottom: none;
}

.export-task__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.export-task__status {
  font-size: 12px;
  color: var(--color-text-primary);
}

.export-task__status.is-success {
  color: var(--color-success);
}

.export-task__status.is-danger {
  color: var(--color-danger);
}

.export-task__status.is-muted {
  color: var(--color-text-placeholder);
}

.export-task__status.is-running {
  color: var(--color-primary);
}

.export-task__time,
.export-task__progress {
  font-size: 11px;
  color: var(--color-text-secondary);
  font-family: var(--font-family-number);
}

.export-task__ops {
  flex: none;
}
</style>
