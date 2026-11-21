import request from '@/utils/request'
import { internalMenus } from '@/mock/menus'
import type { AuthInfo } from '@/types/menu'

export interface LoginPayload {
  username: string
  password: string
}

const useMock = import.meta.env.VITE_USE_MOCK === 'true'

/** 登录。骨架阶段无后端时走本地 mock，返回固定 token。 */
export async function login(payload: LoginPayload): Promise<{ token: string }> {
  if (useMock) {
    if (!payload.username || !payload.password) {
      throw { code: 400, message: '请输入账号和密码' }
    }
    return { token: `mock-token-${Date.now()}` }
  }
  return request({ url: '/v1/auth/login', method: 'post', data: payload })
}

/** 获取当前用户信息 + 菜单树 + 权限。骨架阶段返回内部端 mock 菜单。 */
export async function getAuthInfo(): Promise<AuthInfo> {
  if (useMock) {
    return {
      user: { id: 1, username: 'admin', realName: '管理员', orgId: 1, orgName: '总部' },
      roles: ['admin'],
      perms: [],
      menus: internalMenus
    }
  }
  return request({ url: '/v1/auth/info', method: 'get' })
}
