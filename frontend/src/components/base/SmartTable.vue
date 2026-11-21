<template>
  <el-table ref="tableRef" :key="columnSignature" v-bind="passAttrs" @header-dragend="onHeaderDragend">
    <ColumnNodes />
  </el-table>
</template>

<script setup lang="ts">
import {
  Fragment,
  cloneVNode,
  computed,
  nextTick,
  onActivated,
  onBeforeUnmount,
  onMounted,
  ref,
  useAttrs,
  useSlots,
  watch,
  type VNode
} from 'vue'
import { calcAutoColWidth, DEFAULT_MAX_COL_WIDTH, measureTextWidth } from '@/utils/colWidth'

/**
 * 表格基座（yunshu 标准列表配置）
 * 1. 列宽可拖拽：必须开启 border，Element Plus 才会启动列宽拖拽（源码 handleMouseMove 首行判断 props.border）
 * 2. 拖拽下限 80px；上限统一 DEFAULT_MAX_COL_WIDTH（320px），拖拽与自适应都受上限约束
 * 3. 未手动调整过的列自适应：max(表头宽, 当前页最宽内容 + 内边距)，不低于列 minWidth
 * 4. 用户拖动过的列以用户宽度为准，持久化到 localStorage（persistKey），自适应不再覆盖
 * 5. 重算时机：数据变化、列集合变化、容器尺寸变化、窗口缩放、字体加载完成
 * 6. 每列默认可排序：带 prop 的数据列自动获得 sortable="custom"（服务端排序），
 *    操作列没有 prop 因此天然排除；页面可用 :sortable="false" 显式关闭
 */
defineOptions({ name: 'SmartTable', inheritAttrs: false })

const slots = useSlots()

/**
 * 列节点：给带 prop 的 el-table-column 补默认 sortable="custom"。
 * 采用 cloneVNode 而不是让页面逐列声明，保证两页行为一致、后续新页面自动继承。
 * 同时按统一列设置（columnOrder / columnHidden）调整渲染顺序与显隐。
 */
const ColumnNodes = () => {
  const out: VNode[] = []
  const walk = (nodes: VNode[]) => {
    nodes.forEach((node) => {
      if (!node) return
      if (node.type === Fragment) {
        walk((node.children as VNode[]) || [])
        return
      }
      const name = (node.type as { name?: string } | null)?.name
      const columnProps = (node.props || {}) as Record<string, unknown>
      if (name === 'ElTableColumn' && columnProps.prop) {
        if (columnProps.sortable === undefined) {
          const key = String(columnProps.prop)
          const allowed = props.columnSortable?.[key] !== false
          out.push(cloneVNode(node, { ...columnProps, sortable: allowed ? 'custom' : false }))
          return
        }
        out.push(node)
        return
      }
      out.push(node)
    })
  }
  walk(slots.default?.() || [])
  return applyColumnConfig(out)
}

/** 列的匹配键：prop 优先，无 prop 时用 label（与 useColumnConfig.renderKeyOf 一致） */
function columnNodeKey(node: VNode): string {
  const columnProps = (node.props || {}) as Record<string, unknown>
  return columnProps.prop ? String(columnProps.prop) : `label:${String(columnProps.label || '')}`
}

/** 统一列设置：序号/选择/展开等固定列保持原位，数据列按配置排序与隐藏 */
function applyColumnConfig(nodes: VNode[]): VNode[] {
  const order = props.columnOrder || []
  const hidden = new Set(props.columnHidden || [])
  if (!order.length && !hidden.size) return nodes

  const fixed: VNode[] = []
  const data: VNode[] = []
  nodes.forEach((node) => {
    const name = (node.type as { name?: string } | null)?.name
    const columnProps = (node.props || {}) as Record<string, unknown>
    if (name !== 'ElTableColumn' || columnProps.type) {
      fixed.push(node)
      return
    }
    data.push(node)
  })

  const visibleData = hidden.size
    ? data.filter((node) => !hidden.has(columnNodeKey(node)))
    : data
  if (!order.length) return [...fixed, ...visibleData]

  const orderIndex = new Map(order.map((key, index) => [key, index]))
  const sorted = visibleData
    .map((node, index) => ({
      node,
      index,
      order: orderIndex.has(columnNodeKey(node))
        ? (orderIndex.get(columnNodeKey(node)) as number)
        : Number.MAX_SAFE_INTEGER
    }))
    .sort((a, b) => (a.order === b.order ? a.index - b.index : a.order - b.order))
    .map((item) => item.node)
  return [...fixed, ...sorted]
}

const props = withDefaults(
  defineProps<{
    autoWidth?: boolean
    autoWidthMax?: number
    autoWidthExclude?: string[]
    /** 列宽持久化标识，如 system:org；不传则不持久化 */
    persistKey?: string
    /** 可见列顺序（统一列设置），元素为 prop 或 label:列名 */
    columnOrder?: string[]
    /** 隐藏列（统一列设置） */
    columnHidden?: string[]
    /** 每列是否可排序（统一列设置）；键为 prop，未声明的列默认可排序 */
    columnSortable?: Record<string, boolean>
  }>(),
  {
    autoWidth: true,
    autoWidthMax: DEFAULT_MAX_COL_WIDTH,
    autoWidthExclude: () => [],
    persistKey: '',
    columnOrder: () => [],
    columnHidden: () => [],
    columnSortable: () => ({})
  }
)

/**
 * el-table 的列顺序由列注册顺序决定，仅调整 vnode 顺序不会重排；
 * 顺序变化时整体重建 el-table，确保列按新顺序注册（列宽会按持久化值重新应用）。
 */
const columnSignature = computed(
  () => `${(props.columnOrder || []).join('|')}#${JSON.stringify(props.columnSortable || {})}`
)

const MIN_COLUMN_WIDTH = 80

const attrs = useAttrs()
// el-table 是泛型函数式组件，拿不到稳定的实例构造类型，这里按内部 store 的结构使用
const tableRef = ref<any>()

const passAttrs = computed(() => {
  const {
    autoWidth: _aw,
    'auto-width': _aw2,
    autoWidthMax: _awm,
    'auto-width-max': _awm2,
    autoWidthExclude: _awe,
    'auto-width-exclude': _awe2,
    persistKey: _pk,
    'persist-key': _pk2,
    ...rest
  } = attrs
  return {
    // 不开 border 时 Element Plus 不会启动列宽拖拽
    border: rest.border ?? true,
    allowDragLastColumn: rest.allowDragLastColumn ?? rest['allow-drag-last-column'] ?? true,
    ...rest
  }
})

/** 列声明原值：首次测量时记录，用户拖拽下限以此为参考 */
const decl = new Map<string, { width?: number | string; minWidth?: number | string }>()
/** 用户手动调整过的列宽 */
const userWidths = ref<Record<string, number>>(loadWidths())
let frame = 0
let applying = false

function widthStorageKey() {
  return props.persistKey ? `yunshu:table-widths:${props.persistKey}` : ''
}

function loadWidths(): Record<string, number> {
  if (!props.persistKey) return {}
  try {
    const raw = localStorage.getItem(widthStorageKey())
    return raw ? (JSON.parse(raw) as Record<string, number>) : {}
  } catch {
    return {}
  }
}

function persistWidths() {
  if (!props.persistKey) return
  try {
    localStorage.setItem(widthStorageKey(), JSON.stringify(userWidths.value))
  } catch {
    // 忽略存储失败
  }
}

function leafColumns(): any[] {
  return tableRef.value?.store?.states?.columns?.value || []
}

function columnKey(col: any): string {
  return col?.property || `label:${col?.label || ''}`
}

function sideChrome(el: HTMLElement) {
  const cs = getComputedStyle(el)
  return (
    (parseFloat(cs.paddingLeft) || 0) +
    (parseFloat(cs.paddingRight) || 0) +
    (parseFloat(cs.borderLeftWidth) || 0) +
    (parseFloat(cs.borderRightWidth) || 0)
  )
}

function cellContentWidth(el: HTMLElement, font: string, chrome: number) {
  const sw = el.scrollWidth || 0
  if (sw > (el.clientWidth || 0) + 1) return Math.max(sw - chrome, 0)
  return measureTextWidth(el.textContent || '', font)
}

function applyAutoWidth() {
  if (!props.autoWidth || applying) return
  const root = tableRef.value?.$el as HTMLElement | undefined
  const cols = leafColumns()
  if (!root || !cols.length) return

  const bodyRows = root.querySelectorAll('.el-table__body-wrapper tbody > tr')
  const headerRow = root.querySelector('.el-table__header-wrapper thead tr')
  const sampleIndex = cols.findIndex(
    (col) => col?.property && col.type !== 'selection' && col.type !== 'index' && col.type !== 'expand'
  )
  const sampleCell =
    bodyRows[0]?.children[sampleIndex]?.querySelector('.cell') ||
    headerRow?.children[sampleIndex]?.querySelector('.cell')
  if (!sampleCell) return

  const cellFont = getComputedStyle(sampleCell).font
  const cellChrome = sideChrome(sampleCell as HTMLElement)
  const tdChrome = (sampleCell as HTMLElement).parentElement
    ? sideChrome((sampleCell as HTMLElement).parentElement as HTMLElement)
    : 0
  const pad = cellChrome + tdChrome
  const headerPad = pad

  const exclude = new Set(props.autoWidthExclude)
  const targets: Array<{ col: any; key: string; width?: number; minWidth?: number }> = []

  cols.forEach((col, index) => {
    if (!col) return
    if (col.type === 'selection' || col.type === 'index' || col.type === 'expand') return
    if (col.property && exclude.has(col.property)) return

    const key = columnKey(col)
    if (!decl.has(key)) {
      decl.set(key, { width: col.width, minWidth: col.minWidth })
    }
    const origin = decl.get(key)!

    // 用户拖过的列：以用户宽度为准，自适应不再覆盖；上限与 80px 下限兜底仍生效
    const userWidth = userWidths.value[key]
    if (userWidth != null) {
      const max = Math.max(props.autoWidthMax, MIN_COLUMN_WIDTH)
      const width = Math.min(Math.max(userWidth, MIN_COLUMN_WIDTH), max)
      targets.push({ col, key, width, minWidth: undefined })
      return
    }

    // 页面显式声明 width 的列（排序、状态、操作等）不参与自适应
    if (origin.width !== undefined && origin.width !== null && origin.width !== '') return

    let maxText = 0
    bodyRows.forEach((tr) => {
      const cell = (tr as HTMLElement).children[index]?.querySelector('.cell') as HTMLElement | null
      if (!cell) return
      maxText = Math.max(maxText, cellContentWidth(cell, cellFont, cellChrome))
    })

    targets.push({
      col,
      key,
      ...calcAutoColWidth({
        label: col.label || '',
        font: cellFont,
        maxTextWidth: maxText,
        // 表头含筛选控件的列不能用 DOM 宽度（输入框 100% 宽会形成回环），统一按字段名测量
        headerContentWidth: 0,
        pad,
        headerPad,
        floor: Number(origin.minWidth) || 0,
        max: props.autoWidthMax
      })
    })
  })

  applying = true
  let changed = false
  try {
    targets.forEach(({ col, key, width, minWidth }) => {
      if (width != null) {
        if (col.width !== width) {
          col.width = width
          changed = true
        }
        // 用户宽度优先：清掉自适应阶段可能抬高的最小宽，避免拖窄后被 min-width 顶回去
        if (minWidth === undefined && col.minWidth !== undefined) {
          col.minWidth = undefined
          changed = true
        }
        return
      }
      const origin = decl.get(key)!
      // 未被用户调整的列交回自适应：清理固定宽度，恢复最小宽
      if (col.width !== undefined && col.width !== null && col.width !== '') {
        col.width = undefined
        changed = true
      }
      if (minWidth != null && Number(col.minWidth) !== minWidth) {
        col.minWidth = minWidth
        changed = true
      }
      if (origin.minWidth !== undefined && Number(col.minWidth) < Number(origin.minWidth)) {
        col.minWidth = origin.minWidth
        changed = true
      }
    })
  } finally {
    applying = false
  }

  if (changed) tableRef.value?.doLayout?.()
}

/** 拖拽结束：用户宽度为准，夹在 [80, 上限]，持久化，并让该列脱离自适应 */
function onHeaderDragend(newWidth: number, _oldWidth: number, column: any) {
  if (!column) return
  const key = columnKey(column)
  const max = Math.max(props.autoWidthMax, MIN_COLUMN_WIDTH)
  // 拖拽下限固定为规范 80px，不因原 minWidth 更大而回弹
  const width = Math.min(Math.max(Math.round(newWidth), MIN_COLUMN_WIDTH), max)

  column.width = width
  column.realWidth = width
  column.minWidth = undefined
  userWidths.value[key] = width
  persistWidths()
  nextTick(() => tableRef.value?.doLayout?.())
}

/** 恢复默认列宽：清空用户宽度，所有列重新参与自适应（列设置恢复默认时调用） */
function resetColumnWidths() {
  userWidths.value = {}
  persistWidths()
  nextTick(() => {
    applyAutoWidth()
    tableRef.value?.doLayout?.()
  })
}

function schedule() {
  cancelAnimationFrame(frame)
  frame = requestAnimationFrame(() => {
    frame = 0
    applyAutoWidth()
  })
}

let observer: ResizeObserver | null = null

function onWindowResize() {
  schedule()
}

onMounted(() => {
  userWidths.value = loadWidths()
  nextTick(schedule)
  const root = tableRef.value?.$el as HTMLElement | undefined
  if (root && typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(() => schedule())
    observer.observe(root)
  }
  window.addEventListener('resize', onWindowResize)
  // 字体加载完成后中英文宽度会变化，需重新测量
  document.fonts?.ready?.then(() => schedule())
})

onActivated(() => nextTick(schedule))

onBeforeUnmount(() => {
  cancelAnimationFrame(frame)
  observer?.disconnect()
  observer = null
  window.removeEventListener('resize', onWindowResize)
})

watch(
  () => attrs.data,
  () => nextTick(schedule)
)

watch(
  () => leafColumns().length,
  () => nextTick(schedule)
)

defineExpose({
  getTable: () => tableRef.value,
  resetColumnWidths
})
</script>
