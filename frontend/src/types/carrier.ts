export interface CarrierRecord {
  id: number
  carrierCode: string
  carrierName: string
  contact: string
  phone: string
  deptId: number
  deptName?: string
  status: boolean
  sortOrder: number
  remark?: string
  createBy?: number
  createByName?: string
  createTime?: string
  updateBy?: number
  updateByName?: string
  updateTime?: string
}

export interface CarrierQuery {
  carrierCode?: string
  carrierName?: string
  contact?: string
  phone?: string
  deptId?: number
  status?: boolean
  updateByName?: string
  remark?: string
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
  pageNum?: number
  pageSize?: number
}

export interface CarrierPage {
  records: CarrierRecord[]
  total: number
}

export interface CarrierSavePayload {
  carrierCode: string
  carrierName: string
  contact: string
  phone: string
  status: boolean
  sortOrder: number
  remark?: string
}
