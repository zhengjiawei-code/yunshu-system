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
