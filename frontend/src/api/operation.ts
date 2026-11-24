import request from '@/utils/request'
import type {
  CustomerMatch,
  DeptOption,
  ExportTask,
  PreOrderPage,
  RouteCandidate,
  WaybillAmendPayload,
  WaybillAmendResult,
  WaybillChildren,
  WaybillDetail,
  WaybillPage,
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

/* ------------------------------ 运单管理 ------------------------------ */

/** 运单列表：只母单，参数按 f_ 前缀传列筛选 */
export function getWaybillPage(params: Record<string, unknown>) {
  return request<WaybillPage>({ url: '/v1/operation/waybills', method: 'get', params })
}

export function getWaybillDetail(id: number) {
  return request<WaybillDetail>({ url: `/v1/operation/waybills/${id}`, method: 'get' })
}

export function getWaybillChildren(id: number) {
  return request<WaybillChildren>({ url: `/v1/operation/waybills/${id}/children`, method: 'get' })
}

export function voidWaybill(id: number, data: { reason: string; version: string }) {
  return request<{ id: number; status: number; updateTime: string }>({
    url: `/v1/operation/waybills/${id}/void`,
    method: 'post',
    data
  })
}

export function amendWaybill(id: number, data: WaybillAmendPayload) {
  return request<WaybillAmendResult>({
    url: `/v1/operation/waybills/${id}/amend`,
    method: 'put',
    data
  })
}

/* ------------------------------ 异步导出 ------------------------------ */

export function createExportTask(data: {
  taskType: string
  filters: Record<string, string>
  sort: { sortBy?: string; sortOrder?: string }
  columns: string[]
}) {
  return request<{ taskId: number }>({ url: '/v1/export/tasks', method: 'post', data })
}

export function getExportTask(id: number) {
  return request<ExportTask>({ url: `/v1/export/tasks/${id}`, method: 'get' })
}

export function downloadExportTask(id: number) {
  return request<Blob>({
    url: `/v1/export/tasks/${id}/download`,
    method: 'get',
    responseType: 'blob'
  })
}
