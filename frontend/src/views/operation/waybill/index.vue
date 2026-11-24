<template>
  <div class="page-container">
    <div class="page-content">
      <div class="action-bar">
        <div class="action-left">
          <span class="list-hint">仅显示母单；运单号有「母」标时可查看子单清单</span>
        </div>

        <div class="action-right">
          <el-button class="tool-reset" @click="resetFilters">重置</el-button>
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

      <div class="waybill-table-wrap">
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
          persist-key="operation:waybill"
          :column-order="columnOrder"
          :column-hidden="hiddenKeys"
          :column-sortable="sortableMap"
          class="waybill-table"
          :empty-text="emptyText"
          @sort-change="handleSortChange"
        >
          <el-table-column type="index" label="序号" width="60" align="center" fixed="left" :index="indexMethod" />

          <el-table-column
            v-for="def in visibleColumns"
            :key="def.key"
            :prop="def.prop"
            :label="def.label"
            :width="def.width"
            :min-width="def.width ? undefined : 120"
            :align="def.align"
            :fixed="def.key === 'waybillNo' ? 'left' : undefined"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">{{ def.label }}</span>
                <column-filter
                  v-if="col(def.key).filterType !== 'none'"
                  v-model="filters[def.key]"
                  :type="col(def.key).filterType"
                  :filter-mode="col(def.key).filterMode"
                  :select-multiple="col(def.key).selectMultiple ?? false"
                  :text-mode="textModes[def.key]"
                  :options="filterOptionsOf(def)"
                  @update:mode="(mode: TextMatchMode) => (textModes[def.key] = mode)"
                  @change="onFilterChange(def)"
                />
              </div>
            </template>
            <template #default="{ row }">
              <waybill-cell :column="def" :row="row" @open="onWaybillOpen" />
            </template>
          </el-table-column>

          <el-table-column label="操作" width="160" fixed="right" align="center">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button link type="primary" size="small" @click="goDetail(row)">详情</el-button>
                <el-tooltip
                  :disabled="canAmend(row.status)"
                  content="仅 20 已揽收 ~ 90 末端到车 可改单"
                  placement="top"
                >
                  <span class="btn-wrap">
                    <el-button
                      link
                      type="primary"
                      size="small"
                      :disabled="!canAmend(row.status)"
                      @click="goAmend(row)"
                    >
                      改单
                    </el-button>
                  </span>
                </el-tooltip>
                <el-button
                  v-if="row.hasChildren"
                  link
                  type="primary"
                  size="small"
                  @click="openChildren(row)"
                >
                  子单({{ row.childCount }})
                </el-button>
                <el-tooltip
                  :disabled="row.status !== 900"
                  content="该运单已作废"
                  placement="top"
                >
                  <span class="btn-wrap">
                    <el-button
                      link
                      type="danger"
                      size="small"
                      :disabled="row.status === 900"
                      @click="openVoid(row)"
                    >
                      作废
                    </el-button>
                  </span>
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
        </smart-table>
      </div>
    </div>

    <waybill-children-dialog
      v-model:visible="childrenState.visible"
      :waybill-id="childrenState.id"
    />

    <waybill-void-dialog
      v-model:visible="voidState.visible"
      :waybill-id="voidState.id"
      :waybill-no="voidState.no"
      :status="voidState.status"
      :version="voidState.version"
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
import ColumnFilter from '@/components/base/ColumnFilter.vue'
import ColumnSettingTrigger from '@/components/base/ColumnSettingTrigger.vue'
import type { ColumnDef, ColumnItemState, ColumnOption } from '@/composables/useColumnConfig'
import { useColumnConfig } from '@/composables/useColumnConfig'
import type { TextMatchMode } from '@/utils/textFilter'
import {
  BIZ_TYPE_OPTIONS,
  CHILD_FLAG_OPTIONS,
  DELIVERY_METHOD_OPTIONS,
  NEED_RECEIPT_OPTIONS,
  PACKAGE_TYPES,
  PAY_METHOD_OPTIONS,
  ROUTE_END_TYPE_OPTIONS,
  WAYBILL_STATUS_OPTIONS,
  canAmend,
  formatDateTime,
  formatMoney,
  formatQuantity
} from '@/utils/operationDict'
import {
  createExportTask,
  downloadExportTask,
  getExportTask,
  getOpenDepts,
  getWaybillPage
} from '@/api/operation'
import type { ExportTask, WaybillRecord } from '@/types/operation'
import WaybillCell, { type WaybillCellColumn } from './components/WaybillCell.vue'
import WaybillChildrenDialog from './components/WaybillChildrenDialog.vue'
import WaybillVoidDialog from './components/WaybillVoidDialog.vue'

defineOptions({ name: 'OperationWaybill' })

interface WaybillColumn extends ColumnDef, WaybillCellColumn {
  filterOptions?: ColumnOption[]
  defaultHidden?: boolean
}

const router = useRouter()

/** 默认可见 19 列，列序保持 v1.14 原序（D2）；其余进列池默认隐藏 */
const schema = reactive<WaybillColumn[]>([
  { key: 'waybillNo', label: '运单号', prop: 'waybillNo', width: 170, filterType: 'text', filterMode: 'multi', locked: true },
  { key: 'status', label: '状态', prop: 'status', width: 100, filterType: 'select', selectMultiple: true, filterOptions: WAYBILL_STATUS_OPTIONS },
  { key: 'relatedNo', label: '关联单号', prop: 'relatedNo', width: 150, filterType: 'text', filterMode: 'multi' },
  { key: 'customerName', label: '客户名称', prop: 'customerName', width: 160 },
  { key: 'destName', label: '目的地', prop: 'destName', width: 180 },
  { key: 'destHubName', label: '运达网点', prop: 'destHubName', width: 150, filterType: 'select' },
  { key: 'senderName', label: '发货人', prop: 'senderName', width: 100 },
  { key: 'receiverName', label: '收货人', prop: 'receiverName', width: 100 },
  { key: 'bizType', label: '业务类型', prop: 'bizType', width: 90, filterType: 'select', selectMultiple: true, filterOptions: BIZ_TYPE_OPTIONS, dict: BIZ_TYPE_OPTIONS },
  { key: 'routeName', label: '线路', prop: 'routeName', width: 240 },
  { key: 'quantity', label: '件数', prop: 'quantity', width: 80, filterType: 'numberrange', numeric: 'number', align: 'right' },
  { key: 'weight', label: '重量', prop: 'weight', width: 100, filterType: 'numberrange', numeric: 'number', align: 'right' },
  { key: 'payMethod', label: '支付方式', prop: 'payMethod', width: 100, filterType: 'select', selectMultiple: true, filterOptions: PAY_METHOD_OPTIONS, dict: PAY_METHOD_OPTIONS },
  { key: 'freightFee', label: '运费', prop: 'freightFee', width: 110, filterType: 'numberrange', numeric: 'money', align: 'right' },
  { key: 'declaredValue', label: '声明价值', prop: 'declaredValue', width: 130, filterType: 'numberrange', numeric: 'money', align: 'right' },
  { key: 'validFreight', label: '有效运费', prop: 'validFreight', width: 120, filterType: 'numberrange', numeric: 'money', align: 'right' },
  { key: 'codAmount', label: '代收货款', prop: 'codAmount', width: 120, filterType: 'numberrange', numeric: 'money', align: 'right' },
  { key: 'createByName', label: '开单员', prop: 'createByName', width: 90 },
  { key: 'createTime', label: '开单时间', prop: 'createTime', width: 170, filterType: 'daterange' },
  { key: 'originHubName', label: '始发网点', prop: 'originHubName', width: 150, filterType: 'select', defaultHidden: true },
  { key: 'deptName', label: '开单网点', prop: 'deptName', width: 150, filterType: 'select', defaultHidden: true },
  { key: 'destProvinceName', label: '目的地省', prop: 'destProvinceName', width: 100, defaultHidden: true },
  { key: 'destCityName', label: '目的市', prop: 'destCityName', width: 100, defaultHidden: true },
  { key: 'destDistrictName', label: '目的地区县', prop: 'destDistrictName', width: 100, defaultHidden: true },
  { key: 'salesName', label: '业务员', prop: 'salesName', width: 90, defaultHidden: true },
  { key: 'senderPhone', label: '发货电话', prop: 'senderPhone', width: 130, defaultHidden: true },
  { key: 'senderAddress', label: '发货地址', prop: 'senderAddress', width: 200, defaultHidden: true },
  { key: 'customerPhone', label: '客户电话', prop: 'customerPhone', width: 130, defaultHidden: true },
  { key: 'receiverPhone', label: '收货电话', prop: 'receiverPhone', width: 130, defaultHidden: true },
  { key: 'receiverAddress', label: '收货地址', prop: 'receiverAddress', width: 220, defaultHidden: true },
  { key: 'goodsName', label: '货物名称', prop: 'goodsName', width: 140, defaultHidden: true },
  { key: 'goodsType', label: '货物类型', prop: 'goodsType', width: 100, defaultHidden: true },
  {
    key: 'packageType',
    label: '包装类型',
    prop: 'packageType',
    width: 100,
    filterType: 'select',
    filterOptions: PACKAGE_TYPES.map((item) => ({ label: item, value: item })),
    defaultHidden: true
  },
  { key: 'volume', label: '体积', prop: 'volume', width: 100, filterType: 'numberrange', numeric: 'number', align: 'right', defaultHidden: true },
  { key: 'goodsRemark', label: '货物备注', prop: 'goodsRemark', width: 200, defaultHidden: true },
  { key: 'deliveryFee', label: '送货费', prop: 'deliveryFee', width: 110, filterType: 'numberrange', numeric: 'money', align: 'right', defaultHidden: true },
  { key: 'pickupFee', label: '提货费', prop: 'pickupFee', width: 110, filterType: 'numberrange', numeric: 'money', align: 'right', defaultHidden: true },
  { key: 'otherFee', label: '其他费', prop: 'otherFee', width: 110, filterType: 'numberrange', numeric: 'money', align: 'right', defaultHidden: true },
  { key: 'receiptFee', label: '回单费', prop: 'receiptFee', width: 110, filterType: 'numberrange', numeric: 'money', align: 'right', defaultHidden: true },
  { key: 'makeBillFee', label: '制单费', prop: 'makeBillFee', width: 110, filterType: 'numberrange', numeric: 'money', align: 'right', defaultHidden: true },
  { key: 'insuranceFee', label: '保费', prop: 'insuranceFee', width: 110, filterType: 'numberrange', numeric: 'money', align: 'right', defaultHidden: true },
  { key: 'totalFee', label: '运费合计', prop: 'totalFee', width: 120, filterType: 'numberrange', numeric: 'money', align: 'right', defaultHidden: true },
  { key: 'cashBack', label: '现返', prop: 'cashBack', width: 110, filterType: 'numberrange', numeric: 'money', align: 'right', defaultHidden: true },
  { key: 'oweBack', label: '欠返', prop: 'oweBack', width: 110, filterType: 'numberrange', numeric: 'money', align: 'right', defaultHidden: true },
  { key: 'rebateTotal', label: '返款合计', prop: 'rebateTotal', width: 120, filterType: 'numberrange', numeric: 'money', align: 'right', defaultHidden: true },
  { key: 'prepaidFee', label: '现付运费', prop: 'prepaidFee', width: 120, filterType: 'numberrange', numeric: 'money', align: 'right', defaultHidden: true },
  { key: 'collectFee', label: '到付运费', prop: 'collectFee', width: 120, filterType: 'numberrange', numeric: 'money', align: 'right', defaultHidden: true },
  { key: 'monthlyFee', label: '月结运费', prop: 'monthlyFee', width: 120, filterType: 'numberrange', numeric: 'money', align: 'right', defaultHidden: true },
  { key: 'returnFee', label: '回付运费', prop: 'returnFee', width: 120, filterType: 'numberrange', numeric: 'money', align: 'right', defaultHidden: true },
  { key: 'deliveryMethod', label: '送货方式', prop: 'deliveryMethod', width: 110, filterType: 'select', selectMultiple: true, filterOptions: DELIVERY_METHOD_OPTIONS, dict: DELIVERY_METHOD_OPTIONS, defaultHidden: true },
  { key: 'payeeName', label: '收款人', prop: 'payeeName', width: 100, defaultHidden: true },
  { key: 'bankName', label: '银行名称', prop: 'bankName', width: 140, defaultHidden: true },
  { key: 'bankCardNo', label: '银行卡号', prop: 'bankCardNo', width: 180, defaultHidden: true },
  { key: 'needReceipt', label: '是否回单', prop: 'needReceipt', width: 100, filterType: 'select', selectMultiple: true, filterOptions: NEED_RECEIPT_OPTIONS, dict: NEED_RECEIPT_OPTIONS, defaultHidden: true },
  { key: 'receiptCount', label: '回单数量', prop: 'receiptCount', width: 100, filterType: 'numberrange', numeric: 'number', align: 'right', defaultHidden: true },
  { key: 'waitNotice', label: '是否等通知', prop: 'waitNotice', width: 110, filterType: 'select', selectMultiple: true, filterOptions: NEED_RECEIPT_OPTIONS, dict: NEED_RECEIPT_OPTIONS, defaultHidden: true },
  { key: 'remark', label: '备注', prop: 'remark', width: 200, defaultHidden: true },
  { key: 'isChild', label: '是否子单', prop: 'isChild', width: 100, filterType: 'select', selectMultiple: true, filterOptions: CHILD_FLAG_OPTIONS, dict: CHILD_FLAG_OPTIONS, defaultHidden: true },
  { key: 'childSeq', label: '子单序号', prop: 'childSeq', width: 96, filterType: 'numberrange', numeric: 'number', align: 'right', defaultHidden: true },
  { key: 'routeNodeCount', label: '路由节点数', prop: 'routeNodeCount', width: 100, filterType: 'numberrange', numeric: 'number', align: 'right', defaultHidden: true },
  { key: 'routeEndType', label: '路由类型', prop: 'routeEndType', width: 100, filterType: 'select', selectMultiple: true, filterOptions: ROUTE_END_TYPE_OPTIONS, dict: ROUTE_END_TYPE_OPTIONS, defaultHidden: true },
  { key: 'preOrderNo', label: '来源预开单号', prop: 'preOrderNo', width: 170, defaultHidden: true },
  { key: 'operation', label: '操作', elKey: 'label:操作', width: 160, filterType: 'none', sortable: false, locked: true }
])

const {
  states,
  loading: columnLoading,
  saving: columnSaving,
  col,
  isVisible,
  columnOrder,
  hiddenKeys,
  sortableMap,
  filterModesParam,
  load: loadColumnConfig,
  save: saveColumnConfig,
  reset: resetColumnConfig
} = useColumnConfig('operation:waybill', schema)

const tableRef = ref<InstanceType<typeof SmartTable>>()
const loading = ref(false)
const rows = ref<WaybillRecord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const summary = ref<Record<string, number>>({})
const sortState = reactive<{ sortBy?: string; sortOrder?: 'asc' | 'desc' }>({
  sortBy: undefined,
  sortOrder: undefined
})

const filters = reactive<Record<string, any>>({})
const textModes = reactive<Record<string, TextMatchMode>>({})

const emptyText = computed(() => (loading.value ? ' ' : '暂无匹配的运单'))

/** 列 key → 后端筛选字段；网点/机构下拉按 id 过滤 */
const FILTER_KEY_OVERRIDES: Record<string, string> = {
  destHubName: 'destHubId',
  originHubName: 'originHubId',
  deptName: 'deptId'
}

const visibleColumns = computed(() => schema.filter((item) => item.key !== 'operation' && isVisible(item.key)))

/** 网点/机构下拉选项：取当前用户可管理网点，避免越权 */
const hubOptions = ref<ColumnOption[]>([])
const deptOptions = ref<ColumnOption[]>([])

function filterOptionsOf(def: WaybillColumn): ColumnOption[] | undefined {
  if (def.key === 'destHubName') return hubOptions.value
  if (def.key === 'originHubName') return hubOptions.value
  if (def.key === 'deptName') return deptOptions.value
  return def.filterOptions
}

function initFilters() {
  schema.forEach((item) => {
    if (item.key === 'operation') return
    if (item.filterType === 'numberrange' || item.filterType === 'daterange') {
      filters[item.key] = []
    } else if (item.filterType === 'select' && item.selectMultiple) {
      filters[item.key] = []
    } else {
      filters[item.key] = undefined
    }
  })
}

function buildFilterModes(): string | undefined {
  const keyMap: Record<string, string> = {}
  schema.forEach((item) => {
    if (item.filterType === 'text' && item.filterMode === 'multi') {
      keyMap[item.key] = FILTER_KEY_OVERRIDES[item.key] || item.key
    }
  })
  const modes = filterModesParam(keyMap, textModes)
  return Object.keys(modes).length ? JSON.stringify(modes) : undefined
}

/** 列筛选 → 后端 f_ 参数；空值不传 */
function buildFilterParams(): Record<string, string> {
  const params: Record<string, string> = {}
  schema.forEach((item) => {
    if (item.key === 'operation' || !isVisible(item.key)) return
    const backendKey = FILTER_KEY_OVERRIDES[item.key] || item.key
    const value = filters[item.key]
    if (item.filterType === 'numberrange') {
      const [min, max] = Array.isArray(value) ? value : []
      if (min !== undefined && min !== null && min !== '' || max !== undefined && max !== null && max !== '') {
        params[`f_${backendKey}`] = `${min ?? ''}~${max ?? ''}`
      }
      return
    }
    if (item.filterType === 'daterange') {
      const [start, end] = Array.isArray(value) ? value : []
      if (start || end) params[`f_${backendKey}`] = `${start || ''}~${end || ''}`
      return
    }
    if (Array.isArray(value)) {
      if (value.length) params[`f_${backendKey}`] = value.join(',')
      return
    }
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      params[`f_${backendKey}`] = String(value).trim()
    }
  })
  const modes = buildFilterModes()
  if (modes) params.filterModes = modes
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

function indexMethod(index: number): number {
  return (page.value - 1) * pageSize.value + index + 1
}

async function loadPage() {
  loading.value = true
  try {
    const result = await getWaybillPage(buildQuery())
    rows.value = result.data || []
    total.value = result.total || 0
    summary.value = result.summary || {}
  } catch {
    rows.value = []
    total.value = 0
    summary.value = {}
  } finally {
    loading.value = false
  }
}

function handleSizeChange() {
  page.value = 1
  loadPage()
}

function handleSortChange({ prop, order }: { prop?: string; order?: 'ascending' | 'descending' | null }) {
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

let textTimer: ReturnType<typeof setTimeout> | undefined

function onFilterChange(def: WaybillColumn) {
  if (def.filterType === 'text') {
    if (textTimer) clearTimeout(textTimer)
    textTimer = setTimeout(() => {
      page.value = 1
      loadPage()
    }, 300)
    return
  }
  page.value = 1
  loadPage()
}

function resetFilters() {
  initFilters()
  Object.keys(textModes).forEach((key) => delete textModes[key])
  page.value = 1
  loadPage()
}

function summaryMethod({ columns }: { columns: Array<{ label: string }> }) {
  return columns.map((column, index) => {
    if (index === 0) return `合计（共 ${total.value} 条）`
    const key = column.label
    const def = schema.find((item) => item.label === key)
    if (!def || !def.numeric || !isVisible(def.key)) return '--'
    const value = summary.value[def.key]
    if (value === undefined || value === null) return '--'
    return def.numeric === 'money' ? formatMoney(value) : formatQuantity(value)
  })
}

/* ---------- 行操作 ---------- */
function onWaybillOpen(row: WaybillRecord) {
  if (row.hasChildren) {
    openChildren(row)
    return
  }
  goDetail(row)
}

function goDetail(row: WaybillRecord) {
  router.push(`/operation/waybill/${row.id}`)
}

function goAmend(row: WaybillRecord) {
  if (!canAmend(row.status)) return
  router.push(`/operation/waybill/${row.id}/amend`)
}

const childrenState = reactive<{ visible: boolean; id?: number }>({ visible: false, id: undefined })

function openChildren(row: WaybillRecord) {
  childrenState.id = row.id
  childrenState.visible = true
}

const voidState = reactive<{
  visible: boolean
  id?: number
  no?: string
  status?: number
  version?: string
}>({ visible: false, id: undefined, no: undefined, status: undefined, version: undefined })

function openVoid(row: WaybillRecord) {
  voidState.id = row.id
  voidState.no = row.waybillNo
  voidState.status = row.status
  voidState.version = row.updateTime
  voidState.visible = true
}

/* ---------- 统一列设置 ---------- */
function onStatesUpdate(next: ColumnItemState[]) {
  states.value = next
}

async function handleColumnSave() {
  await saveColumnConfig()
}

async function handleColumnReset() {
  await resetColumnConfig()
  tableRef.value?.resetColumnWidths()
  page.value = 1
  loadPage()
}

/* ---------- 异步导出（D8） ---------- */
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
    const columns = visibleColumns.value.map((item) => item.key)
    const result = await createExportTask({
      taskType: 'waybill',
      filters: buildFilterParams(),
      sort: { sortBy: sortState.sortBy, sortOrder: sortState.sortOrder },
      columns
    })
    ElMessage.success('已提交，可在导出任务中查看')
    await refreshExportTask(result.taskId)
    startExportPolling()
  } catch {
    // 请求层已提示（第四段后端上线前会返回 404）
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
    link.download = task.fileName || `运单列表_${task.id}.xlsx`
    link.click()
    URL.revokeObjectURL(url)
  } catch {
    // 请求层已提示（403/410）
  }
}

onMounted(async () => {
  initFilters()
  await loadColumnConfig()
  try {
    const depts = await getOpenDepts()
    const options = depts.map((item) => ({ label: item.deptName, value: item.id }))
    hubOptions.value = options
    deptOptions.value = options
  } catch {
    // 下拉无数据时仍可用文本筛选
  }
  loadPage()
})

onBeforeUnmount(() => {
  stopExportPolling()
  if (textTimer) clearTimeout(textTimer)
})
</script>

<style scoped>
.list-hint {
  color: var(--color-text-placeholder);
  font-size: 12px;
}

.waybill-table-wrap {
  flex: 1;
  min-height: 0;
}

.waybill-table :deep(.el-table__header th) {
  height: 61px;
  padding: 2px 0;
}

.waybill-table :deep(.el-table__header th .cell) {
  overflow: visible;
  line-height: 1.4;
}

.waybill-table :deep(.el-table__body td) {
  height: 42px;
  padding: 0;
}

/* 合计行贴底：表格固定高度时 footer 固定在底部 */
.waybill-table :deep(.el-table__footer-wrapper) {
  position: sticky;
  bottom: 0;
  z-index: 2;
}

.waybill-table :deep(.el-table__footer td) {
  height: 36px;
  background: var(--color-bg-subtle);
}

.col-head {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: stretch;
}

.col-head__label {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: var(--font-weight-medium);
}

.col-head :deep(.el-date-editor) {
  width: 100%;
}

.col-head :deep(.el-range-separator) {
  flex: 0 0 auto;
  padding: 0 2px;
}

.col-head :deep(.el-range-input) {
  font-size: 12px;
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
