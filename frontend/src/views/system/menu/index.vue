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

          <column-setting-trigger
            :schema="schema"
            :states="states"
            :loading="columnLoading"
            :saving="columnSaving"
            @update:states="onStatesUpdate"
            @save="handleColumnSave"
            @reset="handleColumnReset"
          />
        </div>
      </div>

      <div
        ref="menuTableWrap"
        class="menu-table-wrap"
        @dragstart="onRowDragStart"
        @dragover="onRowDragOver"
        @drop="onRowDrop"
        @dragend="onRowDragEnd"
      >
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
          :row-class-name="menuRowClass"
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
                  :filter-mode="col('menuName').filterMode"
                  :text-mode="textModes.menuName"
                  :options="col('menuName').filterOptions"
                  @update:mode="(mode: TextMatchMode) => (textModes.menuName = mode)"
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
                  :select-multiple="col('menuType').selectMultiple === true"
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
                  :filter-mode="col('routePath').filterMode"
                  :text-mode="textModes.routePath"
                  :options="col('routePath').filterOptions"
                  @update:mode="(mode: TextMatchMode) => (textModes.routePath = mode)"
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
                  :filter-mode="col('component').filterMode"
                  :text-mode="textModes.component"
                  :options="col('component').filterOptions"
                  @update:mode="(mode: TextMatchMode) => (textModes.component = mode)"
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
                  :filter-mode="col('perms').filterMode"
                  :text-mode="textModes.perms"
                  :options="col('perms').filterOptions"
                  @update:mode="(mode: TextMatchMode) => (textModes.perms = mode)"
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
                  :filter-mode="col('terminalType').filterMode"
                  :text-mode="textModes.terminalType"
                  :options="col('terminalType').filterOptions"
                  @update:mode="(mode: TextMatchMode) => (textModes.terminalType = mode)"
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
                  :select-multiple="col('visible').selectMultiple === true"
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
                  :select-multiple="col('status').selectMultiple === true"
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
                    新增目录
                  </el-button>
                  <el-button link type="primary" size="small" @click="openCreateMenu(row.id)">
                    新增菜单
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
                <el-dropdown trigger="click" @command="(command: string) => handleRowCommand(command, row)">
                  <el-button link type="primary" size="small">
                    更多<el-icon class="more-icon"><ArrowDown /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="edit">编辑{{ typeLabel(row.menuType) }}</el-dropdown-item>
                      <el-dropdown-item
                        v-if="!row.children?.length"
                        command="delete"
                        divided
                      >
                        删除{{ typeLabel(row.menuType) }}
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
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
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Refresh } from '@element-plus/icons-vue'
import type { MenuNode } from '@/types/menu'
import type { ColumnDef, ColumnItemState } from '@/composables/useColumnConfig'
import { deleteMenu, getMenuTree, sortMenus } from '@/api/system/menu'
import { useColumnConfig } from '@/composables/useColumnConfig'
import { resolveIcon } from '@/utils/icon'
import { matchByMode, isTextMatchMode, splitBatchValues, BATCH_LIMIT, type TextMatchMode } from '@/utils/textFilter'
import { collectExpandKeys, filterTree } from '@/utils/tree'
import StatusTag from '@/components/base/StatusTag.vue'
import SmartTable from '@/components/base/SmartTable.vue'
import ColumnFilter from '@/components/base/ColumnFilter.vue'
import ColumnSettingTrigger from '@/components/base/ColumnSettingTrigger.vue'
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
  defaultFiltersRaw
} = useColumnConfig('system:menu', schema)

const menuTableRef = ref<InstanceType<typeof SmartTable>>()
const loading = ref(false)
const treeData = ref<MenuNode[]>([])
const expandRowKeys = ref<number[]>([])

const filters = reactive<{
  menuName: string
  menuType: number | number[] | '' | undefined
  routePath: string
  component: string
  perms: string
  terminalType: string
  sortRange: [number | undefined, number | undefined]
  visible: boolean | boolean[] | '' | undefined
  status: boolean | boolean[] | '' | undefined
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

/** 文本列运行时匹配方式：由表头多模式控件写入 */
const textModes = reactive<Record<string, TextMatchMode>>({})

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
  menuName: filters.menuName.trim(),
  routePath: filters.routePath.trim(),
  component: filters.component.trim(),
  perms: filters.perms.trim(),
  terminalType: filters.terminalType.trim(),
  menuType: toValueArray(filters.menuType),
  visible: toValueArray(filters.visible),
  status: toValueArray(filters.status),
  sortMin: filters.sortRange[0] ?? null,
  sortMax: filters.sortRange[1] ?? null,
  updateStart: filters.updateRange[0] || null,
  updateEnd: filters.updateRange[1] || null
}))

function toValueArray<T>(value: T | T[] | '' | undefined): T[] {
  if (Array.isArray(value)) return value
  if (value === '' || value === undefined || value === null) return []
  return [value]
}

const filterActive = computed(() => {
  const n = normalized.value
  return (
    !!n.menuName ||
    !!n.routePath ||
    !!n.component ||
    !!n.perms ||
    !!n.terminalType ||
    n.menuType.length > 0 ||
    n.visible.length > 0 ||
    n.status.length > 0 ||
    n.sortMin !== null ||
    n.sortMax !== null ||
    n.updateStart !== null ||
    n.updateEnd !== null
  )
})

/** 单列匹配：批量超限时由 onFilterChange 统一提示，这里只兜底不命中 */
function safeMatch(raw: unknown, keyword: string, mode: TextMatchMode): boolean {
  try {
    return matchByMode(raw, keyword, mode)
  } catch {
    return false
  }
}

const displayTree = computed(() => {
  if (!filterActive.value) return treeData.value
  const n = normalized.value
  const modes = textModes
  return filterTree(treeData.value, (node) => {
    if (!safeMatch(node.menuName, n.menuName, modes.menuName ?? 'like')) return false
    if (!safeMatch(node.routePath, n.routePath, modes.routePath ?? 'like')) return false
    if (!safeMatch(node.component, n.component, modes.component ?? 'like')) return false
    if (!safeMatch(node.perms, n.perms, modes.perms ?? 'like')) return false
    if (!safeMatch(node.terminalType, n.terminalType, modes.terminalType ?? 'like')) return false
    if (n.menuType.length && !n.menuType.includes(node.menuType)) return false
    if (n.visible.length && !n.visible.some((item) => item === Boolean(node.visible))) return false
    if (n.status.length && !n.status.some((item) => item === Boolean(node.status))) return false
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

function typeLabel(type: number) {
  if (type === 1) return '目录'
  if (type === 2) return '菜单'
  return '按钮'
}

function handleRowCommand(command: string, row: MenuNode) {
  if (command === 'edit') {
    openEdit(row)
    return
  }
  if (command === 'delete') {
    void handleDelete(row)
  }
}

/* ---------- 同级拖拽排序 ----------
 * 本期只开放同级：跨层级会改 parentId，必须走编辑接口的父子矩阵、深度、名称/路由唯一性校验；
 * 拖拽直接改父级容易绕过校验，因此跨层级统一提示改用「编辑」。
 */
const menuTableWrap = ref<HTMLElement>()
const dragState = reactive<{
  sourceId?: number
  targetId?: number
  position?: 'before' | 'after'
  valid: boolean
}>({ valid: false })
let lastCrossWarnAt = 0

function menuRowClass({ row }: { row: MenuNode }) {
  return `menu-row menu-row-${row.id}`
}

function findMenuById(nodes: MenuNode[], id: number): MenuNode | undefined {
  for (const node of nodes) {
    if (node.id === id) return node
    const found = node.children ? findMenuById(node.children, id) : undefined
    if (found) return found
  }
  return undefined
}

function rowIdFromEvent(event: DragEvent): number | undefined {
  const target = event.target as HTMLElement | null
  const tr = target?.closest?.('tr.menu-row') as HTMLElement | null
  if (!tr) return undefined
  const token = Array.from(tr.classList).find((name) => name.startsWith('menu-row-'))
  if (!token) return undefined
  const id = Number(token.slice('menu-row-'.length))
  return Number.isFinite(id) ? id : undefined
}

function rowElementFromEvent(event: DragEvent): HTMLElement | null {
  const target = event.target as HTMLElement | null
  return (target?.closest?.('tr.menu-row') as HTMLElement | null) ?? null
}

function clearDragStyles() {
  menuTableWrap.value
    ?.querySelectorAll('tr.is-dragging, tr.drop-before, tr.drop-after')
    .forEach((row) => row.classList.remove('is-dragging', 'drop-before', 'drop-after'))
}

function resetDragState() {
  dragState.sourceId = undefined
  dragState.targetId = undefined
  dragState.position = undefined
  dragState.valid = false
  clearDragStyles()
}

function onRowDragStart(event: DragEvent) {
  const id = rowIdFromEvent(event)
  if (!id) return
  if (filterActive.value) {
    event.preventDefault()
    ElMessage.warning('筛选状态下不支持拖拽排序，请先重置筛选')
    return
  }
  if (!findMenuById(treeData.value, id)) return
  dragState.sourceId = id
  dragState.valid = false
  event.dataTransfer?.setData('text/plain', String(id))
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
  rowElementFromEvent(event)?.classList.add('is-dragging')
}

function onRowDragOver(event: DragEvent) {
  if (!dragState.sourceId) return
  const targetId = rowIdFromEvent(event)
  if (!targetId) return
  event.preventDefault()
  const source = findMenuById(treeData.value, dragState.sourceId)
  const target = findMenuById(treeData.value, targetId)
  if (!source || !target || source.id === target.id) {
    dragState.valid = false
    clearDragStyles()
    return
  }
  const sameLevel = source.parentId === target.parentId
  if (!sameLevel) {
    dragState.valid = false
    dragState.targetId = targetId
    clearDragStyles()
    if (Date.now() - lastCrossWarnAt > 1200) {
      lastCrossWarnAt = Date.now()
      ElMessage.warning('本期仅支持同级拖拽，跨层级请用「编辑」调整上级节点')
    }
    return
  }
  const tr = rowElementFromEvent(event)
  if (!tr) return
  const rect = tr.getBoundingClientRect()
  const after = event.clientY > rect.top + rect.height / 2
  clearDragStyles()
  menuTableWrap.value?.querySelector(`tr.menu-row-${source.id}`)?.classList.add('is-dragging')
  tr.classList.add(after ? 'drop-after' : 'drop-before')
  dragState.targetId = targetId
  dragState.position = after ? 'after' : 'before'
  dragState.valid = true
}

function onRowDrop(event: DragEvent) {
  if (!dragState.sourceId) return
  event.preventDefault()
  const { sourceId, targetId, position, valid } = dragState
  if (!valid || !targetId || !position) {
    resetDragState()
    return
  }
  void commitDragSort(sourceId!, targetId, position)
}

function onRowDragEnd() {
  resetDragState()
}

async function commitDragSort(sourceId: number, targetId: number, position: 'before' | 'after') {
  const source = findMenuById(treeData.value, sourceId)
  if (!source) {
    resetDragState()
    return
  }
  const siblings = source.parentId
    ? findMenuById(treeData.value, source.parentId)?.children || []
    : treeData.value
  const ordered = [...siblings].sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)
  const originalIds = ordered.map((item) => item.id)
  const fromIndex = ordered.findIndex((item) => item.id === sourceId)
  if (fromIndex < 0) {
    resetDragState()
    return
  }
  const [moved] = ordered.splice(fromIndex, 1)
  let targetIndex = ordered.findIndex((item) => item.id === targetId)
  if (targetIndex < 0) {
    resetDragState()
    return
  }
  if (position === 'after') targetIndex += 1
  ordered.splice(targetIndex, 0, moved)
  const nextIds = ordered.map((item) => item.id)
  resetDragState()
  if (nextIds.join(',') === originalIds.join(',')) return

  try {
    await sortMenus(nextIds)
    await loadData()
    ElMessage.success('排序已保存')
  } catch (error) {
    const message = (error as { message?: string })?.message
    ElMessage.error(message || '排序保存失败，已回滚')
    await loadData()
  }
}

/** 表格重渲染后恢复行可拖拽属性（树展开/筛选都会重建行） */
function applyDraggableRows() {
  menuTableWrap.value
    ?.querySelectorAll('tr.menu-row')
    .forEach((row) => row.setAttribute('draggable', 'true'))
}

watch(
  [displayTree, expandRowKeys],
  () => {
    void nextTick(applyDraggableRows)
  },
  { immediate: true }
)

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
  Object.keys(textModes).forEach((key) => delete textModes[key])
}

/** 表头筛选变化：纯前端筛选，立即重算树与展开项 */
function onFilterChange() {
  if (hasBatchOverflow()) {
    ElMessage.error(`批量查询最多 ${BATCH_LIMIT} 个值，已按不匹配处理`)
  }
  expandRowKeys.value = filterActive.value
    ? collectExpandKeys(displayTree.value)
    : collectExpandKeys(treeData.value, true)
}

/** 批量模式关键字超限检测：与后端上限保持一致，超限时明确提示 */
function hasBatchOverflow(): boolean {
  const n = normalized.value
  const pairs: Array<[string, TextMatchMode]> = [
    [n.menuName, textModes.menuName ?? 'like'],
    [n.routePath, textModes.routePath ?? 'like'],
    [n.component, textModes.component ?? 'like'],
    [n.perms, textModes.perms ?? 'like'],
    [n.terminalType, textModes.terminalType ?? 'like']
  ]
  return pairs.some(
    ([keyword, mode]) =>
      mode === 'batch' && keyword.trim() !== '' && splitBatchValues(keyword).length > BATCH_LIMIT
  )
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
  const defaults = defaultFiltersRaw()
  applyTextDefault('menuName', defaults.menuName, (value) => (filters.menuName = value))
  applyTextDefault('routePath', defaults.routePath, (value) => (filters.routePath = value))
  applyTextDefault('component', defaults.component, (value) => (filters.component = value))
  applyTextDefault('perms', defaults.perms, (value) => (filters.perms = value))
  applyTextDefault('terminalType', defaults.terminalType, (value) => (filters.terminalType = value))

  const menuTypes = toValueList(defaults.menuType).map((item) => Number(item))
  if (menuTypes.length) filters.menuType = menuTypes.length === 1 ? menuTypes[0] : menuTypes

  const visibles = toValueList(defaults.visible).filter(
    (item): item is boolean => typeof item === 'boolean'
  )
  if (visibles.length) filters.visible = visibles.length === 1 ? visibles[0] : visibles

  const statuses = toValueList(defaults.status).filter(
    (item): item is boolean => typeof item === 'boolean'
  )
  if (statuses.length) filters.status = statuses.length === 1 ? statuses[0] : statuses

  if (Array.isArray(defaults.sortOrder)) {
    filters.sortRange = [
      defaults.sortOrder[0] == null ? undefined : Number(defaults.sortOrder[0]),
      defaults.sortOrder[1] == null ? undefined : Number(defaults.sortOrder[1])
    ]
  }
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
    ElMessage.warning('存在下级内容，请先删除下级内容')
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

/* 行操作：单行不换行，避免固定操作列内按钮被裁切 */
.row-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: nowrap;
  white-space: nowrap;
}

.more-icon {
  margin-left: 2px;
}

/* 同级拖拽视觉反馈 */
.menu-table :deep(tr.menu-row) {
  cursor: grab;
}

.menu-table :deep(tr.menu-row.is-dragging) {
  opacity: 0.45;
}

.menu-table :deep(tr.menu-row.drop-before td) {
  box-shadow: inset 0 2px 0 var(--color-primary);
}

.menu-table :deep(tr.menu-row.drop-after td) {
  box-shadow: inset 0 -2px 0 var(--color-primary);
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
