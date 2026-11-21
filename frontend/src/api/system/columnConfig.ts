import request from '@/utils/request'

/** 列配置回读结果；config 为空表示未自定义，使用页面默认 */
export interface ColumnConfigResult {
  pageKey: string
  config?: string
  customized: boolean
  updateTime?: string
}

export function getColumnConfig(pageKey: string): Promise<ColumnConfigResult> {
  return request({ url: `/v1/system/column-configs/${pageKey}`, method: 'get' })
}

export function saveColumnConfig(pageKey: string, config: string): Promise<ColumnConfigResult> {
  return request({
    url: `/v1/system/column-configs/${pageKey}`,
    method: 'put',
    data: { config }
  })
}

export function resetColumnConfig(pageKey: string): Promise<void> {
  return request({ url: `/v1/system/column-configs/${pageKey}`, method: 'delete' })
}
