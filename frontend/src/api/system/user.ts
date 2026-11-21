import request from '@/utils/request'
import type {
  UserPage,
  UserPermInfo,
  UserQuery,
  UserRecord,
  UserSavePayload
} from '@/types/user'

/** GET /api/v1/system/users/page —— 服务端筛选 + 排序 + 分页 */
export function getUserPage(query: UserQuery): Promise<UserPage> {
  return request({ url: '/v1/system/users/page', method: 'get', params: query })
}

/** GET /api/v1/system/users/{id} */
export function getUserDetail(id: number): Promise<UserRecord> {
  return request({ url: `/v1/system/users/${id}`, method: 'get' })
}

/** POST /api/v1/system/users */
export function createUser(payload: UserSavePayload): Promise<number> {
  return request({ url: '/v1/system/users', method: 'post', data: payload })
}

/** PUT /api/v1/system/users/{id} */
export function updateUser(id: number, payload: UserSavePayload): Promise<void> {
  return request({ url: `/v1/system/users/${id}`, method: 'put', data: payload })
}

/** DELETE /api/v1/system/users/{id} */
export function deleteUser(id: number): Promise<void> {
  return request({ url: `/v1/system/users/${id}`, method: 'delete' })
}

/** PUT /api/v1/system/users/{id}/status */
export function changeUserStatus(id: number, status: boolean): Promise<void> {
  return request({ url: `/v1/system/users/${id}/status`, method: 'put', data: { status } })
}

/** PUT /api/v1/system/users/{id}/reset-password —— 固定重置为 123456 */
export function resetUserPassword(id: number): Promise<void> {
  return request({ url: `/v1/system/users/${id}/reset-password`, method: 'put' })
}

/** GET /api/v1/system/users/{id}/perms */
export function getUserPerms(id: number): Promise<UserPermInfo> {
  return request({ url: `/v1/system/users/${id}/perms`, method: 'get' })
}

/** PUT /api/v1/system/users/{id}/perms —— 整体覆盖 */
export function saveUserPerms(id: number, grantedIds: number[], deniedIds: number[]): Promise<void> {
  return request({
    url: `/v1/system/users/${id}/perms`,
    method: 'put',
    data: { grantedIds, deniedIds }
  })
}
