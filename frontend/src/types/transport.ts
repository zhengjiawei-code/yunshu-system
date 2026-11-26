/** 运输管理（装车配载）类型：与后端 TripPageVO / TripDetailVO / BizTrip 对齐 */

export interface TripRecord {
  id: number
  tripNo: string
  tripName: string
  /** 1=干线 2=短驳（本期恒为 1） */
  tripCategory?: number
  /** 10计划 20配载 30在途 40部分到站 50到站 60完成卸车 90关闭 */
  tripStatus: number
  originDeptId?: number
  originDeptName?: string
  planDepartTime?: string
  departTime?: string
  planArriveTime?: string
  actualArriveTime?: string
  vehicleId?: number
  plateNo?: string
  vehicleType?: string
  vehicleLoad?: number
  vehicleVolume?: number
  driverId?: number
  driverName?: string
  driverPhone?: string
  totalWaybillCount?: number
  totalQuantity?: number
  totalWeight?: number
  totalVolume?: number
  /** 重量装载率（百分比数值，如 0.11 表示 0.11%） */
  loadRateWeight?: number
  loadRateVolume?: number
  totalPrepaidFreight?: number
  totalCollectFreight?: number
  totalReturnFreight?: number
  totalMonthlyFreight?: number
  totalFreight?: number
  totalRebate?: number
  totalValidFreight?: number
  totalPickupCost?: number
  totalDeliveryFee?: number
  totalTransferFee?: number
  allocMethod?: number
  prepaidTransportFee?: number
  returnTransportFee?: number
  arrivalTransportFee?: number
  prepaidOilCardFee?: number
  wholeWeighingFee?: number
  wholeInsuranceFee?: number
  bulkLoadFee?: number
  bulkUnloadFee?: number
  bulkOtherFee?: number
  arrivalUnloadFee?: number
  arrivalOtherFee?: number
  totalCostFee?: number
  grossProfit?: number
  departByName?: string
  closeReason?: string
  closeTime?: string
  closeByName?: string
  remark?: string
  createByName?: string
  createTime?: string
  updateByName?: string
  updateTime?: string
}

export interface TripDest {
  id: number
  tripId: number
  subTripNo: string
  seq: number
  destDeptId: number
  destDeptName: string
  /** 10待发车 20在途 30已到站 40已卸车 */
  subStatus: number
  planArriveTime?: string
  arriveTime?: string
  unloadTime?: string
  totalWaybillCount?: number
  totalQuantity?: number
  totalWeight?: number
  totalVolume?: number
  arrivalUnloadFee?: number
  arrivalOtherFee?: number
  arrivalUnloadFeeValid?: boolean
  arrivalOtherFeeValid?: boolean
}

export interface TripPageSummary {
  tripCount: number
  waybillCount: number
  quantity: number
  weight: number
  volume: number
  totalFreight: number
  grossProfit: number
}

export interface TripPage {
  records: TripRecord[]
  total: number
  page: number
  pageSize: number
  summary: TripPageSummary
}

export interface TripDetail {
  trip: TripRecord
  dests: TripDest[]
  summary: TripPageSummary
}

export interface TripOption {
  id: number
  name: string
  type?: string
  load?: number
  volume?: number
  phone?: string
  leaf?: boolean
}

export interface TripSavePayload {
  tripName: string
  planDepartTime: string
  originDeptId?: number
  destDeptIds: number[]
  remark?: string
}

export interface TripUpdatePayload {
  tripName?: string
  planDepartTime?: string
  originDeptId?: number
  /** 传 null 表示不修改目的网点 */
  destDeptIds?: number[] | null
  remark?: string
}

export interface TripDestSavePayload {
  destDeptId?: number
  seq?: number
  planArriveTime?: string
}

export interface TripActionDone {
  id: number
  tripNo?: string
}
