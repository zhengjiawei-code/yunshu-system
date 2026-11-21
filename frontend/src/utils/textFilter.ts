/**
 * 文本列匹配方式：与后端 TextFilterMode 保持同一口径。
 * 关键字为空不产生条件；NE 按 SQL 口径，null / 空值不命中；批量去重后上限 500。
 */
export const TEXT_MATCH_MODE_VALUES = ['exact', 'ne', 'like', 'left', 'right', 'batch'] as const

export type TextMatchMode = (typeof TEXT_MATCH_MODE_VALUES)[number]

export interface TextMatchModeOption {
  value: TextMatchMode
  label: string
  sample: string
}

export const TEXT_MATCH_MODE_OPTIONS: TextMatchModeOption[] = [
  { value: 'exact', label: '等于', sample: 'A001' },
  { value: 'ne', label: '不等于', sample: 'A001' },
  { value: 'like', label: '模糊', sample: '***A001***' },
  { value: 'left', label: '左匹配', sample: 'A001***' },
  { value: 'right', label: '右匹配', sample: '***A001' },
  { value: 'batch', label: '批量', sample: 'A001,A002' }
]

export const DEFAULT_TEXT_MATCH_MODE: TextMatchMode = 'like'

export const BATCH_LIMIT = 500

const BATCH_SPLIT_REGEX = /[,，;；、\s]+/

export function isTextMatchMode(value: unknown): value is TextMatchMode {
  return typeof value === 'string' && (TEXT_MATCH_MODE_VALUES as readonly string[]).includes(value)
}

/** 批量值拆分：支持中英文逗号、分号、顿号、空白与换行，忽略大小写去重 */
export function splitBatchValues(keyword: string): string[] {
  const result: string[] = []
  const seen = new Set<string>()
  for (const part of keyword.split(BATCH_SPLIT_REGEX)) {
    const value = part.trim()
    if (!value) continue
    const key = value.toLowerCase()
    if (seen.has(key)) continue
    seen.add(key)
    result.push(value)
  }
  return result
}

/**
 * 判断原始值是否命中关键字。
 *
 * 批量超过 {@link BATCH_LIMIT} 直接抛错，由调用方转成明确提示，不静默截断。
 */
export function matchByMode(
  raw: unknown,
  keyword: string | null | undefined,
  mode: TextMatchMode = DEFAULT_TEXT_MATCH_MODE
): boolean {
  if (keyword === null || keyword === undefined || keyword.trim() === '') {
    return true
  }
  const expected = keyword.trim()
  if (mode === 'batch') {
    const values = splitBatchValues(expected)
    if (values.length === 0) return true
    if (values.length > BATCH_LIMIT) {
      throw new Error(`批量查询最多 ${BATCH_LIMIT} 个值`)
    }
    if (raw === null || raw === undefined) return false
    const actual = String(raw).toLowerCase()
    return values.some((value) => value.toLowerCase() === actual)
  }
  if (raw === null || raw === undefined) {
    return false
  }
  const actual = String(raw)
  const lowerActual = actual.toLowerCase()
  const lowerExpected = expected.toLowerCase()
  switch (mode) {
    case 'exact':
      return lowerActual === lowerExpected
    case 'ne':
      return actual.trim() !== '' && lowerActual !== lowerExpected
    case 'left':
      return lowerActual.startsWith(lowerExpected)
    case 'right':
      return lowerActual.endsWith(lowerExpected)
    case 'like':
    default:
      return lowerActual.includes(lowerExpected)
  }
}
