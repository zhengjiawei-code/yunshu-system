import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResult } from '@/types/api'

/**
 * 统一请求层（规范 6）
 * - baseURL 取 VITE_API_BASE，开发环境经 vite proxy 转发到后端
 * - 自动注入 Bearer Token
 * - 参数归一化：剔除空值、数组转逗号分隔
 * - 业务码非 200 统一提示并 reject，401 清空登录态
 * - 页面禁止直接使用 axios
 */
const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

function normalizeParams(params?: Record<string, unknown>) {
  if (!params) return params
  const result: Record<string, unknown> = {}
  Object.entries(params).forEach(([key, value]) => {
    if (value === '' || value === undefined || value === null) return
    if (Array.isArray(value)) {
      if (value.length) result[key] = value.join(',')
      return
    }
    result[key] = value
  })
  return result
}

service.interceptors.request.use((config) => {
  const token = localStorage.getItem('yunshu_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  if (config.params) config.params = normalizeParams(config.params)
  return config
})

service.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResult
    // 文件流等非标准结构直接返回
    if (!body || typeof body.code === 'undefined') return response.data
    if (body.code === 200) return body.data
    ElMessage.error(body.message || '请求失败')
    return Promise.reject({ ...body, __toasted: true })
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      localStorage.removeItem('yunshu_token')
      ElMessage.error('登录已失效，请重新登录')
      if (location.pathname !== '/login') {
        location.href = `/login?redirect=${encodeURIComponent(location.pathname + location.search)}`
      }
      return Promise.reject(error)
    }
    const message = error.response?.data?.message || error.message || '网络异常'
    ElMessage.error(message)
    return Promise.reject({ code: status ?? -1, message, __toasted: true })
  }
)

export function request<T = unknown>(config: AxiosRequestConfig): Promise<T> {
  // 响应拦截器已经把 ApiResult 解包为 data，这里的类型收窄由调用方保证
  return service.request(config) as unknown as Promise<T>
}

export default request
