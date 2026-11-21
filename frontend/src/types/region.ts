export interface RegionNode {
  code: string
  name: string
  level: number
  hasChildren: boolean
}

export interface RegionStats {
  total: number
  provinceCount: number
  cityCount: number
  districtCount: number
  townshipCount?: number
  version?: string
  source?: string
  lastSyncTime?: string
  onlineSyncEnabled: boolean
}

export interface RegionBrief {
  code: string
  name: string
  fullName: string
  level: number
}

export interface RegionImportCheck {
  total: number
  added: number
  changed: number
  unchanged: number
  disabled: number
  invalid: number
  errors: string[]
  addedList: RegionBrief[]
  changedList: RegionBrief[]
  disabledList: RegionBrief[]
  truncated: boolean
}

export interface RegionImportResult extends RegionImportCheck {
  logId: number
  jobId: string
  status: string
  message: string
}

export interface RegionSyncLog {
  id: number
  jobId: string
  triggerType: string
  triggeredBy?: number
  triggeredByName?: string
  fileName?: string
  dataVersion?: string
  startedAt: string
  finishedAt?: string
  status: string
  progress: number
  totalCount: number
  addedCount: number
  changedCount: number
  unchangedCount: number
  disabledCount: number
  errorMsg?: string
}

export interface RegionSyncLogPage {
  records: RegionSyncLog[]
  total: number
}

export interface RegionLogDetail extends RegionSyncLog {
  report?: {
    added?: RegionBrief[]
    changed?: RegionBrief[]
    disabled?: RegionBrief[]
    errors?: string[]
    truncated?: boolean
  }
}

/** 详细地址识别结果：matched 表示至少识别一级，partial 表示省/市/区县不完整 */
export interface AddressRecognizeResult {
  provinceCode?: string
  provinceName?: string
  cityCode?: string
  cityName?: string
  districtCode?: string
  districtName?: string
  matched: boolean
  partial: boolean
  ambiguous: boolean
  candidates: string[]
  message: string
}
