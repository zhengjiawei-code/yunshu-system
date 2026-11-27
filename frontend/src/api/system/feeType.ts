import request from '@/utils/request'
import type { FeeTypePage } from '@/types/transport'

/** 费用类型统一列表（#26）：收入 4 项只读虚拟行 + sys_fee_type 配置行 */
export function getFeeTypePage(params: Record<string, unknown>) {
  return request<FeeTypePage>({ url: '/v1/system/fee-type/page', method: 'get', params })
}
