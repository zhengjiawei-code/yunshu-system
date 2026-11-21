import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getColumnConfig,
  resetColumnConfig,
  saveColumnConfig
} from '@/api/system/columnConfig'

/** 筛选控件类型：全平台统一枚举，新增类型必须同步 ColumnFilter 与规范文档 */
export type FilterType =
  | 'none'
  | 'text'
  | 'select'
  | 'multiselect'
  | 'date'
  | 'daterange'
  | 'numberrange'

export interface ColumnOption {
  label: string
  value: string | number | boolean
}

/** 页面列声明：只描述默认值与能力，实际顺序/显隐/筛选类型由用户配置覆盖 */
export interface ColumnDef {
  key: string
  label: string
  prop?: string
  /** SmartTable 排序/隐藏匹配键；无 prop 的列填 label:列名，默认取 prop 或 key */
  elKey?: string
  width?: number | string
  minWidth?: number | string
  align?: 'left' | 'center' | 'right'
  /** 是否可排序，默认 true（操作列等显式 false） */
  sortable?: boolean
  /** 默认筛选控件类型，默认 text */
  filterType?: FilterType
  /** 下拉类筛选项 */
  filterOptions?: ColumnOption[]
  /** 默认查询值：首次进入页面自动带出 */
  defaultFilter?: unknown
  /** 不允许隐藏（如状态、操作等关键列） */
  locked?: boolean
  /** 不参与列设置（如序号列） */
  ignored?: boolean
}

/** 用户可保存的列状态 */
export interface ColumnItemState {
  key: string
  visible: boolean
  sortable: boolean
  filterType: FilterType
  defaultFilter?: unknown
}

export interface ColumnConfigPayload {
  version: number
  columns: ColumnItemState[]
}

const CONFIG_VERSION = 1

function defaultState(def: ColumnDef): ColumnItemState {
  return {
    key: def.key,
    visible: true,
    sortable: def.sortable !== false,
    filterType: def.filterType || 'text',
    defaultFilter: def.defaultFilter
  }
}

/**
 * 统一列设置：所有列表页共用同一协议。
 * 后端按「用户 + 页面标识」持久化，本地只保留列宽（SmartTable persistKey）。
 */
export function useColumnConfig(pageKey: string, schema: ColumnDef[]) {
  const configurable = computed(() => schema.filter((item) => !item.ignored))
  const states = ref<ColumnItemState[]>(configurable.value.map(defaultState))
  const loading = ref(false)
  const saving = ref(false)
  const customized = ref(false)

  function mergeSaved(saved?: ColumnConfigPayload | null): ColumnItemState[] {
    const defs = new Map(configurable.value.map((def) => [def.key, def]))
    const result: ColumnItemState[] = []
    const seen = new Set<string>()
    // 先按用户保存的顺序还原；页面新增的列再追加到末尾，保证顺序配置真实生效
    for (const item of saved?.columns || []) {
      const def = defs.get(item.key)
      if (!def || seen.has(item.key)) continue
      seen.add(item.key)
      const fallback = defaultState(def)
      result.push({
        key: item.key,
        visible: item.visible !== false,
        sortable: item.sortable !== false,
        filterType: item.filterType || fallback.filterType,
        defaultFilter: item.defaultFilter
      })
    }
    for (const def of configurable.value) {
      if (!seen.has(def.key)) result.push(defaultState(def))
    }
    return result
  }

  async function load() {
    loading.value = true
    try {
      const result = await getColumnConfig(pageKey)
      customized.value = !!result.customized
      if (result.config) {
        try {
          states.value = mergeSaved(JSON.parse(result.config) as ColumnConfigPayload)
        } catch {
          // 配置损坏时回退默认，不阻断页面
          states.value = mergeSaved(null)
        }
      } else {
        states.value = mergeSaved(null)
      }
    } catch {
      // 请求层已提示，回落默认配置
      states.value = mergeSaved(null)
    } finally {
      loading.value = false
    }
  }

  async function save() {
    saving.value = true
    try {
      const payload: ColumnConfigPayload = { version: CONFIG_VERSION, columns: states.value }
      const result = await saveColumnConfig(pageKey, JSON.stringify(payload))
      customized.value = !!result.customized
      ElMessage.success('列设置已保存')
    } catch {
      // 请求层已提示
    } finally {
      saving.value = false
    }
  }

  async function reset() {
    saving.value = true
    try {
      await resetColumnConfig(pageKey)
      states.value = mergeSaved(null)
      customized.value = false
      ElMessage.success('已恢复默认列设置')
    } catch {
      // 请求层已提示
    } finally {
      saving.value = false
    }
  }

  function stateOf(key: string): ColumnItemState | undefined {
    return states.value.find((item) => item.key === key)
  }

  /** 列声明 + 用户状态，供模板取排序/筛选类型/宽度 */
  function col(key: string): ColumnDef {
    const def = schema.find((item) => item.key === key)
    const state = stateOf(key)
    if (!def) {
      // 配置里出现未知列时给出兜底，避免模板报错
      return { key, label: key, filterType: 'none' }
    }
    return {
      ...def,
      sortable: state?.sortable ?? def.sortable !== false,
      filterType: state?.filterType ?? def.filterType ?? 'text',
      defaultFilter: state?.defaultFilter
    }
  }

  function isVisible(key: string): boolean {
    return stateOf(key)?.visible !== false
  }

  /** 可见列顺序（传给 SmartTable 做真实渲染顺序调整） */
  const columnOrder = computed(() =>
    states.value.filter((item) => item.visible).map((item) => renderKeyOf(item.key))
  )

  /** 被隐藏的列 key（传给 SmartTable 过滤） */
  const hiddenKeys = computed(() =>
    states.value.filter((item) => !item.visible).map((item) => renderKeyOf(item.key))
  )

  /** 列在 el-table-column 上的匹配键：prop 优先，其次显式 elKey，最后 label */
  function renderKeyOf(key: string): string {
    const def = schema.find((item) => item.key === key)
    if (!def) return key
    return def.prop || def.elKey || key
  }

  /** 默认查询值：仅取可见且有值的列 */
  function defaultFilters(): Record<string, unknown> {
    const result: Record<string, unknown> = {}
    states.value
      .filter((item) => item.visible && item.defaultFilter !== undefined && item.defaultFilter !== null && item.defaultFilter !== '')
      .forEach((item) => {
        result[item.key] = item.defaultFilter
      })
    return result
  }

  return {
    pageKey,
    schema,
    states,
    loading,
    saving,
    customized,
    load,
    save,
    reset,
    col,
    isVisible,
    columnOrder,
    hiddenKeys,
    defaultFilters
  }
}
