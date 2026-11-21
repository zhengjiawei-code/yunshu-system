/** 路由类型：Tab1 1=短驳 2=干线；Tab2 transferType 1=直达 2=外转 */
export interface RouteRecord {
  id: number
  routeCode: string
  routeCategory: 1 | 2
  originDeptId: number
  originDeptName?: string
  destDeptId: number
  destDeptName?: string
  routeName: string
  warehouseArea?: string
  mileage?: number
  transitHours?: number
  contactName?: string
  contactPhone?: string
  serviceName?: string
  servicePhone?: string
  ownerCompanyId: number
  ownerCompanyName?: string
  status: boolean
  sortOrder: number
  remark?: string
  createByName?: string
  createTime?: string
  updateByName?: string
  updateTime?: string
  /** ALL=授权全部；COUNT=按公司授权；分公司视角为空 */
  grantScope?: 'ALL' | 'COUNT' | null
  grantCount?: number
  editable: boolean
  deletable: boolean
}

export interface RouteQuery {
  routeCode?: string
  routeName?: string
  routeCategory?: 1 | 2 | Array<1 | 2>
  originDeptId?: number | number[]
  destDeptId?: number | number[]
  warehouseArea?: string
  mileageMin?: number
  mileageMax?: number
  transitHoursMin?: number
  transitHoursMax?: number
  contactName?: string
  contactPhone?: string
  serviceName?: string
  servicePhone?: string
  ownerCompanyIds?: string
  /** 文本列匹配模式 JSON：{ field: 'exact' | 'ne' | 'like' | 'left' | 'right' | 'batch' } */
  filterModes?: string
  status?: boolean | boolean[]
  grantScope?: 'ALL' | 'PARTIAL'
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
  pageNum?: number
  pageSize?: number
}

export interface RoutePage {
  records: RouteRecord[]
  total: number
}

export interface RouteSavePayload {
  routeCategory: 1 | 2
  originDeptId: number
  destDeptId: number
  routeName?: string
  warehouseArea?: string
  mileage?: number
  transitHours?: number
  contactName?: string
  contactPhone?: string
  serviceName?: string
  servicePhone?: string
  status: boolean
  sortOrder: number
  remark?: string
}

export interface TransferRecord {
  id: number
  routeCode: string
  routeName: string
  transferType: 1 | 2
  applyDeptId: number
  applyDeptName?: string
  regionCode: string
  regionLevel: number
  regionFullName: string
  carrierId?: number
  carrierName?: string
  ownerCompanyId: number
  ownerCompanyName?: string
  status: boolean
  sortOrder: number
  remark?: string
  createByName?: string
  createTime?: string
  updateByName?: string
  updateTime?: string
  grantScope?: 'ALL' | 'COUNT' | null
  grantCount?: number
  editable: boolean
  deletable: boolean
}

export interface TransferQuery {
  routeCode?: string
  routeName?: string
  transferType?: 1 | 2 | Array<1 | 2>
  applyDeptId?: number
  regionCode?: string
  regionLevel?: 2 | 3 | 4 | Array<2 | 3 | 4>
  carrierId?: number | number[]
  status?: boolean | boolean[]
  grantScope?: 'ALL' | 'PARTIAL' | Array<'ALL' | 'PARTIAL'>
  updateByName?: string
  ownerCompanyIds?: string
  filterModes?: string
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
  pageNum?: number
  pageSize?: number
}

export interface TransferPage {
  records: TransferRecord[]
  total: number
}

/** 区划库条目：外转/直达从区划挑选后加入 */
export interface RegionLibraryRecord {
  code: string
  name: string
  level: 2 | 3 | 4
  parentCode?: string
  fullName: string
  provinceName?: string
  cityName?: string
  districtName?: string
  configured: boolean
  routeId?: number
  transferType?: 1 | 2
}

export interface RegionLibraryQuery {
  applyDeptId?: number
  keyword?: string
  level?: 2 | 3 | 4 | Array<2 | 3 | 4>
  configured?: boolean | boolean[]
  parentCode?: string
  provinceCode?: string
  cityCode?: string
  districtKeyword?: string
  townKeyword?: string
  filterModes?: string
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
  pageNum?: number
  pageSize?: number
}

export interface RegionLibraryPage {
  records: RegionLibraryRecord[]
  total: number
}

export interface TransferBatchJoinPayload {
  applyDeptId: number
  regionCodes: string[]
}

export interface TransferEditPayload {
  routeName?: string
  carrierId?: number | null
  status?: boolean
  sortOrder?: number
  remark?: string
}

export interface TransferBatchResult {
  total: number
  created: number
  restored: number
  overwritten: number
  granted: number
  skipped: number
  message: string
  details: string[]
}

export interface RouteGrantRecord {
  id: number
  companyDeptId: number
  companyName?: string
  grantSource: number
  grantSourceName: string
  grantBy?: number
  grantByName?: string
  grantTime?: string
  remark?: string
}

export interface RouteSaveResult {
  id: number
  reused: boolean
  message: string
}

export interface BatchGrantResult {
  success: number
  skipped: number
  message: string
}

export interface RouteMatchNode {
  routeScope: 1 | 2
  routeId: number
  routeCode: string
  category: number
  fromDeptId?: number
  fromDeptName?: string
  toDeptId?: number
  toDeptName?: string
  routeName?: string
  contactName?: string
  contactPhone?: string
  transitHours?: number
  mileage?: number
  warehouseArea?: string
  carrierId?: number
  carrierName?: string
  serviceName?: string
  servicePhone?: string
}

export interface RouteMatchItem {
  categoryRank: number
  categoryLabel: string
  hops: number
  totalTransitHours?: number
  totalMileage?: number
  recommended: boolean
  nodes: RouteMatchNode[]
}

export interface RouteMatchResult {
  matched: boolean
  message: string
  recommendedId?: number
  records: RouteMatchItem[]
}
