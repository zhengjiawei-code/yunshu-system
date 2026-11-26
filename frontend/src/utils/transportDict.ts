/** 装车配载展示字典：与后端 TripEnums 同口径 */

export interface TripDictOption {
  label: string
  value: number
}

export const TRIP_STATUS_OPTIONS: TripDictOption[] = [
  { value: 10, label: '车次计划' },
  { value: 20, label: '装车配载' },
  { value: 30, label: '车辆在途' },
  { value: 40, label: '车辆部分到站' },
  { value: 50, label: '车辆到站' },
  { value: 60, label: '完成卸车' },
  { value: 90, label: '已关闭' }
]

export const TRIP_CATEGORY_OPTIONS: TripDictOption[] = [
  { value: 1, label: '干线' },
  { value: 2, label: '短驳' }
]

export const ALLOC_METHOD_OPTIONS: TripDictOption[] = [
  { value: 1, label: '按重量' },
  { value: 2, label: '按体积' },
  { value: 3, label: '按重泡比计费重量' }
]

export function tripStatusText(value?: number | null): string {
  return TRIP_STATUS_OPTIONS.find((item) => item.value === value)?.label ?? '—'
}

export function tripStatusTagType(
  value?: number | null
): 'primary' | 'success' | 'info' | 'warning' | 'danger' {
  if (value === 10) return 'info'
  if (value === 90) return 'info'
  if (value === 30 || value === 40) return 'warning'
  if (value === 50 || value === 60) return 'success'
  return 'primary'
}

export function tripCategoryText(value?: number | null): string {
  return TRIP_CATEGORY_OPTIONS.find((item) => item.value === value)?.label ?? '—'
}

export function allocMethodText(value?: number | null): string {
  return ALLOC_METHOD_OPTIONS.find((item) => item.value === value)?.label ?? '—'
}
