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

/* ------------------------------ 待配载池（左栏） ------------------------------ */

export interface PoolRow {
  waybillId: number
  waybillNo: string
  /** 1=整票 2=已拆母单余额 3=子单 */
  unitType: number
  parentNo?: string
  childCount?: number
  splitRatio?: number
  status?: number
  customerName?: string
  receiverName?: string
  receiverPhone?: string
  goodsName?: string
  destName?: string
  nextDeptId?: number
  nextDeptName?: string
  originalQuantity?: number
  originalWeight?: number
  originalVolume?: number
  remainingQuantity?: number
  remainingWeight?: number
  remainingVolume?: number
  totalFee?: number
  /** 运费余额：母单余额按剩余金额口径 */
  remainFee?: number
  rebateTotal?: number
  validFreight?: number
  inventoryTime?: string
  createTime?: string
}

export interface PoolSummary {
  unitCount: number
  quantity: number
  weight: number
  volume: number
}

export interface PoolPage {
  records: PoolRow[]
  total: number
  page: number
  pageSize: number
  summary: PoolSummary
}

/* ------------------------------ 已配载（右栏） ------------------------------ */

export interface LoadedRow {
  tripWaybillId: number
  tripDestId?: number
  subTripNo?: string
  destDeptId?: number
  destDeptName?: string
  waybillId: number
  waybillNo: string
  parentNo?: string
  isChild?: number
  unitType?: number
  splitRatio?: number
  status?: number
  payMethod?: number
  /** 0=未结算 1=已结算 2=部分结算 3=无需结算 */
  settleStatus?: number
  loadStatus?: number
  customerName?: string
  senderName?: string
  receiverName?: string
  destName?: string
  goodsName?: string
  quantity?: number
  weight?: number
  volume?: number
  prepaidFee?: number
  collectFee?: number
  returnFee?: number
  monthlyFee?: number
  totalFee?: number
  rebateTotal?: number
  /** 装车运费：配载快照金额 */
  loadFee?: number
  /** 装车返款：配载快照返款 */
  loadRebate?: number
  validFreight?: number
  allocatedFee?: number
  profitCost?: number
  grossProfit?: number
  loadTime?: string
}

export interface LoadedSummary {
  unitCount: number
  quantity: number
  weight: number
  volume: number
  totalFee: number
  rebateTotal: number
  grossProfit: number
}

export interface LoadedPage {
  records: LoadedRow[]
  total: number
  page: number
  pageSize: number
  summary: LoadedSummary
}

/* ------------------------------ 配载/移出结果 ------------------------------ */

export interface LoadFailure {
  waybillId: number
  waybillNo?: string
  reason: string
}

export interface LoadResult {
  successCount: number
  failures: LoadFailure[]
  warnings: string[]
}

/* ------------------------------ 拆单预览/拆单装车 ------------------------------ */

export interface SplitAmounts {
  quantity: number
  weight: number
  volume: number
  prepaidFee: number
  collectFee: number
  returnFee: number
  monthlyFee: number
  deliveryFee: number
  totalFee: number
  cashBack: number
  oweBack: number
  rebateTotal: number
  validFreight: number
}

export interface SplitResult {
  parentWaybillId: number
  parentWaybillNo: string
  originalQuantity: number
  originalWeight: number
  originalVolume: number
  remainingQuantityBefore: number
  remainingWeightBefore: number
  remainingVolumeBefore: number
  splitQuantity: number
  splitRatio: number
  child: SplitAmounts
  parentRemaining: SplitAmounts
  childWaybillId?: number
  childWaybillNo?: string
  childSeq?: number
  childStatus?: number
  routeNodeCount?: number
}

/* ------------------------------ 费用分摊 ------------------------------ */

export interface FeeItemTotal {
  feeCode: string
  feeName: string
  amount: number
  profitEnabled?: boolean
}

export interface FeeUnit {
  tripWaybillId: number
  waybillId: number
  waybillNo: string
  parentNo?: string
  isChild?: number
  quantity?: number
  weight?: number
  volume?: number
  chargeWeight?: number
  volumeWeightRatio?: number
  basis: number
  ratio: number
  fees: Record<string, number>
  allocatedFee: number
  profitCost: number
}

export interface FeeSummary {
  unitCount: number
  incomeTotal: number
  rebateTotal: number
  costTotal: number
  grossProfit: number
}

export interface FeePreview {
  tripId: number
  allocMethod: number
  allocMethodName?: string
  basisTotal: number
  items: FeeItemTotal[]
  units: FeeUnit[]
  summary: FeeSummary
}

export interface FeeTypeRow {
  id?: number
  feeCode: string
  feeName: string
  feeCategory?: number
  enabled?: boolean
  profitEnabled?: boolean
  profitDirection?: number
  sourceField?: string
  sortOrder?: number
  readonly?: boolean
}

export interface FeeTypePage {
  rows: FeeTypeRow[]
  total: number
  page: number
  pageSize: number
  counts?: Record<string, number>
}

/* ------------------------------ 车辆/费用/日志/打印 ------------------------------ */

export interface TripVehiclePayload {
  vehicleId?: number | null
  plateNo?: string
  vehicleType?: string
  vehicleLoad?: number | null
  vehicleVolume?: number | null
  driverId?: number | null
  driverName?: string
  driverPhone?: string
}

export interface TripFeeItemPayload {
  feeCode: string
  amount: number
}

export interface TripFeeSavePayload {
  allocMethod: number
  items: TripFeeItemPayload[]
}

export interface TripLog {
  id: number
  actionType: number
  actionName?: string
  destId?: number
  reason?: string
  operatorName?: string
  beforeSnapshot?: string
  afterSnapshot?: string
  createTime?: string
}

export interface TripPrintRow {
  tripWaybillId: number
  waybillNo: string
  parentNo?: string
  isChild?: number
  subTripNo?: string
  destHubName?: string
  destName?: string
  customerName?: string
  receiverName?: string
  receiverPhone?: string
  goodsName?: string
  quantity?: number
  weight?: number
  volume?: number
  prepaidFee?: number
  collectFee?: number
  returnFee?: number
  monthlyFee?: number
  totalFee?: number
  rebateTotal?: number
  validFreight?: number
}

export interface TripPrintData {
  header: {
    tripId: number
    tripNo: string
    tripName?: string
    tripStatus?: number
    originDeptName?: string
    destNames?: string
    plateNo?: string
    vehicleType?: string
    vehicleLoad?: number
    vehicleVolume?: number
    driverName?: string
    driverPhone?: string
    planDepartTime?: string
    departTime?: string
    planArriveTime?: string
    allocMethodName?: string
  }
  rows: TripPrintRow[]
  totals: {
    unitCount?: number
    quantity?: number
    weight?: number
    volume?: number
    totalFee?: number
    rebateTotal?: number
    validFreight?: number
  }
}
