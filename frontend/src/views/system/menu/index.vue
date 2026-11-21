<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">菜单管理</h2>
        <p class="page-header__desc">维护目录、菜单与按钮权限，支持多级目录自定义</p>
      </div>
    </div>

    <div class="page-content">
      <div class="action-bar">
        <div class="action-left">
          <el-button type="primary" @click="openCreateDirectory()">新增目录</el-button>
          <el-button @click="openCreateMenu()">新增菜单</el-button>
          <el-button link type="primary" @click="expandAll">展开全部</el-button>
          <el-button link @click="collapseAll">收起全部</el-button>
        </div>

        <div class="action-right">
          <el-button class="tool-reset" @click="resetFilters">重置</el-button>
          <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>

          <el-popover placement="bottom-end" :width="440" trigger="click">
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
        </div>
      </div>

      <div class="menu-table-wrap">
        <smart-table
          ref="menuTableRef"
          v-loading="loading"
          :data="displayTree"
          row-key="id"
          height="100%"
          :tree-props="{ children: 'children' }"
          :expand-row-keys="expandRowKeys"
          :empty-text="emptyText"
          persist-key="system:menu"
          :column-order="columnOrder"
          :column-hidden="hiddenKeys"
          :column-sortable="sortableMap"
          show-overflow-tooltip
          scrollbar-always-on
          class="menu-table"
          @sort-change="handleSortChange"
        >
          <el-table-column
            v-if="isVisible('menuName')"
            prop="menuName"
            label="菜单名称"
            min-width="240"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">菜单名称</span>
                <column-filter
                  v-if="col('menuName').filterType !== 'none'"
                  v-model="filters.menuName"
                  :type="col('menuName').filterType"
                  :options="col('menuName').filterOptions"
                  @change="onFilterChange"
                />
              </div>
            </template>
            <template #default="{ row }">
              <span class="menu-name" :class="{ 'is-directory': row.menuType === 1 }">
                <el-icon v-if="row.menuType !== 3" class="menu-name__icon">
                  <component :is="resolveIcon(row.icon)" />
                </el-icon>
                <span class="menu-name__text">{{ row.menuName }}</span>
              </span>
            </template>
          </el-table-column>

          <el-table-column
            v-if="isVisible('menuType')"
            prop="menuType"
            label="类型"
            width="110"
            align="center"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">类型</span>
                <column-filter
                  v-if="col('menuType').filterType !== 'none'"
                  v-model="filters.menuType"
                  :type="col('menuType').filterType"
                  :options="col('menuType').filterOptions"
                  @change="onFilterChange"
                />
              </div>
            </template>
            <template #default="{ row }">
              <status-tag :type="typeTag(row.menuType).type" :text="typeTag(row.menuType).text" />
            </template>
          </el-table-column>

          <el-table-column
            v-if="isVisible('routePath')"
            prop="routePath"
            label="路由地址"
            min-width="200"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">路由地址</span>
                <column-filter
                  v-if="col('routePath').filterType !== 'none'"
                  v-model="filters.routePath"
                  :type="col('routePath').filterType"
                  :options="col('routePath').filterOptions"
                  @change="onFilterChange"
                />
              </div>
            </template>
            <template #default="{ row }">{{ row.routePath || '—' }}</template>
          </el-table-column>

          <el-table-column
            v-if="isVisible('component')"
            prop="component"
            label="组件路径"
            min-width="180"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">组件路径</span>
                <column-filter
                  v-if="col('component').filterType !== 'none'"
                  v-model="filters.component"
                  :type="col('component').filterType"
                  :options="col('component').filterOptions"
                  @change="onFilterChange"
                />
              </div>
            </template>
            <template #default="{ row }">{{ row.component || '—' }}</template>
          </el-table-column>

          <el-table-column v-if="isVisible('perms')" prop="perms" label="权限标识" min-width="160">
            <template #header>
              <div class="col-head">
                <span class="col-head__label">权限标识</span>
                <column-filter
                  v-if="col('perms').filterType !== 'none'"
                  v-model="filters.perms"
                  :type="col('perms').filterType"
                  :options="col('perms').filterOptions"
                  @change="onFilterChange"
                />
              </div>
            </template>
            <template #default="{ row }">
              <span :class="{ 'mono': row.perms }">{{ row.perms || '—' }}</span>
            </template>
          </el-table-column>

          <el-table-column
            v-if="isVisible('terminalType')"
            prop="terminalType"
            label="终端"
            min-width="110"
            align="center"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">终端</span>
                <column-filter
                  v-if="col('terminalType').filterType !== 'none'"
                  v-model="filters.terminalType"
                  :type="col('terminalType').filterType"
                  :options="col('terminalType').filterOptions"
                  @change="onFilterChange"
                />
              </div>
            </template>
            <template #default="{ row }">{{ row.terminalType || '—' }}</template>
          </el-table-column>

          <el-table-column
            v-if="isVisible('sortOrder')"
            prop="sortOrder"
            label="排序"
            width="110"
            align="right"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">排序</span>
                <column-filter
                  v-if="col('sortOrder').filterType !== 'none'"
                  v-model="filters.sortRange"
                  :type="col('sortOrder').filterType"
                  :options="col('sortOrder').filterOptions"
                  @change="onFilterChange"
                />
              </div>
            </template>
          </el-table-column>

          <el-table-column
            v-if="isVisible('visible')"
            prop="visible"
            label="显示"
            width="110"
            align="center"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">显示</span>
                <column-filter
                  v-if="col('visible').filterType !== 'none'"
                  v-model="filters.visible"
                  :type="col('visible').filterType"
                  :options="col('visible').filterOptions"
                  @change="onFilterChange"
                />
              </div>
            </template>
            <template #default="{ row }">
              <status-tag
                v-if="row.menuType !== 3"
                :type="row.visible ? 'success' : 'info'"
                :text="row.visible ? '显示' : '隐藏'"
              />
              <span v-else>—</span>
            </template>
          </el-table-column>

          <el-table-column
            v-if="isVisible('status')"
            prop="status"
            label="状态"
            width="110"
            align="center"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">状态</span>
                <column-filter
                  v-if="col('status').filterType !== 'none'"
                  v-model="filters.status"
                  :type="col('status').filterType"
                  :options="col('status').filterOptions"
                  @change="onFilterChange"
                />
              </div>
            </template>
            <template #default="{ row }">
              <status-tag
                :type="row.status ? 'success' : 'danger'"
                :text="row.status ? '启用' : '禁用'"
              />
            </template>
          </el-table-column>

          <el-table-column
            v-if="isVisible('updateTime')"
            prop="updateTime"
            label="最后修改时间"
            min-width="200"
            align="center"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">最后修改时间</span>
                <column-filter
                  v-if="col('updateTime').filterType !== 'none'"
                  v-model="filters.updateRange"
                  :type="col('updateTime').filterType"
                  :options="col('updateTime').filterOptions"
                  @change="onFilterChange"
                />
              </div>
            </template>
            <template #default="{ row }">{{ row.updateTime || '—' }}</template>
          </el-table-column>

          <el-table-column
            v-if="isVisible('operation')"
            label="操作"
            width="240"
            fixed="right"
            align="right"
            :sortable="false"
          >
            <template #default="{ row }">
              <div class="row-actions">
                <template v-if="row.menuType === 1">
                  <el-button link type="primary" size="small" @click="openCreateDirectory(row.id)">
                    新增子目录
                  </el-button>
                  <el-button link type="primary" size="small" @click="openCreateMenu(row.id)">
                    新增子菜单
                  </el-button>
                </template>
                <el-button
                  v-if="row.menuType === 2"
                  link
                  type="primary"
                  size="small"
                  @click="openCreateButton(row)"
                >
                  新增按钮
                </el-button>
                <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </smart-table>
      </div>
    </div>

    <menu-form-drawer
      v-model="menuDrawer.visible"
      :mode="menuDrawer.mode"
      :menu-type="menuDrawer.menuType"
      :parent-id="menuDrawer.parentId"
      :record="menuDrawer.record"
      :tree="treeData"
      @saved="handleSaved"
    />

    <button-form-drawer
      v-model="buttonDrawer.visible"
      :record="buttonDrawer.record"
      :parent="buttonDrawer.parent"
      :tree="treeData"
      @saved="handleSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Setting } from '@element-plus/icons-vue'
import type { MenuNode } from '@/types/menu'
import type { ColumnDef, ColumnItemState } from '@/composables/useColumnConfig'
import { deleteMenu, getMenuTree } from '@/api/system/menu'
import { useColumnConfig } from '@/composables/useColumnConfig'
import { resolveIcon } from '@/utils/icon'
import { collectExpandKeys, filterTree } from '@/utils/tree'
import StatusTag from '@/components/base/StatusTag.vue'
import SmartTable from '@/components/base/SmartTable.vue'
import ColumnFilter from '@/components/base/ColumnFilter.vue'
import ColumnSetting from '@/components/base/ColumnSetting.vue'
import MenuFormDrawer from './components/MenuFormDrawer.vue'
import ButtonFormDrawer from './components/ButtonFormDrawer.vue'

defineOptions({ name: 'SystemMenu' })

const schema = reactive<ColumnDef[]>([
  { key: 'menuName', label: '菜单名称', prop: 'menuName', filterType: 'text', locked: true },
  {
    key: 'menuType',
    label: '类型',
    prop: 'menuType',
    filterType: 'select',
    filterOptions: [
      { label: '目录', value: 1 },
      { label: '菜单', value: 2 },
      { label: '按钮', value: 3 }
    ]
  },
  { key: 'routePath', label: '路由地址', prop: 'routePath', filterType: 'text' },
  { key: 'component', label: '组件路径', prop: 'component', filterType: 'text' },
  { key: 'perms', label: '权限标识', prop: 'perms', filterType: 'text' },
  { key: 'terminalType', label: '终端', prop: 'terminalType', filterType: 'text' },
  { key: 'sortOrder', label: '排序', prop: 'sortOrder', filterType: 'numberrange', align: 'right' },
  {
    key: 'visible',
    label: '显示',
    prop: 'visible',
    filterType: 'select',
    filterOptions: [
      { label: '显示', value: true },
      { label: '隐藏', value: false }
    ]
  },
  {
    key: 'status',
    label: '状态',
    prop: 'status',
    filterType: 'select',
    filterOptions: [
      { label: '启用', value: true },
      { label: '禁用', value: false }
    ]
  },
  {
    key: 'updateTime',
    label: '最后修改时间',
    prop: 'updateTime',
    filterType: 'daterange',
    align: 'center'
  },
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
  defaultFilters
} = useColumnConfig('system:menu', schema)

const menuTableRef = ref<InstanceType<typeof SmartTable>>()
const loading = ref(false)
const treeData = ref<MenuNode[]>([])
const expandRowKeys = ref<number[]>([])

const filters = reactive<{
  menuName: string
  menuType: number | '' | undefined
  routePath: string
  component: string
  perms: string
  terminalType: string
  sortRange: [number | undefined, number | undefined]
  visible: boolean | '' | undefined
  status: boolean | '' | undefined
  updateRange: string[]
}>({
  menuName: '',
  menuType: '',
  routePath: '',
  component: '',
  perms: '',
  terminalType: '',
  sortRange: [undefined, undefined],
  visible: '',
  status: '',
  updateRange: []
})

/** 服务端排序状态：菜单是树表，排序只作用于同级节点 */
const sortState = reactive<{ sortBy?: string; sortOrder?: 'asc' | 'desc' }>({
  sortBy: undefined,
  sortOrder: undefined
})

function handleSortChange({ prop, order }: { prop?: string; order?: 'ascending' | 'descending' | null }) {
  if (!order || !prop) {
    sortState.sortBy = undefined
    sortState.sortOrder = undefined
  } else {
    sortState.sortBy = prop
    sortState.sortOrder = order === 'ascending' ? 'asc' : 'desc'
  }
  loadData()
}

/** 下拉清空时 Element Plus 可能给 undefined，这里统一归一化 */
const normalized = computed(() => ({
  menuName: filters.menuName.trim().toLowerCase(),
  routePath: filters.routePath.trim().toLowerCase(),
  component: filters.component.trim().toLowerCase(),
  perms: filters.perms.trim().toLowerCase(),
  terminalType: filters.terminalType.trim().toLowerCase(),
  menuType: filters.menuType === '' || filters.menuType === undefined ? null : filters.menuType,
  visible: filters.visible === '' || filters.visible === undefined ? null : filters.visible,
  status: filters.status === '' || filters.status === undefined ? null : filters.status,
  sortMin: filters.sortRange[0] ?? null,
  sortMax: filters.sortRange[1] ?? null,
  updateStart: filters.updateRange[0] || null,
  updateEnd: filters.updateRange[1] || null
}))

const filterActive = computed(() => {
  const n = normalized.value
  return (
    !!n.menuName ||
    !!n.routePath ||
    !!n.component ||
    !!n.perms ||
    !!n.terminalType ||
    n.menuType !== null ||
    n.visible !== null ||
    n.status !== null ||
    n.sortMin !== null ||
    n.sortMax !== null ||
    n.updateStart !== null ||
    n.updateEnd !== null
  )
})

const displayTree = computed(() => {
  if (!filterActive.value) return treeData.value
  const n = normalized.value
  const contains = (value: unknown, keyword: string) =>
    !keyword || String(value ?? '').toLowerCase().includes(keyword)
  return filterTree(treeData.value, (node) => {
    if (!contains(node.menuName, n.menuName)) return false
    if (!contains(node.routePath, n.routePath)) return false
    if (!contains(node.component, n.component)) return false
    if (!contains(node.perms, n.perms)) return false
    if (!contains(node.terminalType, n.terminalType)) return false
    if (n.menuType !== null && node.menuType !== n.menuType) return false
    if (n.visible !== null && Boolean(node.visible) !== n.visible) return false
    if (n.status !== null && Boolean(node.status) !== n.status) return false
    if (n.sortMin !== null && (node.sortOrder ?? 0) < n.sortMin) return false
    if (n.sortMax !== null && (node.sortOrder ?? 0) > n.sortMax) return false
    const updateDate = (node.updateTime || '').slice(0, 10)
    if (n.updateStart && (!updateDate || updateDate < n.updateStart)) return false
    if (n.updateEnd && (!updateDate || updateDate > n.updateEnd)) return false
    return true
  })
})

const emptyText = computed(() => (filterActive.value ? '没有匹配的菜单' : '暂无菜单数据'))

const menuDrawer = reactive<{
  visible: boolean
  mode: 'create' | 'edit'
  menuType: 1 | 2
  parentId: number
  record: MenuNode | null
}>({
  visible: false,
  mode: 'create',
  menuType: 1,
  parentId: 0,
  record: null
})

const buttonDrawer = reactive<{
  visible: boolean
  record: MenuNode | null
  parent: MenuNode | null
}>({
  visible: false,
  record: null,
  parent: null
})

function typeTag(type: number) {
  if (type === 1) return { type: 'info' as const, text: '目录' }
  if (type === 2) return { type: 'primary' as const, text: '菜单' }
  return { type: 'warning' as const, text: '按钮' }
}

async function loadData() {
  loading.value = true
  try {
    treeData.value = await getMenuTree({
      sortBy: sortState.sortBy,
      sortOrder: sortState.sortOrder
    })
    expandRowKeys.value = filterActive.value
      ? collectExpandKeys(displayTree.value)
      : collectExpandKeys(treeData.value, true)
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  } finally {
    loading.value = false
  }
}

function expandAll() {
  expandRowKeys.value = collectExpandKeys(displayTree.value)
}

function collapseAll() {
  expandRowKeys.value = []
}

function resetFilters() {
  filters.menuName = ''
  filters.menuType = ''
  filters.routePath = ''
  filters.component = ''
  filters.perms = ''
  filters.terminalType = ''
  filters.sortRange = [undefined, undefined]
  filters.visible = ''
  filters.status = ''
  filters.updateRange = []
}

/** 表头筛选变化：纯前端筛选，立即重算树与展开项 */
function onFilterChange() {
  expandRowKeys.value = filterActive.value
    ? collectExpandKeys(displayTree.value)
    : collectExpandKeys(treeData.value, true)
}

watch(
  () => [
    filters.menuName,
    filters.menuType,
    filters.routePath,
    filters.component,
    filters.perms,
    filters.terminalType,
    filters.sortRange[0],
    filters.sortRange[1],
    filters.visible,
    filters.status,
    filters.updateRange[0],
    filters.updateRange[1]
  ],
  onFilterChange
)

/* ---------- 统一列设置（后端按用户+页面持久化） ---------- */
function onStatesUpdate(next: ColumnItemState[]) {
  states.value = next
}

async function handleColumnSave() {
  await saveColumnConfig()
}

async function handleColumnReset() {
  await resetColumnConfig()
  menuTableRef.value?.resetColumnWidths()
}

/** 首次进入按默认查询值自动带出筛选 */
function applyDefaultFilters() {
  const defaults = defaultFilters()
  for (const key of ['menuName', 'routePath', 'component', 'perms', 'terminalType'] as const) {
    if (defaults[key] != null) filters[key] = String(defaults[key])
  }
  if (defaults.menuType != null) filters.menuType = Number(defaults.menuType)
  if (defaults.visible != null) filters.visible = Boolean(defaults.visible)
  if (defaults.status != null) filters.status = Boolean(defaults.status)
  if (Array.isArray(defaults.sortOrder)) {
    filters.sortRange = [
      defaults.sortOrder[0] == null ? undefined : Number(defaults.sortOrder[0]),
      defaults.sortOrder[1] == null ? undefined : Number(defaults.sortOrder[1])
    ]
  }
  if (Array.isArray(defaults.updateTime)) {
    filters.updateRange = defaults.updateTime.map((item) => String(item))
  }
}

function openCreateDirectory(parentId = 0) {
  menuDrawer.mode = 'create'
  menuDrawer.menuType = 1
  menuDrawer.parentId = parentId
  menuDrawer.record = null
  menuDrawer.visible = true
}

function openCreateMenu(parentId = 0) {
  menuDrawer.mode = 'create'
  menuDrawer.menuType = 2
  menuDrawer.parentId = parentId
  menuDrawer.record = null
  menuDrawer.visible = true
}

function openEdit(row: MenuNode) {
  if (row.menuType === 3) {
    buttonDrawer.record = row
    buttonDrawer.parent = findParentNode(treeData.value, row.parentId) || null
    buttonDrawer.visible = true
    return
  }
  menuDrawer.mode = 'edit'
  menuDrawer.menuType = row.menuType
  menuDrawer.parentId = row.parentId
  menuDrawer.record = row
  menuDrawer.visible = true
}

function openCreateButton(row: MenuNode) {
  buttonDrawer.record = null
  buttonDrawer.parent = row
  buttonDrawer.visible = true
}

function findParentNode(nodes: MenuNode[], id: number): MenuNode | undefined {
  for (const node of nodes) {
    if (node.id === id) return node
    const found = node.children ? findParentNode(node.children, id) : undefined
    if (found) return found
  }
  return undefined
}

async function handleDelete(row: MenuNode) {
  if (row.children?.length) {
    ElMessage.warning('存在子节点，请先删除子节点')
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除「${row.menuName}」吗？`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteMenu(row.id)
    ElMessage.success('删除成功')
    await loadData()
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  }
}

function handleSaved() {
  loadData()
}

onMounted(async () => {
  await loadColumnConfig()
  applyDefaultFilters()
  await loadData()
})
</script>

<style scoped>
.menu-table-wrap {
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

.menu-table :deep(.el-table__header th .cell) {
  overflow: visible;
  line-height: 1.4;
}

.menu-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.menu-name.is-directory {
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.menu-name__icon {
  font-size: 15px;
  color: var(--color-text-secondary);
}

.menu-name__text {
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

/* 12 列宽于多数窗口，横向滚动条需清晰可辨（规范 3.13 列表撑满与滚动） */
.menu-table :deep(.el-scrollbar__bar.is-horizontal) {
  height: 10px;
}

.menu-table :deep(.el-scrollbar__bar.is-horizontal .el-scrollbar__thumb) {
  opacity: 0.45;
  background-color: var(--color-text-placeholder);
}

.menu-table :deep(.el-scrollbar__bar.is-horizontal .el-scrollbar__thumb:hover) {
  opacity: 0.65;
}
</style>
