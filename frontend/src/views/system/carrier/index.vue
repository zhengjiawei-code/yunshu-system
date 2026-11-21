<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">承运商管理</h2>
        <p class="page-header__desc">维护外转/直达路由使用的承运商主数据，归属机构由创建人主机构自动写入</p>
      </div>
    </div>

    <div class="page-content">
      <div class="action-bar">
        <div class="action-left">
          <el-button type="primary" @click="openCreate">新增承运商</el-button>
        </div>

        <div class="action-right">
          <el-button @click="resetFilters">重置</el-button>
          <el-button :icon="Download" @click="handleExport">导出</el-button>

          <el-popover placement="bottom-end" :width="560" trigger="click">
            <template #reference>
              <el-button :icon="Setting">列设置</el-button>
            </template>
            <column-setting
              :schema="schema"
              :states="states"
              :loading="columnLoading"
              :saving="columnSaving"
              @update:states="onStatesUpdate"
              @save="handleColumnSave"
              @reset="handleColumnReset"
            />
          </el-popover>

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

      <div class="carrier-table-wrap">
        <smart-table
          ref="tableRef"
          v-loading="loading"
          :data="rows"
          row-key="id"
          height="100%"
          :empty-text="'暂无匹配的承运商'"
          persist-key="system:carrier"
          :column-order="columnOrder"
          :column-hidden="hiddenKeys"
          :column-sortable="sortableMap"
          show-summary
          :summary-method="summaryMethod"
          show-overflow-tooltip
          scrollbar-always-on
          class="carrier-table"
          @sort-change="handleSortChange"
        >
          <el-table-column type="index" label="序号" width="64" align="center" :index="rowIndex" />

          <el-table-column
            v-if="isVisible('carrierCode')"
            prop="carrierCode"
            label="承运商编码"
            min-width="150"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">承运商编码</span>
                <column-filter
                  v-if="col('carrierCode').filterType !== 'none'"
                  v-model="filters.carrierCode"
                  :type="col('carrierCode').filterType"
                  :filter-mode="col('carrierCode').filterMode"
                  :text-mode="textModes.carrierCode"
                  :options="col('carrierCode').filterOptions"
                  @update:mode="(mode: TextMatchMode) => (textModes.carrierCode = mode)"
                  @change="onTextFilter"
                />
              </div>
            </template>
            <template #default="{ row }">
              <span class="mono">{{ row.carrierCode }}</span>
            </template>
          </el-table-column>

          <el-table-column
            v-if="isVisible('carrierName')"
            prop="carrierName"
            label="承运商名称"
            min-width="200"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">承运商名称</span>
                <column-filter
                  v-if="col('carrierName').filterType !== 'none'"
                  v-model="filters.carrierName"
                  :type="col('carrierName').filterType"
                  :filter-mode="col('carrierName').filterMode"
                  :text-mode="textModes.carrierName"
                  :options="col('carrierName').filterOptions"
                  @update:mode="(mode: TextMatchMode) => (textModes.carrierName = mode)"
                  @change="onTextFilter"
                />
              </div>
            </template>
          </el-table-column>

          <el-table-column
            v-if="isVisible('contact')"
            prop="contact"
            label="联系人"
            min-width="130"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">联系人</span>
                <column-filter
                  v-if="col('contact').filterType !== 'none'"
                  v-model="filters.contact"
                  :type="col('contact').filterType"
                  :filter-mode="col('contact').filterMode"
                  :text-mode="textModes.contact"
                  :options="col('contact').filterOptions"
                  @update:mode="(mode: TextMatchMode) => (textModes.contact = mode)"
                  @change="onTextFilter"
                />
              </div>
            </template>
          </el-table-column>

          <el-table-column
            v-if="isVisible('phone')"
            prop="phone"
            label="联系电话"
            min-width="150"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">联系电话</span>
                <column-filter
                  v-if="col('phone').filterType !== 'none'"
                  v-model="filters.phone"
                  :type="col('phone').filterType"
                  :filter-mode="col('phone').filterMode"
                  :text-mode="textModes.phone"
                  :options="col('phone').filterOptions"
                  @update:mode="(mode: TextMatchMode) => (textModes.phone = mode)"
                  @change="onTextFilter"
                />
              </div>
            </template>
          </el-table-column>

          <el-table-column
            v-if="isVisible('deptName')"
            prop="deptName"
            label="归属机构"
            min-width="180"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">归属机构</span>
                <column-filter
                  v-if="col('deptName').filterType !== 'none'"
                  v-model="filters.deptId"
                  :type="col('deptName').filterType"
                  :select-multiple="col('deptName').selectMultiple === true"
                  :options="deptOptions"
                  @change="onFilterChange"
                />
              </div>
            </template>
            <template #default="{ row }">{{ row.deptName || '—' }}</template>
          </el-table-column>

          <el-table-column
            v-if="isVisible('status')"
            prop="status"
            label="状态"
            min-width="120"
            align="center"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">状态</span>
                <column-filter
                  v-if="col('status').filterType !== 'none'"
                  v-model="filters.status"
                  :type="col('status').filterType"
                  :select-multiple="col('status').selectMultiple === true"
                  :options="col('status').filterOptions"
                  @change="onFilterChange"
                />
              </div>
            </template>
            <template #default="{ row }">
              <status-tag :type="row.status ? 'primary' : 'info'" :text="row.status ? '启用' : '停用'" />
            </template>
          </el-table-column>

          <el-table-column
            v-if="isVisible('sortOrder')"
            prop="sortOrder"
            label="排序号"
            min-width="110"
            align="right"
          />

          <el-table-column
            v-if="isVisible('updateByName')"
            prop="updateByName"
            label="修改人"
            min-width="130"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">修改人</span>
                <column-filter
                  v-if="col('updateByName').filterType !== 'none'"
                  v-model="filters.updateByName"
                  :type="col('updateByName').filterType"
                  :filter-mode="col('updateByName').filterMode"
                  :text-mode="textModes.updateByName"
                  :options="col('updateByName').filterOptions"
                  @update:mode="(mode: TextMatchMode) => (textModes.updateByName = mode)"
                  @change="onTextFilter"
                />
              </div>
            </template>
            <template #default="{ row }">{{ row.updateByName || '—' }}</template>
          </el-table-column>

          <el-table-column
            v-if="isVisible('updateTime')"
            prop="updateTime"
            label="修改时间"
            min-width="180"
            align="center"
          />

          <el-table-column v-if="isVisible('remark')" prop="remark" label="备注" min-width="160">
            <template #header>
              <div class="col-head">
                <span class="col-head__label">备注</span>
                <column-filter
                  v-if="col('remark').filterType !== 'none'"
                  v-model="filters.remark"
                  :type="col('remark').filterType"
                  :filter-mode="col('remark').filterMode"
                  :text-mode="textModes.remark"
                  :options="col('remark').filterOptions"
                  @update:mode="(mode: TextMatchMode) => (textModes.remark = mode)"
                  @change="onTextFilter"
                />
              </div>
            </template>
            <template #default="{ row }">{{ row.remark || '—' }}</template>
          </el-table-column>

          <el-table-column
            v-if="isVisible('operation')"
            label="操作"
            width="120"
            fixed="right"
            align="right"
          >
            <template #default="{ row }">
              <div class="row-actions">
                <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </smart-table>
      </div>
    </div>

    <carrier-form-drawer
      v-model="drawer.visible"
      :mode="drawer.mode"
      :record="drawer.record"
      @saved="loadPage"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Setting } from '@element-plus/icons-vue'
import type { CarrierQuery, CarrierRecord } from '@/types/carrier'
import type { ManageScopeNode } from '@/types/dept'
import type { ColumnDef, ColumnItemState } from '@/composables/useColumnConfig'
import { deleteCarrier, getCarrierPage } from '@/api/system/carrier'
import { getManageScope } from '@/api/system/dept'
import { useColumnConfig } from '@/composables/useColumnConfig'
import { isTextMatchMode, type TextMatchMode } from '@/utils/textFilter'
import StatusTag from '@/components/base/StatusTag.vue'
import SmartTable from '@/components/base/SmartTable.vue'
import ColumnFilter from '@/components/base/ColumnFilter.vue'
import ColumnSetting from '@/components/base/ColumnSetting.vue'
import CarrierFormDrawer from './components/CarrierFormDrawer.vue'

defineOptions({ name: 'SystemCarrier' })

const schema = reactive<ColumnDef[]>([
  { key: 'carrierCode', label: '承运商编码', prop: 'carrierCode', filterType: 'text', locked: true },
  { key: 'carrierName', label: '承运商名称', prop: 'carrierName', filterType: 'text' },
  { key: 'contact', label: '联系人', prop: 'contact', filterType: 'text' },
  { key: 'phone', label: '联系电话', prop: 'phone', filterType: 'text' },
  { key: 'deptName', label: '归属机构', prop: 'deptName', filterType: 'select', filterOptions: [] },
  {
    key: 'status',
    label: '状态',
    prop: 'status',
    filterType: 'select',
    filterOptions: [
      { label: '启用', value: true },
      { label: '停用', value: false }
    ]
  },
  // 排序号/修改时间后端未提供区间筛选参数，保持无筛选，避免前端无效控件
  { key: 'sortOrder', label: '排序号', prop: 'sortOrder', filterType: 'none', align: 'right' },
  { key: 'updateByName', label: '修改人', prop: 'updateByName', filterType: 'text' },
  { key: 'updateTime', label: '修改时间', prop: 'updateTime', filterType: 'none', align: 'center' },
  { key: 'remark', label: '备注', prop: 'remark', filterType: 'text' },
  {
    key: 'operation',
    label: '操作',
    elKey: 'label:操作',
    filterType: 'none',
    sortable: false,
    locked: true
  }
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
  load: loadColumnConfig,
  save: saveColumnConfig,
  reset: resetColumnConfig,
  defaultFiltersRaw,
  filterModesParam
} = useColumnConfig('system:carrier', schema)

const tableRef = ref<InstanceType<typeof SmartTable>>()
const loading = ref(false)
const rows = ref<CarrierRecord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const scopeTree = ref<ManageScopeNode[]>([])
const deptOptions = computed(() => flattenDeptOptions(scopeTree.value))

const filters = reactive<{
  carrierCode: string
  carrierName: string
  contact: string
  phone: string
  deptId: number | number[] | undefined
  status: boolean | boolean[] | undefined
  updateByName: string
  remark: string
}>({
  carrierCode: '',
  carrierName: '',
  contact: '',
  phone: '',
  deptId: undefined,
  status: undefined,
  updateByName: '',
  remark: ''
})

/** 文本列运行时匹配方式：由表头多模式控件写入 */
const textModes = reactive<Record<string, TextMatchMode>>({})

const FILTER_FIELD_MAP: Record<string, string> = {
  carrierCode: 'carrierCode',
  carrierName: 'carrierName',
  contact: 'contact',
  phone: 'phone',
  updateByName: 'updateByName',
  remark: 'remark'
}

function buildFilterModes(): string | undefined {
  const modes = filterModesParam(FILTER_FIELD_MAP, textModes)
  return Object.keys(modes).length ? JSON.stringify(modes) : undefined
}

const sortState = reactive<{ sortBy?: string; sortOrder?: 'asc' | 'desc' }>({
  sortBy: undefined,
  sortOrder: undefined
})

const drawer = reactive<{
  visible: boolean
  mode: 'create' | 'edit'
  record: CarrierRecord | null
}>({
  visible: false,
  mode: 'create',
  record: null
})

function rowIndex(index: number) {
  return (page.value - 1) * pageSize.value + index + 1
}

function buildQuery(pageNum = page.value, size = pageSize.value): CarrierQuery {
  return {
    carrierCode: isVisible('carrierCode') ? filters.carrierCode.trim() || undefined : undefined,
    carrierName: isVisible('carrierName') ? filters.carrierName.trim() || undefined : undefined,
    contact: isVisible('contact') ? filters.contact.trim() || undefined : undefined,
    phone: isVisible('phone') ? filters.phone.trim() || undefined : undefined,
    deptId: isVisible('deptName') ? filters.deptId : undefined,
    status: isVisible('status') ? filters.status : undefined,
    updateByName: isVisible('updateByName') ? filters.updateByName.trim() || undefined : undefined,
    remark: isVisible('remark') ? filters.remark.trim() || undefined : undefined,
    filterModes: buildFilterModes(),
    sortBy: sortState.sortBy,
    sortOrder: sortState.sortOrder,
    pageNum,
    pageSize: size
  }
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

function onTextFilter() {
  if (textTimer) clearTimeout(textTimer)
  textTimer = setTimeout(() => {
    page.value = 1
    loadPage()
  }, 300)
}

function onFilterChange() {
  page.value = 1
  loadPage()
}

async function loadPage() {
  loading.value = true
  try {
    const result = await getCarrierPage(buildQuery())
    rows.value = result.records
    total.value = result.total
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  } finally {
    loading.value = false
  }
}

async function loadScope() {
  try {
    const scope = await getManageScope()
    scopeTree.value = scope.tree
    // 归属机构默认值编辑器需要完整的机构选项
    const deptColumn = schema.find((item) => item.key === 'deptName')
    if (deptColumn) deptColumn.filterOptions = deptOptions.value
  } catch {
    // 选项失败不阻断列表
  }
}

function flattenDeptOptions(nodes: ManageScopeNode[]): Array<{ label: string; value: number }> {
  const result: Array<{ label: string; value: number }> = []
  const walk = (list: ManageScopeNode[]) => {
    list.forEach((node) => {
      result.push({ label: node.deptName, value: node.id })
      if (node.children?.length) walk(node.children)
    })
  }
  walk(nodes)
  return result
}

function handleSizeChange() {
  page.value = 1
  loadPage()
}

function resetFilters() {
  filters.carrierCode = ''
  filters.carrierName = ''
  filters.contact = ''
  filters.phone = ''
  filters.deptId = undefined
  filters.status = undefined
  filters.updateByName = ''
  filters.remark = ''
  sortState.sortBy = undefined
  sortState.sortOrder = undefined
  tableRef.value?.getTable()?.clearSort?.()
  page.value = 1
  loadPage()
}

/* ---------- 统一列设置（后端按用户+页面持久化） ---------- */
function onStatesUpdate(next: ColumnItemState[]) {
  states.value = next
}

async function handleColumnSave() {
  await saveColumnConfig()
  if (hiddenKeys.value.length) loadPage()
}

async function handleColumnReset() {
  await resetColumnConfig()
  tableRef.value?.resetColumnWidths()
  page.value = 1
  loadPage()
}

/** 首次进入按默认查询值自动带出筛选；默认机构已失效时忽略并提示 */
function applyDefaultFilters() {
  const defaults = defaultFiltersRaw()
  applyTextDefault('carrierCode', defaults.carrierCode, (value) => (filters.carrierCode = value))
  applyTextDefault('carrierName', defaults.carrierName, (value) => (filters.carrierName = value))
  applyTextDefault('contact', defaults.contact, (value) => (filters.contact = value))
  applyTextDefault('phone', defaults.phone, (value) => (filters.phone = value))
  applyTextDefault('updateByName', defaults.updateByName, (value) => (filters.updateByName = value))
  applyTextDefault('remark', defaults.remark, (value) => (filters.remark = value))

  const deptIds = toValueList(defaults.deptName).map((item) => Number(item))
  if (deptIds.length) {
    const valid = deptIds.filter((id) => deptOptions.value.some((item) => item.value === id))
    if (valid.length) {
      filters.deptId = valid.length === 1 ? valid[0] : valid
    } else {
      ElMessage.warning('默认查询机构已删除或不在管理范围，已忽略该默认值')
    }
  }

  const statuses = toValueList(defaults.status).filter(
    (item): item is boolean => typeof item === 'boolean'
  )
  if (statuses.length) filters.status = statuses
}

function toValueList(value: unknown): unknown[] {
  if (Array.isArray(value)) return value
  if (value === null || value === undefined || value === '') return []
  return [value]
}

function applyTextDefault(key: string, value: unknown, setter: (value: string) => void) {
  if (value === null || value === undefined) return
  if (typeof value === 'object' && !Array.isArray(value)) {
    const item = value as { mode?: unknown; value?: unknown }
    setter(item.value === null || item.value === undefined ? '' : String(item.value))
    if (isTextMatchMode(item.mode)) textModes[key] = item.mode
    return
  }
  setter(String(value))
}

/** 合计：仅展示总条数（标准列表口径） */
function summaryMethod({ columns }: { columns: Array<{ label: string }> }) {
  return columns.map((_, index) => (index === 0 ? '' : index === 1 ? `合计 ${total.value} 个承运商` : ''))
}

async function handleExport() {
  const exportColumns = schema.filter(
    (item) => item.key !== 'operation' && isVisible(item.key)
  )
  try {
    const result = await getCarrierPage(buildQuery(1, 10000))
    const header = ['序号', ...exportColumns.map((item) => item.label)]
    const body = result.records.map((row, index) => [
      String(index + 1),
      ...exportColumns.map((item) => {
        if (item.key === 'status') return row.status ? '启用' : '停用'
        const value = row[item.key as keyof CarrierRecord]
        return value === undefined || value === null || value === '' ? '—' : String(value)
      })
    ])
    const csv = [header, ...body].map((line) => line.map(csvCell).join(',')).join('\r\n')
    const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `承运商管理_${exportStamp()}.csv`
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${result.total} 条`)
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  }
}

function csvCell(value: string): string {
  return /[",\r\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value
}

function exportStamp(): string {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`
}

function openCreate() {
  drawer.mode = 'create'
  drawer.record = null
  drawer.visible = true
}

function openEdit(row: CarrierRecord) {
  drawer.mode = 'edit'
  drawer.record = row
  drawer.visible = true
}

async function handleDelete(row: CarrierRecord) {
  try {
    await ElMessageBox.confirm(`确定删除承运商「${row.carrierName}」吗？`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteCarrier(row.id)
    ElMessage.success('删除成功')
    await loadPage()
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  }
}

onMounted(async () => {
  await Promise.all([loadScope(), loadColumnConfig()])
  applyDefaultFilters()
  loadPage()
})
</script>

<style scoped>
.carrier-table-wrap {
  flex: 1;
  min-height: 0;
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

.carrier-table :deep(.el-table__header th .cell) {
  overflow: visible;
  line-height: 1.4;
}

.mono {
  font-family: var(--font-family-number);
  font-size: var(--font-size-extra-small);
}

.row-actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: nowrap;
  gap: 2px;
}

.row-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.column-setting {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.column-setting__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 6px;
  margin-bottom: 4px;
  font-size: var(--font-size-extra-small);
  color: var(--color-text-secondary);
  border-bottom: 1px solid var(--color-border-light);
}

.column-setting__item {
  height: 26px;
  margin-right: 0;
}

.table-pager {
  margin-left: 4px;
}
</style>
