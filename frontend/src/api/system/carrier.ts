import request from '@/utils/request'
import type { CarrierPage, CarrierQuery, CarrierRecord, CarrierSavePayload } from '@/types/carrier'

export function getCarrierPage(query: CarrierQuery): Promise<CarrierPage> {
  return request({ url: '/v1/system/carriers/page', method: 'get', params: query })
}

export function getCarrierOptions(): Promise<CarrierRecord[]> {
  return request({ url: '/v1/system/carriers/options', method: 'get' })
}

export function getCarrierDetail(id: number): Promise<CarrierRecord> {
  return request({ url: `/v1/system/carriers/${id}`, method: 'get' })
}

export function createCarrier(payload: CarrierSavePayload): Promise<number> {
  return request({ url: '/v1/system/carriers', method: 'post', data: payload })
}

export function updateCarrier(id: number, payload: CarrierSavePayload): Promise<void> {
  return request({ url: `/v1/system/carriers/${id}`, method: 'put', data: payload })
}

export function deleteCarrier(id: number): Promise<void> {
  return request({ url: `/v1/system/carriers/${id}`, method: 'delete' })
}
