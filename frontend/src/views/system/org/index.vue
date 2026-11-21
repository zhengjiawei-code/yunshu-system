<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">机构管理</h2>
        <p class="page-header__desc">维护集团-公司-部门-分拨中心-网点树形架构与机构级业务授权</p>
      </div>
    </div>

    <div class="page-content">
      <div class="org-layout">
        <aside class="org-tree-panel">
          <div class="org-tree-panel__head">
            <span class="org-tree-panel__title">机构树</span>
            <div class="org-tree-panel__tools">
              <el-tooltip content="展开全部" placement="top">
                <el-button link :icon="Expand" @click="setTreeExpanded(true)" />
              </el-tooltip>
              <el-tooltip content="收起全部" placement="top">
                <el-button link :icon="Fold" @click="setTreeExpanded(false)" />
              </el-tooltip>
            </div>
          </div>
          <div v-loading="loading" class="org-tree-panel__body">
            <el-tree
              ref="treeRef"
              class="org-tree"
              :data="treeData"
              node-key="id"
              highlight-current
              default-expand-all
              :expand-on-click-node="false"
              :props="{ label: 'deptName', children: 'children' }"
              @node-click="handleSelect"
            >
              <template #default="{ data }">
                <span class="tree-node" :class="{ 'is-disabled': !data.status }">
                  <span class="tree-node__text" :title="data.deptName">{{ data.deptName }}</span>
                  <span v-if="!data.status" class="tree-node__badge">禁用</span>
                </span>
              </template>
            </el-tree>
          </div>
        </aside>

        <section class="org-list-panel">
          <div class="org-list-panel__head">
            <div class="org-list-panel__title">
              <span class="org-list-panel__name">{{ selectedNode?.deptName || '机构列表' }}</span>
              <span class="org-list-panel__count">共 {{ total }} 个机构</span>
            </div>
          </div>

          <div class="action-bar">
            <div class="action-left">
              <el-button type="primary" @click="openCreate(selectedId)">新增机构</el-button>
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

          <div class="org-table-wrap">
            <smart-table
              ref="orgTableRef"
              v-loading="loading"
              :data="rows"
              row-key="id"
              height="100%"
              :empty-text="emptyText"
              persist-key="system:org"
              show-summary
              :summary-method="summaryMethod"
              show-overflow-tooltip
              scrollbar-always-on
              class="org-table"
              @sort-change="handleSortChange"
            >
              <el-table-column
                v-if="columnState.deptName"
                prop="deptName"
                label="机构名称"
                min-width="240"
              >
                <template #header>
                  <div class="col-head">
                    <span class="col-head__label">机构名称</span>
                    <el-input
                      v-model="filters.deptName"
                      size="small"
                      placeholder="请输入"
                      clearable
                      @input="onTextFilter"
                    />
                  </div>
                </template>
                <template #default="{ row }">
                  <span class="org-name__text">{{ row.deptName }}</span>
                </template>
              </el-table-column>

              <el-table-column
                v-if="columnState.parentName"
                prop="parentName"
                label="上级机构"
                min-width="200"
                :show-overflow-tooltip="false"
              >
                <template #header>
                  <div class="col-head">
                    <span class="col-head__label">上级机构</span>
                    <el-select
                      v-model="filters.parentId"
                      size="small"
                      placeholder="全部"
                      clearable
                      filterable
                      @change="onFilterChange"
                    >
                      <el-option
                        v-for="item in parentOptions"
                        :key="item.id"
                        :label="item.label"
                        :value="item.id"
                      />
                    </el-select>
                  </div>
                </template>
                <template #default="{ row }">
                  <el-tooltip :content="row.parentPath" placement="top" :show-after="300">
                    <span class="org-parent">{{ row.parentName }}</span>
                  </el-tooltip>
                </template>
              </el-table-column>

              <el-table-column
                v-if="columnState.deptType"
                prop="deptType"
                label="类型"
                min-width="136"
                align="center"
              >
                <template #header>
                  <div class="col-head">
                    <span class="col-head__label">类型</span>
                    <el-select
                      v-model="filters.deptType"
                      size="small"
                      placeholder="全部"
                      clearable
                      @change="onFilterChange"
                    >
                      <el-option
                        v-for="item in typeOptions"
                        :key="item.value"
                        :label="item.label"
                        :value="item.value"
                      />
                    </el-select>
                  </div>
                </template>
                <template #default="{ row }">
                  <status-tag :type="typeTag(row.deptType).type" :text="typeTag(row.deptType).text" />
                </template>
              </el-table-column>

              <el-table-column
                v-if="columnState.deptNature"
                prop="deptNature"
                label="机构性质"
                min-width="136"
                align="center"
              >
                <template #header>
                  <div class="col-head">
                    <span class="col-head__label">机构性质</span>
                    <el-select
                      v-model="filters.deptNature"
                      size="small"
                      placeholder="全部"
                      clearable
                      @change="onFilterChange"
                    >
                      <el-option
                        v-for="item in natureOptions"
                        :key="item.value"
                        :label="item.label"
                        :value="item.value"
                      />
                    </el-select>
                  </div>
                </template>
                <template #default="{ row }">
                  <status-tag
                    :type="natureTag(row.deptNature).type"
                    :text="natureTag(row.deptNature).text"
                  />
                </template>
              </el-table-column>

              <el-table-column
                v-if="columnState.deptCode"
                prop="deptCode"
                label="机构编码"
                min-width="160"
              >
                <template #header>
                  <div class="col-head">
                    <span class="col-head__label">机构编码</span>
                    <el-input
                      v-model="filters.deptCode"
                      size="small"
                      placeholder="请输入"
                      clearable
                      @input="onTextFilter"
                    />
                  </div>
                </template>
                <template #default="{ row }">
                  <span class="mono">{{ row.deptCode }}</span>
                </template>
              </el-table-column>

              <el-table-column v-if="columnState.leader" prop="leader" label="负责人" min-width="136">
                <template #header>
                  <div class="col-head">
                    <span class="col-head__label">负责人</span>
                    <el-input
                      v-model="filters.leader"
                      size="small"
                      placeholder="请输入"
                      clearable
                      @input="onTextFilter"
                    />
                  </div>
                </template>
                <template #default="{ row }">{{ row.leader || '—' }}</template>
              </el-table-column>

              <el-table-column
                v-if="columnState.phone"
                prop="phone"
                label="联系电话"
                min-width="140"
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
                <template #default="{ row }">{{ row.phone || '—' }}</template>
              </el-table-column>

              <el-table-column
                v-if="columnState.regionName"
                prop="regionName"
                label="所在地区"
                min-width="240"
                sortable="custom"
              >
                <template #header>
                  <div class="col-head">
                    <span class="col-head__label">所在地区</span>
                    <div class="region-filter">
                      <el-select
                        v-model="filters.provinceCode"
                        size="small"
                        placeholder="省"
                        clearable
                        filterable
                        @change="handleProvinceFilter"
                      >
                        <el-option
                          v-for="item in provinceOptions"
                          :key="item.code"
                          :label="item.name"
                          :value="item.code"
                        />
                      </el-select>
                      <el-select
                        v-model="filters.cityCode"
                        size="small"
                        placeholder="市"
                        clearable
                        filterable
                        :disabled="!filters.provinceCode"
                        @change="onFilterChange"
                      >
                        <el-option
                          v-for="item in filterCityOptions"
                          :key="item.code"
                          :label="item.name"
                          :value="item.code"
                        />
                      </el-select>
                    </div>
                  </div>
                </template>
                <template #default="{ row }">{{ row.regionName || '—' }}</template>
              </el-table-column>

              <el-table-column
                v-if="columnState.isLeaf"
                prop="isLeaf"
                label="是否末端"
                min-width="136"
                align="center"
              >
                <template #header>
                  <div class="col-head">
                    <span class="col-head__label">是否末端</span>
                    <el-select
                      v-model="filters.isLeaf"
                      size="small"
                      placeholder="全部"
                      clearable
                      @change="onFilterChange"
                    >
                      <el-option label="是" :value="true" />
                      <el-option label="否" :value="false" />
                    </el-select>
                  </div>
                </template>
                <template #default="{ row }">
                  <status-tag
                    v-if="row.deptType === 5"
                    :type="row.isLeaf ? 'primary' : 'info'"
                    :text="row.isLeaf ? '是' : '否'"
                  />
                  <span v-else>—</span>
                </template>
              </el-table-column>

              <el-table-column
                v-if="columnState.applyCompanyNames"
                prop="applyCompanyNames"
                label="适用公司"
                min-width="200"
                sortable="custom"
              >
                <template #header>
                  <div class="col-head">
                    <span class="col-head__label">适用公司</span>
                    <el-select
                      v-model="filters.applyCompanyId"
                      size="small"
                      placeholder="全部"
                      clearable
                      filterable
                      @change="onFilterChange"
                    >
                      <el-option
                        v-for="item in companyOptions"
                        :key="item.id"
                        :label="item.deptName"
                        :value="item.id"
                      />
                    </el-select>
                  </div>
                </template>
                <template #default="{ row }">
                  <span v-if="row.deptType === 5 && row.isLeaf">
                    {{ row.applyCompanyNames || '未配置' }}
                  </span>
                  <span v-else>—</span>
                </template>
              </el-table-column>

              <el-table-column
                v-if="columnState.status"
                prop="status"
                label="状态"
                min-width="136"
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
                      <el-option label="禁用" :value="false" />
                    </el-select>
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
                v-if="columnState.createByName"
                prop="createByName"
                label="创建人"
                min-width="136"
              >
                <template #header>
                  <div class="col-head">
                    <span class="col-head__label">创建人</span>
                    <el-input
                      v-model="filters.createByName"
                      size="small"
                      placeholder="请输入"
                      clearable
                      @input="onTextFilter"
                    />
                  </div>
                </template>
                <template #default="{ row }">{{ row.createByName || '—' }}</template>
              </el-table-column>

              <el-table-column
                v-if="columnState.createTime"
                prop="createTime"
                label="创建时间"
                min-width="200"
                align="center"
              >
                <template #header>
                  <div class="col-head">
                    <span class="col-head__label">创建时间</span>
                    <el-date-picker
                      v-model="filters.createRange"
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
                <template #default="{ row }">{{ row.createTime || '—' }}</template>
              </el-table-column>

              <el-table-column
                v-if="columnState.updateByName"
                prop="updateByName"
                label="最后修改人"
                min-width="136"
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

              <el-table-column label="操作" width="170" fixed="right" align="right">
                <template #default="{ row }">
                  <div class="row-actions">
                    <el-button link type="primary" size="small" @click="openAuth(row)">
                      机构授权
                    </el-button>
                    <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
                    <el-tooltip v-if="row.id === HQ_ID" content="内置总部不允许删除" placement="top">
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
        </section>
      </div>
    </div>

    <dept-form-drawer
      v-model="drawer.visible"
      :mode="drawer.mode"
      :parent-id="drawer.parentId"
      :record="drawer.record"
      :tree="treeData"
      @saved="handleSaved"
    />

    <dept-auth-drawer
      v-model="authDrawer.visible"
      :record="authDrawer.record"
      @saved="loadPage"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Expand, Fold, Setting } from '@element-plus/icons-vue'
import type { DeptNature, DeptNode, DeptQuery, DeptRow, DeptType } from '@/types/dept'
import type { RegionNode } from '@/types/region'
import { deleteDept, getDeptPage, getDeptTree } from '@/api/system/dept'
import { getRegionChildren } from '@/api/system/region'
import StatusTag from '@/components/base/StatusTag.vue'
import SmartTable from '@/components/base/SmartTable.vue'
import DeptFormDrawer from './components/DeptFormDrawer.vue'
import DeptAuthDrawer from './components/DeptAuthDrawer.vue'

defineOptions({ name: 'SystemOrg' })

const HQ_ID = 1
const COLUMN_STORAGE_KEY = 'yunshu_org_columns_v1'

const typeOptions: Array<{ label: string; value: DeptType }> = [
  { label: '总部', value: 1 },
  { label: '公司', value: 2 },
  { label: '部门', value: 3 },
  { label: '分拨中心', value: 4 },
  { label: '网点', value: 5 }
]

const natureOptions: Array<{ label: string; value: DeptNature }> = [
  { label: '自营', value: 1 },
  { label: '加盟', value: 2 },
  { label: '合作', value: 3 }
]

const columnOptions: Array<{ key: string; label: string; locked?: boolean }> = [
  { key: 'deptName', label: '机构名称', locked: true },
  { key: 'parentName', label: '上级机构' },
  { key: 'deptType', label: '类型' },
  { key: 'deptNature', label: '机构性质' },
  { key: 'deptCode', label: '机构编码' },
  { key: 'leader', label: '负责人' },
  { key: 'phone', label: '联系电话' },
  { key: 'regionName', label: '所在地区' },
  { key: 'isLeaf', label: '是否末端' },
  { key: 'applyCompanyNames', label: '适用公司' },
  { key: 'status', label: '状态' },
  { key: 'createByName', label: '创建人' },
  { key: 'createTime', label: '创建时间' },
  { key: 'updateByName', label: '最后修改人' },
  { key: 'updateTime', label: '最后修改时间' }
]

const treeRef = ref<any>()
const orgTableRef = ref<InstanceType<typeof SmartTable>>()
const loading = ref(false)
const treeData = ref<DeptNode[]>([])
const selectedId = ref<number>(HQ_ID)
const rows = ref<DeptRow[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const columnState = reactive<Record<string, boolean>>(loadColumnState())

const filters = reactive<{
  deptName: string
  deptCode: string
  deptType: DeptType | undefined
  deptNature: DeptNature | undefined
  parentId: number | undefined
  leader: string
  phone: string
  provinceCode: string
  cityCode: string
  applyCompanyId: number | undefined
  isLeaf: boolean | undefined
  status: boolean | undefined
  createByName: string
  updateByName: string
  createRange: string[]
  updateRange: string[]
}>({
  deptName: '',
  deptCode: '',
  deptType: undefined,
  deptNature: undefined,
  parentId: undefined,
  leader: '',
  phone: '',
  provinceCode: '',
  cityCode: '',
  applyCompanyId: undefined,
  isLeaf: undefined,
  status: undefined,
  createByName: '',
  updateByName: '',
  createRange: [],
  updateRange: []
})

/** 省市筛选下拉：省份来自区划根节点，城市随省份联动 */
const provinceOptions = ref<RegionNode[]>([])
const filterCityOptions = ref<RegionNode[]>([])

/** 适用公司候选：机构树中所有启用的公司 */
const companyOptions = computed(() => {
  const result: DeptNode[] = []
  const walk = (nodes: DeptNode[]) => {
    nodes.forEach((node) => {
      if (node.deptType === 2 && node.status) result.push(node)
      if (node.children) walk(node.children)
    })
  }
  walk(treeData.value)
  return result
})

/** 服务端排序状态：sortBy 为空表示默认 sort_order asc, id asc */
const sortState = reactive<{ sortBy?: string; sortOrder?: 'asc' | 'desc' }>({
  sortBy: undefined,
  sortOrder: undefined
})

const selectedNode = computed(() => findDept(treeData.value, selectedId.value))

const emptyText = computed(() => '暂无匹配的机构')

const parentOptions = computed(() => {
  const root = selectedNode.value
  if (!root) return [] as Array<{ id: number; label: string }>
  const options: Array<{ id: number; label: string }> = [{ id: root.id, label: root.deptName }]
  const walk = (node: DeptNode, depth: number) => {
    const children = [...(node.children || [])].sort(
      (a, b) => (a.sortOrder || 0) - (b.sortOrder || 0) || a.id - b.id
    )
    children.forEach((child) => {
      options.push({ id: child.id, label: `${'　'.repeat(depth)}${child.deptName}` })
      walk(child, depth + 1)
    })
  }
  walk(root, 1)
  return options
})

const drawer = reactive<{
  visible: boolean
  mode: 'create' | 'edit'
  parentId: number
  record: DeptNode | null
}>({
  visible: false,
  mode: 'create',
  parentId: HQ_ID,
  record: null
})

const authDrawer = reactive<{
  visible: boolean
  record: DeptNode | null
}>({
  visible: false,
  record: null
})

function typeTag(type: number) {
  if (type === 1) return { type: 'primary' as const, text: '总部' }
  if (type === 2) return { type: 'primary' as const, text: '公司' }
  if (type === 4) return { type: 'warning' as const, text: '分拨中心' }
  if (type === 5) return { type: 'info' as const, text: '网点' }
  return { type: 'info' as const, text: '部门' }
}

function natureTag(nature: number) {
  if (nature === 2) return { type: 'warning' as const, text: '加盟' }
  if (nature === 3) return { type: 'info' as const, text: '合作' }
  return { type: 'primary' as const, text: '自营' }
}

function findDept(nodes: DeptNode[], id: number): DeptNode | undefined {
  for (const node of nodes) {
    if (node.id === id) return node
    const found = node.children ? findDept(node.children, id) : undefined
    if (found) return found
  }
  return undefined
}

function isEmpty(value: unknown): boolean {
  return value === undefined || value === null || value === ''
}

function buildQuery(pageNum = page.value, size = pageSize.value): DeptQuery {
  return {
    rootId: selectedId.value,
    deptName: filters.deptName.trim() || undefined,
    deptCode: filters.deptCode.trim() || undefined,
    deptType: isEmpty(filters.deptType) ? undefined : filters.deptType,
    deptNature: isEmpty(filters.deptNature) ? undefined : filters.deptNature,
    parentId: isEmpty(filters.parentId) ? undefined : filters.parentId,
    leader: filters.leader.trim() || undefined,
    phone: filters.phone.trim() || undefined,
    provinceCode: filters.provinceCode || undefined,
    cityCode: filters.cityCode || undefined,
    applyCompanyId: isEmpty(filters.applyCompanyId) ? undefined : filters.applyCompanyId,
    isLeaf: isEmpty(filters.isLeaf) ? undefined : filters.isLeaf,
    status: isEmpty(filters.status) ? undefined : filters.status,
    createByName: filters.createByName.trim() || undefined,
    updateByName: filters.updateByName.trim() || undefined,
    createTimeStart: filters.createRange[0] || undefined,
    createTimeEnd: filters.createRange[1] || undefined,
    updateTimeStart: filters.updateRange[0] || undefined,
    updateTimeEnd: filters.updateRange[1] || undefined,
    sortBy: sortState.sortBy,
    sortOrder: sortState.sortOrder,
    pageNum,
    pageSize: size
  }
}

/** 表头点击排序：走服务端 sort_by / sort_order，取消排序回到默认 sort_order asc, id asc */
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

/** 省份变更：清空已选城市并按新省份加载城市，再触发查询 */
async function handleProvinceFilter() {
  filters.cityCode = ''
  filterCityOptions.value = []
  if (filters.provinceCode) {
    try {
      filterCityOptions.value = await getRegionChildren(filters.provinceCode)
    } catch (error) {
      const message = (error as { message?: string })?.message
      if (message) ElMessage.error(message)
    }
  }
  onFilterChange()
}

async function loadProvinceOptions() {
  try {
    provinceOptions.value = await getRegionChildren('0')
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  }
}

async function loadPage() {
  loading.value = true
  try {
    const result = await getDeptPage(buildQuery())
    rows.value = result.records
    total.value = result.total
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  } finally {
    loading.value = false
  }
}

async function loadData() {
  loading.value = true
  try {
    treeData.value = await getDeptTree()
    if (!findDept(treeData.value, selectedId.value)) {
      selectedId.value = HQ_ID
    }
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  } finally {
    loading.value = false
  }
  await loadPage()
}

function handleSelect(data: DeptNode) {
  selectedId.value = data.id
  filters.parentId = undefined
  page.value = 1
  loadPage()
}

function handleSizeChange() {
  page.value = 1
  loadPage()
}

function setTreeExpanded(expanded: boolean) {
  const store = treeRef.value?.store
  const nodes = store?._getAllNodes?.() || Object.values(store?.nodesMap || {})
  ;(nodes as Array<{ expanded: boolean }>).forEach((node) => {
    node.expanded = expanded
  })
}

function resetFilters() {
  filters.deptName = ''
  filters.deptCode = ''
  filters.deptType = undefined
  filters.deptNature = undefined
  filters.parentId = undefined
  filters.leader = ''
  filters.phone = ''
  filters.provinceCode = ''
  filters.cityCode = ''
  filters.applyCompanyId = undefined
  filterCityOptions.value = []
  filters.isLeaf = undefined
  filters.status = undefined
  filters.createByName = ''
  filters.updateByName = ''
  filters.createRange = []
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
  deptName: () => (filters.deptName = ''),
  parentName: () => (filters.parentId = undefined),
  deptType: () => (filters.deptType = undefined),
  deptNature: () => (filters.deptNature = undefined),
  deptCode: () => (filters.deptCode = ''),
  leader: () => (filters.leader = ''),
  phone: () => (filters.phone = ''),
  regionName: () => {
    filters.provinceCode = ''
    filters.cityCode = ''
    filterCityOptions.value = []
  },
  isLeaf: () => (filters.isLeaf = undefined),
  applyCompanyNames: () => (filters.applyCompanyId = undefined),
  status: () => (filters.status = undefined),
  createByName: () => (filters.createByName = ''),
  updateByName: () => (filters.updateByName = ''),
  createTime: () => (filters.createRange = []),
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
    // 隐藏了正在排序的列时同步取消排序，避免“看不见排序条件却仍在排序”
    if (sortState.sortBy === key) {
      sortState.sortBy = undefined
      sortState.sortOrder = undefined
      orgTableRef.value?.getTable()?.clearSort?.()
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
  orgTableRef.value?.resetColumnWidths()
  ElMessage.success('已恢复默认列')
}

function summaryMethod({ columns }: { columns: Array<{ label: string }> }) {
  return columns.map((_, index) => (index === 0 ? `合计 ${total.value} 个机构` : ''))
}

/** 导出：跟随当前筛选与可见列，导出全部页 */
async function handleExport() {
  const exportColumns = columnOptions.filter((item) => columnState[item.key])
  try {
    const result = await getDeptPage(buildQuery(1, 10000))
    const header = exportColumns.map((item) => item.label)
    const body = result.records.map((row) =>
      exportColumns.map((item) => {
        if (item.key === 'deptType') return typeTag(row.deptType).text
        if (item.key === 'deptNature') return natureTag(row.deptNature).text
        if (item.key === 'isLeaf') {
          return row.deptType === 5 ? (row.isLeaf ? '是' : '否') : '—'
        }
        if (item.key === 'status') return row.status ? '启用' : '禁用'
        const value = row[item.key as keyof DeptRow]
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
    link.download = `机构管理_${exportStamp()}.csv`
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

function openCreate(parentId = HQ_ID) {
  drawer.mode = 'create'
  drawer.parentId = parentId
  drawer.record = null
  drawer.visible = true
}

function openEdit(row: DeptNode) {
  drawer.mode = 'edit'
  drawer.parentId = row.parentId
  drawer.record = row
  drawer.visible = true
}

function openAuth(row: DeptNode) {
  authDrawer.record = row
  authDrawer.visible = true
}

async function handleDelete(row: DeptNode) {
  const node = findDept(treeData.value, row.id)
  if (node?.children?.length) {
    ElMessage.warning('该机构下存在子机构，请先删除子机构')
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除「${row.deptName}」吗？`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteDept(row.id)
    ElMessage.success('删除成功')
    await loadData()
  } catch (error) {
    const message = (error as { message?: string })?.message
    if (message) ElMessage.error(message)
  }
}

async function handleSaved() {
  await loadData()
}

onMounted(() => {
  loadData()
  loadProvinceOptions()
})
</script>

<style scoped>
.org-layout {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: var(--spacing-base);
}

/* 左侧机构树：固定 208px，树面板独立滚动 */
.org-tree-panel {
  flex: 0 0 208px;
  width: 208px;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
  background-color: var(--color-bg-subtle);
}

.org-tree-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px 8px 12px;
  border-bottom: 1px solid var(--color-border-light);
}

.org-tree-panel__title {
  font-size: var(--font-size-small);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
  white-space: nowrap;
}

.org-tree-panel__tools {
  display: flex;
  align-items: center;
  gap: 2px;
}

.org-tree-panel__tools :deep(.el-button + .el-button) {
  margin-left: 0;
}

.org-tree-panel__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 6px 4px;
}

.org-tree :deep(.el-tree-node__content) {
  height: 32px;
  border-radius: var(--radius-xs);
}

.org-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background-color: var(--color-bg-active);
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  width: 100%;
  padding-right: 6px;
}

.tree-node__text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--font-size-small);
  color: var(--color-text-primary);
}

.tree-node.is-disabled .tree-node__text {
  color: var(--color-text-placeholder);
}

/* 禁用用中性灰标识，不用红绿 */
.tree-node__badge {
  flex-shrink: 0;
  padding: 0 4px;
  font-size: 10px;
  line-height: 16px;
  color: var(--color-text-secondary);
  background-color: var(--color-bg-disabled);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-xs);
}

.org-list-panel {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.org-list-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-base);
  margin-bottom: var(--spacing-sm);
}

.org-list-panel__title {
  display: flex;
  align-items: baseline;
  gap: var(--spacing-sm);
  min-width: 0;
}

.org-list-panel__name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.org-list-panel__count {
  font-size: var(--font-size-extra-small);
  color: var(--color-text-placeholder);
  white-space: nowrap;
}

.org-table-wrap {
  flex: 1;
  min-height: 0;
}

/* 表头内的筛选控件：字段名不换行 */
.col-head {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: stretch;
}

/* 所在地区：省、市两个下拉并排放进 240px 列宽 */
.region-filter {
  display: flex;
  gap: 4px;
}

.region-filter :deep(.el-select) {
  flex: 1;
  min-width: 0;
}

.col-head__label {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: var(--font-weight-medium);
}

/* 日期区间筛选控件压进 200px 列宽（规范：日期区间列最小宽 200px） */
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

.org-table :deep(.el-table__header th .cell) {
  overflow: visible;
  line-height: 1.4;
}

.org-name__text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.org-parent {
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

/* 11 列宽于多数窗口，横向滚动条需清晰可辨（规范 3.13） */
.org-table :deep(.el-scrollbar__bar.is-horizontal) {
  height: 10px;
}

.org-table :deep(.el-scrollbar__bar.is-horizontal .el-scrollbar__thumb) {
  opacity: 0.45;
  background-color: var(--color-text-placeholder);
}

.org-table :deep(.el-scrollbar__bar.is-horizontal .el-scrollbar__thumb:hover) {
  opacity: 0.65;
}
</style>
