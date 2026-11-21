/** 后端统一返回结构 */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

/** 分页返回结构（合计与 data/total 同级，规范 3.6） */
export interface PageResult<T> {
  data: T[]
  total: number
  page: number
  pageSize: number
  summary?: Record<string, number | string>
}
