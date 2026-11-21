import request from '@/utils/request'
import type {
  AddressRecognizeResult,
  RegionImportCheck,
  RegionImportResult,
  RegionLogDetail,
  RegionNode,
  RegionStats,
  RegionSyncLogPage
} from '@/types/region'

export function getRegionChildren(parentCode = '0'): Promise<RegionNode[]> {
  return request({ url: '/v1/system/regions/children', method: 'get', params: { parentCode } })
}

export function searchRegions(keyword: string, limit = 20): Promise<RegionNode[]> {
  return request({ url: '/v1/system/regions/search', method: 'get', params: { keyword, limit } })
}

export function getRegionStats(): Promise<RegionStats> {
  return request({ url: '/v1/system/regions/stats', method: 'get' })
}

/** 详细地址识别省/市/区县（仅本地区划数据） */
export function recognizeAddress(text: string): Promise<AddressRecognizeResult> {
  return request({ url: '/v1/system/regions/recognize-address', method: 'post', data: { text } })
}

function formData(file: File, version?: string): FormData {
  const data = new FormData()
  data.append('file', file)
  if (version) data.append('version', version)
  return data
}

export function precheckRegionFile(file: File, version?: string): Promise<RegionImportCheck> {
  return request({
    url: '/v1/system/regions/import/precheck',
    method: 'post',
    data: formData(file, version),
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function importRegionFile(file: File, version?: string): Promise<RegionImportResult> {
  return request({
    url: '/v1/system/regions/import',
    method: 'post',
    data: formData(file, version),
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function syncRegionOnline(): Promise<RegionImportResult> {
  return request({ url: '/v1/system/regions/sync-online', method: 'post' })
}

export function getRegionSyncLogs(pageNum = 1, pageSize = 20): Promise<RegionSyncLogPage> {
  return request({ url: '/v1/system/regions/sync-logs', method: 'get', params: { pageNum, pageSize } })
}

export function getRegionSyncLogDetail(id: number): Promise<RegionLogDetail> {
  return request({ url: `/v1/system/regions/sync-logs/${id}`, method: 'get' })
}
