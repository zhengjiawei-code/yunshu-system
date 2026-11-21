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

          <el-input
            v-model="filters.menuName"
            class="filter-name"
            placeholder="菜单名称"
            clearable
          />
          <el-select v-model="filters.menuType" class="filter-select" placeholder="类型" clearable>
            <el-option label="目录" :value="1" />
            <el-option label="菜单" :value="2" />
            <el-option label="按钮" :value="3" />
          </el-select>
          <el-select v-model="filters.status" class="filter-select" placeholder="状态" clearable>
            <el-option label="启用" :value="true" />
            <el-option label="禁用" :value="false" />
          </el-select>
        </div>

        <div class="action-right">
          <el-button class="tool-reset" @click="resetFilters">重置</el-button>
          <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
        </div>
      </div>

      <div class="menu-table-wrap">
        <smart-table
          v-loading="loading"
          :data="displayTree"
          row-key="id"
          height="100%"
          :tree-props="{ children: 'children' }"
          :expand-row-keys="expandRowKeys"
          :empty-text="emptyText"
          persist-key="system:menu"
          show-overflow-tooltip
          scrollbar-always-on
          class="menu-table"
          @sort-change="handleSortChange"
        >
          <el-table-column prop="menuName" label="菜单名称" min-width="240">
            <template #default="{ row }">
              <span class="menu-name" :class="{ 'is-directory': row.menuType === 1 }">
                <el-icon v-if="row.menuType !== 3" class="menu-name__icon">
                  <component :is="resolveIcon(row.icon)" />
                </el-icon>
                <span class="menu-name__text">{{ row.menuName }}</span>
              </span>
            </template>
          </el-table-column>

          <el-table-column prop="menuType" label="类型" width="90" align="center">
            <template #default="{ row }">
              <status-tag :type="typeTag(row.menuType).type" :text="typeTag(row.menuType).text" />
            </template>
          </el-table-column>

          <el-table-column prop="routePath" label="路由地址" min-width="200">
            <template #default="{ row }">{{ row.routePath || '—' }}</template>
          </el-table-column>

          <el-table-column prop="component" label="组件路径" min-width="180">
            <template #default="{ row }">{{ row.component || '—' }}</template>
          </el-table-column>

          <el-table-column prop="perms" label="权限标识" min-width="160">
            <template #default="{ row }">
              <span :class="{ 'mono': row.perms }">{{ row.perms || '—' }}</span>
            </template>
          </el-table-column>

          <el-table-column prop="terminalType" label="终端" min-width="90" align="center">
            <template #default="{ row }">{{ row.terminalType || '—' }}</template>
          </el-table-column>

          <el-table-column prop="sortOrder" label="排序" width="80" align="right" />

          <el-table-column prop="visible" label="显示" width="80" align="center">
            <template #default="{ row }">
              <status-tag
                v-if="row.menuType !== 3"
                :type="row.visible ? 'success' : 'info'"
                :text="row.visible ? '显示' : '隐藏'"
              />
              <span v-else>—</span>
            </template>
          </el-table-column>

          <el-table-column prop="status" label="状态" width="80" align="center">
            <template #default="{ row }">
              <status-tag
                :type="row.status ? 'success' : 'danger'"
                :text="row.status ? '启用' : '禁用'"
              />
            </template>
          </el-table-column>

          <el-table-column prop="updateTime" label="最后修改时间" min-width="170" align="center">
            <template #default="{ row }">{{ row.updateTime || '—' }}</template>
          </el-table-column>

          <el-table-column label="操作" width="240" fixed="right" align="right">
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
import { Refresh } from '@element-plus/icons-vue'
import type { MenuNode } from '@/types/menu'
import { deleteMenu, getMenuTree } from '@/api/system/menu'
import { resolveIcon } from '@/utils/icon'
import { collectExpandKeys, filterTree } from '@/utils/tree'
import StatusTag from '@/components/base/StatusTag.vue'
import SmartTable from '@/components/base/SmartTable.vue'
import MenuFormDrawer from './components/MenuFormDrawer.vue'
import ButtonFormDrawer from './components/ButtonFormDrawer.vue'

defineOptions({ name: 'SystemMenu' })

const loading = ref(false)
const treeData = ref<MenuNode[]>([])
const expandRowKeys = ref<number[]>([])

const filters = reactive<{
  menuName: string
  menuType: number | '' | undefined
  status: boolean | '' | undefined
}>({
  menuName: '',
  menuType: '',
  status: ''
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
  keyword: filters.menuName.trim().toLowerCase(),
  menuType: filters.menuType === '' || filters.menuType === undefined ? null : filters.menuType,
  status: filters.status === '' || filters.status === undefined ? null : filters.status
}))

const filterActive = computed(
  () =>
    !!normalized.value.keyword ||
    normalized.value.menuType !== null ||
    normalized.value.status !== null
)

const displayTree = computed(() => {
  if (!filterActive.value) return treeData.value
  const { keyword, menuType, status } = normalized.value
  return filterTree(treeData.value, (node) => {
    if (keyword && !node.menuName.toLowerCase().includes(keyword)) return false
    if (menuType !== null && node.menuType !== menuType) return false
    if (status !== null && node.status !== status) return false
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
  filters.status = ''
}

watch(
  () => [filters.menuName, filters.menuType, filters.status],
  () => {
    expandRowKeys.value = filterActive.value ? collectExpandKeys(displayTree.value) : collectExpandKeys(treeData.value, true)
  }
)

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

onMounted(loadData)
</script>

<style scoped>
.menu-table-wrap {
  flex: 1;
  min-height: 0;
}

.filter-name {
  width: 180px;
}

.filter-select {
  width: 120px;
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
