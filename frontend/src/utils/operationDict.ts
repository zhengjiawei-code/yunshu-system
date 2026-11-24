/** 运单管理展示字典：与后端 WaybillEnums 同口径，禁止在页面内写死码值文案 */

export interface DictOption {
  label: string
  value: number
}

export const WAYBILL_STATUS_OPTIONS: DictOption[] = [
  { value: 10, label: '待揽收' },
  { value: 20, label: '已揽收' },
  { value: 30, label: '网点装车' },
  { value: 40, label: '网点发车' },
  { value: 50, label: '分拨到车' },
  { value: 60, label: '分拨卸车' },
  { value: 70, label: '分拨装车' },
  { value: 80, label: '分拨发车' },
  { value: 90, label: '末端到车' },
  { value: 100, label: '到站待提' },
  { value: 110, label: '到站待送' },
  { value: 120, label: '到站待转' },
  { value: 130, label: '已签收' },
  { value: 900, label: '已作废' }
]

export const BIZ_TYPE_OPTIONS: DictOption[] = [
  { value: 1, label: '整车' },
  { value: 2, label: '零担' }
]

export const PAY_METHOD_OPTIONS: DictOption[] = [
  { value: 1, label: '现付' },
  { value: 2, label: '到付' },
  { value: 3, label: '月结' },
  { value: 4, label: '回付' },
  { value: 5, label: '多笔付' }
]

export const DELIVERY_METHOD_OPTIONS: DictOption[] = [
  { value: 1, label: '送货' },
  { value: 2, label: '自提' },
  { value: 3, label: '大车直送' }
]

export const NEED_RECEIPT_OPTIONS: DictOption[] = [
  { value: 0, label: '否' },
  { value: 1, label: '是' }
]

export const ROUTE_END_TYPE_OPTIONS: DictOption[] = [
  { value: 1, label: '直达' },
  { value: 2, label: '外转' }
]

export const CHILD_FLAG_OPTIONS: DictOption[] = [
  { value: 0, label: '母单' },
  { value: 1, label: '子单' }
]

/** 包装类型预设，与开单页保持一致 */
export const PACKAGE_TYPES = ['袋装', '纸箱', '桶装', '托盘', '木箱', '编织袋', '卷装', '裸装', '其他']

export function statusText(value?: number | null): string {
  return WAYBILL_STATUS_OPTIONS.find((item) => item.value === value)?.label ?? '—'
}

/** 状态标签色：签收成功、作废灰、末端三类预警、其余主色 */
export function statusTagType(value?: number | null): 'success' | 'info' | 'warning' | 'danger' | 'primary' {
  if (value === 900) return 'info'
  if (value === 130) return 'success'
  if (value === 100 || value === 110 || value === 120) return 'warning'
  if (value === 10) return 'info'
  return 'primary'
}

export function dictText(options: DictOption[], value?: number | null): string {
  return options.find((item) => item.value === value)?.label ?? '—'
}

/** 金额：两位小数 + 千分位 */
export function formatMoney(value?: number | string | null): string {
  if (value === null || value === undefined || value === '') return '0.00'
  const num = Number(value)
  if (Number.isNaN(num)) return '0.00'
  return num.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

/** 数量/重量/体积：最多 3 位小数，去尾零 */
export function formatQuantity(value?: number | string | null): string {
  if (value === null || value === undefined || value === '') return '—'
  const num = Number(value)
  if (Number.isNaN(num)) return '—'
  return num.toLocaleString('zh-CN', { maximumFractionDigits: 3 })
}

/** 后端 LocalDateTime（ISO 或空格分隔）统一展示为 yyyy-MM-dd HH:mm:ss */
export function formatDateTime(value?: string | null): string {
  if (!value) return '—'
  const text = value.replace('T', ' ')
  return text.length > 19 ? text.slice(0, 19) : text
}

/** 改单允许状态：20 已揽收 ~ 90 末端到车（含端点） */
export function canAmend(status?: number | null): boolean {
  return status !== null && status !== undefined && status >= 20 && status <= 90
}
