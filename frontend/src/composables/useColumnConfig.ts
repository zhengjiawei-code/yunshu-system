import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getColumnConfig,
  resetColumnConfig,
  saveColumnConfig
} from '@/api/system/columnConfig'
import {
  isTextMatchMode,
  type TextMatchMode,
  DEFAULT_TEXT_MATCH_MODE
} from '@/utils/textFilter'

/** 筛选控件类型：全平台统一枚举，新增类型必须同步 ColumnFilter 与规范文档 */
export type FilterType =
  | 'none'
  | 'text'
  | 'select'
  | 'multiselect'
  | 'date'
  | 'daterange'
  | 'numberrange'

/** 文本列查询模式：单模式固定模糊；多模式可在表头选择六种匹配方式 */
export type FilterMode = 'single' | 'multi'

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
  /** 文本列默认查询模式，默认 single */
  filterMode?: FilterMode | null
  /** 下拉列默认是否多选，默认 false */
  selectMultiple?: boolean | null
  /** 下拉类筛选项 */
  filterOptions?: ColumnOption[]
  /** 默认查询值：首次进入页面自动带出 */
  defaultFilter?: unknown
  /** 不允许隐藏（如状态、操作等关键列） */
  locked?: boolean
  /** 默认隐藏（列定义保留，列设置可勾回） */
  defaultHidden?: boolean
  /** 不展示“启用筛选”开关（多值标签、系统排序等本就不提供筛选的列） */
  filterHidden?: boolean
  /** 不参与列设置（如序号列） */
  ignored?: boolean
}

/** 用户可保存的列状态 */
export interface ColumnItemState {
  key: string
  visible: boolean
  sortable: boolean
  filterType: FilterType
  filterMode?: FilterMode | null
  selectMultiple?: boolean | null
  defaultFilter?: unknown
}

export interface ColumnConfigPayload {
  version: number
  columns: ColumnItemState[]
}

/** 文本列运行时匹配方式：键为列 key，值为本次查询选择的模式 */
export type RuntimeTextModes = Partial<Record<string, TextMatchMode>>

/**
 * 配置版本：
 * 1 允许用户改筛选类型；
 * 2 控件类型只跟随 schema，用户只能关闭筛选；
 * 3 一次性迁移：历史配置的「是否启用筛选」重置为默认全开（保留顺序/显隐/匹配模式/默认值），
 *   同时为 defaultHidden 的新列落一次默认隐藏。
 */
const CONFIG_VERSION = 3
const DATE_TYPES: FilterType[] = ['date', 'daterange']

function isDateType(type: FilterType): boolean {
  return DATE_TYPES.includes(type)
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function schemaTypeOf(def: ColumnDef): FilterType {
  return def.filterType || 'text'
}

/** 默认查询值里的匹配方式；多模式默认值结构为 { mode, value } */
function defaultMatchModeOf(value: unknown): TextMatchMode {
  if (isPlainObject(value) && isTextMatchMode(value.mode)) {
    return value.mode
  }
  return DEFAULT_TEXT_MATCH_MODE
}

function modeValueOf(value: unknown): unknown {
  if (isPlainObject(value) && 'value' in value) {
    return value.value
  }
  return value
}

/**
 * v1 → v2 内存迁移 + 非法配置归一。
 *
 * v1 允许用户改筛选类型；v2 起控件类型只跟随页面 schema，用户只能关闭筛选。
 * 日期类型不提供任何筛选配置项，历史默认值在这里清空。
 */
function normalizeState(
  def: ColumnDef,
  saved?: Partial<ColumnItemState> | null,
  migrate = false
): ColumnItemState {
  const targetType = schemaTypeOf(def)
  const savedType = saved?.filterType
  let filterType: FilterType = targetType
  if (migrate) {
    // 历史配置迁移：所有可筛列统一回到默认全开
    filterType = targetType
  } else if (savedType === 'none') {
    filterType = 'none'
  } else if (savedType === 'multiselect' && targetType === 'select') {
    filterType = 'select'
  }

  let filterMode: FilterMode | null = null
  if (filterType === 'text') {
    if (saved?.filterMode === 'multi' || saved?.filterMode === 'single') {
      filterMode = saved.filterMode
    } else {
      filterMode = def.filterMode === 'multi' ? 'multi' : 'single'
    }
  }

  let selectMultiple: boolean | null = null
  if (filterType === 'select') {
    if (typeof saved?.selectMultiple === 'boolean') {
      selectMultiple = saved.selectMultiple
    } else if (savedType === 'multiselect') {
      selectMultiple = true
    } else if (def.selectMultiple === true) {
      selectMultiple = true
    } else {
      selectMultiple = Array.isArray(saved?.defaultFilter)
    }
  }

  let defaultFilter = saved?.defaultFilter
  if (filterType === 'none' || isDateType(filterType)) {
    defaultFilter = null
  } else if (filterType === 'text') {
    if (filterMode === 'multi') {
      if (isPlainObject(defaultFilter)) {
        defaultFilter = {
          mode: defaultMatchModeOf(defaultFilter),
          value: typeof defaultFilter.value === 'string' ? defaultFilter.value : ''
        }
      } else if (typeof defaultFilter === 'string' && defaultFilter !== '') {
        defaultFilter = { mode: DEFAULT_TEXT_MATCH_MODE, value: defaultFilter }
      } else if (defaultFilter === undefined || defaultFilter === null) {
        defaultFilter = null
      } else {
        defaultFilter = { mode: DEFAULT_TEXT_MATCH_MODE, value: String(defaultFilter) }
      }
    } else if (isPlainObject(defaultFilter)) {
      defaultFilter = defaultFilter.value ?? null
    }
  } else if (filterType === 'select') {
    if (selectMultiple) {
      if (Array.isArray(defaultFilter)) {
        // 保持数组
      } else if (defaultFilter === undefined || defaultFilter === null || defaultFilter === '') {
        defaultFilter = null
      } else {
        defaultFilter = [defaultFilter]
      }
    } else if (Array.isArray(defaultFilter)) {
      defaultFilter = defaultFilter[0] ?? null
    }
  }

  return {
    key: def.key,
    visible: migrate && def.defaultHidden ? false : saved?.visible !== false,
    sortable: saved?.sortable !== false,
    filterType,
    filterMode,
    selectMultiple,
    defaultFilter
  }
}

function defaultState(def: ColumnDef): ColumnItemState {
  return normalizeState(def, null, true)
}

/**
 * 统一列设置：所有列表页共用同一协议。
 * 后端按「用户 + 页面标识」持久化，本地只保留列宽（SmartTable persistKey）。
 */
export function useColumnConfig(
  pageKey: string,
  schema: ColumnDef[],
  options: { leadingKeys?: string[] } = {}
) {
  const configurable = computed(() => schema.filter((item) => !item.ignored))
  /** 页面指定的前置列：存量配置与默认配置都需保证这些列排在最前，顺序以数组为准 */
  const leadingKeys = options.leadingKeys ?? []
  const states = ref<ColumnItemState[]>(configurable.value.map(defaultState))
  const loading = ref(false)
  const saving = ref(false)
  const customized = ref(false)

  function mergeSaved(saved?: ColumnConfigPayload | null): ColumnItemState[] {
    const defs = new Map(configurable.value.map((def) => [def.key, def]))
    const result: ColumnItemState[] = []
    const seen = new Set<string>()
    // 旧版本配置一次性迁移：筛选重置全开、defaultHidden 列落为隐藏
    const migrate = !saved || (saved.version ?? 0) < CONFIG_VERSION
    // 先按用户保存的顺序还原；页面新增的列再追加到末尾，保证顺序配置真实生效
    for (const item of saved?.columns || []) {
      const def = defs.get(item.key)
      if (!def || seen.has(item.key)) continue
      seen.add(item.key)
      result.push(normalizeState(def, item, migrate))
    }
    for (const def of configurable.value) {
      if (!seen.has(def.key)) result.push(defaultState(def))
    }
    return applyLeadingOrder(result)
  }

  /**
   * 强制前置列排在首位：存量配置保存的是旧顺序，仅改 schema 默认顺序不会生效；
   * 这里按页面声明的 leadingKeys 调整内存顺序，未声明的页面原样返回。
   */
  function applyLeadingOrder(list: ColumnItemState[]): ColumnItemState[] {
    if (!leadingKeys.length) return list
    const front = leadingKeys
      .map((key) => list.find((item) => item.key === key))
      .filter((item): item is ColumnItemState => !!item)
    if (!front.length) return list
    const prefix = list.slice(0, front.length).map((item) => item.key)
    if (prefix.join('|') === front.map((item) => item.key).join('|')) return list
    const frontKeys = new Set(front.map((item) => item.key))
    return [...front, ...list.filter((item) => !frontKeys.has(item.key))]
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
    const filterType = state?.filterType ?? schemaTypeOf(def)
    return {
      ...def,
      sortable: state?.sortable ?? def.sortable !== false,
      filterType,
      filterMode: filterType === 'text' ? state?.filterMode ?? def.filterMode ?? 'single' : null,
      selectMultiple: filterType === 'select' ? state?.selectMultiple ?? def.selectMultiple ?? false : null,
      defaultFilter: state?.defaultFilter
    }
  }

  function isVisible(key: string): boolean {
    return stateOf(key)?.visible !== false
  }

  /** 文本列当前查询模式；非文本列返回 null */
  function filterModeOf(key: string): FilterMode | null {
    const state = stateOf(key)
    if (!state || state.filterType !== 'text') return null
    return state.filterMode === 'multi' ? 'multi' : 'single'
  }

  /**
   * 生成列表查询的 filterModes 参数（对象）。
   *
   * @param keyFieldMap 列 key → 后端字段名；未配置的列不参与
   * @param runtimeModes 本次查询在表头选择的匹配方式（列 key → 模式），缺省取默认值的 mode
   *
   * 第一段页面尚未接入，返回对象由第二段 buildQuery 决定是否 JSON.stringify。
   */
  function filterModesParam(
    keyFieldMap: Record<string, string>,
    runtimeModes: RuntimeTextModes = {}
  ): Record<string, TextMatchMode> {
    const result: Record<string, TextMatchMode> = {}
    states.value.forEach((item) => {
      if (!item.visible || item.filterType !== 'text' || item.filterMode !== 'multi') return
      const field = keyFieldMap[item.key]
      if (!field) return
      const mode = runtimeModes[item.key] ?? defaultMatchModeOf(item.defaultFilter)
      if (mode !== DEFAULT_TEXT_MATCH_MODE) {
        result[field] = mode
      }
    })
    return result
  }

  /** 可见列顺序（传给 SmartTable 做真实渲染顺序调整） */
  const columnOrder = computed(() =>
    states.value.filter((item) => item.visible).map((item) => renderKeyOf(item.key))
  )

  /** 被隐藏的列 key（传给 SmartTable 过滤） */
  const hiddenKeys = computed(() =>
    states.value.filter((item) => !item.visible).map((item) => renderKeyOf(item.key))
  )

  /** 每列是否可排序（键为渲染键），供 SmartTable 真实应用 */
  const sortableMap = computed(() => {
    const result: Record<string, boolean> = {}
    states.value.forEach((item) => {
      result[renderKeyOf(item.key)] = item.sortable
    })
    return result
  })

  /** 列在 el-table-column 上的匹配键：prop 优先，其次显式 elKey，最后 label */
  function renderKeyOf(key: string): string {
    const def = schema.find((item) => item.key === key)
    if (!def) return key
    return def.prop || def.elKey || key
  }

  /**
   * 默认查询值（完整结构）：文本多模式返回 { mode, value }，下拉多选返回数组。
   * 供已完成筛选接入的页面使用；未接入的页面继续用 defaultFilters() 的兼容展开。
   */
  function defaultFiltersRaw(): Record<string, unknown> {
    const result: Record<string, unknown> = {}
    states.value
      .filter((item) => item.visible)
      .forEach((item) => {
        const value = item.defaultFilter
        if (value !== undefined && value !== null && value !== '') {
          result[item.key] = value
        }
      })
    return result
  }

  /**
   * 默认查询值：仅取可见且有值的列。
   *
   * 第一段页面仍按单值/单选的旧协议接收，因此这里做兼容展开：
   * 文本多模式只返回 value，下拉多选只返回第一个值。
   * 第二段页面接入后改用 filterModesParam + 完整默认值结构。
   */
  function defaultFilters(): Record<string, unknown> {
    const result: Record<string, unknown> = {}
    states.value
      .filter((item) => item.visible)
      .forEach((item) => {
        let value = item.defaultFilter
        if (item.filterType === 'text' && item.filterMode === 'multi') {
          value = modeValueOf(item.defaultFilter)
        } else if (item.filterType === 'select' && item.selectMultiple && Array.isArray(value)) {
          value = value[0]
        }
        if (value !== undefined && value !== null && value !== '') {
          result[item.key] = value
        }
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
    filterModeOf,
    filterModesParam,
    columnOrder,
    hiddenKeys,
    sortableMap,
    defaultFilters,
    defaultFiltersRaw
  }
}
