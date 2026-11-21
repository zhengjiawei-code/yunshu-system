<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">用户管理</h2>
        <p class="page-header__desc">
          维护登录账号、所属机构、角色与账号级直授；数据范围默认本机构及以下，过滤开关未启用
        </p>
      </div>
    </div>

    <div class="page-content">
      <div class="action-bar">
        <div class="action-left">
          <el-button type="primary" @click="openCreate">新增用户</el-button>
        </div>

        <div class="action-right">
          <el-button class="tool-reset" @click="resetFilters">重置</el-button>
          <el-button class="tool-export" :icon="Download" @click="handleExport">导出</el-button>

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

      <div class="user-table-wrap">
        <smart-table
          ref="userTableRef"
          v-loading="loading"
          :data="rows"
          row-key="id"
          height="100%"
          :empty-text="emptyText"
          persist-key="system:user"
          :column-order="columnOrder"
          :column-hidden="hiddenKeys"
          show-summary
          :summary-method="summaryMethod"
          show-overflow-tooltip
          scrollbar-always-on
          class="user-table"
          @sort-change="handleSortChange"
        >
          <el-table-column type="index" label="序号" width="80" align="center" :index="indexMethod" />

          <el-table-column
            v-if="isVisible('username')"
            prop="username"
            label="用户名"
            min-width="130"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">用户名</span>
                <column-filter
                  v-if="col('username').filterType !== 'none'"
                  v-model="filters.username"
                  :type="col('username').filterType"
                  :options="col('username').filterOptions"
                  @change="onTextFilter"
                />
              </div>
            </template>
            <template #default="{ row }">
              <span class="mono">{{ row.username }}</span>
            </template>
          </el-table-column>

          <el-table-column
            v-if="isVisible('realName')"
            prop="realName"
            label="真实姓名"
            min-width="120"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">真实姓名</span>
                <column-filter
                  v-if="col('realName').filterType !== 'none'"
                  v-model="filters.realName"
                  :type="col('realName').filterType"
                  :options="col('realName').filterOptions"
                  @change="onTextFilter"
                />
              </div>
            </template>
            <template #default="{ row }">{{ row.realName }}</template>
          </el-table-column>

          <el-table-column v-if="isVisible('phone')" prop="phone" label="手机号" min-width="130">
            <template #header>
              <div class="col-head">
                <span class="col-head__label">手机号</span>
                <column-filter
                  v-if="col('phone').filterType !== 'none'"
                  v-model="filters.phone"
                  :type="col('phone').filterType"
                  :options="col('phone').filterOptions"
                  @change="onTextFilter"
                />
              </div>
            </template>
            <template #default="{ row }">
              <span class="mono">{{ row.phone || '—' }}</span>
            </template>
          </el-table-column>

          <el-table-column v-if="isVisible('email')" prop="email" label="邮箱" min-width="160">
            <template #header>
              <div class="col-head">
                <span class="col-head__label">邮箱</span>
                <column-filter
                  v-if="col('email').filterType !== 'none'"
                  v-model="filters.email"
                  :type="col('email').filterType"
                  :options="col('email').filterOptions"
                  @change="onTextFilter"
                />
              </div>
            </template>
            <template #default="{ row }">{{ row.email || '—' }}</template>
          </el-table-column>

          <el-table-column
            v-if="isVisible('deptName')"
            prop="deptName"
            label="所属机构"
            min-width="160"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">所属机构</span>
                <column-filter
                  v-if="col('deptName').filterType !== 'none'"
                  v-model="filters.deptId"
                  :type="col('deptName').filterType"
                  :options="deptOptions"
                  @change="onFilterChange"
                />
              </div>
            </template>
            <template #default="{ row }">{{ row.deptName || '—' }}</template>
          </el-table-column>

          <el-table-column
            v-if="isVisible('authOrgNames')"
            label="可登录机构"
            min-width="160"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">可登录机构</span>
              </div>
            </template>
            <template #default="{ row }">
              <div v-if="row.authOrgNames.length" class="org-tags">
                <el-tooltip
                  v-for="name in row.authOrgNames.slice(0, 2)"
                  :key="name"
                  :content="name"
                  placement="top"
                >
                  <el-tag size="small" effect="plain" class="org-tag">{{ name }}</el-tag>
                </el-tooltip>
                <el-tooltip
                  v-if="row.authOrgNames.length > 2"
                  :content="row.authOrgNames.join('、')"
                  placement="top"
                >
                  <el-tag size="small" type="info" effect="plain" class="org-tag">
                    +{{ row.authOrgNames.length - 2 }}
                  </el-tag>
                </el-tooltip>
              </div>
              <span v-else>—</span>
            </template>
          </el-table-column>

          <el-table-column v-if="isVisible('roleNames')" label="角色" min-width="160">
            <template #header>
              <div class="col-head">
                <span class="col-head__label">角色</span>
                <column-filter
                  v-if="col('roleNames').filterType !== 'none'"
                  v-model="filters.roleId"
                  :type="col('roleNames').filterType"
                  :options="roleFilterOptions"
                  @change="onFilterChange"
                />
              </div>
            </template>
            <template #default="{ row }">{{ row.roleNames.length ? row.roleNames.join('、') : '—' }}</template>
          </el-table-column>

          <el-table-column
            v-if="isVisible('directPerm')"
            label="直接授权"
            width="130"
            align="center"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">直接授权</span>
                <column-filter
                  v-if="col('directPerm').filterType !== 'none'"
                  v-model="filters.hasDirectPerm"
                  :type="col('directPerm').filterType"
                  :options="col('directPerm').filterOptions"
                  @change="onFilterChange"
                />
              </div>
            </template>
            <template #default="{ row }">
              <div class="perm-count">
                <el-tag v-if="row.grantedCount" size="small" type="success" effect="plain">
                  +{{ row.grantedCount }}
                </el-tag>
                <el-tag v-if="row.deniedCount" size="small" type="danger" effect="plain">
                  -{{ row.deniedCount }}
                </el-tag>
                <span v-if="!row.grantedCount && !row.deniedCount">—</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column
            v-if="isVisible('status')"
            prop="status"
            label="状态"
            width="100"
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
              <el-switch
                :model-value="row.status"
                :loading="statusChangingIds.has(row.id)"
                :before-change="() => confirmStatusChange(row)"
                @change="(value: boolean) => applyStatusChange(row, value)"
              />
            </template>
          </el-table-column>

          <el-table-column
            v-if="isVisible('updateByName')"
            prop="updateByName"
            label="最后修改人"
            min-width="120"
          >
            <template #header>
              <div class="col-head">
                <span class="col-head__label">最后修改人</span>
                <column-filter
                  v-if="col('updateByName').filterType !== 'none'"
                  v-model="filters.updateByName"
                  :type="col('updateByName').filterType"
                  :options="col('updateByName').filterOptions"
                  @change="onTextFilter"
                />
              </div>
            </template>
            <template #default="{ row }">{{ row.updateByName || '—' }}</template>
          </el-table-column>

          <el-table-column
            v-if="isVisible('updateTime')"
            prop="updateTime"
            label="最后修改时间"
            min-width="220"
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
            width="260"
            fixed="right"
            align="right"
          >
            <template #default="{ row }">
              <div class="row-actions">
                <el-tooltip v-if="isSuperAdmin(row)" content="超级管理员拥有全部权限，无需配置" placement="top">
                  <span class="btn-wrap">
                    <el-button link type="primary" size="small" disabled>权限</el-button>
                  </span>
                </el-tooltip>
                <el-button v-else link type="primary" size="small" @click="openPerm(row)">权限</el-button>

                <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>

                <el-button link type="warning" size="small" @click="handleResetPassword(row)">
                  重置密码
                </el-button>

                <el-tooltip v-if="isSuperAdmin(row)" content="超级管理员不允许删除" placement="top">
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

    <user-form-drawer
      v-model="drawer.visible"
      :mode="drawer.mode"
      :record="drawer.record"
      @saved="handleSaved"
    />

    <user-perm-dialog
      v-model="permDialog.visible"
      :user="permDialog.user"
      @saved="loadPage"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Setting } from '@element-plus/icons-vue'
import type { DeptNode } from '@/types/dept'
import type { RoleRecord } from '@/types/role'
import type { UserQuery, UserRecord } from '@/types/user'
import type { ColumnDef, ColumnItemState } from '@/composables/useColumnConfig'
import { deleteUser, getUserPage, resetUserPassword, changeUserStatus } from '@/api/system/user'
import { getDeptOptions } from '@/api/system/dept'
import { getRoleOptions } from '@/api/system/role'
import { useColumnConfig } from '@/composables/useColumnConfig'
import SmartTable from '@/components/base/SmartTable.vue'
import ColumnFilter from '@/components/base/ColumnFilter.vue'
import ColumnSetting from '@/components/base/ColumnSetting.vue'
import UserFormDrawer from './components/UserFormDrawer.vue'
import UserPermDialog from './components/UserPermDialog.vue'

defineOptions({ name: 'SystemUser' })

const schema = reactive<ColumnDef[]>([
  { key: 'username', label: '用户名', prop: 'username', filterType: 'text', locked: true },
  { key: 'realName', label: '真实姓名', prop: 'realName', filterType: 'text', locked: true },
  { key: 'phone', label: '手机号', prop: 'phone', filterType: 'text' },
  { key: 'email', label: '邮箱', prop: 'email', filterType: 'text' },
  { key: 'deptName', label: '所属机构', prop: 'deptName', filterType: 'select', filterOptions: [] },
  {
    key: 'authOrgNames',
    label: '可登录机构',
    elKey: 'label:可登录机构',
    filterType: 'none',
    sortable: false
  },
  {
    key: 'roleNames',
    label: '角色',
    elKey: 'label:角色',
    filterType: 'select',
    filterOptions: [],
    sortable: false
  },
  {
    key: 'directPerm',
    label: '直接授权',
    elKey: 'label:直接授权',
    filterType: 'select',
    filterOptions: [
      { label: '有直授', value: true },
      { label: '无直授', value: false }
    ],
    sortable: false
  },
  {
    key: 'status',
    label: '状态',
    prop: 'status',
    filterType: 'select',
    filterOptions: [
      { label: '启用', value: true },
      { label: '禁用', value: false }
    ],
    locked: true
  },
  { key: 'updateByName', label: '最后修改人', prop: 'updateByName', filterType: 'text' },
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
  load: loadColumnConfig,
  save: saveColumnConfig,
  reset: resetColumnConfig,
  defaultFilters
} = useColumnConfig('system:user', schema)

const userTableRef = ref<InstanceType<typeof SmartTable>>()
const loading = ref(false)
const rows = ref<UserRecord[]>([])
const statusChangingIds = ref<Set<number>>(new Set())
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const deptTree = ref<DeptNode[]>([])
const roleOptions = ref<RoleRecord[]>([])

const deptOptions = computed(() =>
  flattenDeptOptions(deptTree.value)
)
const roleFilterOptions = computed(() =>
  roleOptions.value.map((item) => ({ label: item.roleName, value: item.id }))
)

const filters = reactive<{
  username: string
  realName: string
  phone: string
  email: string
  deptId?: number
  roleId?: number
  hasDirectPerm?: boolean
  status?: boolean
  updateByName: string
  updateRange: string[]
}>({
  username: '',
  realName: '',
  phone: '',
  email: '',
  deptId: undefined,
  roleId: undefined,
  hasDirectPerm: undefined,
  status: undefined,
  updateByName: '',
  updateRange: []
})

/** 服务端排序状态：sortBy 为空表示默认 id asc */
const sortState = reactive<{ sortBy?: string; sortOrder?: 'asc' | 'desc' }>({
  sortBy: undefined,
  sortOrder: undefined
})

const emptyText = computed(() => '暂无匹配的用户')

const drawer = reactive<{
  visible: boolean
  mode: 'create' | 'edit'
  record: UserRecord | null
}>({
  visible: false,
  mode: 'create',
  record: null
})

const permDialog = reactive<{
  visible: boolean
  user: UserRecord | null
}>({
  visible: false,
  user: null
})

function isSuperAdmin(row: UserRecord): boolean {
  return row.id === 1
}

function indexMethod(index: number): number {
  return (page.value - 1) * pageSize.value + index + 1
}

function buildQuery(pageNum = page.value, size = pageSize.value): UserQuery {
  return {
    username: isVisible('username') ? filters.username.trim() || undefined : undefined,
    realName: isVisible('realName') ? filters.realName.trim() || undefined : undefined,
    phone: isVisible('phone') ? filters.phone.trim() || undefined : undefined,
    email: isVisible('email') ? filters.email.trim() || undefined : undefined,
    deptId: isVisible('deptName') ? filters.deptId : undefined,
    roleId: isVisible('roleNames') ? filters.roleId : undefined,
    hasDirectPerm: isVisible('directPerm') ? filters.hasDirectPerm : undefined,
    status: isVisible('status') ? filters.status : undefined,
    updateByName: isVisible('updateByName')
      ? filters.updateByName.trim() || undefined
      : undefined,
    updateTimeStart: isVisible('updateTime') ? filters.updateRange[0] || undefined : undefined,
    updateTimeEnd: isVisible('updateTime') ? filters.updateRange[1] || undefined : undefined,
    sortBy: sortState.sortBy,
    sortOrder: sortState.sortOrder,
    pageNum,
    pageSize: size
  }
}

/** 表头点击排序：走服务端 sortBy / sortOrder，取消排序回到默认 id asc */
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
    const result = await getUserPage(buildQuery())
    rows.value = result.records
    total.value = result.total
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    const [depts, roles] = await Promise.all([getDeptOptions(), getRoleOptions()])
    deptTree.value = depts
    roleOptions.value = roles
    // 默认值编辑器与表头筛选项共用同一份选项
    const deptColumn = schema.find((item) => item.key === 'deptName')
    if (deptColumn) deptColumn.filterOptions = deptOptions.value
    const roleColumn = schema.find((item) => item.key === 'roleNames')
    if (roleColumn) roleColumn.filterOptions = roleFilterOptions.value
  } catch {
    // 选项加载失败不阻断列表
  }
}

function flattenDeptOptions(nodes: DeptNode[]): Array<{ label: string; value: number }> {
  const result: Array<{ label: string; value: number }> = []
  const walk = (list: DeptNode[]) => {
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
  filters.username = ''
  filters.realName = ''
  filters.phone = ''
  filters.email = ''
  filters.deptId = undefined
  filters.roleId = undefined
  filters.hasDirectPerm = undefined
  filters.status = undefined
  filters.updateByName = ''
  filters.updateRange = []
  page.value = 1
  loadPage()
}

/* ---------- 统一列设置（后端按用户+页面持久化） ---------- */
function onStatesUpdate(next: ColumnItemState[]) {
  states.value = next
}

async function handleColumnSave() {
  await saveColumnConfig()
}

async function handleColumnReset() {
  await resetColumnConfig()
  userTableRef.value?.resetColumnWidths()
  page.value = 1
  loadPage()
}

/** 首次进入按默认查询值自动带出筛选；默认机构/角色失效时忽略并提示 */
function applyDefaultFilters() {
  const defaults = defaultFilters()
  if (defaults.username != null) filters.username = String(defaults.username)
  if (defaults.realName != null) filters.realName = String(defaults.realName)
  if (defaults.phone != null) filters.phone = String(defaults.phone)
  if (defaults.email != null) filters.email = String(defaults.email)
  if (defaults.updateByName != null) filters.updateByName = String(defaults.updateByName)
  if (typeof defaults.hasDirectPerm === 'boolean') filters.hasDirectPerm = defaults.hasDirectPerm
  if (typeof defaults.status === 'boolean') filters.status = defaults.status
  if (defaults.deptId != null) {
    const deptId = Number(defaults.deptId)
    if (deptOptions.value.some((item) => item.value === deptId)) {
      filters.deptId = deptId
    } else {
      ElMessage.warning('默认查询机构已删除或不在管理范围，已忽略该默认值')
    }
  }
  if (defaults.roleId != null) {
    const roleId = Number(defaults.roleId)
    if (roleOptions.value.some((item) => item.id === roleId)) {
      filters.roleId = roleId
    } else {
      ElMessage.warning('默认查询角色已删除，已忽略该默认值')
    }
  }
  if (Array.isArray(defaults.updateTime)) {
    filters.updateRange = defaults.updateTime.map((item) => String(item))
  }
}

function summaryMethod({ columns }: { columns: Array<{ label: string }> }) {
  return columns.map((_, index) => (index === 0 ? `合计 ${total.value} 个用户` : ''))
}

/** 导出：跟随当前筛选与可见列，导出全部页 */
async function handleExport() {
  const exportColumns = schema.filter(
    (item) => item.key !== 'operation' && isVisible(item.key)
  )
  try {
    const result = await getUserPage(buildQuery(1, 10000))
    const header = exportColumns.map((item) => item.label)
    const body = result.records.map((row) =>
      exportColumns.map((item) => exportValue(row, item.key))
    )
    const csv = [header, ...body]
      .map((line) => line.map(csvCell).join(','))
      .join('\r\n')
    const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `用户管理_${exportStamp()}.csv`
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${result.total} 条`)
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  }
}

function exportValue(row: UserRecord, key: string): string {
  switch (key) {
    case 'authOrgNames':
      return row.authOrgNames.length ? row.authOrgNames.join('、') : '—'
    case 'roleNames':
      return row.roleNames.length ? row.roleNames.join('、') : '—'
    case 'directPerm':
      if (!row.grantedCount && !row.deniedCount) return '—'
      return `+${row.grantedCount} / -${row.deniedCount}`
    case 'status':
      return row.status ? '启用' : '禁用'
    default: {
      const value = row[key as keyof UserRecord]
      return value === undefined || value === null || value === '' ? '—' : String(value)
    }
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

function openEdit(row: UserRecord) {
  drawer.mode = 'edit'
  drawer.record = row
  drawer.visible = true
}

function openPerm(row: UserRecord) {
  permDialog.user = row
  permDialog.visible = true
}

async function handleResetPassword(row: UserRecord) {
  try {
    await ElMessageBox.confirm(
      `确定将「${row.realName}（${row.username}）」的密码重置为 123456 吗？`,
      '重置密码',
      { confirmButtonText: '重置', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await resetUserPassword(row.id)
    ElMessage.success('密码已重置为 123456')
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  }
}

/** 行内启停：二次确认；失败时重拉列表回滚为服务端状态 */
async function confirmStatusChange(row: UserRecord): Promise<boolean> {
  const next = !row.status
  const action = next ? '启用' : '停用'
  const impact = next
    ? '启用后该账号可正常登录并按权限访问系统。'
    : '停用后该账号将无法登录，已登录会话会在服务端失效。'
  try {
    await ElMessageBox.confirm(`确定${action}用户「${row.realName}（${row.username}）」吗？${impact}`, `${action}确认`, {
      confirmButtonText: action,
      cancelButtonText: '取消',
      type: 'warning'
    })
    return true
  } catch {
    return false
  }
}

async function applyStatusChange(row: UserRecord, value: boolean) {
  const next = new Set(statusChangingIds.value)
  next.add(row.id)
  statusChangingIds.value = next
  try {
    await changeUserStatus(row.id, value)
    row.status = value
    ElMessage.success(value ? '已启用' : '已停用')
  } catch (error) {
    const message = (error as { message?: string })?.message
    ElMessage.error(message || '状态更新失败')
    // 回滚界面与服务端一致（超管/本人等拦截场景）
    await loadPage()
  } finally {
    const done = new Set(statusChangingIds.value)
    done.delete(row.id)
    statusChangingIds.value = done
  }
}

async function handleDelete(row: UserRecord) {
  try {
    await ElMessageBox.confirm(`确定删除用户「${row.realName}（${row.username}）」吗？`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteUser(row.id)
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

onMounted(async () => {
  await Promise.all([loadOptions(), loadColumnConfig()])
  applyDefaultFilters()
  loadPage()
})
</script>

<style scoped>
.user-table-wrap {
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

.user-table :deep(.el-table__header th .cell) {
  overflow: visible;
  line-height: 1.4;
}

/* 可登录机构：标签截断，不撑开列 */
.org-tags {
  display: flex;
  flex-wrap: nowrap;
  gap: 4px;
  overflow: hidden;
}

.org-tag {
  max-width: 84px;
  flex-shrink: 0;
}

.org-tag :deep(.el-tag__content) {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.org-tags .org-tag:first-child {
  flex-shrink: 1;
  min-width: 0;
}

.perm-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
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
