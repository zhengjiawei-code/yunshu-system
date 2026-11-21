/**
 * 列宽计算工具（移植自 luohuo，spec 3.2.2）
 * - 中文 ≈14px、ASCII ≈8px
 * - 自适应宽 = max(表头宽, 当前页最宽内容 + 内边距)，不低于列自身 minWidth
 * - 超过上限时改为固定宽度，由表格级 show-overflow-tooltip 兜底
 */

/**
 * 列宽上限（yunshu 标准：统一 320px，不按列类型区分）
 * 超过上限固定为该宽度，内容走省略号 + tooltip，避免超长列吃掉整行。
 */
export const DEFAULT_MAX_COL_WIDTH = 320

const FALLBACK_CELL_PAD = 24

function estimateTextWidth(str: string): number {
  let width = 0
  for (const ch of str) {
    width += /[\u4e00-\u9fa5]/.test(ch) ? 14 : 8
  }
  return width
}

let textCtx: CanvasRenderingContext2D | null | undefined

function getTextCtx() {
  if (textCtx !== undefined) return textCtx
  if (typeof document === 'undefined') {
    textCtx = null
    return textCtx
  }
  textCtx = document.createElement('canvas').getContext('2d')
  return textCtx
}

/** 精确测量文本宽度；不支持 canvas 时回落到字符估算 */
export function measureTextWidth(text: string | number | null | undefined, font = '12px sans-serif'): number {
  const str = text == null ? '' : String(text)
  const ctx = getTextCtx()
  if (!ctx) return estimateTextWidth(str)
  ctx.font = font
  return ctx.measureText(str).width
}

export interface AutoWidthOptions {
  label: string
  font?: string
  headerFont?: string
  maxTextWidth?: number
  headerContentWidth?: number
  pad?: number
  headerPad?: number
  floor?: number
  max?: number
}

export function calcAutoColWidth(options: AutoWidthOptions) {
  const {
    label,
    font = '12px sans-serif',
    headerFont,
    maxTextWidth = 0,
    headerContentWidth = 0,
    pad = FALLBACK_CELL_PAD,
    headerPad = FALLBACK_CELL_PAD,
    floor = 0,
    max = DEFAULT_MAX_COL_WIDTH
  } = options

  const headerTextWidth = Math.max(measureTextWidth(label, headerFont || font), headerContentWidth)
  const target = Math.ceil(Math.max(headerTextWidth + headerPad, maxTextWidth + pad))
  const lower = Math.max(Number(floor) || 0, 0)
  const cap = Math.max(Number(max) || DEFAULT_MAX_COL_WIDTH, lower)

  if (target > cap) return { width: cap, minWidth: undefined, truncated: true }
  return { width: undefined, minWidth: Math.max(target, lower), truncated: false }
}
