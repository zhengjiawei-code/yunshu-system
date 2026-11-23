import request from '@/utils/request'
import type {
  CustomerMatch,
  DeptOption,
  PreOrderPage,
  RouteCandidate,
  WaybillSavePayload,
  WaybillSaveResult
} from '@/types/operation'

/** 可选开单网点 */
export function getOpenDepts() {
  return request<DeptOption[]>({ url: '/v1/operation/open-depts', method: 'get' })
}

/** 目的地候选（目的地 × 路由） */
export function getDestinations(params: { keyword: string; originDeptId?: number }) {
  return request<RouteCandidate[]>({ url: '/v1/operation/destinations', method: 'get', params })
}

/** 指定区县的路由候选 */
export function getRouteCandidates(params: { destDistrictCode: string; originDeptId?: number }) {
  return request<RouteCandidate[]>({ url: '/v1/operation/route-candidates', method: 'get', params })
}

/** 客户自动匹配 */
export function matchCustomer(params: { senderName?: string; senderPhone?: string }) {
  return request<CustomerMatch>({ url: '/v1/operation/customers/match', method: 'get', params })
}

/** 开单保存 */
export function createWaybill(data: WaybillSavePayload) {
  return request<WaybillSaveResult>({ url: '/v1/operation/waybills', method: 'post', data })
}

/** 预开单只读列表（本阶段占位） */
export function getPreOrders(params: { status?: number; keyword?: string; page?: number; pageSize?: number }) {
  return request<PreOrderPage>({ url: '/v1/operation/pre-orders', method: 'get', params })
}
