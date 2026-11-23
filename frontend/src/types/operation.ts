export interface DeptOption {
  id: number
  deptName: string
  deptCode: string
  deptType: number
}

export interface RouteNode {
  seq: number
  routeScope: number
  routeId: number
  routeCode: string
  routeCategory?: number
  transferType?: number
  fromDeptId?: number
  fromDeptName?: string
  toDeptId?: number
  toDeptName?: string
  destRegionCode?: string
  destRegionName?: string
  carrierId?: number
  carrierName?: string
  warehouseArea?: string
  mileage?: number
  transitHours?: number
}

export interface RouteCandidate {
  chainText: string
  nodeCount?: number
  endType?: number
  matchLevel?: number
  totalHours?: number
  totalMileage?: number
  recommended?: boolean
  noRoute?: boolean
  routeKeys: string[]
  provinceCode?: string
  provinceName?: string
  cityCode?: string
  cityName?: string
  districtCode?: string
  districtName?: string
  destName?: string
  destHubId?: number
  destHubName?: string
  nodes: RouteNode[]
}

export interface CustomerMatchItem {
  id: number
  customerName: string
  contactPhone?: string
  address?: string
}

export interface CustomerMatch {
  matched: boolean
  candidates: CustomerMatchItem[]
}

export interface GoodsItemPayload {
  goodsName: string
  goodsType?: string
  packageType?: string
  quantity: number
  weight: number
  volume: number
  goodsRemark?: string
}

export interface WaybillSavePayload {
  originDeptId?: number
  relatedNo?: string
  salesId?: number
  salesName?: string
  bizType: number
  destDistrictCode: string
  routeKeys: string[]
  senderName: string
  senderPhone: string
  senderCompany?: string
  senderAddress: string
  receiverName: string
  receiverPhone: string
  receiverCompany?: string
  receiverProvinceCode?: string
  receiverCityCode?: string
  receiverDistrictCode?: string
  receiverAddress: string
  goodsList: GoodsItemPayload[]
  payMethod?: number
  freightFee?: number
  deliveryFee?: number
  pickupFee: number
  otherFee: number
  receiptFee: number
  makeBillFee: number
  insuranceFee: number
  declaredValue: number
  cashBack: number
  oweBack: number
  prepaidFee?: number
  collectFee?: number
  monthlyFee?: number
  returnFee?: number
  deliveryMethod?: number
  codAmount: number
  payeeName?: string
  bankName?: string
  bankCardNo?: string
  needReceipt: number
  receiptCount?: number
  waitNotice: number
  remark?: string
}

export interface WaybillSaveResult {
  id: number
  waybillNo: string
  status: number
  payMethod: number
  totalFee: number
  rebateTotal: number
  validFreight: number
  prepaidFee: number
  collectFee: number
  monthlyFee: number
  returnFee: number
  customerId?: number
  customerName?: string
  originHubId?: number
  originHubName?: string
  destHubId?: number
  destHubName?: string
  routeName?: string
  routeNodeCount?: number
  routeEndType?: number
  destName?: string
  createTime?: string
}

export interface PreOrder {
  id: number
  preOrderNo: string
  status: number
  customerName: string
  customerPhone?: string
  destName: string
  goodsName: string
  quantity: number
  weight: number
  volume: number
  expectPickupTime?: string
  submitTime: string
  senderName?: string
  receiverName?: string
}

export interface PreOrderPage {
  data: PreOrder[]
  total: number
  page: number
  pageSize: number
  summary: Record<string, number>
}
