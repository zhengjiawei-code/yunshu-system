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

          <el-popover placement="bottom-end" :width="200" trigger="click">
            <template #reference>
              <el-button :icon="Setting">列设置</el-button>
            </template>
            <div class="column-setting">
              <div class="column-setting__head">
                <span>显示列</span>
                <el-button link type="primary" size="small" @click="resetColumns">恢复默认</el-button>
              </div>
              <el-checkbox
                v-for="item in columnOptions"
                :key="item.key"
                v-model="columnState[item.key]"
                :disabled="item.locked"
                class="column-setting__item"
                @change="(value: boolean) => onColumnToggle(item.key, value)"
              >
                {{ item.label }}
              </el-checkbox>
            </div>
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
          show-summary
          :summary-method="summaryMethod"
          show-overflow-tooltip
          scrollbar-always-on
          class="carrier-table"
          @sort-change="handleSortChange"
        >
          <el-table-column type="index" label="序号" width="64" align="center" :index="rowIndex" />

          <el-table-column
            v-if="columnState.carrierCode"
            prop="carrierCode"
            label="承运商编码"
            min-width="150"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">承运商编码</span>
                <el-input
                  v-model="filters.carrierCode"
                  size="small"
                  placeholder="请输入"
                  clearable
                  @input="onTextFilter"
                />
              </div>
            </template>
            <template #default="{ row }">
              <span class="mono">{{ row.carrierCode }}</span>
            </template>
          </el-table-column>

          <el-table-column
            v-if="columnState.carrierName"
            prop="carrierName"
            label="承运商名称"
            min-width="200"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">承运商名称</span>
                <el-input
                  v-model="filters.carrierName"
                  size="small"
                  placeholder="请输入"
                  clearable
                  @input="onTextFilter"
                />
              </div>
            </template>
          </el-table-column>

          <el-table-column v-if="columnState.contact" prop="contact" label="联系人" min-width="130">
            <template #header>
              <div class="col-head">
                <span class="col-head__label">联系人</span>
                <el-input
                  v-model="filters.contact"
                  size="small"
                  placeholder="请输入"
                  clearable
                  @input="onTextFilter"
                />
              </div>
            </template>
          </el-table-column>

          <el-table-column
            v-if="columnState.phone"
            prop="phone"
            label="联系电话"
            min-width="150"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">联系电话</span>
                <el-input
                  v-model="filters.phone"
                  size="small"
                  placeholder="请输入"
                  clearable
                  @input="onTextFilter"
                />
              </div>
            </template>
          </el-table-column>

          <el-table-column
            v-if="columnState.deptName"
            prop="deptName"
            label="归属机构"
            min-width="180"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">归属机构</span>
                <el-tree-select
                  v-model="filters.deptId"
                  size="small"
                  :data="scopeTree"
                  :props="{ label: 'deptName', children: 'children' }"
                  node-key="id"
                  check-strictly
                  clearable
                  placeholder="全部"
                  @change="onFilterChange"
                />
              </div>
            </template>
            <template #default="{ row }">{{ row.deptName || '—' }}</template>
          </el-table-column>

          <el-table-column
            v-if="columnState.status"
            prop="status"
            label="状态"
            min-width="120"
            align="center"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">状态</span>
                <el-select
                  v-model="filters.status"
                  size="small"
                  placeholder="全部"
                  clearable
                  @change="onFilterChange"
                >
                  <el-option label="启用" :value="true" />
                  <el-option label="停用" :value="false" />
                </el-select>
              </div>
            </template>
            <template #default="{ row }">
              <status-tag :type="row.status ? 'primary' : 'info'" :text="row.status ? '启用' : '停用'" />
            </template>
          </el-table-column>

          <el-table-column
            v-if="columnState.sortOrder"
            prop="sortOrder"
            label="排序号"
            min-width="110"
            align="right"
          />

          <el-table-column
            v-if="columnState.updateByName"
            prop="updateByName"
            label="修改人"
            min-width="130"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">修改人</span>
                <el-input
                  v-model="filters.updateByName"
                  size="small"
                  placeholder="请输入"
                  clearable
                  @input="onTextFilter"
                />
              </div>
            </template>
            <template #default="{ row }">{{ row.updateByName || '—' }}</template>
          </el-table-column>

          <el-table-column
            v-if="columnState.updateTime"
            prop="updateTime"
            label="修改时间"
            min-width="180"
            align="center"
          />

          <el-table-column
            v-if="columnState.remark"
            prop="remark"
            label="备注"
            min-width="160"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">备注</span>
                <el-input
                  v-model="filters.remark"
                  size="small"
                  placeholder="请输入"
                  clearable
                  @input="onTextFilter"
                />
              </div>
            </template>
            <template #default="{ row }">{{ row.remark || '—' }}</template>
          </el-table-column>

          <el-table-column label="操作" width="120" fixed="right" align="right">
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Setting } from '@element-plus/icons-vue'
import type { CarrierQuery, CarrierRecord } from '@/types/carrier'
import type { ManageScopeNode } from '@/types/dept'
import { deleteCarrier, getCarrierPage } from '@/api/system/carrier'
import { getManageScope } from '@/api/system/dept'
import StatusTag from '@/components/base/StatusTag.vue'
import SmartTable from '@/components/base/SmartTable.vue'
import CarrierFormDrawer from './components/CarrierFormDrawer.vue'

defineOptions({ name: 'SystemCarrier' })

const COLUMN_STORAGE_KEY = 'yunshu_carrier_columns_v1'

const columnOptions: Array<{ key: string; label: string; locked?: boolean }> = [
  { key: 'carrierCode', label: '承运商编码', locked: true },
  { key: 'carrierName', label: '承运商名称' },
  { key: 'contact', label: '联系人' },
  { key: 'phone', label: '联系电话' },
  { key: 'deptName', label: '归属机构' },
  { key: 'status', label: '状态' },
  { key: 'sortOrder', label: '排序号' },
  { key: 'updateByName', label: '修改人' },
  { key: 'updateTime', label: '修改时间' },
  { key: 'remark', label: '备注' }
]

const tableRef = ref<InstanceType<typeof SmartTable>>()
const loading = ref(false)
const rows = ref<CarrierRecord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const scopeTree = ref<ManageScopeNode[]>([])
const columnState = reactive<Record<string, boolean>>(loadColumnState())

const filters = reactive<{
  carrierCode: string
  carrierName: string
  contact: string
  phone: string
  deptId: number | undefined
  status: boolean | undefined
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
    carrierCode: filters.carrierCode.trim() || undefined,
    carrierName: filters.carrierName.trim() || undefined,
    contact: filters.contact.trim() || undefined,
    phone: filters.phone.trim() || undefined,
    deptId: filters.deptId,
    status: filters.status,
    updateByName: filters.updateByName.trim() || undefined,
    remark: filters.remark.trim() || undefined,
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
  } catch {
    // 选项失败不阻断列表
  }
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

function loadColumnState(): Record<string, boolean> {
  const defaults: Record<string, boolean> = {}
  columnOptions.forEach((item) => {
    defaults[item.key] = true
  })
  try {
    const raw = localStorage.getItem(COLUMN_STORAGE_KEY)
    if (!raw) return defaults
    const saved = JSON.parse(raw) as Record<string, boolean>
    columnOptions.forEach((item) => {
      if (item.locked) return
      if (typeof saved[item.key] === 'boolean') defaults[item.key] = saved[item.key]
    })
  } catch {
    // 存储损坏时回退默认
  }
  return defaults
}

function persistColumnState() {
  try {
    localStorage.setItem(COLUMN_STORAGE_KEY, JSON.stringify(columnState))
  } catch {
    // 忽略存储失败
  }
}

const FILTER_RESET: Record<string, () => void> = {
  carrierCode: () => (filters.carrierCode = ''),
  carrierName: () => (filters.carrierName = ''),
  contact: () => (filters.contact = ''),
  phone: () => (filters.phone = ''),
  deptName: () => (filters.deptId = undefined),
  status: () => (filters.status = undefined),
  updateByName: () => (filters.updateByName = ''),
  remark: () => (filters.remark = '')
}

function onColumnToggle(key: string, visible: boolean) {
  persistColumnState()
  if (!visible && FILTER_RESET[key]) {
    FILTER_RESET[key]()
    if (sortState.sortBy === key) {
      sortState.sortBy = undefined
      sortState.sortOrder = undefined
      tableRef.value?.getTable()?.clearSort?.()
    }
    page.value = 1
    loadPage()
  }
}

function resetColumns() {
  columnOptions.forEach((item) => {
    columnState[item.key] = true
  })
  persistColumnState()
  tableRef.value?.resetColumnWidths()
  ElMessage.success('已恢复默认列')
}

/** 合计：仅展示总条数（标准列表口径） */
function summaryMethod({ columns }: { columns: Array<{ label: string }> }) {
  return columns.map((_, index) => (index === 0 ? '' : index === 1 ? `合计 ${total.value} 个承运商` : ''))
}

async function handleExport() {
  const exportColumns = columnOptions.filter((item) => columnState[item.key])
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

onMounted(() => {
  loadPage()
  loadScope()
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
