import request from '@/utils/request'
import type {
  TripDetail,
  TripDest,
  TripDestSavePayload,
  TripOption,
  TripPage,
  TripSavePayload,
  TripUpdatePayload
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
