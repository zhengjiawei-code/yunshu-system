<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">角色管理</h2>
        <p class="page-header__desc">维护功能角色与菜单/按钮权限；数据范围由用户管理单独配置</p>
      </div>
    </div>

    <div class="page-content">
      <div class="action-bar">
        <div class="action-left">
          <el-button type="primary" @click="openCreate">新增角色</el-button>
        </div>

        <div class="action-right">
          <el-button class="tool-reset" @click="resetFilters">重置</el-button>
          <el-button class="tool-export" :icon="Download" @click="handleExport">导出</el-button>

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

      <div class="role-table-wrap">
        <smart-table
          ref="roleTableRef"
          v-loading="loading"
          :data="rows"
          row-key="id"
          height="100%"
          :empty-text="emptyText"
          persist-key="system:role"
          show-summary
          :summary-method="summaryMethod"
          show-overflow-tooltip
          scrollbar-always-on
          class="role-table"
          @sort-change="handleSortChange"
        >
          <el-table-column type="index" label="序号" width="80" align="center" :index="indexMethod" />

          <el-table-column v-if="columnState.roleName" prop="roleName" label="角色名称" min-width="150">
            <template #header>
              <div class="col-head">
                <span class="col-head__label">角色名称</span>
                <el-input
                  v-model="filters.roleName"
                  size="small"
                  placeholder="请输入"
                  clearable
                  @input="onTextFilter"
                />
              </div>
            </template>
            <template #default="{ row }">
              <span class="role-name">{{ row.roleName }}</span>
            </template>
          </el-table-column>

          <el-table-column v-if="columnState.roleCode" prop="roleCode" label="角色编码" min-width="150">
            <template #header>
              <div class="col-head">
                <span class="col-head__label">角色编码</span>
                <el-input
                  v-model="filters.roleCode"
                  size="small"
                  placeholder="请输入"
                  clearable
                  @input="onTextFilter"
                />
              </div>
            </template>
            <template #default="{ row }">
              <span class="mono">{{ row.roleCode }}</span>
            </template>
          </el-table-column>

          <el-table-column v-if="columnState.roleDesc" prop="roleDesc" label="角色描述" min-width="170">
            <template #header>
              <div class="col-head">
                <span class="col-head__label">角色描述</span>
                <el-input
                  v-model="filters.roleDesc"
                  size="small"
                  placeholder="请输入"
                  clearable
                  @input="onTextFilter"
                />
              </div>
            </template>
            <template #default="{ row }">{{ row.roleDesc || '—' }}</template>
          </el-table-column>

          <el-table-column
            v-if="columnState.updateByName"
            prop="updateByName"
            label="最后修改人"
            min-width="120"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">最后修改人</span>
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
            label="最后修改时间"
            min-width="200"
            align="center"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">最后修改时间</span>
                <el-date-picker
                  v-model="filters.updateRange"
                  size="small"
                  type="daterange"
                  value-format="YYYY-MM-DD"
                  start-placeholder="开始"
                  end-placeholder="结束"
                  unlink-panels
                  @change="onFilterChange"
                />
              </div>
            </template>
            <template #default="{ row }">{{ row.updateTime || '—' }}</template>
          </el-table-column>

          <el-table-column label="操作" width="200" fixed="right" align="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-tooltip
                  v-if="isAdmin(row)"
                  content="超级管理员角色不允许修改权限"
                  placement="top"
                >
                  <span class="btn-wrap">
                    <el-button link type="primary" size="small" disabled>权限分配</el-button>
                  </span>
                </el-tooltip>
                <el-button v-else link type="primary" size="small" @click="openAuth(row)">
                  权限分配
                </el-button>

                <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>

                <el-tooltip v-if="isAdmin(row)" content="超级管理员角色不允许删除" placement="top">
                  <span class="btn-wrap">
                    <el-button link type="danger" size="small" disabled>删除</el-button>
                  </span>
                </el-tooltip>
                <el-button v-else link type="danger" size="small" @click="handleDelete(row)">
                  删除
                </el-button>
              </div>
            </template>
          </el-table-column>
        </smart-table>
      </div>
    </div>

    <role-form-drawer
      v-model="drawer.visible"
      :mode="drawer.mode"
      :record="drawer.record"
      @saved="handleSaved"
    />

    <role-auth-dialog
      v-model="authDialog.visible"
      :role="authDialog.role"
      @saved="loadPage"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Setting } from '@element-plus/icons-vue'
import type { RoleQuery, RoleRecord } from '@/types/role'
import { deleteRole, getRolePage } from '@/api/system/role'
import SmartTable from '@/components/base/SmartTable.vue'
import RoleFormDrawer from './components/RoleFormDrawer.vue'
import RoleAuthDialog from './components/RoleAuthDialog.vue'

defineOptions({ name: 'SystemRole' })

const COLUMN_STORAGE_KEY = 'yunshu_role_columns_v1'

const columnOptions: Array<{ key: string; label: string; locked?: boolean }> = [
  { key: 'roleName', label: '角色名称', locked: true },
  { key: 'roleCode', label: '角色编码' },
  { key: 'roleDesc', label: '角色描述' },
  { key: 'updateByName', label: '最后修改人' },
  { key: 'updateTime', label: '最后修改时间' }
]

const roleTableRef = ref<InstanceType<typeof SmartTable>>()
const loading = ref(false)
const rows = ref<RoleRecord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const columnState = reactive<Record<string, boolean>>(loadColumnState())

const filters = reactive<{
  roleName: string
  roleCode: string
  roleDesc: string
  updateByName: string
  updateRange: string[]
}>({
  roleName: '',
  roleCode: '',
  roleDesc: '',
  updateByName: '',
  updateRange: []
})

/** 服务端排序状态：sortBy 为空表示默认 id asc */
const sortState = reactive<{ sortBy?: string; sortOrder?: 'asc' | 'desc' }>({
  sortBy: undefined,
  sortOrder: undefined
})

const emptyText = computed(() => '暂无匹配的角色')

const drawer = reactive<{
  visible: boolean
  mode: 'create' | 'edit'
  record: RoleRecord | null
}>({
  visible: false,
  mode: 'create',
  record: null
})

const authDialog = reactive<{
  visible: boolean
  role: RoleRecord | null
}>({
  visible: false,
  role: null
})

function isAdmin(row: RoleRecord): boolean {
  return row.roleCode === 'admin'
}

function indexMethod(index: number): number {
  return (page.value - 1) * pageSize.value + index + 1
}

function buildQuery(pageNum = page.value, size = pageSize.value): RoleQuery {
  return {
    roleName: filters.roleName.trim() || undefined,
    roleCode: filters.roleCode.trim() || undefined,
    roleDesc: filters.roleDesc.trim() || undefined,
    updateByName: filters.updateByName.trim() || undefined,
    updateTimeStart: filters.updateRange[0] || undefined,
    updateTimeEnd: filters.updateRange[1] || undefined,
    sortBy: sortState.sortBy,
    sortOrder: sortState.sortOrder,
    pageNum,
    pageSize: size
  }
}

/** 表头点击排序：走服务端 sort_by / sort_order，取消排序回到默认 id asc */
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
    const result = await getRolePage(buildQuery())
    rows.value = result.records
    total.value = result.total
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  } finally {
    loading.value = false
  }
}

function handleSizeChange() {
  page.value = 1
  loadPage()
}

function resetFilters() {
  filters.roleName = ''
  filters.roleCode = ''
  filters.roleDesc = ''
  filters.updateByName = ''
  filters.updateRange = []
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

/** 隐藏列时同步清空该列筛选，避免“看不见条件却仍在过滤” */
const FILTER_RESET: Record<string, () => void> = {
  roleName: () => (filters.roleName = ''),
  roleCode: () => (filters.roleCode = ''),
  roleDesc: () => (filters.roleDesc = ''),
  updateByName: () => (filters.updateByName = ''),
  updateTime: () => (filters.updateRange = [])
}

function persistColumnState() {
  try {
    localStorage.setItem(COLUMN_STORAGE_KEY, JSON.stringify(columnState))
  } catch {
    // 忽略存储失败
  }
}

function onColumnToggle(key: string, visible: boolean) {
  persistColumnState()
  if (!visible && FILTER_RESET[key]) {
    FILTER_RESET[key]()
    // 隐藏了正在排序的列时同步取消排序
    if (sortState.sortBy === key) {
      sortState.sortBy = undefined
      sortState.sortOrder = undefined
      roleTableRef.value?.getTable()?.clearSort?.()
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
  roleTableRef.value?.resetColumnWidths()
  ElMessage.success('已恢复默认列')
}

function summaryMethod({ columns }: { columns: Array<{ label: string }> }) {
  return columns.map((_, index) => (index === 0 ? `合计 ${total.value} 个角色` : ''))
}

/** 导出：跟随当前筛选与可见列，导出全部页 */
async function handleExport() {
  const exportColumns = columnOptions.filter((item) => columnState[item.key])
  try {
    const result = await getRolePage(buildQuery(1, 10000))
    const header = exportColumns.map((item) => item.label)
    const body = result.records.map((row) =>
      exportColumns.map((item) => {
        const value = row[item.key as keyof RoleRecord]
        return value === undefined || value === null || value === '' ? '—' : String(value)
      })
    )
    const csv = [header, ...body]
      .map((line) => line.map(csvCell).join(','))
      .join('\r\n')
    const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `角色管理_${exportStamp()}.csv`
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

function openEdit(row: RoleRecord) {
  drawer.mode = 'edit'
  drawer.record = row
  drawer.visible = true
}

function openAuth(row: RoleRecord) {
  authDialog.role = row
  authDialog.visible = true
}

async function handleDelete(row: RoleRecord) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.roleName}」吗？`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteRole(row.id)
    ElMessage.success('删除成功')
    await loadPage()
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  }
}

async function handleSaved() {
  await loadPage()
}

onMounted(loadPage)
</script>

<style scoped>
.role-table-wrap {
  flex: 1;
  min-height: 0;
}

/* 表头内的筛选控件 */
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

/* 日期区间筛选控件压进 200px 列宽 */
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

.role-table :deep(.el-table__header th .cell) {
  overflow: visible;
  line-height: 1.4;
}

.role-name {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.row-actions :deep(.el-button) {
  flex-shrink: 0;
}

.row-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.btn-wrap {
  display: inline-flex;
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
