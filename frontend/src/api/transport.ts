import request from '@/utils/request'
import type {
  FeePreview,
  LoadedPage,
  LoadResult,
  PoolPage,
  SplitResult,
  TripDetail,
  TripDest,
  TripDestSavePayload,
  TripFeeSavePayload,
  TripLog,
  TripOption,
  TripPage,
  TripPrintData,
  TripSavePayload,
  TripUpdatePayload,
  TripVehiclePayload
} from '@/types/transport'

/* ------------------------------ 车次（#1–5） ------------------------------ */

/** 车次分页列表 */
export function getTripPage(params: Record<string, unknown>) {
  return request<TripPage>({ url: '/v1/transport/trip/page', method: 'get', params })
}

/** 车次下拉选项：dept / vehicle / driver / vehicleType */
export function getTripOptions(type: 'dept' | 'vehicle' | 'driver' | 'vehicleType', keyword?: string) {
  return request<TripOption[]>({ url: '/v1/transport/trip/options', method: 'get', params: { type, keyword } })
}

/** 车次详情（含子车次与汇总） */
export function getTripDetail(id: number) {
  return request<TripDetail>({ url: `/v1/transport/trip/${id}`, method: 'get' })
}

/** 创建车次：生成车次号与子车次 */
export function createTrip(data: TripSavePayload) {
  return request<{ id: number; tripNo: string; subTrips: TripDest[] }>({
    url: '/v1/transport/trip',
    method: 'post',
    data
  })
}

/** 修改车次：仅车次计划/装车配载状态 */
export function updateTrip(id: number, data: TripUpdatePayload) {
  return request<void>({ url: `/v1/transport/trip/${id}`, method: 'put', data })
}

/** 删除车次：仅车次计划且无配载 */
export function deleteTrip(id: number) {
  return request<void>({ url: `/v1/transport/trip/${id}`, method: 'delete' })
}

/* ------------------------------ 目的网点（#21–23） ------------------------------ */

export function addTripDest(tripId: number, data: TripDestSavePayload) {
  return request<TripDest>({ url: `/v1/transport/trip/${tripId}/dests`, method: 'post', data })
}

export function updateTripDest(tripId: number, destId: number, data: TripDestSavePayload) {
  return request<void>({ url: `/v1/transport/trip/${tripId}/dests/${destId}`, method: 'put', data })
}

export function deleteTripDest(tripId: number, destId: number) {
  return request<void>({ url: `/v1/transport/trip/${tripId}/dests/${destId}`, method: 'delete' })
}

/* ------------------------------ 车次操作（#13/#14/#17） ------------------------------ */

export function departTrip(id: number, planArriveTime: string) {
  return request<{ tripId: number; tripStatus: number }>({
    url: `/v1/transport/trip/${id}/depart`,
    method: 'post',
    data: { planArriveTime }
  })
}

export function cancelDepartTrip(id: number, reason: string) {
  return request<{ tripId: number; tripStatus: number }>({
    url: `/v1/transport/trip/${id}/cancel-depart`,
    method: 'post',
    data: { reason }
  })
}

export function closeTrip(id: number, reason: string) {
  return request<{ tripId: number; tripStatus: number }>({
    url: `/v1/transport/trip/${id}/close`,
    method: 'post',
    data: { reason }
  })
}

/* ------------------------------ 装车配载（#6–9） ------------------------------ */

/** 待配载池：资格校验在服务端一次做足 */
export function getTripPool(tripId: number, params: Record<string, unknown>) {
  return request<PoolPage>({ url: `/v1/transport/trip/${tripId}/pool`, method: 'get', params })
}

/** 已配载列表 */
export function getTripLoaded(tripId: number, params: Record<string, unknown>) {
  return request<LoadedPage>({ url: `/v1/transport/trip/${tripId}/waybills`, method: 'get', params })
}

/** 批量配载（右移）：只落库，失败清单原样返回 */
export function loadTripWaybills(tripId: number, waybillIds: number[]) {
  return request<LoadResult>({
    url: `/v1/transport/trip/${tripId}/waybills`,
    method: 'post',
    data: { waybillIds }
  })
}

/** 批量移出（左移） */
export function removeTripWaybills(tripId: number, waybillIds: number[]) {
  return request<LoadResult>({
    url: `/v1/transport/trip/${tripId}/waybills`,
    method: 'delete',
    data: { waybillIds }
  })
}

/** 拆单预览（不落库） */
export function previewTripSplit(tripId: number, parentWaybillId: number, quantity: number) {
  return request<SplitResult>({
    url: `/v1/transport/trip/${tripId}/split-preview`,
    method: 'get',
    params: { parentWaybillId, quantity }
  })
}

/** 拆单装车：生成子单并直接配载 */
export function splitLoadTripWaybill(tripId: number, parentWaybillId: number, quantity: number) {
  return request<SplitResult>({
    url: `/v1/transport/trip/${tripId}/split-load`,
    method: 'post',
    data: { parentWaybillId, quantity }
  })
}

/* ------------------------------ 车辆 / 费用 / 日志 / 打印 ------------------------------ */

/** 保存车辆/司机信息（#10） */
export function saveTripVehicle(tripId: number, data: TripVehiclePayload) {
  return request<void>({ url: `/v1/transport/trip/${tripId}/vehicle`, method: 'put', data })
}

/** 车辆快速新增（#24）：返回 vehicleId */
export function quickCreateVehicle(data: {
  plateNo: string
  vehicleType?: string
  vehicleLoad?: number | null
  vehicleVolume?: number | null
}) {
  return request<number>({ url: '/v1/transport/vehicle/quick', method: 'post', data })
}

/** 司机快速新增（#25）：返回 driverId */
export function quickCreateDriver(data: {
  driverName: string
  driverPhone: string
  licenseNo?: string
}) {
  return request<number>({ url: '/v1/transport/driver/quick', method: 'post', data })
}

/** 保存成本项 + 全车分摊方式（#11） */
export function saveTripFees(tripId: number, data: TripFeeSavePayload) {
  return request<FeePreview>({ url: `/v1/transport/trip/${tripId}/fees`, method: 'put', data })
}

/** 费用分摊预览（#12） */
export function previewTripFees(tripId: number, data: TripFeeSavePayload) {
  return request<FeePreview>({
    url: `/v1/transport/trip/${tripId}/fees/preview`,
    method: 'get',
    params: {
      allocMethod: data.allocMethod,
      items: JSON.stringify(data.items),
      destItems: JSON.stringify(data.destItems || [])
    }
  })
}

/** 车次操作日志（#19） */
export function getTripLogs(tripId: number) {
  return request<TripLog[]>({ url: `/v1/transport/trip/${tripId}/logs`, method: 'get' })
}

/** 打印装车单数据（#18） */
export function getTripPrint(tripId: number, subTripId?: number) {
  return request<TripPrintData>({
    url: `/v1/transport/trip/${tripId}/print`,
    method: 'get',
    params: { subTripId }
  })
}
