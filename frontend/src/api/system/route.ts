import request from '@/utils/request'
import type {
  BatchGrantResult,
  RegionLibraryQuery,
  RegionLibraryPage,
  RouteEditableField,
  RouteGrantRecord,
  RouteMatchResult,
  RoutePage,
  RouteQuery,
  RouteRecord,
  RouteSavePayload,
  RouteSaveResult,
  TransferBatchJoinPayload,
  TransferBatchResult,
  TransferEditPayload,
  TransferPage,
  TransferQuery,
  TransferRecord,
  RouteUsage
} from '@/types/route'

/* Tab1 短驳/干线 */
export function getRoutePage(query: RouteQuery): Promise<RoutePage> {
  return request({ url: '/v1/system/routes/page', method: 'get', params: query })
}

export function getRouteDetail(id: number): Promise<RouteRecord> {
  return request({ url: `/v1/system/routes/${id}`, method: 'get' })
}

export function createRoute(payload: RouteSavePayload): Promise<RouteSaveResult> {
  return request({ url: '/v1/system/routes', method: 'post', data: payload })
}

export function updateRoute(id: number, payload: RouteSavePayload): Promise<void> {
  return request({ url: `/v1/system/routes/${id}`, method: 'put', data: payload })
}

/** 短驳/干线单字段增量更新：value 传空字符串表示清空（落库 NULL），返回落库规范值 */
export function updateRouteField(
  id: number,
  field: RouteEditableField,
  value: string
): Promise<string | null> {
  return request({ url: `/v1/system/routes/${id}/field`, method: 'put', data: { field, value } })
}

export function deleteRoute(id: number): Promise<void> {
  return request({ url: `/v1/system/routes/${id}`, method: 'delete' })
}

/* Tab2 直达/外转（捕鱼式双栏：区划库 + 已配置列表） */
export function getTransferPage(query: TransferQuery): Promise<TransferPage> {
  return request({ url: '/v1/system/routes/transfers/page', method: 'get', params: query })
}

export function getTransferDetail(id: number): Promise<TransferRecord> {
  return request({ url: `/v1/system/routes/transfers/${id}`, method: 'get' })
}

export function getRegionLibrary(params: RegionLibraryQuery): Promise<RegionLibraryPage> {
  return request({ url: '/v1/system/routes/transfers/region-library', method: 'get', params })
}

export function batchJoinTransfer(payload: TransferBatchJoinPayload): Promise<TransferBatchResult> {
  return request({ url: '/v1/system/routes/transfers/batch-join', method: 'post', data: payload })
}

export function updateTransfer(id: number, payload: TransferEditPayload): Promise<void> {
  return request({ url: `/v1/system/routes/transfers/${id}`, method: 'put', data: payload })
}

export function updateTransferServices(
  id: number,
  payload: { pickupEnabled: boolean; deliveryEnabled: boolean }
): Promise<void> {
  return request({ url: `/v1/system/routes/transfers/${id}/services`, method: 'put', data: payload })
}

export function updateTransferAlias(id: number, alias: string): Promise<void> {
  return request({ url: `/v1/system/routes/transfers/${id}/alias`, method: 'put', data: { alias } })
}

export function switchTransferType(payload: {
  ids: number[]
  transferType: 1 | 2
}): Promise<TransferBatchResult> {
  return request({ url: '/v1/system/routes/transfers/batch/type', method: 'put', data: payload })
}

export function batchTransferCarrier(payload: {
  ids: number[]
  carrierId: number
}): Promise<TransferBatchResult> {
  return request({ url: '/v1/system/routes/transfers/batch/carrier', method: 'put', data: payload })
}

export function batchTransferStatus(payload: {
  ids: number[]
  status: boolean
}): Promise<TransferBatchResult> {
  return request({ url: '/v1/system/routes/transfers/batch/status', method: 'put', data: payload })
}

export function deleteTransfer(id: number): Promise<void> {
  return request({ url: `/v1/system/routes/transfers/${id}`, method: 'delete' })
}

/** 路由使用情况：使用公司清单 + 移除/取消授权判定 */
export function getTransferUsage(id: number): Promise<RouteUsage> {
  return request({ url: `/v1/system/routes/transfers/${id}/usage`, method: 'get' })
}

/* 授权 */
export function getRouteGrants(routeScope: 1 | 2, routeId: number): Promise<RouteGrantRecord[]> {
  return request({
    url: `/v1/system/routes/${routeId}/grants`,
    method: 'get',
    params: { routeScope }
  })
}

export function grantRoute(routeScope: 1 | 2, routeId: number, companyDeptIds: number[]): Promise<number> {
  return request({
    url: `/v1/system/routes/${routeId}/grants`,
    method: 'post',
    params: { routeScope },
    data: { companyDeptIds }
  })
}

export function revokeRouteGrant(routeScope: 1 | 2, routeId: number, companyDeptId: number): Promise<void> {
  return request({
    url: `/v1/system/routes/${routeId}/grants/${companyDeptId}`,
    method: 'delete',
    params: { routeScope }
  })
}

export function batchGrantRoutes(
  routeScope: 1 | 2,
  routeIds: number[],
  companyDeptIds: number[]
): Promise<BatchGrantResult> {
  return request({
    url: '/v1/system/routes/grants/batch',
    method: 'post',
    data: { routeScope, routeIds, companyDeptIds }
  })
}

/* 开单匹配 */
export function matchRoutes(params: {
  destRegionCode?: string
  destDeptId?: number
  originDeptId?: number
  applyDeptId?: number
}): Promise<RouteMatchResult> {
  return request({ url: '/v1/system/routes/match', method: 'get', params })
}
